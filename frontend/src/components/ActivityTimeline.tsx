import type { Activity } from '../types/task.ts'

/**
 * 对应后端 audit.TaskAction，未知动作原样显示，后端新增动作时不至于显示成空白。
 * 值类型写成 string | undefined 是为了让下面的 ?? 兜底成立（原因见 TimeLogList 的同类注释）。
 */
const ACTION_LABELS: Record<string, string | undefined> = {
  CREATED: '创建',
  UPDATED: '更新',
  STATUS_CHANGED: '状态变更',
  DELETED: '删除',
  TIMER_STARTED: '开始计时',
  TIMER_STOPPED: '结束计时',
  TIME_LOGGED: '补录耗时',
}

/** 后端存的是实体列名，直接给用户看英文列名没意义；未登记的字段原样显示。 */
const FIELD_LABELS: Record<string, string | undefined> = {
  title: '标题',
  description: '描述',
  status: '状态',
  priority: '优先级',
  importance: '重要性',
  urgency: '紧急性',
  projectId: '项目',
  parentTaskId: '父任务',
  source: '来源',
  dueOn: '截止日',
  estimatedMinutes: '预计耗时',
  actualMinutes: '实际耗时',
  aiReplaceable: 'AI 可替代',
  aiReplacementNote: 'AI 替代方案',
  nextAction: '下一步',
  notes: '备注',
}

function timeOf(iso: string): number {
  const parsed = Date.parse(iso)
  return Number.isNaN(parsed) ? 0 : parsed
}

function formatMoment(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString()
}

/**
 * 旧值与新值的拼接。只有一侧有值时不画箭头：否则会写出「→ 已完成」这种读不通的记录
 * （创建、开始计时这类动作本来就只有新值）。
 */
function changeText(oldValue: string | null, newValue: string | null): string | null {
  const from = oldValue ?? ''
  const to = newValue ?? ''
  if (from !== '' && to !== '') return `${from} → ${to}`
  if (to !== '') return to
  if (from !== '') return from
  return null
}

interface ActivityTimelineProps {
  activities: Activity[]
}

export function ActivityTimeline({ activities }: ActivityTimelineProps) {
  if (activities.length === 0) {
    return <p className="text-sm text-slate-500">还没有变更记录。</p>
  }

  // 后端返回的顺序不保证，这里按时间倒序排一遍——最新的改动放在最上面才符合「先看最近发生了什么」。
  const ordered = [...activities].sort((a, b) => timeOf(b.createdAt) - timeOf(a.createdAt))

  return (
    <ul className="space-y-3">
      {ordered.map((item) => {
        const change = changeText(item.oldValue, item.newValue)
        return (
          <li key={item.id} className="border-l border-slate-200 pl-3">
            <p className="text-sm text-slate-900">
              <span className="font-medium">{ACTION_LABELS[item.action] ?? item.action}</span>
              {item.field && (
                <span className="ml-2 text-slate-500">{FIELD_LABELS[item.field] ?? item.field}</span>
              )}
            </p>
            {change && <p className="mt-0.5 break-all text-sm text-slate-600">{change}</p>}
            <p className="mt-0.5 text-xs text-slate-400">{formatMoment(item.createdAt)}</p>
          </li>
        )
      })}
    </ul>
  )
}
