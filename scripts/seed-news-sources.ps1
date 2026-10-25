#requires -Version 5.1
<#
.SYNOPSIS
    为资讯订阅来源（来源管理）初始化预置种子数据。

.DESCRIPTION
    面向开发库（H2 文件库 backend/data/aiassistant）的数据初始化，可重复执行：
    给每个「活跃且尚未拥有同名来源」的用户插入 5 个预置 RSS 来源。
    预置来源均已实测可达且 robots 放行（2026-10-24），采集管道可直接抓取。

    H2 文件库被后端独占，脚本会先停掉占用 8080 端口的后端进程；执行完不自动拉起，
    恢复服务请运行 .\scripts\restart-dev.ps1 -Target backend。

    幂等语义：按「用户 + 来源名」判重，重复执行不产生重复行；
    用户手动删掉的种子来源再执行本脚本会被补回（回退用 -Clean，勿与补种混用）。

.PARAMETER Clean
    回退：按 URL 精确删除预置来源（用户自建或其他来源不受影响）。

.EXAMPLE
    .\scripts\seed-news-sources.ps1

.EXAMPLE
    .\scripts\seed-news-sources.ps1 -Clean
#>
[CmdletBinding()]
param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$dbUrl = "jdbc:h2:file:" + ($repoRoot -replace '\\', '/') + "/backend/data/aiassistant"

# 预置来源，默认分类与 PRD「AI 与高校课程考核」主题对齐：
# 芥末堆（教育行业资讯）归 POLICY，极客公园归 HOTSPOT，其余科技媒体归 TECH。
$seedSources = @(
    @{ Name = '少数派';   Url = 'https://sspai.com/feed'            ; Category = 'TECH' },
    @{ Name = '爱范儿';   Url = 'https://www.ifanr.com/feed'        ; Category = 'TECH' },
    @{ Name = '雷锋网';   Url = 'https://www.leiphone.com/feed'     ; Category = 'TECH' },
    @{ Name = '极客公园'; Url = 'https://www.geekpark.net/rss'      ; Category = 'HOTSPOT' },
    @{ Name = '芥末堆';   Url = 'https://www.jiemodui.com/rss.xml'  ; Category = 'POLICY' }
)

# ---- 定位 H2 jar（后端依赖同版本，取本机 Maven 仓库最新一份）----
$h2Repo = Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2'
if (-not (Test-Path $h2Repo)) {
    throw "找不到 H2 依赖目录：$h2Repo（先构建过一次后端即可生成）"
}
$h2Jar = Get-ChildItem $h2Repo -Directory | ForEach-Object {
    Get-ChildItem $_.FullName -Filter 'h2-*.jar' | Where-Object { $_.Name -notmatch 'sources|javadoc' }
} | Sort-Object Name -Descending | Select-Object -First 1
if (-not $h2Jar) { throw "Maven 仓库里没有 h2-*.jar：$h2Repo" }

# ---- 停后端释放 H2 文件锁（与 restart-dev.ps1 同一套按端口停进程逻辑）----
$connections = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
foreach ($conn in $connections) {
    $processId = $conn.OwningProcess
    if ($processId -eq 0 -or $processId -eq 4) {
        throw "端口 8080 被系统进程占用（PID $processId），拒绝结束，请手动处理"
    }
    $processName = (Get-Process -Id $processId -ErrorAction SilentlyContinue).ProcessName
    Write-Host "[seed] 停止后端 PID $processId ($processName)，释放 H2 文件锁" -ForegroundColor Yellow
    & taskkill.exe /PID $processId /T /F 2>&1 | Out-Null
}
if ($connections) {
    $deadline = (Get-Date).AddSeconds(15)
    while ((Get-Date) -lt $deadline -and (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
        Start-Sleep -Milliseconds 300
    }
}

# ---- 生成 SQL：幂等按「用户 + 来源名」判重；回退按 URL 精确删除 ----
$statements = if ($Clean) {
    $urls = ($seedSources | ForEach-Object { "'" + $_.Url + "'" }) -join ', '
    @("DELETE FROM info_sources WHERE url IN ($urls);")
} else {
    foreach ($source in $seedSources) {
        @"
INSERT INTO info_sources
    (owner_id, name, type, url, parse_rule, default_category,
     enabled, consecutive_failures, auto_disabled, created_at, updated_at)
SELECT u.id, '$($source.Name)', 'RSS', '$($source.Url)', NULL, '$($source.Category)',
       TRUE, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u
WHERE u.deleted_at IS NULL
  AND u.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM info_sources s
                  WHERE s.owner_id = u.id AND s.name = '$($source.Name)');
"@
    }
}
$sqlFile = Join-Path $env:TEMP ("ai-news-seed-{0}.sql" -f $PID)
# 无 BOM 的 UTF-8：PS 5.1 的 Out-File -Encoding utf8 会带 BOM，H2 RunScript 读到 BOM 会报语法错
[System.IO.File]::WriteAllText($sqlFile, ($statements -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))

try {
    # file.encoding 指定 UTF-8：SQL 文件里的中文来源名按 UTF-8 读入
    & java -Dfile.encoding=UTF-8 -cp $h2Jar.FullName org.h2.tools.RunScript `
        -url $dbUrl -user sa -script $sqlFile
    if ($LASTEXITCODE -ne 0) { throw "RunScript 退出码 $LASTEXITCODE，种子执行失败" }

    $mode = if ($Clean) { '回退' } else { '种子' }
    Write-Host "[seed] $mode 完成，当前预置来源分布：" -ForegroundColor Green
    # 报表查询：来源名是从库里读出的中文，切到 UTF-8 输出避免乱码
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $reportSql = "SELECT u.username AS USERNAME, s.name AS NAME, s.default_category AS CATEGORY " +
        "FROM info_sources s JOIN users u ON u.id = s.owner_id " +
        "WHERE s.url IN ($( ($seedSources | ForEach-Object { "'" + $_.Url + "'" }) -join ', ' )) " +
        "ORDER BY u.username, s.name"
    & java -Dfile.encoding=UTF-8 -cp $h2Jar.FullName org.h2.tools.Shell `
        -url $dbUrl -user sa -sql $reportSql
}
finally {
    Remove-Item $sqlFile -ErrorAction SilentlyContinue
}

if (-not $Clean) {
    Write-Host '[seed] 服务未自动拉起，恢复请运行：.\scripts\restart-dev.ps1 -Target backend' -ForegroundColor Cyan
}
