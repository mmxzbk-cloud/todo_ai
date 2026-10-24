import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '../api/client.ts'
import { projectsApi } from '../api/projects.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { tagsApi } from '../api/tags.ts'
import { useAiStatus, useSetAiEnabled } from '../hooks/useAi.ts'
import type { Project } from '../types/project.ts'
import type { Tag } from '../types/tag.ts'

const INPUT_CLASS =
  'rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500'

/** 色板的默认取色：勾选「颜色」时先给一个中性值，用户再自己挑。 */
const DEFAULT_COLOR = '#6366f1'

/**
 * 「颜色」字段的统一交互：勾选才启用色板，取消勾选表示不要颜色（提交时落 null）。
 * 用 type=color 而不是文本框：色板只能产出合法的十六进制值，
 * 让用户手填 #RRGGBB 既不直观，也难排查「填了但没生效」的问题。
 */
function ColorField({
  id,
  value,
  onChange,
}: {
  id: string
  value: string | null
  onChange: (value: string | null) => void
}) {
  return (
    <span className="flex items-center gap-2">
      <label htmlFor={`${id}-enabled`} className="flex items-center gap-1.5 text-sm text-slate-700">
        <input
          id={`${id}-enabled`}
          type="checkbox"
          className="h-4 w-4"
          checked={value !== null}
          onChange={(event) => onChange(event.target.checked ? DEFAULT_COLOR : null)}
        />
        颜色
      </label>
      <input
        id={id}
        type="color"
        aria-label="选择颜色"
        value={value ?? DEFAULT_COLOR}
        disabled={value === null}
        onChange={(event) => onChange(event.target.value)}
        className="h-8 w-10 cursor-pointer rounded border border-slate-300 bg-white p-0.5 disabled:opacity-40"
      />
    </span>
  )
}

/**
 * 后端把「重名」表达成一个稳定的 code 而不是 message，前端在这里把它翻成人话：
 * 直接显示后端 message 会带上列名之类的实现细节，而用户只需要知道改个名字。
 */
function describeError(error: unknown, duplicate?: { code: string; text: string }): string {
  if (error instanceof ApiError) {
    if (duplicate && error.code === duplicate.code) return duplicate.text
    return error.message
  }
  return '发生未知错误，请看浏览器控制台'
}

const PROJECT_NAME_TAKEN = { code: 'PROJECT_NAME_TAKEN', text: '已有同名项目，换一个名字试试' }
const TAG_NAME_TAKEN = { code: 'TAG_NAME_TAKEN', text: '已有同名标签，换一个名字试试' }

