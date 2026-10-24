import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { projectsApi } from '../../api/projects.ts'
import { queryKeys } from '../../api/queryKeys.ts'
import { tagsApi } from '../../api/tags.ts'
import { describeError } from '../../hooks/useTasks.ts'
import { useAiStatus } from '../../hooks/useAi.ts'
import {
  useAdoptNewsAi,
  useConvertNewsTask,
  useNewsItem,
  useRegenerateNewsAi,
  useSetNewsRead,
  useSetNewsStar,
  useUpdateNewsClassification,
} from '../../hooks/useNews.ts'
import {
  NEWS_AI_STATUS_LABELS,
  NEWS_CATEGORIES,
  NEWS_CATEGORY_LABELS,
} from '../../types/news.ts'
import type { NewsCategory } from '../../types/news.ts'

const INPUT_CLASS =
  'rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500'

function formatTime(value: string | null): string {
  if (value == null) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

interface ConvertDraft {
  title: string
  description: string
  projectId: number | null
  tagIds: number[]
}

/**
 * 资讯详情弹窗（PRD US-03/04/06 的详情侧）：正文与摘要、AI 建议采纳、
 * 分类标签修改、星标/已读、转任务。AI 相关入口只在总开关打开时出现（AC 4.2 / 5.1）。
 */
export function NewsItemDetailModal({ itemId, onClose }: { itemId: number; onClose: () => void }) {
  const itemQuery = useNewsItem(itemId)
  const projectsQuery = useQuerySafeProjects()
  const tagsQuery = useQuerySafeTags()
  const aiEnabled = useAiStatus()

  const setRead = useSetNewsRead()
  const setStar = useSetNewsStar()
  const regenerateAi = useRegenerateNewsAi()
  const adoptAi = useAdoptNewsAi()
  const updateClassification = useUpdateNewsClassification()
  const convertTask = useConvertNewsTask()

  const [message, setMessage] = useState<string | null>(null)
  const [categoryDraft, setCategoryDraft] = useState<NewsCategory | ''>('')
  const [tagIdDraft, setTagIdDraft] = useState<number[]>([])
  const [convertDraft, setConvertDraft] = useState<ConvertDraft | null>(null)
  const [createdTaskId, setCreatedTaskId] = useState<number | null>(null)

  const item = itemQuery.data

  // 打开详情即视为已读（未读红点与列表计数随之消失）；失败静默：详情照常可看
  useEffect(() => {
    if (item != null && item.readAt == null) {
      setRead.mutate({ id: item.id, read: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item?.id])

  useEffect(() => {
    if (item != null) {
      setCategoryDraft(item.category ?? '')
      setTagIdDraft(item.tags.map((tag) => tag.id))
    }
  }, [item])

  function handleConvert() {
    if (item == null || convertDraft == null) return
    convertTask.mutate(
      {
        id: item.id,
        input: {
          title: convertDraft.title,
          description: convertDraft.description,
          projectId: convertDraft.projectId,
          tagIds: convertDraft.tagIds,
        },
      },
      {
        onSuccess: (result) => {
          setCreatedTaskId(result.taskId)
          setMessage(`已创建任务「${result.taskTitle}」`)
        },
        onError: (error) => setMessage(describeError(error)),
      },
    )
  }

  function startConvert() {
    if (item == null) return
    setCreatedTaskId(null)
    const summaryPart = item.aiSummary == null ? '' : `\n摘要：${item.aiSummary}`
    setConvertDraft({
      title: `研读：${item.title}`.slice(0, 200),
      description: `原文链接：${item.url}\n来源：${item.sourceName}${summaryPart}`,
      projectId: null,
      tagIds: item.tags.map((tag) => tag.id),
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-900/40 p-4">
      <div className="my-8 w-full max-w-3xl rounded-lg bg-white p-6 shadow-xl">
        {item == null && <p className="text-sm text-slate-500">加载中…</p>}

        {item != null && (
          <>
            <div className="mb-2 flex items-start justify-between gap-3">
              <h2 className="text-lg font-semibold leading-snug">{item.title}</h2>
              <button
                type="button"
                onClick={onClose}
                className="rounded px-2 py-1 text-sm text-slate-500 hover:bg-slate-100"
              >
                关闭
              </button>
            </div>
            <p className="mb-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-500">
              <span>{item.sourceName}</span>
              <span>{item.category == null ? '未分类' : NEWS_CATEGORY_LABELS[item.category]}</span>
              <span>发布：{formatTime(item.publishedAt)}</span>
              <span>入库：{formatTime(item.createdAt)}</span>
              <span>{NEWS_AI_STATUS_LABELS[item.aiStatus]}</span>
              <a
                href={item.url}
                target="_blank"
                rel="noreferrer"
                className="text-slate-900 underline hover:text-slate-600"
              >
                打开原文 ↗
              </a>
            </p>

            <div className="mb-3 flex gap-2">
              <button
                type="button"
                onClick={() =>
                  setStar.mutate(
                    { id: item.id, starred: !item.starred },
                    { onError: (error) => setMessage(describeError(error)) },
                  )
                }
                className={`rounded-md border px-3 py-1.5 text-sm hover:bg-slate-100 ${
                  item.starred ? 'border-amber-400 text-amber-600' : 'border-slate-300 text-slate-600'
                }`}
              >
                {item.starred ? '★ 已标星' : '☆ 标星'}
              </button>
              <button
                type="button"
                onClick={() =>
                  setRead.mutate(
                    { id: item.id, read: item.readAt == null },
                    { onError: (error) => setMessage(describeError(error)) },
                  )
                }
                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100"
              >
                {item.readAt == null ? '标记未读' : '标记已读'}
              </button>
            </div>

            {message != null && <p className="mb-3 text-sm text-slate-600">{message}</p>}

            {item.aiSummary != null && (
              <div className="mb-3 rounded-md bg-slate-50 p-3 text-sm">
                <p className="font-medium text-slate-700">AI 摘要</p>
                <p className="mt-1 text-slate-600">{item.aiSummary}</p>
                {item.aiKeyPoints.length > 0 && (
                  <ul className="mt-2 list-disc pl-5 text-slate-600">
                    {item.aiKeyPoints.map((point) => (
                      <li key={point}>{point}</li>
                    ))}
                  </ul>
                )}
                {item.aiError != null && <p className="mt-2 text-red-600">上次处理失败：{item.aiError}</p>}
                <div className="mt-2 flex flex-wrap gap-2">
                  {aiEnabled.data === true && (
                    <button
                      type="button"
                      onClick={() =>
                        regenerateAi.mutate(item.id, {
                          onSuccess: () => setMessage('已重新生成'),
                          onError: (error) => setMessage(describeError(error)),
                        })
                      }
                      disabled={regenerateAi.isPending}
                      className="rounded-md border border-slate-300 px-2 py-1 text-xs hover:bg-slate-100 disabled:opacity-50"
                    >
                      {regenerateAi.isPending ? '生成中…' : '重新生成'}
                    </button>
                  )}
                  {item.aiSuggestedCategory != null && item.aiSuggestedCategory !== item.category && (
                    <button
                      type="button"
                      onClick={() =>
                        adoptAi.mutate(
                          { id: item.id, input: { adoptCategory: true } },
                          {
                            onSuccess: () => setMessage('已采纳建议分类'),
                            onError: (error) => setMessage(describeError(error)),
                          },
                        )
                      }
                      className="rounded-md border border-slate-300 px-2 py-1 text-xs hover:bg-slate-100"
                    >
                      采纳建议分类（{NEWS_CATEGORY_LABELS[item.aiSuggestedCategory]}）
                    </button>
                  )}
                  {item.aiSuggestedTags.length > 0 && (
                    <button
                      type="button"
                      onClick={() =>
                        adoptAi.mutate(
                          { id: item.id, input: { adoptTags: true } },
                          {
                            onSuccess: () => setMessage('已采纳建议标签'),
                            onError: (error) => setMessage(describeError(error)),
                          },
                        )
                      }
                      className="rounded-md border border-slate-300 px-2 py-1 text-xs hover:bg-slate-100"
                    >
                      采纳建议标签（{item.aiSuggestedTags.join('、')}）
                    </button>
                  )}
                </div>
              </div>
            )}

            <div className="mb-3 grid gap-3 sm:grid-cols-2">
              <label className="text-sm">
                分类（可改后保存）
                <select
                  value={categoryDraft}
                  onChange={(event) => setCategoryDraft(event.target.value as NewsCategory | '')}
                  className={`${INPUT_CLASS} mt-1 w-full`}
                >
                  <option value="">未分类</option>
                  {NEWS_CATEGORIES.map((category) => (
                    <option key={category} value={category}>
                      {NEWS_CATEGORY_LABELS[category]}
                    </option>
                  ))}
                </select>
              </label>
              <div className="text-sm">
                标签（可改后保存）
                <div className="mt-1 flex flex-wrap gap-2">
                  {(tagsQuery.data ?? []).map((tag) => (
                    <label key={tag.id} className="flex items-center gap-1 text-slate-600">
                      <input
                        type="checkbox"
                        checked={tagIdDraft.includes(tag.id)}
                        onChange={(event) =>
                          setTagIdDraft((previous) =>
                            event.target.checked
                              ? [...previous, tag.id]
                              : previous.filter((id) => id !== tag.id),
                          )
                        }
                      />
                      {tag.name}
                    </label>
                  ))}
                  {(tagsQuery.data ?? []).length === 0 && <span className="text-slate-400">还没有标签</span>}
                </div>
              </div>
            </div>
            <button
              type="button"
              onClick={() =>
                updateClassification.mutate(
                  {
                    id: item.id,
                    input: {
                      category: categoryDraft === '' ? null : categoryDraft,
                      tagIds: tagIdDraft,
                    },
                  },
                  {
                    onSuccess: () => setMessage('分类与标签已保存'),
                    onError: (error) => setMessage(describeError(error)),
                  },
                )
              }
              disabled={updateClassification.isPending}
              className="mb-4 rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100 disabled:opacity-50"
            >
              保存分类与标签
            </button>

            {item.relatedTasks.length > 0 && (
              <div className="mb-4 text-sm">
                <p className="font-medium text-slate-700">已建任务</p>
                <ul className="mt-1 list-disc pl-5">
                  {item.relatedTasks.map((task) => (
                    <li key={task.taskId}>
                      {task.deleted ? (
                        <span className="text-slate-400">{task.title}（任务已删除）</span>
                      ) : (
                        <a
                          href={`/tasks/${task.taskId}`}
                          className="text-slate-900 underline hover:text-slate-600"
                        >
                          {task.title}
                        </a>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {convertDraft == null ? (
              <button
                type="button"
                onClick={startConvert}
                className="mb-4 rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700"
              >
                转为任务
              </button>
            ) : (
              <div className="mb-4 rounded-md border border-slate-200 p-3">
                <p className="mb-2 text-sm font-medium text-slate-700">转为任务（可修改后创建）</p>
                <label className="block text-sm">
                  任务标题
                  <input
                    value={convertDraft.title}
                    onChange={(event) => setConvertDraft({ ...convertDraft, title: event.target.value })}
                    className={`${INPUT_CLASS} mt-1 w-full`}
                    maxLength={200}
                  />
                </label>
                <label className="mt-2 block text-sm">
                  任务描述
                  <textarea
                    value={convertDraft.description}
                    onChange={(event) =>
                      setConvertDraft({ ...convertDraft, description: event.target.value })
                    }
                    className={`${INPUT_CLASS} mt-1 h-28 w-full resize-y`}
                    maxLength={4000}
                  />
                </label>
                <div className="mt-2 grid gap-3 sm:grid-cols-2">
                  <label className="text-sm">
                    项目（可选）
                    <select
                      value={convertDraft.projectId ?? ''}
                      onChange={(event) =>
                        setConvertDraft({
                          ...convertDraft,
                          projectId: event.target.value === '' ? null : Number(event.target.value),
                        })
                      }
                      className={`${INPUT_CLASS} mt-1 w-full`}
                    >
                      <option value="">不指定</option>
                      {(projectsQuery.data ?? [])
                        .filter((project) => project.archivedAt == null)
                        .map((project) => (
                          <option key={project.id} value={project.id}>
                            {project.name}
                          </option>
                        ))}
                    </select>
                  </label>
                  <div className="text-sm">
                    标签（可选）
                    <div className="mt-1 flex flex-wrap gap-2">
                      {(tagsQuery.data ?? []).map((tag) => (
                        <label key={tag.id} className="flex items-center gap-1 text-slate-600">
                          <input
                            type="checkbox"
                            checked={convertDraft.tagIds.includes(tag.id)}
                            onChange={(event) =>
                              setConvertDraft({
                                ...convertDraft,
                                tagIds: event.target.checked
                                  ? [...convertDraft.tagIds, tag.id]
                                  : convertDraft.tagIds.filter((id) => id !== tag.id),
                              })
                            }
                          />
                          {tag.name}
                        </label>
                      ))}
                    </div>
                  </div>
                </div>
                <div className="mt-3 flex gap-2">
                  <button
                    type="button"
                    onClick={handleConvert}
                    disabled={convertTask.isPending}
                    className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                  >
                    {convertTask.isPending ? '创建中…' : '创建任务'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setConvertDraft(null)}
                    className="rounded-md px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-100"
                  >
                    取消
                  </button>
                  {createdTaskId != null && (
                    <a
                      href={`/tasks/${createdTaskId}`}
                      className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100"
                    >
                      查看任务
                    </a>
                  )}
                </div>
              </div>
            )}

            {item.contentText != null && (
              <div className="max-h-72 overflow-y-auto whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-sm leading-relaxed text-slate-700">
                {item.contentText}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

/**
 * 项目/标签查询的兜底封装：详情弹窗在任一基础数据失败时仍应可用
 * （下拉为空即可），错误不弹全局提示。
 */
function useQuerySafeProjects() {
  return useQuery({ queryKey: queryKeys.projects, queryFn: () => projectsApi.list(true) })
}

function useQuerySafeTags() {
  return useQuery({ queryKey: queryKeys.tags, queryFn: () => tagsApi.list() })
}
