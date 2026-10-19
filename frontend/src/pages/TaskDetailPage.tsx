import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '../api/client.ts'
import { projectsApi } from '../api/projects.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { tasksApi } from '../api/tasks.ts'
import { ActivityTimeline } from '../components/ActivityTimeline.tsx'
import { TagPicker } from '../components/TagPicker.tsx'
import { TimeLogList } from '../components/TimeLogList.tsx'
import { TimerPanel } from '../components/TimerPanel.tsx'
import {
  IMPORTANCE_LABELS,
  IMPORTANCE_LEVELS,
  PRIORITY_LABELS,
  TASK_STATUS_LABELS,
  URGENCY_LABELS,
  URGENCY_LEVELS,
} from '../types/task.ts'
import type { TaskSummary, TaskUpdateInput } from '../types/task.ts'

// 重要性与紧急性的取值类型直接从常量数组推导，而不是 import 一个同名类型：
// 数组本身是冻结契约里点名的导出，推导出来的联合类型与 T5 那边必然一致。
type ImportanceValue = (typeof IMPORTANCE_LEVELS)[number]
type UrgencyValue = (typeof URGENCY_LEVELS)[number]

const INPUT_CLASS =
  'w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500'

/**
 * 表单草稿一律用字符串：输入框的值本来就是字符串，在这里转成 string 之后再往上报，
 * 可以避免「空值」和「0」在往返转换中被混为一谈（预计耗时 0 是合法值）。
 */
interface TaskDraft {
  title: string
  notes: string
  nextAction: string
  dueOn: string
  estimatedMinutes: string
  importance: ImportanceValue
  urgency: UrgencyValue
  /** 三态：'' 未评估 / 'true' 可以替代 / 'false' 不能替代。 */
  aiReplaceable: '' | 'true' | 'false'
  aiReplacementNote: string
  /** '' 表示无项目。 */
  projectId: string
}

const EMPTY_DRAFT: TaskDraft = {
  title: '',
  notes: '',
  nextAction: '',
  dueOn: '',
  estimatedMinutes: '',
  importance: 'MEDIUM',
  urgency: 'MEDIUM',
  aiReplaceable: '',
  aiReplacementNote: '',
  projectId: '',
}

function describeError(error: unknown): string {
  return error instanceof ApiError ? error.message : '发生未知错误，请看浏览器控制台'
}

function toDraft(task: TaskSummary): TaskDraft {
  return {
    title: task.title,
    notes: task.notes ?? '',
    nextAction: task.nextAction ?? '',
    dueOn: task.dueOn ?? '',
    // 预计耗时 0 是有效值，只有空（没估过）才显示成空。这里用 == null 而不是 === null：
    // 后端假如哪天把 null 字段整体略掉，把 undefined 当成 0 或者字符串 "undefined" 都是错的。
    estimatedMinutes: task.estimatedMinutes == null ? '' : String(task.estimatedMinutes),
    importance: task.importance,
    urgency: task.urgency,
    aiReplaceable: task.aiReplaceable == null ? '' : task.aiReplaceable ? 'true' : 'false',
    aiReplacementNote: task.aiReplacementNote ?? '',
    projectId: task.projectId == null ? '' : String(task.projectId),
  }
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function parseMinutes(value: string): number | null {
  const trimmed = value.trim()
  if (trimmed === '') return null
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) && parsed >= 0 ? Math.round(parsed) : null
}

function parseTriState(value: TaskDraft['aiReplaceable']): boolean | null {
  if (value === 'true') return true
  if (value === 'false') return false
  return null
}

/**
 * 拼出 PUT 的请求体。PUT 是整体替换，所以没在表单里出现的字段必须显式回传原值：
 * 漏掉 parentTaskId 会让子任务脱离父任务，漏掉 description 会把别处写的描述清空。
 *
 * 这里刻意不写返回类型注解：字面量直接标注成 TaskUpdateInput 会触发「多余属性」检查，
 * 而这份 payload 是照着后端 TaskRequest 的 16 个字段写的，不是照着前端的局部类型写的。
 */
