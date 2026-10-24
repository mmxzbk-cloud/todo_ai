#requires -Version 5.1
<#
.SYNOPSIS
    重启本地开发服务：先停止占用端口的进程，再启动前后端。

.DESCRIPTION
    后端端口 8080（backend/src/main/resources/application.yml），前端端口 5173（Vite 默认）。
    停止时按端口找出监听进程，并结束整棵进程树，避免 Maven / Vite / JVM 残留继续占用端口或 H2 文件锁。

.PARAMETER Target
    重启目标：all（默认，前后端都重启）、backend、frontend。

.EXAMPLE
    .\scripts\restart-dev.ps1

.EXAMPLE
    .\scripts\restart-dev.ps1 -Target backend
#>
[CmdletBinding()]
param(
    [ValidateSet('all', 'backend', 'frontend')]
    [string]$Target = 'all'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$servicePorts = @{
    backend  = 8080
    frontend = 5173
}

$targets = switch ($Target) {
    'all' { @('backend', 'frontend') }
    default { @($Target) }
}

function Get-ListeningProcessId {
    param([int]$Port)

    try {
        $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
        if ($connections) {
            return @($connections | Select-Object -ExpandProperty OwningProcess -Unique)
        }
        return @()
    }
    catch {
        # 没有 NetTCPIP 模块时退回 netstat，输出形如：
        #   TCP    0.0.0.0:8080    0.0.0.0:0    LISTENING    24360
        $processIds = @()
        foreach ($line in (netstat -ano -p tcp)) {
            if ($line -match 'LISTENING' -and $line -match ":$Port\s") {
                $lastField = ($line.Trim() -split '\s+')[-1]
                if ($lastField -match '^\d+$') {
                    $processIds += [int]$lastField
                }
            }
        }
        return @($processIds | Sort-Object -Unique)
    }
}

function Wait-PortReleased {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 15
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Get-ListeningProcessId -Port $Port)) {
            return $true
        }
        Start-Sleep -Milliseconds 300
    }
    return -not (Get-ListeningProcessId -Port $Port)
}

function Stop-ServiceOnPort {
    param(
        [string]$Name,
        [int]$Port
    )

    $processIds = @(Get-ListeningProcessId -Port $Port)
    if ($processIds.Count -eq 0) {
        Write-Host "[$Name] 端口 $Port 未被占用，跳过停止" -ForegroundColor DarkGray
        return
    }

    foreach ($processId in $processIds) {
        if ($processId -eq 0 -or $processId -eq 4) {
            throw "[$Name] 端口 $Port 被系统进程占用，拒绝结束，请手动处理"
        }

        $processName = try {
            (Get-Process -Id $processId -ErrorAction Stop).ProcessName
        }
        catch {
            'unknown'
        }

        Write-Host "[$Name] 停止 PID $processId ($processName)，端口 $Port" -ForegroundColor Yellow
        # /T 连同子进程一起结束，避免 Maven、Vite 的父进程残留
        & taskkill.exe /PID $processId /T /F 2>&1 | Out-Null
    }

    if (-not (Wait-PortReleased -Port $Port)) {
        throw "[$Name] 端口 $Port 在 15 秒内仍未释放，请手动检查占用进程"
    }

    Write-Host "[$Name] 端口 $Port 已释放" -ForegroundColor Green
}

foreach ($service in $targets) {
    Stop-ServiceOnPort -Name $service -Port $servicePorts[$service]
}

# 给 H2 文件锁、端口 TIME_WAIT 一点回收时间
Start-Sleep -Milliseconds 500

if (-not (Get-Command pnpm.cmd -ErrorAction SilentlyContinue)) {
    throw '找不到 pnpm.cmd，请确认已安装 pnpm 并在 PATH 中'
}

$startScript = switch ($Target) {
    'backend' { 'dev:backend' }
    'frontend' { 'dev:frontend' }
    default { 'dev' }
}

Write-Host "[restart] 启动 pnpm $startScript" -ForegroundColor Cyan
& pnpm.cmd $startScript
exit $LASTEXITCODE
