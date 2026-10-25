import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'

import { ApiError } from '../api/client.ts'
import { describeError, useBatchCreateTasks } from '../hooks/useTasks.ts'

/** 与后端 TaskService.MAX_BATCH_SIZE 保持一致：一次批量导入的任务数上限。 */
const MAX_BATCH_SIZE = 50

/**
 * 行首的序号 / 项目符，拆条时剥掉：
 * - 符号类（- * • ·）要求后面有空白，避免吃掉「-5℃ 记录体温」这类以符号开头的正文；
 * - 「1.」要求后面不跟数字，不然 3.14 这类小数开头的标题会被误伤；「1)」「1、」直接匹配；
 * - 中文序号只认「一、」这种带顿号的写法，「三步走」不受影响。
 */
const LIST_MARKER =
  /^(?:[-*•·]+\s+|\d{1,3}\.(?!\d)\s*|\d{1,3}[)、]\s*|[（(]\d{1,3}[)）]\s*|[①-⑳]\s*|[一二三四五六七八九十]{1,3}、\s*)/

/** 按行拆条：每行一条待办，剥掉行首序号与项目符，空行跳过。 */
function splitTaskLines(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.replace(LIST_MARKER, '').trim())
    .filter((line) => line !== '')
}

/**
 * 后端字段错误的键是 tasks[2].title 这种形态（Bean Validation 对集合元素的路径），
 * 换算成「第 3 条」用户才找得到是哪一行出了问题；其余错误原样透传。
 */
function batchErrorLines(error: unknown): string[] {
  if (!(error instanceof ApiError)) {
    return [describeError(error)]
  }
  const lineErrors = Object.entries(error.fieldErrors)
    .map(([field, message]) => {
      const match = /^tasks\[(\d+)\]/.exec(field)
      return match ? { index: Number(match[1]), message } : null
    })
    .filter((entry): entry is { index: number; message: string } => entry !== null)
    .sort((a, b) => a.index - b.index)
  if (lineErrors.length === 0) {
    return [describeError(error)]
  }
  return lineErrors.map((entry) => `第 ${entry.index + 1} 条：${entry.message}`)
}

/**
 * 批量导入：一段多行文本一次拆成多条待办。
 * 拆分是前端的确定性规则（按行 + 剥序号），提交前先预览识别结果——
 * 识别错了改一下文本再导就行，不会产生「建错了要逐条删」的成本。
 */
export function BatchTaskImport() {
  const batchCreate = useBatchCreateTasks()
  const [open, setOpen] = useState(false)
  const [text, setText] = useState('')
  const [notice, setNotice] = useState<string | null>(null)

  const titles = useMemo(() => splitTaskLines(text), [text])
  const tooMany = titles.length > MAX_BATCH_SIZE
  const canSubmit = titles.length > 0 && !tooMany && !batchCreate.isPending

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setNotice(null)
    batchCreate.mutate(
      titles.map((title) => ({ title })),
      {
        onSuccess: (created) => {
          setNotice(`已导入 ${created.length} 条待办。`)
          setText('')
        },
      },
    )
  }

  if (!open) {
    return (
      <div className="mt-4 border-t border-slate-100 pt-4">
        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-expanded={false}
          className="text-sm text-slate-500 hover:text-slate-900"
        >
          批量导入（粘贴多行文本，自动拆成多条待办）
        </button>
      </div>
    )
  }

  return (
    <div className="mt-4 border-t border-slate-100 pt-4">
      <form onSubmit={handleSubmit} noValidate className="space-y-2">
        <div className="flex items-center justify-between">
          <label htmlFor="batch-task-text" className="text-sm font-medium text-slate-700">
            批量导入
          </label>
          <button
            type="button"
            onClick={() => setOpen(false)}
            aria-expanded
            className="text-sm text-slate-500 hover:text-slate-900"
          >
            收起
          </button>
        </div>
        <textarea
          id="batch-task-text"
          value={text}
          onChange={(event) => setText(event.target.value)}
          rows={6}
          placeholder={'一行一条待办，带序号或项目符也没关系，会自动剥掉：\n1. 买菜\n2. 写周报\n- 预约体检'}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
        />
        <div className="flex flex-wrap items-center gap-3">
          <span aria-live="polite" className="text-sm text-slate-500">
            {tooMany
              ? `识别出 ${titles.length} 条，一次最多导入 ${MAX_BATCH_SIZE} 条`
              : titles.length === 0
                ? '每行识别为一条待办'
                : `识别出 ${titles.length} 条待办`}
          </span>
          <button
            type="submit"
            disabled={!canSubmit}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
          >
            {batchCreate.isPending ? '导入中…' : `导入 ${Math.min(titles.length, MAX_BATCH_SIZE)} 条`}
          </button>
        </div>
      </form>

      {titles.length > 0 && !tooMany && (
        <ol className="mt-2 max-h-40 list-inside list-decimal space-y-1 overflow-y-auto rounded-md bg-slate-50 p-3 text-sm text-slate-600">
          {titles.map((title, index) => (
            <li key={`${index}-${title}`}>{title}</li>
          ))}
        </ol>
      )}

      {tooMany && (
        <p role="alert" className="mt-2 text-sm text-red-600">
          超出单次上限，请分批导入。
        </p>
      )}
      {batchCreate.error &&
        batchErrorLines(batchCreate.error)
          .slice(0, 3)
          .map((line) => (
            <p key={line} role="alert" className="mt-2 text-sm text-red-600">
              {line}
            </p>
          ))}
      {notice && (
        <p role="status" className="mt-2 text-sm text-emerald-700">
          {notice}
        </p>
      )}
    </div>
  )
}
