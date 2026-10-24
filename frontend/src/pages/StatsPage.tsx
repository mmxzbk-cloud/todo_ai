import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { statsApi } from '../api/stats.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { TimeStatsCard } from '../components/TimeStatsCard.tsx'
import { describeError } from '../hooks/useTasks.ts'
import { PRIORITIES, TASK_STATUSES, TASK_STATUS_LABELS } from '../types/task.ts'
import type { StatsDayCompletion } from '../types/stats.ts'

type RangeKey = '7d' | '30d'

const RANGE_OPTIONS: { key: RangeKey; label: string }[] = [
  { key: '7d', label: '最近 7 天' },
  { key: '30d', label: '最近 30 天' },
]

/** 概览统计不提供「今天」单日档：单日的完成数没有回顾价值，最小 7 天。 */
const DAYS_FOR: Record<RangeKey, number> = { '7d': 7, '30d': 30 }

function formatCountBar(count: number, max: number): string {
  if (max <= 0 || count <= 0) return '0%'
  return `${Math.max(Math.round((count / max) * 100), 4)}%`
}

/** 概览数字块：一个标签 + 一个大数字，逾期等告警口径用 tone 染色。 */
function StatBox({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${tone ?? 'text-slate-900'}`}>{value}</p>
    </div>
  )
}

/** 完成趋势的单日行：日期 + 比例条 + 完成数。 */
function DayCompletionRow({ day, maxCount }: { day: StatsDayCompletion; maxCount: number }) {
  return (
    <li className="text-sm">
      <div className="flex items-baseline gap-2">
        <span className="w-12 shrink-0 tabular-nums text-slate-500">{day.date.slice(5)}</span>
        <span className="min-w-0 flex-1">
          <span className="block h-2 rounded bg-slate-200">
            <span className="block h-2 rounded bg-slate-700" style={{ width: formatCountBar(day.count, maxCount) }} />
          </span>
        </span>
        <span className="w-8 shrink-0 text-right tabular-nums text-slate-900">{day.count}</span>
      </div>
    </li>
  )
}

/**
 * 统计：任务概览与耗时汇总的回顾页。
 *
 * 上半部分是任务口径（状态/优先级分布、逾期、区间内新建与完成趋势），
 * 下半部分直接复用今日页的耗时统计卡片（US-05），两份数据各管各的口径，不互相换算。
 */
function StatsPage() {
  const [rangeKey, setRangeKey] = useState<RangeKey>('30d')
  const days = DAYS_FOR[rangeKey]

  const stats = useQuery({
    queryKey: queryKeys.taskStats(days),
    queryFn: () => statsApi.tasks(days),
  })

  const data = stats.data
  const totalTasks = data ? Object.values(data.byStatus).reduce((sum, count) => sum + count, 0) : 0
  const maxDayCount = data ? Math.max(...data.completedByDay.map((day) => day.count), 0) : 0
  const maxPriorityCount = data ? Math.max(...Object.values(data.byPriority), 0) : 0

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold">统计</h1>
        <p className="mt-1 text-sm text-slate-500">
          任务分布与完成趋势，配合耗时统计看「时间花在哪」。
        </p>
      </header>

      {stats.isPending && <p className="text-sm text-slate-500">加载统计中…</p>}

      {stats.isError && (
        <p role="alert" className="text-sm text-red-600">
          {describeError(stats.error)}
        </p>
      )}

      {data && (
        <>
          <section className="rounded-lg border border-slate-200 bg-white p-5">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-base font-medium text-slate-900">任务概览</h2>
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

            {totalTasks === 0 ? (
              <p className="text-sm text-slate-500">还没有任务，先去今日页记一条。</p>
            ) : (
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                  {TASK_STATUSES.map((status) => (
                    <StatBox
                      key={status}
                      label={TASK_STATUS_LABELS[status]}
                      value={data.byStatus[status]}
                    />
                  ))}
                  <StatBox
                    label="已逾期"
                    value={data.overdue}
                    tone={data.overdue > 0 ? 'text-rose-600' : 'text-slate-900'}
                  />
                </div>

                <p className="text-sm text-slate-600">
                  近 {days} 天新建 <span className="font-semibold tabular-nums text-slate-900">{data.createdInRange}</span> 条，
                  完成 <span className="font-semibold tabular-nums text-slate-900">{data.completedInRange}</span> 条。
                </p>

                <div>
                  <h3 className="mb-2 text-xs font-medium text-slate-500">完成趋势（按天）</h3>
                  <ul className="space-y-1.5">
                    {data.completedByDay.map((day) => (
                      <DayCompletionRow key={day.date} day={day} maxCount={maxDayCount} />
                    ))}
                  </ul>
                </div>

                <div>
                  <h3 className="mb-2 text-xs font-medium text-slate-500">优先级分布</h3>
                  <ul className="space-y-1.5">
                    {PRIORITIES.map((priority) => (
                      <li key={priority} className="flex items-center gap-2 text-sm">
                        <span className="w-8 shrink-0 text-slate-700">{priority}</span>
                        <span className="h-2 w-24 shrink-0 rounded bg-slate-200">
                          <span
                            className="block h-2 rounded bg-slate-500"
                            style={{ width: formatCountBar(data.byPriority[priority], maxPriorityCount) }}
                          />
                        </span>
                        <span className="tabular-nums text-slate-900">{data.byPriority[priority]}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            )}
          </section>

          {/* 耗时统计（US-05）：区间切换在卡片内部，与任务概览互不干扰。 */}
          <TimeStatsCard />
        </>
      )}
    </div>
  )
}

export default StatsPage