function toUpdateInput(draft: TaskDraft, task: TaskSummary) {
  return {
    title: draft.title.trim(),
    notes: emptyToNull(draft.notes),
    nextAction: emptyToNull(draft.nextAction),
    dueOn: emptyToNull(draft.dueOn),
    estimatedMinutes: parseMinutes(draft.estimatedMinutes),
    importance: draft.importance,
    urgency: draft.urgency,
    projectId: draft.projectId === '' ? null : Number(draft.projectId),
    aiReplaceable: parseTriState(draft.aiReplaceable),
    aiReplacementNote: emptyToNull(draft.aiReplacementNote),
    // 优先级由后端按 importance×urgency 推导：显式传 null，只有两者真的变了后端才会重推，
    // 这样在别处手工覆盖过的优先级不会被详情页的一次保存抹掉。
    priority: null,
    status: task.status,
    description: task.description,
    source: task.source,
    parentTaskId: task.parentTaskId,
    version: task.version,
  }
}

interface FieldProps {
  label: string
  htmlFor: string
  error?: string | undefined
  children: ReactNode
}

function Field({ label, htmlFor, error, children }: FieldProps) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {error && <p className="mt-1 text-sm text-red-600">{error}</p>}
    </div>
  )
}

function TaskDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const taskId = Number(params.id)
  const taskIdIsValid = Number.isFinite(taskId)

  const [message, setMessage] = useState<string | null>(null)
  const [draft, setDraft] = useState<TaskDraft>(EMPTY_DRAFT)
  // 记录草稿是按哪个任务初始化的：从 /tasks/5 跳到 /tasks/7 时组件不会卸载，必须重建草稿。
  // 刻意不按 version 重建：在页面上点一下「开始计时」也会让后端把状态推成 DOING 并换 version，
  // 按 version 重建会把用户正在编辑的内容无声清掉。保存时用的 version 始终取自最新的详情，
  // 所以冲突提示之后直接再点一次保存就会成功，不需要用户重填。
  const [syncedTaskId, setSyncedTaskId] = useState<number | null>(null)

  const detailQuery = useQuery({
    queryKey: queryKeys.task(taskId),
    queryFn: () => tasksApi.get(taskId),
    enabled: taskIdIsValid,
  })

  // 一次取回含归档的全部项目：任务所属项目可能已被归档，只取未归档列表会让下拉框显示成空白。
  // 查询键是固定的 ['projects']，没有放参数的位置；因为 staleTime 为 0，每次挂载都会用这里的参数
  // 重新取一次，所以别处若用「不含归档」填过同一份缓存，也不会把它当成含归档的结果用。
  const projectsQuery = useQuery({
    queryKey: queryKeys.projects,
    queryFn: () => projectsApi.list(true),
  })

  const detail = detailQuery.data

  if (detail && syncedTaskId !== detail.task.id) {
    setSyncedTaskId(detail.task.id)
    setDraft(toDraft(detail.task))
  }

  const saveMutation = useMutation({
    mutationFn: (input: TaskUpdateInput) => tasksApi.update(taskId, input),
    onSuccess: () => {
      setMessage('已保存')
      void queryClient.invalidateQueries({ queryKey: queryKeys.task(taskId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'VERSION_CONFLICT') {
        // 另一个标签页先改了这条任务，本地的 version 已经过期，重试也是同样结果。
        // 作废本地详情，让用户看到服务端的最新值之后再决定要不要重填。
        setMessage('任务已被其他页面修改，请刷新后重试')
        void queryClient.invalidateQueries({ queryKey: queryKeys.task(taskId) })
        return
      }
      setMessage(describeError(error))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => tasksApi.remove(taskId),
    onSuccess: () => {
      // 详情本身已经不存在，没必要失效它的查询；只需把还列着这条任务的页面作废。
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
      navigate('/board')
    },
    onError: (error) => setMessage(describeError(error)),
  })

  const fieldErrors: Record<string, string> =
    saveMutation.error instanceof ApiError ? saveMutation.error.fieldErrors : {}
  const fieldError = (name: string): string | undefined => fieldErrors[name]

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!detail) return
    setMessage(null)
    saveMutation.mutate(toUpdateInput(draft, detail.task))
  }

  function handleDelete() {
    if (!detail) return
    // 用的是浏览器的原生确认框：删除是软删除，但任务会立刻从今日与看板消失，
    // 一次点击就让它消失的代价比多一次确认高。
    if (!window.confirm(`确定删除「${detail.task.title}」吗？`)) return
    setMessage(null)
    deleteMutation.mutate()
  }

  if (!taskIdIsValid) {
    return (
      <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
        地址里的任务 id 不是数字。
      </div>
    )
  }

  if (detailQuery.isPending) {
    return <p className="text-sm text-slate-500">加载任务详情中…</p>
  }

  if (detailQuery.error || !detail) {
    return (
      <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
        {detailQuery.error ? describeError(detailQuery.error) : '没有拿到任务详情。'}
      </div>
    )
  }

  const task = detail.task

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Link to="/board" className="text-sm text-slate-500 hover:text-slate-900">
        ← 返回看板
      </Link>

      {message && (
        <div
          role="alert"
          className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"
        >
          {message}
        </div>
      )}

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h1 className="text-xl font-semibold text-slate-900">{task.title}</h1>

        <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-slate-500">状态</dt>
            <dd className="text-slate-900">{TASK_STATUS_LABELS[task.status]}</dd>
          </div>
          <div>
            <dt className="text-slate-500">优先级</dt>
            <dd className="text-slate-900">{PRIORITY_LABELS[task.priority]}</dd>
          </div>
          <div>
            <dt className="text-slate-500">项目</dt>
            <dd className="text-slate-900">{task.projectName ?? '未归属'}</dd>
          </div>
          <div>
            <dt className="text-slate-500">截止日</dt>
            <dd className="text-slate-900">{task.dueOn ?? '未设置'}</dd>
          </div>
          <div>
            <dt className="text-slate-500">耗时（实际 / 预计）</dt>
            <dd className="tabular-nums text-slate-900">
              {task.actualMinutes} / {task.estimatedMinutes ?? '—'} 分钟
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">重要性 / 紧急性</dt>
            <dd className="text-slate-900">
              {IMPORTANCE_LABELS[task.importance]} / {URGENCY_LABELS[task.urgency]}
            </dd>
          </div>
        </dl>

        {/* 描述不在编辑表单里（本轮表单只管需求点名的那些字段），但 PUT 是整体替换，
            所以保存时会原样回传。这里只读展示，免得一条已有描述在页面上完全看不见。 */}
        {task.description && (
          <p className="mt-4 whitespace-pre-wrap text-sm text-slate-600">{task.description}</p>
        )}
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-4 text-base font-medium text-slate-900">编辑</h2>

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <Field label="标题" htmlFor="detail-title" error={fieldError('title')}>
            <input
              id="detail-title"
              value={draft.title}
              onChange={(event) => setDraft({ ...draft, title: event.target.value })}
              aria-invalid={Boolean(fieldError('title'))}
              className={INPUT_CLASS}
            />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="截止日" htmlFor="detail-due-on" error={fieldError('dueOn')}>
              <input
                id="detail-due-on"
                type="date"
                value={draft.dueOn}
                onChange={(event) => setDraft({ ...draft, dueOn: event.target.value })}
                className={INPUT_CLASS}
              />
            </Field>

            <Field label="预计耗时（分钟）" htmlFor="detail-estimated" error={fieldError('estimatedMinutes')}>
              <input
                id="detail-estimated"
                type="number"
                min={0}
                value={draft.estimatedMinutes}
                onChange={(event) => setDraft({ ...draft, estimatedMinutes: event.target.value })}
                className={INPUT_CLASS}
              />
            </Field>

            <Field label="重要性" htmlFor="detail-importance">
              <select
                id="detail-importance"
                value={draft.importance}
                onChange={(event) =>
                  setDraft({ ...draft, importance: event.target.value as ImportanceValue })
                }
                className={INPUT_CLASS}
              >
                {IMPORTANCE_LEVELS.map((value) => (
                  <option key={value} value={value}>
                    {IMPORTANCE_LABELS[value]}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="紧急性" htmlFor="detail-urgency">
              <select
                id="detail-urgency"
                value={draft.urgency}
                onChange={(event) =>
                  setDraft({ ...draft, urgency: event.target.value as UrgencyValue })
                }
                className={INPUT_CLASS}
              >
                {URGENCY_LEVELS.map((value) => (
                  <option key={value} value={value}>
                    {URGENCY_LABELS[value]}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="项目" htmlFor="detail-project" error={fieldError('projectId')}>
              <select
                id="detail-project"
                value={draft.projectId}
                onChange={(event) => setDraft({ ...draft, projectId: event.target.value })}
                className={INPUT_CLASS}
              >
                <option value="">未归属</option>
                {(projectsQuery.data ?? []).map((project) => (
                  <option key={project.id} value={String(project.id)}>
                    {project.name}
                    {project.archivedAt == null ? '' : '（已归档）'}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="AI 可替代" htmlFor="detail-ai-replaceable">
              <select
                id="detail-ai-replaceable"
                value={draft.aiReplaceable}
                onChange={(event) =>
                  setDraft({ ...draft, aiReplaceable: event.target.value as TaskDraft['aiReplaceable'] })
                }
                className={INPUT_CLASS}
              >
                {/* 三态不能压成两态：未评估与「评估为否」的下一步动作完全不同。 */}
                <option value="">未评估</option>
                <option value="true">可以替代</option>
                <option value="false">不能替代</option>
              </select>
            </Field>
          </div>

          <Field label="AI 替代方案" htmlFor="detail-ai-note" error={fieldError('aiReplacementNote')}>
            <textarea
              id="detail-ai-note"
              rows={2}
              value={draft.aiReplacementNote}
              onChange={(event) => setDraft({ ...draft, aiReplacementNote: event.target.value })}
              className={`${INPUT_CLASS} resize-none`}
            />
          </Field>

          <Field label="下一步" htmlFor="detail-next-action" error={fieldError('nextAction')}>
            <input
              id="detail-next-action"
              value={draft.nextAction}
              onChange={(event) => setDraft({ ...draft, nextAction: event.target.value })}
              className={INPUT_CLASS}
            />
          </Field>

          <Field label="备注" htmlFor="detail-notes" error={fieldError('notes')}>
            <textarea
              id="detail-notes"
              rows={3}
              value={draft.notes}
              onChange={(event) => setDraft({ ...draft, notes: event.target.value })}
              className={`${INPUT_CLASS} resize-none`}
            />
          </Field>

          <p className="text-xs text-slate-400">
            清空日期或文本会显式提交 null（整体替换语义），保存时带上当前版本号 {task.version}，
            别处改过就会收到冲突提示。
          </p>

          <button
            type="submit"
            disabled={saveMutation.isPending}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
          >
            {saveMutation.isPending ? '保存中…' : '保存'}
          </button>
        </form>
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-4 text-base font-medium text-slate-900">标签</h2>
        <TagPicker taskId={taskId} selectedTagIds={detail.tags.map((tag) => tag.id)} />
      </section>

      {detail.children.length > 0 && (
        <section className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="mb-3 text-base font-medium text-slate-900">子任务</h2>
          {/* 只在详情页做「跳进去」，不在详情页建子任务：层级固定两层，新建入口留在列表页。 */}
          <ul className="divide-y divide-slate-100">
            {detail.children.map((child) => (
              <li key={child.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                <Link to={`/tasks/${child.id}`} className="text-slate-900 hover:underline">
                  {child.title}
                </Link>
                <span className="shrink-0 text-slate-500">{TASK_STATUS_LABELS[child.status]}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-4 text-base font-medium text-slate-900">计时</h2>
        <TimerPanel taskId={taskId} />
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-4 text-base font-medium text-slate-900">耗时明细</h2>
        <TimeLogList timeLogs={detail.timeLogs} />
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-4 text-base font-medium text-slate-900">变更历史</h2>
        <ActivityTimeline activities={detail.activities} />
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-2 text-base font-medium text-slate-900">删除任务</h2>
        <p className="mb-3 text-sm text-slate-500">
          删除后任务会从今日与看板中消失（后端是软删除，数据仍留在库里）。
        </p>
        <button
          type="button"
          onClick={handleDelete}
          disabled={deleteMutation.isPending}
          className="rounded-md border border-red-200 px-3 py-2 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
        >
          {deleteMutation.isPending ? '删除中…' : '删除任务'}
        </button>
      </section>
    </div>
  )
}

export default TaskDetailPage