function SettingsPage() {
  const queryClient = useQueryClient()

  const [showArchived, setShowArchived] = useState(false)
  const [projectName, setProjectName] = useState('')
  const [projectColor, setProjectColor] = useState<string | null>(null)
  const [projectMessage, setProjectMessage] = useState<string | null>(null)

  const [tagName, setTagName] = useState('')
  const [tagColor, setTagColor] = useState<string | null>(null)
  const [tagMessage, setTagMessage] = useState<string | null>(null)

  // 行内编辑：同一时间只编辑一行，草稿放在状态里，保存成功后退出编辑态。
  type EntityDraft = { id: number; name: string; color: string | null }
  const [editingProject, setEditingProject] = useState<EntityDraft | null>(null)
  const [editingTag, setEditingTag] = useState<EntityDraft | null>(null)

  // AI 总开关（US-01）。checked 绑定服务端状态：保存失败时缓存不动，开关自动回弹（AC 1.6）。
  const aiStatus = useAiStatus()
  const [aiMessage, setAiMessage] = useState<string | null>(null)
  const setAiEnabled = useSetAiEnabled()

  function handleToggleAi(enabled: boolean) {
    setAiMessage(null)
    setAiEnabled.mutate(enabled, {
      onSuccess: (data) => setAiMessage(data.aiEnabled ? '已开启 AI 能力' : '已关闭 AI 能力'),
      onError: (error) => setAiMessage(describeError(error)),
    })
  }

  // 一次取回含归档的全部项目，开关只做前端过滤。查询键是固定的 ['projects']，没有放参数的位置，
  // 用「同一个键 + 不同参数」切换会读到上一次参数的缓存；staleTime 为 0，每次挂载都会重新取一次。
  const projectsQuery = useQuery({
    queryKey: queryKeys.projects,
    queryFn: () => projectsApi.list(true),
  })

  const tagsQuery = useQuery({ queryKey: queryKeys.tags, queryFn: tagsApi.list })

  const createProject = useMutation({
    mutationFn: () =>
      projectsApi.create({
        name: projectName.trim(),
        color: projectColor ?? undefined,
      }),
    onSuccess: () => {
      setProjectName('')
      setProjectColor(null)
      setProjectMessage(null)
      void queryClient.invalidateQueries({ queryKey: queryKeys.projects })
    },
    onError: (error) => setProjectMessage(describeError(error, PROJECT_NAME_TAKEN)),
  })

  // 项目名与颜色会随任务卡片展示（服务端回填），改完必须把任务缓存一并失效，
  // 否则卡片上的项目名/色点会一直停在旧值，看起来就像「改了没生效」。
  const invalidateTaskCaches = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.today })
    void queryClient.invalidateQueries({ queryKey: queryKeys.board })
    void queryClient.invalidateQueries({ queryKey: queryKeys.taskLists })
  }

  const updateProject = useMutation({
    mutationFn: (draft: EntityDraft) =>
      projectsApi.update(draft.id, { name: draft.name.trim(), color: draft.color ?? undefined }),
    onSuccess: () => {
      setEditingProject(null)
      setProjectMessage('项目已更新')
      void queryClient.invalidateQueries({ queryKey: queryKeys.projects })
      invalidateTaskCaches()
    },
    onError: (error) => setProjectMessage(describeError(error, PROJECT_NAME_TAKEN)),
  })

  const toggleProjectArchive = useMutation({
    mutationFn: (project: Project) =>
      project.archivedAt == null ? projectsApi.archive(project.id) : projectsApi.unarchive(project.id),
    onSuccess: (project) => {
      setProjectMessage(project.archivedAt == null ? '已取消归档' : '已归档')
      void queryClient.invalidateQueries({ queryKey: queryKeys.projects })
    },
    onError: (error) => setProjectMessage(describeError(error)),
  })

  const createTag = useMutation({
    mutationFn: () =>
      tagsApi.create({
        name: tagName.trim(),
        color: tagColor ?? undefined,
      }),
    onSuccess: () => {
      setTagName('')
      setTagColor(null)
      setTagMessage(null)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tags })
    },
    onError: (error) => setTagMessage(describeError(error, TAG_NAME_TAKEN)),
  })

  const updateTag = useMutation({
    mutationFn: (draft: EntityDraft) =>
      tagsApi.update(draft.id, { name: draft.name.trim(), color: draft.color ?? undefined }),
    onSuccess: () => {
      setEditingTag(null)
      setTagMessage('标签已更新')
      void queryClient.invalidateQueries({ queryKey: queryKeys.tags })
      invalidateTaskCaches()
    },
    onError: (error) => setTagMessage(describeError(error, TAG_NAME_TAKEN)),
  })

  const removeTag = useMutation({
    mutationFn: (id: number) => tagsApi.remove(id),
    onSuccess: () => {
      setTagMessage(null)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tags })
    },
    onError: (error) => setTagMessage(describeError(error)),
  })

  const projects = (projectsQuery.data ?? []).filter(
    (project) => showArchived || project.archivedAt == null,
  )

  function handleCreateProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setProjectMessage(null)
    createProject.mutate()
  }

  function handleCreateTag(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setTagMessage(null)
    createTag.mutate()
  }

  function handleRemoveTag(tag: Tag) {
    // 标签删除会连带解除任务关联，影响范围比「看不见了」大，所以问一次再删。
    if (!window.confirm(`确定删除标签「${tag.name}」吗？已关联该标签的任务会失去这个标签。`)) return
    setTagMessage(null)
    removeTag.mutate(tag.id)
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      {/* 页面级标题只给屏幕阅读器：视觉上两段内容各自的小标题已经说清楚这是什么页面了。 */}
      <h1 className="sr-only">设置</h1>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-1 text-lg font-semibold text-slate-900">AI 能力</h2>
        <p className="mb-4 text-sm text-slate-500">
          关闭后，所有 AI 入口将隐藏，系统不会发起任何 AI 请求；耗时统计等其余功能不受影响。
        </p>

        {aiMessage && (
          <p
            role="alert"
            className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800"
          >
            {aiMessage}
          </p>
        )}

        <label className="flex items-center gap-3 text-sm text-slate-700">
          <input
            type="checkbox"
            className="h-4 w-4"
            checked={aiStatus.data ?? false}
            disabled={aiStatus.isPending || setAiEnabled.isPending}
            onChange={(event) => handleToggleAi(event.target.checked)}
          />
          启用 AI 能力（优先级建议 / 方案建议 / AI 评估）
        </label>
        {aiStatus.isPending && <p className="mt-2 text-xs text-slate-400">读取开关状态中…</p>}
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-1 text-lg font-semibold text-slate-900">项目</h2>
        <p className="mb-4 text-sm text-slate-500">
          项目只归档不删除：删掉项目会让历史任务失去归属信息，归档同样能让它从下拉里消失。
        </p>

        {projectMessage && (
          <p
            role="alert"
            className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800"
          >
            {projectMessage}
          </p>
        )}

        <form onSubmit={handleCreateProject} noValidate className="mb-4 flex flex-wrap items-end gap-2">
          <div>
            <label htmlFor="project-name" className="mb-1 block text-sm font-medium text-slate-700">
              名称
            </label>
            <input
              id="project-name"
              value={projectName}
              onChange={(event) => setProjectName(event.target.value)}
              placeholder="例如：AI 助手"
              className={`${INPUT_CLASS} w-56`}
            />
          </div>
          <ColorField id="project-color" value={projectColor} onChange={setProjectColor} />
          <button
            type="submit"
            disabled={createProject.isPending || projectName.trim() === ''}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
          >
            新建项目
          </button>
        </form>

        <label className="mb-3 flex items-center gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            className="h-4 w-4"
            checked={showArchived}
            onChange={(event) => setShowArchived(event.target.checked)}
          />
          显示已归档
        </label>

        {projectsQuery.isPending && <p className="text-sm text-slate-500">加载项目中…</p>}

        {projectsQuery.error && (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {describeError(projectsQuery.error)}
          </p>
        )}

        {projectsQuery.data && projects.length === 0 && (
          <p className="text-sm text-slate-500">
            {showArchived ? '还没有项目。' : '还没有项目（也可能全部已归档，勾选上面的开关看看）。'}
          </p>
        )}

        {projects.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {projects.map((project) =>
              editingProject?.id === project.id ? (
                <li key={project.id} className="py-2">
                  <form
                    onSubmit={(event) => {
                      event.preventDefault()
                      setProjectMessage(null)
                      updateProject.mutate(editingProject)
                    }}
                    className="flex flex-wrap items-center gap-2"
                  >
                    <input
                      value={editingProject.name}
                      onChange={(event) => setEditingProject({ ...editingProject, name: event.target.value })}
                      aria-label="项目名称"
                      className={`${INPUT_CLASS} w-48`}
                    />
                    <ColorField
                      id={`edit-project-color-${project.id}`}
                      value={editingProject.color}
                      onChange={(color) => setEditingProject({ ...editingProject, color })}
                    />
                    <button
                      type="submit"
                      disabled={updateProject.isPending || editingProject.name.trim() === ''}
                      className="rounded-md bg-slate-900 px-2 py-1 text-xs font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                    >
                      保存
                    </button>
                    <button
                      type="button"
                      onClick={() => setEditingProject(null)}
                      className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50"
                    >
                      取消
                    </button>
                  </form>
                </li>
              ) : (
                <li key={project.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                  <span className="flex min-w-0 items-center gap-2">
                    <span className="text-xs text-slate-400">#{project.id}</span>
                    {project.color && (
                      <span
                        aria-hidden="true"
                        className="inline-block h-2.5 w-2.5 shrink-0 rounded-full border border-slate-300"
                        style={{ backgroundColor: project.color }}
                      />
                    )}
                    <span className="truncate text-slate-900">{project.name}</span>
                    {project.archivedAt != null && (
                      <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
                        已归档
                      </span>
                    )}
                  </span>
                  <span className="flex shrink-0 items-center gap-2">
                    <button
                      type="button"
                      onClick={() =>
                        setEditingProject({ id: project.id, name: project.name, color: project.color })
                      }
                      className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50"
                    >
                      编辑
                    </button>
                    <button
                      type="button"
                      onClick={() => toggleProjectArchive.mutate(project)}
                      disabled={toggleProjectArchive.isPending}
                      className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-50"
                    >
                      {project.archivedAt == null ? '归档' : '取消归档'}
                    </button>
                  </span>
                </li>
              ),
            )}
          </ul>
        )}
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-1 text-lg font-semibold text-slate-900">标签</h2>
        <p className="mb-4 text-sm text-slate-500">标签在任务详情页勾选，用来给跨项目的任务归类。</p>

        {tagMessage && (
          <p
            role="alert"
            className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800"
          >
            {tagMessage}
          </p>
        )}

        <form onSubmit={handleCreateTag} noValidate className="mb-4 flex flex-wrap items-end gap-2">
          <div>
            <label htmlFor="tag-name" className="mb-1 block text-sm font-medium text-slate-700">
              名称
            </label>
            <input
              id="tag-name"
              value={tagName}
              onChange={(event) => setTagName(event.target.value)}
              placeholder="例如：等外部回复"
              className={`${INPUT_CLASS} w-56`}
            />
          </div>
          <ColorField id="tag-color" value={tagColor} onChange={setTagColor} />
          <button
            type="submit"
            disabled={createTag.isPending || tagName.trim() === ''}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
          >
            新建标签
          </button>
        </form>

        {tagsQuery.isPending && <p className="text-sm text-slate-500">加载标签中…</p>}

        {tagsQuery.error && (
          <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {describeError(tagsQuery.error)}
          </p>
        )}

        {tagsQuery.data && tagsQuery.data.length === 0 && (
          <p className="text-sm text-slate-500">还没有标签。</p>
        )}

        {tagsQuery.data && tagsQuery.data.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {tagsQuery.data.map((tag) =>
              editingTag?.id === tag.id ? (
                <li key={tag.id} className="py-2">
                  <form
                    onSubmit={(event) => {
                      event.preventDefault()
                      setTagMessage(null)
                      updateTag.mutate(editingTag)
                    }}
                    className="flex flex-wrap items-center gap-2"
                  >
                    <input
                      value={editingTag.name}
                      onChange={(event) => setEditingTag({ ...editingTag, name: event.target.value })}
                      aria-label="标签名称"
                      className={`${INPUT_CLASS} w-48`}
                    />
                    <ColorField
                      id={`edit-tag-color-${tag.id}`}
                      value={editingTag.color}
                      onChange={(color) => setEditingTag({ ...editingTag, color })}
                    />
                    <button
                      type="submit"
                      disabled={updateTag.isPending || editingTag.name.trim() === ''}
                      className="rounded-md bg-slate-900 px-2 py-1 text-xs font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                    >
                      保存
                    </button>
                    <button
                      type="button"
                      onClick={() => setEditingTag(null)}
                      className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50"
                    >
                      取消
                    </button>
                  </form>
                </li>
              ) : (
                <li key={tag.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                  <span className="flex min-w-0 items-center gap-2">
                    {tag.color && (
                      <span
                        aria-hidden="true"
                        className="inline-block h-2.5 w-2.5 shrink-0 rounded-full border border-slate-300"
                        style={{ backgroundColor: tag.color }}
                      />
                    )}
                    <span className="truncate text-slate-900">{tag.name}</span>
                  </span>
                  <span className="flex shrink-0 items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setEditingTag({ id: tag.id, name: tag.name, color: tag.color })}
                      className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50"
                    >
                      编辑
                    </button>
                    <button
                      type="button"
                      onClick={() => handleRemoveTag(tag)}
                      disabled={removeTag.isPending}
                      aria-label={`删除标签：${tag.name}`}
                      className="rounded-md px-2 py-1 text-xs text-slate-500 hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                    >
                      删除
                    </button>
                  </span>
                </li>
              ),
            )}
          </ul>
        )}
      </section>
    </div>
  )
}

export default SettingsPage
