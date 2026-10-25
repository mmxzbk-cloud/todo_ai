import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

/** 单根柱子：label 是横轴刻度（如 '10-24'），detail 是提示里的附加行（如当天主要投入）。 */
export interface StatsBarDatum {
  label: string
  value: number
  detail?: string
}

interface StatsBarChartProps {
  data: StatsBarDatum[]
  /** SVG 内部的文本读屏拿不到，用这句话向读屏用户概括整张图。 */
  ariaLabel: string
  /** 数值展示格式（如分钟转「X 小时 Y 分」）；不传直接显示数字。 */
  formatValue?: (value: number) => string
}

function ChartTooltip({
  active,
  payload,
  formatValue,
}: {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: StatsBarDatum }>
  formatValue?: (value: number) => string
}) {
  const datum = payload?.[0]?.payload
  if (!active || !datum) return null
  return (
    <div className="rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs shadow-sm">
      <p className="tabular-nums text-slate-500">{datum.label}</p>
      <p className="mt-0.5 font-medium tabular-nums text-slate-900">
        {formatValue ? formatValue(datum.value) : datum.value}
      </p>
      {datum.detail && <p className="mt-0.5 text-slate-500">{datum.detail}</p>}
    </div>
  )
}

/**
 * 统计页共用的柱状图（完成趋势、按天耗时）。柱色固定 slate-700，沿用旧版比例条的用色；
 * 数值细节靠悬停提示与页面上的明细分页列表兜底。
 */
export function StatsBarChart({ data, ariaLabel, formatValue }: StatsBarChartProps) {
  return (
    <div role="img" aria-label={ariaLabel}>
      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 10, fill: '#64748b' }}
            tickLine={false}
            axisLine={{ stroke: '#cbd5e1' }}
            interval="preserveStartEnd"
          />
          <YAxis
            allowDecimals={false}
            width={30}
            tick={{ fontSize: 10, fill: '#64748b' }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            cursor={{ fill: 'rgba(100, 116, 139, 0.08)' }}
            content={<ChartTooltip formatValue={formatValue} />}
          />
          {/* 明细分页、缓存重取都会让数据数组换个引用，入场动画会跟着重播一遍造成闪烁，直接关掉。 */}
          <Bar dataKey="value" fill="#334155" radius={[2, 2, 0, 0]} maxBarSize={28} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
