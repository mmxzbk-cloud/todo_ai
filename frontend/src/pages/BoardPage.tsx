import { useState } from 'react'

import { TaskCard } from '../components/TaskCard.tsx'
import { describeError, useBoard } from '../hooks/useTasks.ts'
import { BOARD_STATUSES, TASK_STATUS_LABELS } from '../types/task.ts'
import type { BoardColumn } from '../types/task.ts'

/**
 * 看板：固定四列，不做拖拽。
 *
 * 改状态一律走卡片上的行内下拉（键盘可达）。拖拽必须再配一套键盘入口才算可用，
 * 与其做两套入口、两处维护，不如只留一套谁都能用的。
 */
function BoardPage() {
  const board = useBoard()
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // 服务端保证四列都返回；这里再兜一层是因为「少一列」在界面上表现为整块消失而不会报错，
  // 与其等有人发现，不如就地补成空列。
  const columns: BoardColumn[] = BOARD_STATUSES.map(
    (status) => board.data?.find((column) => column.status === status) ?? { status, tasks: [] },
  )

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold">看板</h1>
        <p className="mt-1 text-sm text-slate-500">
          用卡片上的下拉改状态；已取消的任务不上看板，要去列表或详情里看。
        </p>
      </header>

      {errorMessage && (
        <div
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700"
        >
          {errorMessage}
        </div>
      )}

      {board.isPending ? (
        <p className="text-sm text-slate-500">加载中…</p>
      ) : board.isError ? (
        <p role="alert" className="text-sm text-red-600">
          {describeError(board.error)}
        </p>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {columns.map((column) => (
            <section
              key={column.status}
              className="rounded-lg border border-slate-200 bg-slate-100/60 p-3"
            >
              <h2 className="mb-3 flex items-center justify-between text-sm font-medium text-slate-700">
                {TASK_STATUS_LABELS[column.status]}
                <span className="text-xs font-normal text-slate-500">{column.tasks.length}</span>
              </h2>

              {column.tasks.length === 0 ? (
                <p className="px-1 text-sm text-slate-400">暂无</p>
              ) : (
                <div className="space-y-3">
                  {column.tasks.map((task) => (
                    <TaskCard key={task.id} task={task} onError={setErrorMessage} />
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>
      )}
    </div>
  )
}

export default BoardPage
