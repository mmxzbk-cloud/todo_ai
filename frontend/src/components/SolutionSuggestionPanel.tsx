import { useState } from 'react'

import { aiApi } from '../api/ai.ts'
import { describeError } from '../hooks/useTasks.ts'
import type { SolutionSuggestion } from '../types/ai.ts'

/** 「下一步行动」字段的落库上限（tasks.next_action，与后端一致）。 */
const NEXT_ACTION_LIMIT = 500

interface SolutionSuggestionPanelProps {
  taskId: number
  /** 「填入下一步行动」把建议写进编辑表单草稿，由用户再修改并手动保存（AC 3.3）。 */
  onFillNextAction: (text: string) => void
}

/**
 * AI 方案建议（US-03）。三个动作都只动展示或草稿：填入只改表单草稿、
 * 复制进剪贴板、忽略关卡片；AI 不直接落库（AC 3.3 / 3.4）。
 */
export function SolutionSuggestionPanel({ taskId, onFillNextAction }: SolutionSuggestionPanelProps) {
  const [suggestion, setSuggestion] = useState<SolutionSuggestion | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [requesting, setRequesting] = useState(false)

  function request() {
    if (requesting) return
    setErrorMessage(null)
    setNotice(null)
    setRequesting(true)
    aiApi
      .suggestSolution(taskId)
      .then((result) => setSuggestion(result))
      .catch((error: unknown) => setErrorMessage(describeError(error)))
      .finally(() => setRequesting(false))
  }

  function fillNextAction() {
    if (!suggestion?.nextStep) return
    // 超长按字段上限截断并提示（PRD US-03 核心规则 4），不静默丢字也不让保存报错
    if (suggestion.nextStep.length > NEXT_ACTION_LIMIT) {
      onFillNextAction(suggestion.nextStep.slice(0, NEXT_ACTION_LIMIT))
      setNotice(`建议超过 ${NEXT_ACTION_LIMIT} 字，已截断。已填入「下一步」，保存后生效`)
      return
    }
    onFillNextAction(suggestion.nextStep)
    setNotice('已填入「下一步」，记得保存才会生效')
  }

  function copyAll() {
    if (!suggestion) return
    const text = [suggestion.approach, suggestion.nextStep].filter(Boolean).join('\n\n下一步：')
    navigator.clipboard
      .writeText(text)
      .then(() => setNotice('已复制到剪贴板'))
      .catch(() => setErrorMessage('复制失败，请手动选择文本复制'))
  }

  return (
    <div className="rounded-md border border-indigo-100 bg-indigo-50/40 p-3">
      {!suggestion ? (
        <button
          type="button"
          onClick={request}
          disabled={requesting}
          className="rounded-md border border-indigo-200 bg-white px-3 py-1.5 text-xs font-medium text-indigo-700 hover:bg-indigo-50 disabled:opacity-50"
        >
          {requesting ? 'AI 思考中…' : 'AI 方案建议'}
        </button>
      ) : (
        <div className="space-y-2 text-sm">
          <div className="space-y-1">
            {suggestion.approach && (
              <p className="whitespace-pre-wrap text-slate-700">{suggestion.approach}</p>
            )}
            {suggestion.nextStep && (
              <p className="text-slate-700">
                <span className="text-xs text-slate-500">建议的下一步：</span>
                {suggestion.nextStep}
              </p>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {suggestion.nextStep && (
              <button
                type="button"
                onClick={fillNextAction}
                className="rounded bg-slate-900 px-2 py-1 text-xs font-medium text-white hover:bg-slate-700"
              >
                填入下一步行动
              </button>
            )}
            <button
              type="button"
              onClick={copyAll}
              className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-white"
            >
              复制
            </button>
            <button type="button" onClick={() => setSuggestion(null)} className="text-xs text-slate-500 hover:text-slate-900">
              忽略
            </button>
          </div>
        </div>
      )}

      {notice && <p className="mt-2 text-xs text-emerald-700">{notice}</p>}
      {errorMessage && (
        <p role="alert" className="mt-2 text-xs text-red-600">
          {errorMessage}
        </p>
      )}
    </div>
  )
}
