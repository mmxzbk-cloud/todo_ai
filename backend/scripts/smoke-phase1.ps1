# 阶段一主线冒烟脚本
#
# 用途：每次改动后端后重跑一遍，确认「登录 → 录入 → 计时 → 改状态到完成 → 详情看到耗时与变更历史」这条链路仍然通。
# 前置：后端已在 http://localhost:8080 运行；跑之前不需要清库（脚本用时间戳生成唯一用户名，可反复执行）。
#
# 用法：powershell -File backend\scripts\smoke-phase1.ps1

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api/v1'
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$user1 = "smoke_a_$stamp"
$user2 = "smoke_b_$stamp"
$password = 'SmokePass123'

$script:pass = 0
$script:fail = 0

function Check([string]$name, [bool]$condition, $actual) {
	if ($condition) { $script:pass++; Write-Output "PASS  $name" }
	else { $script:fail++; Write-Output "FAIL  $name  (实际: $actual)" }
}

function Invoke-Api {
	param(
		[string]$Method,
		[string]$Path,
		$Body,
		[string]$Token
	)
	$headers = @{}
	if ($Token) { $headers['Authorization'] = "Bearer $Token" }
	$params = @{ Method = $Method; Uri = "$base$Path"; Headers = $headers }
	if ($null -ne $Body) {
		$params['ContentType'] = 'application/json'
		$params['Body'] = ($Body | ConvertTo-Json -Depth 5 -Compress)
	}
	Invoke-RestMethod @params
}

# 调用并返回原始状态码 + 错误体 code，用于断言 401/404/409 这类失败路径
function Invoke-ApiExpectFailure {
	param([string]$Method, [string]$Path, $Body, [string]$Token)
	$headers = @{}
	if ($Token) { $headers['Authorization'] = "Bearer $Token" }
	$params = @{ Method = $Method; Uri = "$base$Path"; Headers = $headers }
	if ($null -ne $Body) {
		$params['ContentType'] = 'application/json'
		$params['Body'] = ($Body | ConvertTo-Json -Depth 5)
	}
	try {
		Invoke-RestMethod @params | Out-Null
		return @{ status = 200; code = $null }
	} catch {
		$status = $_.Exception.Response.StatusCode.value__
		$code = $null
		try { $code = ($_.ErrorDetails.Message | ConvertFrom-Json).code } catch { }
		return @{ status = $status; code = $code }
	}
}

Write-Output "=== 阶段一主线冒烟 ($stamp) ==="

# --- 1. 注册与登录 ---
$u1 = Invoke-Api -Method Post -Path '/auth/register' -Body @{
	username = $user1; password = $password; displayName = '冒烟甲'; email = "$user1@example.com"
}
Check '注册返回用户名与默认时区' ($u1.username -eq $user1 -and $u1.timezone -eq 'Asia/Shanghai') $u1

$session = Invoke-Api -Method Post -Path '/auth/login' -Body @{ username = $user1; password = $password }
Check '登录返回两个令牌与用户信息' ($null -ne $session.accessToken -and $null -ne $session.refreshToken -and $session.user.username -eq $user1) $session

$token = $session.accessToken
$me = Invoke-Api -Method Get -Path '/auth/me' -Token $token
Check '/auth/me 用令牌解析出当前用户' ($me.username -eq $user1) $me

$u2 = Invoke-Api -Method Post -Path '/auth/register' -Body @{
	username = $user2; password = $password; displayName = '冒烟乙'
}
$session2 = Invoke-Api -Method Post -Path '/auth/login' -Body @{ username = $user2; password = $password }
$token2 = $session2.accessToken

# --- 2. 越权与未认证 ---
$anon = Invoke-ApiExpectFailure -Method Get -Path '/tasks'
Check '无令牌访问任务列表返回 401' ($anon.status -eq 401) $anon

# --- 3. 项目与任务 ---
$project = Invoke-Api -Method Post -Path '/projects' -Token $token -Body @{ name = "冒烟项目 $stamp" }
Check '新建项目返回 id' ($null -ne $project.id) $project

$task = Invoke-Api -Method Post -Path '/tasks' -Token $token -Body @{
	title = '冒烟：走完录入到完成'
	projectId = $project.id
	source = '冒烟脚本'
	importance = 'HIGH'
	urgency = 'HIGH'
	nextAction = '点开始计时'
	estimatedMinutes = 30
}
Check '重要性紧急都高时优先级推导为 P0' ($task.priority -eq 'P0') $task
Check '新任务默认状态 TODO' ($task.status -eq 'TODO') $task
Check '新建时实际耗时为 0' ($task.actualMinutes -eq 0) $task

$otherUserRead = Invoke-ApiExpectFailure -Method Get -Path "/tasks/$($task.id)" -Token $token2
Check '他人令牌读同一任务返回 404 且码为 TASK_NOT_FOUND' ($otherUserRead.status -eq 404 -and $otherUserRead.code -eq 'TASK_NOT_FOUND') $otherUserRead

# --- 4. 改状态 ---
$doing = Invoke-Api -Method Patch -Path "/tasks/$($task.id)/status" -Token $token -Body @{ status = 'DOING' }
Check '改状态为 DOING' ($doing.status -eq 'DOING') $doing

