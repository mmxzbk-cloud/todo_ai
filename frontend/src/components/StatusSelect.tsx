import { describeError, useChangeTaskStatus } from '../hooks/useTasks.ts'
import { TASK_STATUSES, TASK_STATUS_LABELS } from '../types/task.ts'
import type { TaskStatus, TaskSummary } from '../types/task.ts'

interface StatusSelectProps {
  task: TaskSummary
  /** 失败时把提示交给页面统一展示；传 null 表示清掉上一条。 */
  onError?: (message: string | null) => void
}

/**
 * 行内改状态下拉。
 *
 * 看板不做拖拽（设计约定：拖拽必须另配键盘可达的入口，与其做两套不如只做下拉这一套），
 * 所以这个下拉就是改状态的唯一入口，label 必须存在，只是视觉上藏起来。
 *
 * 失败回滚靠缓存：value 取自 task.status（查询缓存），onError 里把缓存写回快照后
 * 这个受控 select 自己就退回服务端状态了，组件里不需要再存一份本地值。
 */
export function StatusSelect({ task, onError }: StatusSelectProps) {
  const changeStatus = useChangeTaskStatus()
  const selectId = `task-status-${task.id}`

  return (
    <>
      <label htmlFor={selectId} className="sr-only">
        {`更改任务「${task.title}」的状态`}
      </label>
      <select
        id={selectId}
        value={task.status}
        disabled={changeStatus.isPending}
        onChange={(event) => {
          onError?.(null)
          changeStatus.mutate(
            { id: task.id, status: event.target.value as TaskStatus },
            { onError: (error) => onError?.(describeError(error)) },
          )
        }}
        className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm outline-none focus:border-slate-500 disabled:opacity-50"
      >
        {TASK_STATUSES.map((value) => (
          <option key={value} value={value}>
            {TASK_STATUS_LABELS[value]}
          </option>
        ))}
      </select>
    </>
  )
}
