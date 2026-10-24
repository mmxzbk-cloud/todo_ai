import { useState } from 'react'

import { aiApi } from '../api/ai.ts'
import { describeError, useChangeTaskPriority } from '../hooks/useTasks.ts'
import { PRIORITY_LABELS } from '../types/task.ts'
import type { PrioritySuggestion } from '../types/ai.ts'

interface PrioritySuggestionPanelProps {
  taskId: number
  /**
   * detail：详情页优先级区域旁的完整入口；compact：任务卡片上的轻量入口。
   * 两个变体共用同一套请求与采纳逻辑，只是版式不同。
   */
  variant: 'detail' | 'compact'
  /** 卡片上的失败提示交给页面统一展示；详情页就地展示。 */
  onError?: (message: string | null) => void
}

/**
 * AI 建议优先级（US-02）。建议不直接写入：点「采纳」才走既有的改优先级通道
 * （变更历史照常留痕），「忽略」或重新请求都不会动任务数据（AC 2.3）。
 * 加载未完成时按钮禁用，不可重复点击（AC 2.4）。
 */
export function PrioritySuggestionPanel({ taskId, variant, onError }: PrioritySuggestionPanelProps) {
  const [suggestion, setSuggestion] = useState<PrioritySuggestion | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [requesting, setRequesting] = useState(false)

  const changePriority = useChangeTaskPriority()

  function request() {
    if (requesting) return
    onError?.(null)
    setErrorMessage(null)
    setNotice(null)
    setRequesting(true)
    aiApi
      .suggestPriority(taskId)
      .then((result) => setSuggestion(result))
      .catch((error: unknown) => {
        const text = describeError(error)
        setErrorMessage(text)
        onError?.(text)
      })
      .finally(() => setRequesting(false))
  }

  function adopt() {
    if (!suggestion) return
    changePriority.mutate(
      { id: taskId, priority: suggestion.priority },
      {
        onSuccess: () => {
          setSuggestion(null)
          setNotice(`已采纳，优先级更新为 ${PRIORITY_LABELS[suggestion.priority]}`)
        },
        onError: (error) => {
          const text = describeError(error)
          setErrorMessage(text)
          onError?.(text)
        },
      },
    )
  }

  const isCompact = variant === 'compact'
  const buttonClass = isCompact
    ? 'rounded-md border border-slate-300 px-1.5 py-0.5 text-xs text-slate-500 hover:bg-slate-50 disabled:opacity-50'
    : 'rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-50'

  return (
    <div className={isCompact ? 'contents' : 'mt-2'}>
      <button
        type="button"
        onClick={request}
        disabled={requesting}
        className={buttonClass}
        title="让 AI 给出优先级建议"
      >
        {requesting ? 'AI 思考中…' : 'AI 建议'}
      </button>

      {suggestion && (
        <div
          className={
            isCompact
              ? 'mt-2 w-full rounded-md border border-indigo-100 bg-indigo-50/60 px-2 py-1.5 text-xs text-slate-700'
              : 'mt-2 rounded-md border border-indigo-100 bg-indigo-50/60 px-3 py-2 text-xs text-slate-700'
          }
        >
          <p>
            建议 <span className="font-medium">{PRIORITY_LABELS[suggestion.priority]}</span>
            ：{suggestion.reason}
          </p>
          <div className="mt-1 flex items-center gap-2">
            <button
              type="button"
              onClick={adopt}
              disabled={changePriority.isPending}
              className="rounded bg-slate-900 px-2 py-0.5 text-xs font-medium text-white hover:bg-slate-700 disabled:opacity-50"
            >
              {changePriority.isPending ? '采纳中…' : '采纳'}
            </button>
            <button
              type="button"
              onClick={() => setSuggestion(null)}
              className="text-xs text-slate-500 hover:text-slate-900"
            >
              忽略
            </button>
          </div>
        </div>
      )}

      {!isCompact && notice && <p className="mt-1 text-xs text-emerald-700">{notice}</p>}
      {!isCompact && errorMessage && (
        <p role="alert" className="mt-1 text-xs text-red-600">
          {errorMessage}
        </p>
      )}
    </div>
  )
}
