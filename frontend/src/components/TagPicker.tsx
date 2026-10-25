import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '../api/client.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { tagsApi } from '../api/tags.ts'
import { tasksApi } from '../api/tasks.ts'
import type { Tag } from '../types/tag.ts'

function describeError(error: unknown): string {
  return error instanceof ApiError ? error.message : '发生未知错误，请看浏览器控制台'
}

interface TagPickerProps {
  taskId: number
  /** 当前已关联的标签 id，来自任务详情。 */
  selectedTagIds: number[]
}

export function TagPicker({ taskId, selectedTagIds }: TagPickerProps) {
  const queryClient = useQueryClient()
  const [message, setMessage] = useState<string | null>(null)

  const tagsQuery = useQuery({ queryKey: queryKeys.tags, queryFn: tagsApi.list })

  // 勾选只改本地草稿，点「保存标签」才提交：后端 PUT /tasks/{id}/tags 是整体替换语义，
  // 每勾一下发一次请求既会放大误操作的后果，也会让「保存」这个动作失去意义。
  const [draft, setDraft] = useState<number[]>(selectedTagIds)

  // 用「归一化后的选中集合」比较，而不是 useEffect + 依赖数组：详情每次重取都会产生新的数组实例，
  // 按引用判断会陷入「重置草稿 → 重渲染 → 再重置」的循环。渲染期的 setState 会被 React 立刻
  // 重放，不会先提交一帧错误内容。
  const selectionKey = selectedTagIds.join(',')
  const [syncedKey, setSyncedKey] = useState(selectionKey)
  if (syncedKey !== selectionKey) {
    setSyncedKey(selectionKey)
    setDraft(selectedTagIds)
  }

  const saveMutation = useMutation({
    mutationFn: () => tasksApi.assignTags(taskId, draft),
    onSuccess: () => {
      setMessage('标签已保存')
      void queryClient.invalidateQueries({ queryKey: queryKeys.task(taskId) })
      // 标签集合会反映在今日/看板/列表的卡片上：只刷详情的话，回到列表看到的还是旧标签。
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
      void queryClient.invalidateQueries({ queryKey: queryKeys.taskLists })
    },
    onError: (error) => setMessage(describeError(error)),
  })

  function toggle(tag: Tag) {
    setDraft((prev) => (prev.includes(tag.id) ? prev.filter((id) => id !== tag.id) : [...prev, tag.id]))
  }

  return (
    <div className="space-y-3">
      {message && (
        <p role="alert" className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {message}
        </p>
      )}

      {tagsQuery.isPending && <p className="text-sm text-slate-500">加载标签中…</p>}

      {tagsQuery.error && (
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {describeError(tagsQuery.error)}
        </p>
      )}

      {tagsQuery.data && tagsQuery.data.length === 0 && (
        <p className="text-sm text-slate-500">
          还没有标签，先去
          <Link to="/settings" className="mx-1 text-slate-900 underline">
            设置页
          </Link>
          新建一个。
        </p>
      )}

      {tagsQuery.data && tagsQuery.data.length > 0 && (
        <>
          <ul className="flex flex-wrap gap-2">
            {tagsQuery.data.map((tag) => {
              const checked = draft.includes(tag.id)
              return (
                <li key={tag.id}>
                  <label className="flex cursor-pointer items-center gap-2 rounded-md border border-slate-200 px-3 py-1 text-sm text-slate-700 has-checked:border-slate-900 has-checked:bg-slate-900 has-checked:text-white">
                    {/* 复选框保持可见：它是键盘操作与聚焦提示的唯一载体，藏起来会让键盘用户不知道焦点在哪。 */}
                    <input
                      type="checkbox"
                      className="h-4 w-4"
                      checked={checked}
                      onChange={() => toggle(tag)}
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

          <button
            type="button"
            onClick={() => saveMutation.mutate()}
            disabled={saveMutation.isPending}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
          >
            {saveMutation.isPending ? '保存中…' : '保存标签'}
          </button>
        </>
      )}
    </div>
  )
}
