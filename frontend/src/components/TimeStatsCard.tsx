import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { statsApi } from '../api/stats.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { describeError } from '../hooks/useTasks.ts'
import type { StatsDayUsage } from '../types/stats.ts'

type RangeKey = 'today' | '7d' | '30d'

const RANGE_OPTIONS: { key: RangeKey; label: string }[] = [
  { key: 'today', label: '今天' },
  { key: '7d', label: '最近 7 天' },
  { key: '30d', label: '最近 30 天' },
]

/** 本地「今天」的 ISO 日期。不能用 toISOString：它按 UTC 取日期，东八区晚上会差一天。 */
function localToday(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function localDateMinusDays(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function rangeFor(key: RangeKey): { from: string; to: string } {
  const today = localToday()
  switch (key) {
    case 'today':
      return { from: today, to: today }
    case '7d':
      return { from: localDateMinusDays(6), to: today }
    case '30d':
      return { from: localDateMinusDays(29), to: today }
  }
}

function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} 分`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`
}

function formatPercent(share: number): string {
  return `${Math.round(share * 1000) / 10}%`
}

/** 当天柱状行：日期 + 比例条 + 分钟数 + 投入最高的任务。 */
function DayRow({ day, maxMinutes }: { day: StatsDayUsage; maxMinutes: number }) {
  const width = maxMinutes > 0 ? Math.max((day.minutes / maxMinutes) * 100, day.minutes > 0 ? 4 : 0) : 0
  const topTask = day.topTasks[0]
  return (
    <li className="text-sm">
      <div className="flex items-baseline gap-2">
        <span className="w-12 shrink-0 tabular-nums text-slate-500">{day.date.slice(5)}</span>
        <span className="min-w-0 flex-1">
          <span className="block h-2 rounded bg-slate-200">
            <span className="block h-2 rounded bg-slate-700" style={{ width: `${width}%` }} />
          </span>
        </span>
        <span className="w-16 shrink-0 text-right tabular-nums text-slate-900">
          {day.minutes > 0 ? formatMinutes(day.minutes) : '—'}
        </span>
      </div>
      {topTask && day.minutes > 0 && (
        <p className="ml-14 truncate text-xs text-slate-400" title={topTask.title}>
          主要投入:{topTask.title}
        </p>
      )}
    </li>
  )
}

/**
 * 耗时统计卡片（US-05）。只读，不受 AI 总开关影响；
 * 默认显示今天，可切最近 7 天 / 30 天（AC 5.3）。
 * 进行中的计时分钟数为 0、不计入合计，这里以「进行中」单独展示（AC 5.7）。
 */
export function TimeStatsCard() {
  const [rangeKey, setRangeKey] = useState<RangeKey>('today')
  const { from, to } = rangeFor(rangeKey)

  const stats = useQuery({
    queryKey: queryKeys.timeStats(from, to),
    queryFn: () => statsApi.time(from, to),
  })

  const data = stats.data
  const maxDayMinutes = data ? Math.max(...data.days.map((day) => day.minutes), 0) : 0

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-base font-medium text-slate-900">耗时统计</h2>
        <div className="flex items-center gap-1" role="group" aria-label="统计区间">
          {RANGE_OPTIONS.map((option) => (
            <button
              key={option.key}
              type="button"
              onClick={() => setRangeKey(option.key)}
              aria-pressed={rangeKey === option.key}
              className={`rounded-md px-2 py-1 text-xs ${
                rangeKey === option.key
                  ? 'bg-slate-900 font-medium text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {stats.isPending && <p className="text-sm text-slate-500">加载统计中…</p>}

      {stats.isError && (
        <p role="alert" className="text-sm text-red-600">
          {describeError(stats.error)}
        </p>
      )}

      {data && (
        <div className="space-y-4">
          <p className="text-sm text-slate-600">
            合计投入 <span className="text-lg font-semibold tabular-nums text-slate-900">{formatMinutes(data.totalMinutes)}</span>
            {data.running && (
              <span className="ml-3 rounded bg-emerald-50 px-1.5 py-0.5 text-xs text-emerald-700">
                进行中:{data.running.taskTitle}
              </span>
            )}
          </p>

          {data.totalMinutes === 0 && !data.running ? (
            <p className="text-sm text-slate-500">这段时间还没有计时记录。</p>
          ) : (
            <>
              {data.totalMinutes > 0 && (
                <div>
                  <h3 className="mb-2 text-xs font-medium text-slate-500">按天</h3>
                  <ul className="space-y-1.5">
                    {data.days.map((day) => (
                      <DayRow key={day.date} day={day} maxMinutes={maxDayMinutes} />
                    ))}
                  </ul>
                </div>
              )}

              {data.projects.length > 0 && (
                <div>
                  <h3 className="mb-2 text-xs font-medium text-slate-500">按项目</h3>
                  <ul className="space-y-1.5">
                    {data.projects.map((project) => (
                      <li key={project.projectId ?? 'none'} className="flex items-center gap-2 text-sm">
                        <span className="min-w-0 flex-1 truncate text-slate-700">{project.projectName}</span>
                        <span className="h-2 w-24 shrink-0 rounded bg-slate-200">
                          <span
                            className="block h-2 rounded bg-slate-500"
                            style={{ width: `${Math.round(project.share * 100)}%` }}
                          />
                        </span>
                        <span className="w-24 shrink-0 text-right tabular-nums text-slate-900">
                          {formatMinutes(project.minutes)}（{formatPercent(project.share)}）
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </section>
  )
}
