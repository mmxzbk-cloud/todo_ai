import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'

import { ApiError } from '../api/client.ts'
import { projectsApi } from '../api/projects.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { tagsApi } from '../api/tags.ts'
import { tasksApi } from '../api/tasks.ts'
import { describeError, useCreateTask } from '../hooks/useTasks.ts'
import {
  IMPORTANCE_LABELS,
  IMPORTANCE_LEVELS,
  URGENCY_LABELS,
  URGENCY_LEVELS,
} from '../types/task.ts'
import type { Importance, Urgency } from '../types/task.ts'

/**
 * 快速录入：默认只填标题，其余字段收在「更多」里。
 *
 * 录入成本决定记录量。今日视图上摆一个七字段的表单，人会先在脑子里过一遍字段再决定要不要记，
 * 于是当场的琐事就漏掉了；所以可选项默认折叠，代价是牺牲了一点「填全」的自觉性。
 */
export function QuickTaskForm() {
  const create = useCreateTask()
  const [title, setTitle] = useState('')
  const [expanded, setExpanded] = useState(false)
  const [dueOn, setDueOn] = useState('')
  const [importance, setImportance] = useState('')
  const [urgency, setUrgency] = useState('')
  const [estimatedMinutes, setEstimatedMinutes] = useState('')
  // 项目用 '' 表示「未归属」，与详情页编辑表单的草稿约定一致。
  const [projectId, setProjectId] = useState('')
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([])
  // 标签在任务创建之后才绑得上（创建接口不收 tagIds），绑定失败时任务已经建好，
  // 用一条提示而不是把整次录入报成失败。
  const [tagNotice, setTagNotice] = useState<string | null>(null)

  // 与详情页共用同一个 ['projects'] / ['tags'] 查询键：staleTime 为 0，每次挂载都会按
  // 本处参数重取，不会把别处（含归档列表）的缓存当成未归档列表用。
  const projectsQuery = useQuery({ queryKey: queryKeys.projects, queryFn: () => projectsApi.list(false) })
  const tagsQuery = useQuery({ queryKey: queryKeys.tags, queryFn: tagsApi.list })

  const fieldErrors = create.error instanceof ApiError ? create.error.fieldErrors : {}
  const titleError = fieldErrors.title
  // title 的错误挂在输入框下；其余字段名（后端将来加的也算）与非字段错误归到表单级，
  // 免得出现「后端明明报了错，界面上一个字都没有」。
  const otherErrors = Object.entries(fieldErrors)
    .filter(([field]) => field !== 'title')
    .map(([field, message]) => ({ field, message }))
  const formError =
    create.error !== null && Object.keys(fieldErrors).length === 0 ? describeError(create.error) : null

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setTagNotice(null)
    // 提交那一刻把勾选固化下来：onSuccess 里表单已重置，再读 state 就是空数组了。
    const tagIds = selectedTagIds
    create.mutate(
      {
        title: title.trim(),
        // 可选项用 null 表示「用户没填」：后端对 text 类字段的 null 就是清空，新建时等价于不写。
        projectId: projectId === '' ? null : Number(projectId),
        dueOn: dueOn === '' ? null : dueOn,
        importance: importance === '' ? null : (importance as Importance),
        urgency: urgency === '' ? null : (urgency as Urgency),
        estimatedMinutes: estimatedMinutes === '' ? null : Math.trunc(Number(estimatedMinutes)),
      },
      {
        onSuccess: (created) => {
          // 只重置表单，不跳转：快速录入的价值就是能连着录好几条。
          setTitle('')
          setDueOn('')
          setImportance('')
          setUrgency('')
          setEstimatedMinutes('')
          setProjectId('')
          setSelectedTagIds([])
          setExpanded(false)
          if (tagIds.length === 0) {
            return
          }
          // 标签沿用既有契约 PUT /tasks/{id}/tags（详情页 TagPicker 同一个接口），
          // 创建接口本身不收 tagIds，这里在创建成功后补一次绑定。
          tasksApi.assignTags(created.id, tagIds).catch((error: unknown) => {
            setTagNotice(
              `任务已创建，但标签保存失败：${describeError(error)}（可在任务详情页重新关联）`,
            )
          })
        },
      },
    )
  }

  return (
    // 刻意不加 required、也不做浏览器校验：空标题要能提交出去并由后端回 400 + fieldErrors，
    // 这样「字段级错误回显」这条链路才是真被跑通的（沿用被替换掉的 TaskForm 的做法）。
    <form onSubmit={handleSubmit} noValidate className="space-y-3">
      <div className="flex flex-wrap items-start gap-2">
        <div className="min-w-[12rem] flex-1">
          <label htmlFor="quick-task-title" className="sr-only">
            任务标题
          </label>
          <input
            id="quick-task-title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="要做什么？"
            aria-invalid={Boolean(titleError)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
          />
          {titleError && <p className="mt-1 text-sm text-red-600">{titleError}</p>}
        </div>
        <button
          type="submit"
          disabled={create.isPending}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {create.isPending ? '添加中…' : '添加'}
        </button>
      </div>

      <button
        type="button"
        onClick={() => setExpanded((value) => !value)}
        aria-expanded={expanded}
        className="text-sm text-slate-500 hover:text-slate-900"
      >
        {expanded ? '收起可选字段' : '更多（截止日 / 项目 / 标签 / 重要性 / 紧急性 / 预计耗时）'}
      </button>

      {expanded && (
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="quick-task-due" className="mb-1 block text-xs text-slate-600">
              截止日
            </label>
            <input
              id="quick-task-due"
              type="date"
              value={dueOn}
              onChange={(event) => setDueOn(event.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm outline-none focus:border-slate-500"
            />
          </div>

          <div>
            <label htmlFor="quick-task-estimated" className="mb-1 block text-xs text-slate-600">
              预计耗时（分钟）
            </label>
            <input
              id="quick-task-estimated"
              type="number"
              min={0}
              step={5}
              value={estimatedMinutes}
              onChange={(event) => setEstimatedMinutes(event.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm outline-none focus:border-slate-500"
            />
          </div>

          <div>
            <label htmlFor="quick-task-importance" className="mb-1 block text-xs text-slate-600">
              重要性
            </label>
            <select
              id="quick-task-importance"
              value={importance}
              onChange={(event) => setImportance(event.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm outline-none focus:border-slate-500"
            >
              <option value="">未设置</option>
              {IMPORTANCE_LEVELS.map((value) => (
                <option key={value} value={value}>
                  {IMPORTANCE_LABELS[value]}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="quick-task-urgency" className="mb-1 block text-xs text-slate-600">
              紧急性
            </label>
            <select
              id="quick-task-urgency"
              value={urgency}
              onChange={(event) => setUrgency(event.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm outline-none focus:border-slate-500"
            >
              <option value="">未设置</option>
              {URGENCY_LEVELS.map((value) => (
                <option key={value} value={value}>
                  {URGENCY_LABELS[value]}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="quick-task-project" className="mb-1 block text-xs text-slate-600">
              项目
            </label>
            <select
              id="quick-task-project"
              value={projectId}
              onChange={(event) => setProjectId(event.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm outline-none focus:border-slate-500"
            >
              <option value="">未归属</option>
              {(projectsQuery.data ?? []).map((project) => (
                <option key={project.id} value={String(project.id)}>
                  {project.name}
                </option>
              ))}
            </select>
          </div>

          <div className="sm:col-span-2">
            <span className="mb-1 block text-xs text-slate-600">标签</span>
            {tagsQuery.isPending && <p className="text-sm text-slate-500">加载标签中…</p>}
            {tagsQuery.error && (
              <p role="alert" className="text-sm text-red-600">
                {describeError(tagsQuery.error)}
              </p>
            )}
            {tagsQuery.data && tagsQuery.data.length === 0 && (
              <p className="text-sm text-slate-500">还没有标签，先去设置页新建一个。</p>
            )}
            {tagsQuery.data && tagsQuery.data.length > 0 && (
              <ul className="flex flex-wrap gap-2">
                {tagsQuery.data.map((tag) => {
                  const checked = selectedTagIds.includes(tag.id)
                  return (
                    <li key={tag.id}>
                      <label className="flex cursor-pointer items-center gap-2 rounded-md border border-slate-200 px-3 py-1 text-sm text-slate-700 has-checked:border-slate-900 has-checked:bg-slate-900 has-checked:text-white">
                        <input
                          type="checkbox"
                          className="h-4 w-4"
                          checked={checked}
                          onChange={() =>
                            setSelectedTagIds((prev) =>
                              prev.includes(tag.id) ? prev.filter((id) => id !== tag.id) : [...prev, tag.id],
                            )
                          }
                        />
                        {tag.color && (
                          <span
                            aria-hidden="true"
                            className="inline-block h-2.5 w-2.5 rounded-full border border-slate-300"
                            style={{ backgroundColor: tag.color }}
                          />
                        )}
                        {tag.name}
                      </label>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </div>
      )}

      {otherErrors.map(({ field, message }) => (
        <p key={field} className="text-sm text-red-600">
          {message}
        </p>
      ))}
      {formError && <p className="text-sm text-red-600">{formError}</p>}
      {tagNotice && (
        <p role="alert" className="text-sm text-amber-700">
          {tagNotice}
        </p>
      )}
    </form>
  )
}
