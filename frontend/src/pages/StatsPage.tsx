import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'

import { statsApi } from '../api/stats.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { StatsBarChart } from '../components/charts/StatsBarChart.tsx'
import { StatsDonutChart } from '../components/charts/StatsDonutChart.tsx'
import { TimeStatsCard } from '../components/TimeStatsCard.tsx'
import { describeError } from '../hooks/useTasks.ts'
import { PRIORITIES, TASK_STATUSES, TASK_STATUS_LABELS } from '../types/task.ts'

type RangeKey = '7d' | '30d'

const RANGE_OPTIONS: { key: RangeKey; label: string }[] = [
  { key: '7d', label: '最近 7 天' },
  { key: '30d', label: '最近 30 天' },
]

/** 概览统计不提供「今天」单日档：单日的完成数没有回顾价值，最小 7 天。 */
const DAYS_FOR: Record<RangeKey, number> = { '7d': 7, '30d': 30 }

/** 与优先级徽标同一套色相（PrioritySelect 的 P0 rose / P1 amber / P2 sky / P3 slate），加深到 -600 保证白底可读。 */
const PRIORITY_CHART_COLORS: Record<string, string> = {
  P0: '#e11d48',
  P1: '#d97706',
  P2: '#0284c7',
  P3: '#94a3b8',
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
                  <StatsBarChart
                    ariaLabel={`近 ${days} 天每天完成任务数柱状图`}
                    data={data.completedByDay.map((day) => ({ label: day.date.slice(5), value: day.count }))}
                  />
                </div>

                <div>
                  <h3 className="mb-2 text-xs font-medium text-slate-500">优先级分布</h3>
                  <StatsDonutChart
                    ariaLabel="各优先级任务数环形图"
                    colors={PRIORITIES.map((priority) => PRIORITY_CHART_COLORS[priority])}
                    data={PRIORITIES.map((priority) => ({ name: priority, value: data.byPriority[priority] }))}
                  />
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
