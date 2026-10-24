import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { useAiStatus } from '../hooks/useAi.ts'
import {
  describeError,
  errorCode,
  useRunningTimer,
  useStartTimer,
  useStopTimer,
} from '../hooks/useTasks.ts'
import type { TaskSummary } from '../types/task.ts'
import { PrioritySelect } from './PrioritySelect.tsx'
import { PrioritySuggestionPanel } from './PrioritySuggestionPanel.tsx'
import { StatusSelect } from './StatusSelect.tsx'
import { formatElapsed } from './timeFormat.ts'

interface TaskCardProps {
  task: TaskSummary
  /** 改状态、改优先级或计时的失败提示交给页面统一展示（一次只显示一条，不必在每张卡片上各报一次）。 */
  onError?: (message: string | null) => void
}

/** 本地「今天」的 ISO 日期。不能用 toISOString：它按 UTC 取日期，东八区晚上会差一天。 */
function localToday(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** 分钟数对人友好一点：90 分比 1 小时 30 分更难在扫视时比较。 */
function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} 分`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`
}

/**
 * 一张任务卡片：今日视图与看板共用。
 *
 * 版面固定三行，从上到下是「标记 → 内容 → 计时」：
 * 首行只放状态与优先级两个可编辑标记，扫一眼就知道这条在什么状态、多急；
 * 中间是标题与项目/截止日，占满宽度，标题不再被右侧的控件挤成竖排；
 * 末行只放计时读数与开始/结束按钮，计时是这条任务上唯一会每秒变化的东西，单独一行不会干扰阅读。
 *
 * 只依赖 TaskSummary 上已有的字段（含后端回填的项目名/项目色与标签），不自己再拉详情——
 * 列表里每张卡各发一次请求是典型的 N+1。
 */
export function TaskCard({ task, onError }: TaskCardProps) {
  const runningTimer = useRunningTimer()
  const startTimer = useStartTimer()
  const stopTimer = useStopTimer()
  // AI 总开关：关闭时轻量入口不渲染、不发请求（AC 2.6）。多张卡片共用同一份缓存。
  const aiEnabled = useAiStatus()
  const [now, setNow] = useState(() => Date.now())

  const runningLog = runningTimer.data?.log ?? null
  const isRunningSelf = runningTimer.data?.task.id === task.id
  // 服务端同一时刻只允许一条计时，别人在跑时这个按钮点了必然 409，干脆不给点。
  const timerDisabled = startTimer.isPending || runningLog !== null

  // 只有本任务在跑时才走秒：别的任务在跑时这张卡片上没有会变的数字，没必要每秒重渲染一次。
  // 不在 effect 里同步 setNow：卡片挂载时刻一定早于（或等于）计时开始时刻，
  // formatElapsed 会把负差夹成 0，第一个 tick 到来后读数就是对的。
  useEffect(() => {
    if (!isRunningSelf) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [isRunningSelf])

  const today = localToday()
  // 已完成/已取消的任务不再算逾期：它们没有「还没做完」这个含义了。
  const isOverdue =
    task.dueOn !== null &&
    task.dueOn < today &&
    task.status !== 'DONE' &&
    task.status !== 'CANCELLED'

  function handleStop() {
    if (!runningLog) return
    onError?.(null)
    stopTimer.mutate(
      { taskId: task.id, logId: runningLog.id, markDone: false },
      {
        onError: (error) => {
          onError?.(
            errorCode(error) === 'TIMER_NOT_RUNNING'
              ? '这条计时已经结束了'
              : describeError(error),
          )
        },
      },
    )
  }

  return (
    <article className="rounded-lg border border-slate-200 bg-white p-4">
      {/* 计时状态要能播报（设计约定）。这个节点常驻 DOM：临时插进去的 live region
          不一定被读屏捕捉到，只有「已存在的区域里文字变了」才稳。 */}
      <span className="sr-only" aria-live="polite">
        {isRunningSelf ? '正在计时' : ''}
      </span>

      {/* 首行：状态与优先级两个标记（都可就地编辑），开关打开时再加一个轻量的 AI 建议入口。 */}
      <div className="flex flex-wrap items-center gap-2">
        <StatusSelect task={task} onError={onError} />
        <PrioritySelect task={task} onError={onError} />
        {aiEnabled && <PrioritySuggestionPanel taskId={task.id} variant="compact" onError={onError} />}
      </div>

      {/* 中间：任务内容本身。 */}
      <div className="mt-2 min-w-0">
        <Link
          to={`/tasks/${task.id}`}
          className={`text-sm font-medium break-words hover:underline ${
            task.status === 'DONE' ? 'text-slate-400 line-through' : 'text-slate-900'
          }`}
        >
          {task.title}
        </Link>

        {(task.projectName || task.tags.length > 0 || task.dueOn) && (
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
            {task.projectName && (
              <span className="flex items-center gap-1.5">
                {task.projectColor && (
                  <span
                    aria-hidden="true"
                    className="inline-block h-2 w-2 shrink-0 rounded-full border border-slate-300"
                    style={{ backgroundColor: task.projectColor }}
                  />
                )}
                {task.projectName}
              </span>
            )}
            {task.tags.length > 0 && (
              <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
                {task.tags.map((tag) => (
                  <span key={tag.id} className="flex items-center gap-1">
                    {tag.color && (
                      <span
                        aria-hidden="true"
                        className="inline-block h-2 w-2 shrink-0 rounded-full border border-slate-300"
                        style={{ backgroundColor: tag.color }}
                      />
                    )}
                    {tag.name}
                  </span>
                ))}
              </span>
            )}
            {task.dueOn && (
              <span className={isOverdue ? 'font-medium text-rose-600' : undefined}>
                {isOverdue ? `已逾期 ${task.dueOn}` : `截止 ${task.dueOn}`}
              </span>
            )}
          </div>
        )}
      </div>

      {/* 末行：只有计时信息与开始/结束入口。 */}
      <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-2">
        <span className="flex flex-wrap items-center gap-x-2 text-xs text-slate-500">
          {isRunningSelf && runningLog && (
            <span className="tabular-nums text-slate-700">
              计时中 {formatElapsed(runningLog.startedAt, now)}
            </span>
          )}
          <span>
            实际 {formatMinutes(task.actualMinutes)}
            {task.estimatedMinutes === null ? '' : ` / 预计 ${formatMinutes(task.estimatedMinutes)}`}
          </span>
        </span>

        {isRunningSelf && runningLog ? (
          <button
            type="button"
            disabled={stopTimer.isPending}
            onClick={handleStop}
            className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            {stopTimer.isPending ? '结束中…' : '结束计时'}
          </button>
        ) : (
          <button
            type="button"
            disabled={timerDisabled}
            title={runningLog !== null ? '已有其它任务在计时' : undefined}
            onClick={() => {
              onError?.(null)
              startTimer.mutate(task.id, {
                onError: (error) => {
                  onError?.(
                    errorCode(error) === 'TIMER_ALREADY_RUNNING'
                      ? '已有计时在进行中'
                      : describeError(error),
                  )
                },
              })
            }}
            className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            {startTimer.isPending ? '开始中…' : '开始计时'}
          </button>
        )}
      </div>
    </article>
  )
}