$stale = Invoke-ApiExpectFailure -Method Put -Path "/tasks/$($task.id)" -Token $token -Body @{
	title = '冒烟：走完录入到完成'; version = $task.version
}
Check '用过期 version 更新返回 409 VERSION_CONFLICT' ($stale.status -eq 409 -and $stale.code -eq 'VERSION_CONFLICT') $stale

# --- 5. 计时 ---
$log = Invoke-Api -Method Post -Path "/tasks/$($task.id)/timers" -Token $token
Check '开始计时返回一条未结束的流水' ($null -ne $log.id -and $null -eq $log.endedAt) $log

$running = Invoke-Api -Method Get -Path '/timers/running' -Token $token
Check '正在计时的记录可查询到，且指向同一任务' ($running.log.id -eq $log.id -and $running.task.id -eq $task.id) $running

$conflict = Invoke-ApiExpectFailure -Method Post -Path "/tasks/$($task.id)/timers" -Token $token
Check '重复开始计时返回 409 TIMER_ALREADY_RUNNING' ($conflict.status -eq 409 -and $conflict.code -eq 'TIMER_ALREADY_RUNNING') $conflict

$stopped = Invoke-Api -Method Post -Path "/tasks/$($task.id)/timers/$($log.id)/stop?markDone=true" -Token $token
Check '结束计时并标记完成：状态 DONE 且有完成时刻' ($stopped.task.status -eq 'DONE' -and $null -ne $stopped.task.completedAt) $stopped
Check '结束计时写了分钟数且至少 1 分钟' ($stopped.log.minutes -ge 1 -and $null -ne $stopped.log.endedAt) $stopped.log

$afterStop = Invoke-Api -Method Get -Path '/timers/running' -Token $token
Check '没有在跑的计时时返回空' ($null -eq $afterStop) $afterStop

# --- 6. 详情：耗时与变更历史 ---
$detail = Invoke-Api -Method Get -Path "/tasks/$($task.id)" -Token $token
$actions = @($detail.activities | ForEach-Object { $_.action })
Check '详情里的实际耗时已累加' ($detail.task.actualMinutes -eq $stopped.log.minutes) $detail.task.actualMinutes
Check '详情里有耗时流水' (@($detail.timeLogs).Count -ge 1) $detail.timeLogs
Check '变更历史含创建/状态变更/开始计时/结束计时' (
	($actions -contains 'CREATED') -and ($actions -contains 'STATUS_CHANGED') -and
	($actions -contains 'TIMER_STARTED') -and ($actions -contains 'TIMER_STOPPED')
) $actions
Check '详情里任务标题与原值一致' ($detail.task.title -eq '冒烟：走完录入到完成') $detail.task.title

# --- 7. 列表与看板 ---
$today = Invoke-Api -Method Get -Path '/tasks/today' -Token $token
Check '已完成的任务不出现在今日视图' (@($today | Where-Object { $_.id -eq $task.id }).Count -eq 0) @($today).Count

$board = Invoke-Api -Method Get -Path '/tasks/board' -Token $token
$doneColumn = $board | Where-Object { $_.status -eq 'DONE' }
Check '看板有固定四列' (@($board).Count -eq 4) @($board | ForEach-Object { $_.status })
Check '完成的任务落在 DONE 列' (@($doneColumn.tasks | Where-Object { $_.id -eq $task.id }).Count -eq 1) @($doneColumn.tasks).Count

$page = Invoke-Api -Method Get -Path '/tasks?page=0&size=10&status=DONE' -Token $token
Check '分页列表按状态筛选命中' ($page.total -ge 1 -and @($page.items).Count -ge 1) $page

# --- 8. 标签 ---
$tag = Invoke-Api -Method Post -Path '/tags' -Token $token -Body @{ name = "冒烟标签 $stamp" }
Check '新建标签返回 id' ($null -ne $tag.id) $tag
$tagged = Invoke-Api -Method Put -Path "/tasks/$($task.id)/tags" -Token $token -Body @{ tagIds = @($tag.id) }
Check '给任务关联标签后详情里能读到' (@($tagged.tags | Where-Object { $_.id -eq $tag.id }).Count -eq 1) $tagged.tags
$dupTag = Invoke-ApiExpectFailure -Method Post -Path '/tags' -Token $token -Body @{ name = "冒烟标签 $stamp" }
Check '同名标签返回 409 TAG_NAME_TAKEN' ($dupTag.status -eq 409 -and $dupTag.code -eq 'TAG_NAME_TAKEN') $dupTag

# --- 9. 登出后刷新令牌失效 ---
Invoke-Api -Method Post -Path '/auth/logout' -Token $token -Body @{ refreshToken = $session.refreshToken } | Out-Null
$afterLogout = Invoke-ApiExpectFailure -Method Post -Path '/auth/refresh' -Body @{ refreshToken = $session.refreshToken }
Check '登出后旧刷新令牌不能再用' ($afterLogout.status -eq 401) $afterLogout

Write-Output "=== 通过 $($script:pass) 项，失败 $($script:fail) 项 ==="
if ($script:fail -gt 0) { exit 1 }
