import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { aiApi } from '../api/ai.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { describeError } from '../hooks/useTasks.ts'
import { tasksApi } from '../api/tasks.ts'
import { REPLACEABILITY_LABELS } from '../types/ai.ts'
import type { ReplaceabilityVerdict } from '../types/ai.ts'
import type { TaskSummary, TaskUpdateInput } from '../types/task.ts'

/** 「AI 替代方案/说明」字段的落库上限（tasks.ai_replacement_note，与后端一致）。 */
const NOTE_LIMIT = 2000

interface ReplaceabilitySuggestionPanelProps {
  task: TaskSummary
  /** 写入成功后把新值同步给编辑表单草稿，避免表单还停在旧值上（下次保存会误覆盖）。 */
  onWritten: (aiReplaceable: boolean, note: string) => void
}

/** 三态徽标配色：可=绿、部分可=琥珀、不可=灰。 */
const VERDICT_BADGE_CLASSES: Record<ReplaceabilityVerdict, string> = {
  YES: 'bg-emerald-100 text-emerald-800',
  PARTIAL: 'bg-amber-100 text-amber-800',
  NO: 'bg-slate-200 text-slate-700',
}

/**
 * AI 评估任务可替代性（US-04）。点「写入」才落库，且走既有的任务更新通道
 * （变更历史照常留痕，AC 4.8）；已有人工评估时先弹覆盖确认（AC 4.4）；
 * 「忽略」不动任何字段，未评估状态保持原样（AC 4.5）。
 */
export function ReplaceabilitySuggestionPanel({ task, onWritten }: ReplaceabilitySuggestionPanelProps) {
  const [assessment, setAssessment] = useState<{ verdict: ReplaceabilityVerdict; explanation: string } | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [requesting, setRequesting] = useState(false)
  const [writing, setWriting] = useState(false)

  const queryClient = useQueryClient()

  function request() {
    if (requesting) return
    setErrorMessage(null)
    setNotice(null)
    setRequesting(true)
    aiApi
      .assessReplaceability(task.id)
      .then((result) => setAssessment(result))
      .catch((error: unknown) => setErrorMessage(describeError(error)))
      .finally(() => setRequesting(false))
  }

  /** 三态结论到现有布尔字段的落库映射（PRD A.4 第 3 条方案 a）：部分可 → 是 + 说明标注「部分可」。 */
  function toNote(verdict: ReplaceabilityVerdict, explanation: string): string {
    const text = verdict === 'PARTIAL' ? `部分可：${explanation}` : explanation
    return text.length > NOTE_LIMIT ? text.slice(0, NOTE_LIMIT) : text
  }

  function write() {
    if (!assessment || writing) return
    if (task.aiReplaceable != null) {
      // 已有人工评估：覆盖前明示（AC 4.4）。原生 confirm 与删除任务同一交互惯例。
      const confirmed = window.confirm('该任务已有人工评估，AI 评估将覆盖原值，确定写入吗？')
      if (!confirmed) return
    }
    setWriting(true)
    setErrorMessage(null)
    const note = toNote(assessment.verdict, assessment.explanation)
    // PUT 是整体替换：没在本次决策里的字段必须原样回传，漏一个就会把别处的值清掉
    const input: TaskUpdateInput = {
      title: task.title,
      description: task.description,
      source: task.source,
      importance: task.importance,
      urgency: task.urgency,
      // 优先级显式传 null：本次决策不改优先级，也别让后端重推手工覆盖过的值
      priority: null,
      status: task.status,
      dueOn: task.dueOn,
      estimatedMinutes: task.estimatedMinutes,
      aiReplaceable: assessment.verdict === 'NO' ? false : true,
      aiReplacementNote: note,
      nextAction: task.nextAction,
      notes: task.notes,
      projectId: task.projectId,
      parentTaskId: task.parentTaskId,
      version: task.version,
    }
    tasksApi
      .update(task.id, input)
      .then((updated) => {
        setAssessment(null)
        setNotice('已写入「AI 可替代」与说明')
        onWritten(updated.aiReplaceable === true, updated.aiReplacementNote ?? '')
        void queryClient.invalidateQueries({ queryKey: queryKeys.task(task.id) })
        void queryClient.invalidateQueries({ queryKey: queryKeys.today })
        void queryClient.invalidateQueries({ queryKey: queryKeys.board })
      })
      .catch((error: unknown) => setErrorMessage(describeError(error)))
      .finally(() => setWriting(false))
  }

  return (
    <div className="rounded-md border border-indigo-100 bg-indigo-50/40 p-3">
      {!assessment ? (
        <button
          type="button"
          onClick={request}
          disabled={requesting}
          className="rounded-md border border-indigo-200 bg-white px-3 py-1.5 text-xs font-medium text-indigo-700 hover:bg-indigo-50 disabled:opacity-50"
        >
          {requesting ? 'AI 评估中…' : 'AI 评估'}
        </button>
      ) : (
        <div className="space-y-2 text-sm">
          <p className="flex flex-wrap items-center gap-2">
            <span
              className={`rounded px-1.5 py-0.5 text-xs font-medium ${VERDICT_BADGE_CLASSES[assessment.verdict]}`}
            >
              {REPLACEABILITY_LABELS[assessment.verdict]}
            </span>
            <span className="text-slate-700">{assessment.explanation}</span>
          </p>
          {task.aiReplaceable != null && (
            <p className="text-xs text-amber-700">写入将覆盖已有的人工评估。</p>
          )}
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={write}
              disabled={writing}
              className="rounded bg-slate-900 px-2 py-1 text-xs font-medium text-white hover:bg-slate-700 disabled:opacity-50"
            >
              {writing ? '写入中…' : '写入'}
            </button>
            <button
              type="button"
              onClick={() => setAssessment(null)}
              className="text-xs text-slate-500 hover:text-slate-900"
            >
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
