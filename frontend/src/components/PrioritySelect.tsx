import { describeError, useChangeTaskPriority } from '../hooks/useTasks.ts'
import { PRIORITIES, PRIORITY_LABELS } from '../types/task.ts'
import type { Priority, TaskSummary } from '../types/task.ts'

interface PrioritySelectProps {
  task: TaskSummary
  /** 失败时把提示交给页面统一展示；传 null 表示清掉上一条。 */
  onError?: (message: string | null) => void
}

/** 与只读徽标同一套配色：改成下拉之后仍然是「一眼能扫出色阶」的标记，而不是普通输入框。 */
const PRIORITY_BADGE_CLASSES: Record<Priority, string> = {
  P0: 'border-rose-200 bg-rose-50 text-rose-700',
  P1: 'border-amber-200 bg-amber-50 text-amber-700',
  P2: 'border-sky-200 bg-sky-50 text-sky-700',
  P3: 'border-slate-200 bg-slate-50 text-slate-600',
}

/**
 * 行内改优先级。与 StatusSelect 同构：label 视觉隐藏但存在（读屏要知道这是改哪条任务的优先级），
 * 受控值取自查询缓存，失败时 hook 会重取，下拉自己就退回服务端状态。
 */
export function PrioritySelect({ task, onError }: PrioritySelectProps) {
  const changePriority = useChangeTaskPriority()
  const selectId = `task-priority-${task.id}`

  return (
    <>
      <label htmlFor={selectId} className="sr-only">
        {`更改任务「${task.title}」的优先级`}
      </label>
      <select
        id={selectId}
        value={task.priority}
        disabled={changePriority.isPending}
        onChange={(event) => {
          onError?.(null)
          changePriority.mutate(
            { id: task.id, priority: event.target.value as Priority },
            { onError: (error) => onError?.(describeError(error)) },
          )
        }}
        className={`rounded border px-1.5 py-1 text-xs font-medium outline-none focus:border-slate-500 disabled:opacity-50 ${PRIORITY_BADGE_CLASSES[task.priority]}`}
      >
        {PRIORITIES.map((value) => (
          <option key={value} value={value}>
            {PRIORITY_LABELS[value]}
          </option>
        ))}
      </select>
    </>
  )
}
