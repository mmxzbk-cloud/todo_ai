import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'

import { projectsApi } from '../api/projects.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { TaskCard } from '../components/TaskCard.tsx'
import { describeError, useTaskList } from '../hooks/useTasks.ts'
import { TASK_STATUSES, TASK_STATUS_LABELS } from '../types/task.ts'
import type { TaskListParams, TaskStatus } from '../types/task.ts'

const PAGE_SIZE = 20

const SELECT_CLASS =
  'rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm outline-none focus:border-slate-500'

/**
 * 任务列表：全部任务的检索入口。
 *
 * 检索条件在服务端做（GET /api/v1/tasks 的 status / projectId / q），前端不做二次过滤：
 * 分页总数必须与服务端口径一致，客户端自己过滤会把「共 N 条」变成错的。
 * 关键词输入防抖 300ms 才发请求——每敲一个字符打一次库没有意义。
 */
function TasksPage() {
  const [searchInput, setSearchInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<TaskStatus | ''>('')
  const [projectId, setProjectId] = useState('')
  const [page, setPage] = useState(0)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(searchInput.trim())
      setPage(0)
    }, 300)
    return () => window.clearTimeout(timer)
  }, [searchInput])

  const params: TaskListParams = {
    q: keyword === '' ? undefined : keyword,
    status: status === '' ? undefined : status,
    projectId: projectId === '' ? undefined : Number(projectId),
    page,
    size: PAGE_SIZE,
  }
  const list = useTaskList(params)

  // 归档项目下的任务仍要能被检索出来，所以取全部项目（含归档）做下拉选项。
  const projects = useQuery({ queryKey: queryKeys.projects, queryFn: () => projectsApi.list(true) })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    // 防抖已有 300ms，回车立即搜索只是把同一件事提前，这里拦截默认提交避免整页刷新。
    event.preventDefault()
    setKeyword(searchInput.trim())
    setPage(0)
  }

  const total = list.data?.total ?? 0
  const tasks = list.data?.items ?? []

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold">任务</h1>
        <p className="mt-1 text-sm text-slate-500">
          按关键词、状态与项目检索全部任务，包括已完成与已取消的。
        </p>
      </header>

      <section className="rounded-lg border border-slate-200 bg-white p-4">
        <form onSubmit={handleSubmit} className="flex flex-wrap items-center gap-2">
          <label htmlFor="task-search" className="sr-only">
            按标题关键词搜索
          </label>
          <input
            id="task-search"
            type="search"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="搜索任务标题…"
            className={`${SELECT_CLASS} min-w-56 flex-1`}
          />
          <label htmlFor="task-status" className="sr-only">
            按状态筛选
          </label>
          <select
            id="task-status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as TaskStatus | '')
              setPage(0)
            }}
            className={SELECT_CLASS}
          >
            <option value="">全部状态</option>
            {TASK_STATUSES.map((value) => (
              <option key={value} value={value}>
                {TASK_STATUS_LABELS[value]}
              </option>
            ))}
          </select>
          <label htmlFor="task-project" className="sr-only">
            按项目筛选
          </label>
          <select
            id="task-project"
            value={projectId}
            onChange={(event) => {
              setProjectId(event.target.value)
              setPage(0)
            }}
            className={SELECT_CLASS}
          >
            <option value="">全部项目</option>
            {(projects.data ?? []).map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
                {project.archivedAt != null ? '（已归档）' : ''}
              </option>
            ))}
          </select>
        </form>
      </section>

      {errorMessage && (
        <div
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700"
        >
          {errorMessage}
        </div>
      )}

      <section>
        <h2 className="mb-3 text-base font-medium">
          结果
          <span className="ml-2 text-sm font-normal text-slate-400">共 {total} 条</span>
        </h2>

        {list.isPending ? (
          <p className="text-sm text-slate-500">加载中…</p>
        ) : list.isError ? (
          <p role="alert" className="text-sm text-red-600">
            {describeError(list.error)}
          </p>
        ) : tasks.length === 0 ? (
          <p className="text-sm text-slate-500">没有符合条件的任务，换个关键词或清掉筛选试试。</p>
        ) : (
          <ul className="space-y-3">
            {tasks.map((task) => (
              <li key={task.id}>
                <TaskCard task={task} onError={setErrorMessage} />
              </li>
            ))}
          </ul>
        )}

        {list.data && total > PAGE_SIZE && (
          <nav aria-label="分页" className="mt-4 flex items-center justify-end gap-3 text-sm">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((value) => Math.max(value - 1, 0))}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              上一页
            </button>
            <span className="tabular-nums text-slate-500">
              第 {page + 1} 页 / 共 {Math.ceil(total / PAGE_SIZE)} 页
            </span>
            <button
              type="button"
              disabled={(page + 1) * PAGE_SIZE >= total}
              onClick={() => setPage((value) => value + 1)}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              下一页
            </button>
          </nav>
        )}
      </section>
    </div>
  )
}

export default TasksPage
