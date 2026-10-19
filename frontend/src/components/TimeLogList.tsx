import type { TimeLog } from '../types/task.ts'

/**
 * source 后端存的是字符串（TIMER / MANUAL），这里只做展示映射，遇到未知值原样显示。
 * 值类型写成 string | undefined 是为了让下面的 ?? 兜底成立：索引签名本身返回 string，
 * TS 会判定 `??` 的右操作数不可达并报错（右侧永远取不到）。
 */
const SOURCE_LABELS: Record<string, string | undefined> = {
  TIMER: '计时',
  MANUAL: '手工补录',
}

function formatMoment(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString()
}

interface TimeLogListProps {
  timeLogs: TimeLog[]
}

export function TimeLogList({ timeLogs }: TimeLogListProps) {
  // 正在跑的那段 minutes 还是 0，本来就没什么可加的，但它会让「正在进行的记录」显示成 0 分钟，
  // 所以下面单独把它标成「进行中」而不是显示 0。
  const totalMinutes = timeLogs.reduce((sum, log) => sum + log.minutes, 0)

  if (timeLogs.length === 0) {
    return <p className="text-sm text-slate-500">还没有耗时记录，点上面的「开始计时」试试。</p>
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-slate-600">
        合计 <span className="font-medium tabular-nums text-slate-900">{totalMinutes}</span> 分钟
      </p>

      <ul className="divide-y divide-slate-100">
        {timeLogs.map((log) => (
          <li key={log.id} className="flex flex-wrap items-baseline justify-between gap-2 py-2 text-sm">
            <span className="text-slate-700">
              {formatMoment(log.startedAt)} → {log.endedAt == null ? '进行中' : formatMoment(log.endedAt)}
            </span>
            <span className="flex items-center gap-2">
              <span className="tabular-nums text-slate-900">{log.minutes} 分钟</span>
              <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
                {SOURCE_LABELS[log.source] ?? log.source}
              </span>
              {log.note && <span className="text-xs text-slate-400">{log.note}</span>}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
