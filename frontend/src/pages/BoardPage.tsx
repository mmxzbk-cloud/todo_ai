import { useState } from 'react'

import { TaskCard } from '../components/TaskCard.tsx'
import { describeError, useBoard, useTaskList } from '../hooks/useTasks.ts'
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
  const [showCancelled, setShowCancelled] = useState(false)

  // 收起时不发请求：取消的任务不是进看板就要看的东西，展开才拉。
  // 一次拉 50 条（后端上限 100）足够覆盖个人积压，超出的用总数提示说清，不做分页。
  const cancelled = useTaskList({ status: 'CANCELLED', size: 50 }, { enabled: showCancelled })

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
          用卡片上的下拉改状态；已取消的任务不上看板，需要时在下方「已取消」区展开查看。
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

      {/* 已取消的任务不在四列里（产品约定），但也不能没有入口：默认收起，展开才拉列表。
          卡片复用 TaskCard，行内下拉就能把取消的任务恢复回其他状态。 */}
      <section className="rounded-lg border border-slate-200 bg-slate-100/60 p-3">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-sm font-medium text-slate-700">已取消</h2>
          <button
            type="button"
            aria-expanded={showCancelled}
            aria-controls="cancelled-section"
            onClick={() => setShowCancelled((value) => !value)}
            className="rounded-md px-2 py-1 text-xs text-slate-600 hover:bg-slate-200 hover:text-slate-900"
          >
            {showCancelled ? '收起' : '展开'}
          </button>
        </div>

        <div id="cancelled-section">
          {showCancelled &&
            (cancelled.isPending ? (
              <p className="px-1 pt-3 text-sm text-slate-400">加载中…</p>
            ) : cancelled.isError ? (
              <p role="alert" className="px-1 pt-3 text-sm text-red-600">
                {describeError(cancelled.error)}
              </p>
            ) : cancelled.data.items.length === 0 ? (
              <p className="px-1 pt-3 text-sm text-slate-400">没有已取消的任务</p>
            ) : (
              <div className="pt-3">
                <p className="mb-3 px-1 text-xs text-slate-500">
                  共 {cancelled.data.total} 条
                  {cancelled.data.total > cancelled.data.items.length
                    ? `，仅显示前 ${cancelled.data.items.length} 条`
                    : ''}
                  ；用卡片上的下拉可恢复到其他状态。
                </p>
                <div className="space-y-3">
                  {cancelled.data.items.map((task) => (
                    <TaskCard key={task.id} task={task} onError={setErrorMessage} />
                  ))}
                </div>
              </div>
            ))}
        </div>
      </section>
    </div>
  )
}

export default BoardPage
