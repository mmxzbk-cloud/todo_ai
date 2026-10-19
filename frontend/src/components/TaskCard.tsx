import { Link } from 'react-router-dom'

import { describeError, errorCode, useRunningTimer, useStartTimer } from '../hooks/useTasks.ts'
import { PRIORITY_LABELS } from '../types/task.ts'
import type { Priority, TaskSummary } from '../types/task.ts'
import { StatusSelect } from './StatusSelect.tsx'

interface TaskCardProps {
  task: TaskSummary
  /** 改状态或开始计时的失败提示交给页面统一展示（一次只显示一条，不必在每张卡片上各报一次）。 */
  onError?: (message: string | null) => void
}

const PRIORITY_BADGE_CLASSES: Record<Priority, string> = {
  P0: 'border-rose-200 bg-rose-50 text-rose-700',
  P1: 'border-amber-200 bg-amber-50 text-amber-700',
  P2: 'border-sky-200 bg-sky-50 text-sky-700',
  P3: 'border-slate-200 bg-slate-50 text-slate-600',
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
 * 只依赖 TaskSummary 上已有的字段（含后端回填的 projectName），不自己再拉详情——
 * 列表里每张卡各发一次请求是典型的 N+1。
 */
export function TaskCard({ task, onError }: TaskCardProps) {
  const runningTimer = useRunningTimer()
  const startTimer = useStartTimer()

  const runningLog = runningTimer.data?.log ?? null
  const isRunningSelf = runningTimer.data?.task.id === task.id
  // 服务端同一时刻只允许一条计时，别人在跑时这个按钮点了必然 409，干脆不给点。
  const timerDisabled = startTimer.isPending || runningLog !== null

  const today = localToday()
  // 已完成/已取消的任务不再算逾期：它们没有「还没做完」这个含义了。
  const isOverdue =
    task.dueOn !== null &&
    task.dueOn < today &&
    task.status !== 'DONE' &&
    task.status !== 'CANCELLED'

  return (
    <article className="rounded-lg border border-slate-200 bg-white p-4">
      {/* 计时状态要能播报（设计约定）。这个节点常驻 DOM：临时插进去的 live region
          不一定被读屏捕捉到，只有「已存在的区域里文字变了」才稳。 */}
      <span className="sr-only" aria-live="polite">
        {isRunningSelf ? '正在计时' : ''}
      </span>

      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <Link
            to={`/tasks/${task.id}`}
            className={`text-sm font-medium hover:underline ${
              task.status === 'DONE' ? 'text-slate-400 line-through' : 'text-slate-900'
            }`}
          >
            {task.title}
          </Link>

          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
            {task.projectName && <span>{task.projectName}</span>}
            {task.dueOn && (
              <span className={isOverdue ? 'font-medium text-rose-600' : undefined}>
                {isOverdue ? `已逾期 ${task.dueOn}` : `截止 ${task.dueOn}`}
              </span>
            )}
            <span>
              实际 {formatMinutes(task.actualMinutes)}
              {task.estimatedMinutes === null
                ? ''
                : ` / 预计 ${formatMinutes(task.estimatedMinutes)}`}
            </span>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <span
            className={`rounded border px-1.5 py-0.5 text-xs font-medium ${PRIORITY_BADGE_CLASSES[task.priority]}`}
          >
            {PRIORITY_LABELS[task.priority]}
          </span>
          <StatusSelect task={task} onError={onError} />
        </div>
      </div>

      <div className="mt-2 flex items-center gap-2">
        <button
          type="button"
          disabled={timerDisabled}
          title={runningLog !== null && !isRunningSelf ? '已有其它任务在计时' : undefined}
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
          {runningLog !== null ? '计时中…' : startTimer.isPending ? '开始中…' : '开始计时'}
        </button>
      </div>
    </article>
  )
}
