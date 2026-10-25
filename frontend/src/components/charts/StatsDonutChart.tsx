import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'

/** 单个扇区：name 进图例与提示，value 决定弧长（分钟、条数均可）。 */
export interface StatsDonutDatum {
  name: string
  value: number
}

interface StatsDonutChartProps {
  data: StatsDonutDatum[]
  /** 扇区颜色，与 data 顺序一一对应；超出长度时循环取色。 */
  colors: string[]
  /** SVG 内部的文本读屏拿不到，用这句话向读屏用户概括整张图。 */
  ariaLabel: string
  /** 数值展示格式（如分钟转「X 小时 Y 分」）；不传直接显示数字。 */
  formatValue?: (value: number) => string
}

function ChartTooltip({
  active,
  payload,
  formatValue,
  total,
}: {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: unknown }>
  formatValue?: (value: number) => string
  total: number
}) {
  const datum = payload?.[0]?.payload as StatsDonutDatum | undefined
  if (!active || !datum) return null
  const share = total > 0 ? Math.round((datum.value / total) * 1000) / 10 : 0
  return (
    <div className="rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs shadow-sm">
      <p className="text-slate-500">{datum.name}</p>
      <p className="mt-0.5 font-medium tabular-nums text-slate-900">
        {formatValue ? formatValue(datum.value) : datum.value}（{share}%）
      </p>
    </div>
  )
}

/**
 * 统计页共用的环形图（优先级分布、按项目耗时）。提示里带占比，替代旧版比例条旁边的百分比列；
 * 全量为 0 时调用方不该渲染本组件（没有弧可画）。
 */
export function StatsDonutChart({ data, colors, ariaLabel, formatValue }: StatsDonutChartProps) {
  const total = data.reduce((sum, datum) => sum + datum.value, 0)
  return (
    <div role="img" aria-label={ariaLabel}>
      <ResponsiveContainer width="100%" height={220}>
        <PieChart>
          {/* 父组件重渲染会让 data 换引用，入场动画跟着重播造成闪烁，直接关掉。 */}
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            innerRadius="55%"
            outerRadius="80%"
            paddingAngle={2}
            stroke="none"
            isAnimationActive={false}
          >
            {data.map((datum, index) => (
              <Cell key={datum.name} fill={colors[index % colors.length]} />
            ))}
          </Pie>
          <Tooltip content={<ChartTooltip formatValue={formatValue} total={total} />} />
          <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}
