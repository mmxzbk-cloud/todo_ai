/**
 * 任务域的类型与常量，取值必须与后端完全一致：
 * 枚举在后端是 Java enum、在库里是带 CHECK 约束的字符串；这里用 const 数组 + 联合类型表达。
 * 之所以不用 TS enum：tsconfig 开了 erasableSyntaxOnly，enum 会被编译成运行时代码，不允许使用。
 */
import type { Tag } from './tag.ts'

// ---------------------------------------------------------------- 状态

export const TASK_STATUSES = ['TODO', 'DOING', 'BLOCKED', 'DONE', 'CANCELLED'] as const

export type TaskStatus = (typeof TASK_STATUSES)[number]

/** 看板固定四列：CANCELLED 不上看板（取消的任务只在列表与详情里查得到），与后端 TaskStatus.BOARD_ORDER 一致。 */
export const BOARD_STATUSES = ['TODO', 'DOING', 'BLOCKED', 'DONE'] as const

export type BoardStatus = (typeof BOARD_STATUSES)[number]

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: '待办',
  DOING: '进行中',
  BLOCKED: '阻塞',
  DONE: '已完成',
  CANCELLED: '已取消',
}

// ---------------------------------------------------------------- 优先级矩阵

export const IMPORTANCE_LEVELS = ['HIGH', 'MEDIUM', 'LOW'] as const

export type Importance = (typeof IMPORTANCE_LEVELS)[number]

export const URGENCY_LEVELS = ['HIGH', 'MEDIUM', 'LOW'] as const

export type Urgency = (typeof URGENCY_LEVELS)[number]

export const PRIORITIES = ['P0', 'P1', 'P2', 'P3'] as const

export type Priority = (typeof PRIORITIES)[number]

export const IMPORTANCE_LABELS: Record<Importance, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' }

export const URGENCY_LABELS: Record<Urgency, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' }

/**
 * P0–P3 的字符串本身就是给人看的标签（不像状态那样是内部代号），所以这里不做翻译，
 * 保留这个映射只是为了「所有枚举展示文案都从这里取」这条规矩不被破例。
 */
export const PRIORITY_LABELS: Record<Priority, string> = { P0: 'P0', P1: 'P1', P2: 'P2', P3: 'P3' }

// ---------------------------------------------------------------- 出参

/**
 * 对应后端 task/dto/TaskResponse，字段名与顺序都与它一一对应。
 * 注意这里只放看板与列表需要的字段全集：详情页的标签/子任务/耗时明细/变更历史在 TaskDetail 上。
 */
export interface TaskSummary {
  id: number
  title: string
  description: string | null
  projectId: number | null
  projectName: string | null
  /** 项目色由服务端随项目一起回填，卡片直接用它画色点，不必前端再查项目列表。 */
  projectColor: string | null
  /** 列表上直接可展示的标签（按名字排序）；与标签的关联由服务端批量回填。 */
  tags: Tag[]
  parentTaskId: number | null
  source: string | null
  importance: Importance
  urgency: Urgency
  priority: Priority
  status: TaskStatus
  /** 只有日期语义，形如 '2026-10-18'；可以直接当字符串比较（ISO 日期字典序即时间序）。 */
  dueOn: string | null
  estimatedMinutes: number | null
  actualMinutes: number
  /** 三态：null = 未评估，与 false（评估为不可替代）是两回事。 */
  aiReplaceable: boolean | null
  aiReplacementNote: string | null
  nextAction: string | null
  notes: string | null
  completedAt: string | null
  sortOrder: number
  version: number
  createdAt: string
  updatedAt: string
}

/**
 * 一段耗时流水。endedAt 为 null 表示这一段还在跑。
 * source 后端返回的是字符串原值，这里收窄成两个字面量，前端拿到未知值时也不该崩溃。
 */
export interface TimeLog {
  id: number
  taskId: number
  startedAt: string
  endedAt: string | null
  minutes: number
  source: 'TIMER' | 'MANUAL'
  note: string | null
}

/** 一条变更审计。action 是后端枚举名（CREATED/STATUS_CHANGED/…），保留 string 以便后端新增动作时前端不崩。 */
export interface Activity {
  id: number
  action: string
  field: string | null
  oldValue: string | null
  newValue: string | null
  actorId: number | null
  createdAt: string
}

/**
 * 对应后端 TaskDetailResponse：**嵌套**结构，不是 TaskSummary 的扩展。
 * 读标题要走 detail.task.title，别把它当平面对象用。
 */
export interface TaskDetail {
  task: TaskSummary
  tags: Tag[]
  children: TaskSummary[]
  timeLogs: TimeLog[]
  activities: Activity[]
}

/** 当前正在跑的计时：带上任务，卡片不必再为「在给哪个任务计时」多请求一次。 */
export interface RunningTimer {
  log: TimeLog
  task: TaskSummary
}

/** 结束计时的出参与 RunningTimer 同形（闭合后的那一段 + 更新后的任务），用别名避免两处各写一份。 */
export type TimerStopResult = RunningTimer

/** 看板一列。看板固定四列，空列也会返回，所以 tasks 可能是空数组而不是缺这一列。 */
export interface BoardColumn {
  status: TaskStatus
  tasks: TaskSummary[]
}

/** 对应后端 common/PageResponse。page 从 0 开始，与后端一致。 */
export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

// ---------------------------------------------------------------- 入参

/**
 * 新建入参，对应后端 TaskRequest（不含 version）。
 * 字段名与后端一致；后端对未带的字段落库默认值，所以除 title 外都可省略。
 */
export interface TaskInput {
  title: string
  description?: string | null
  projectId?: number | null
  parentTaskId?: number | null
  source?: string | null
  importance?: Importance | null
  urgency?: Urgency | null
  priority?: Priority | null
  status?: TaskStatus | null
  dueOn?: string | null
  estimatedMinutes?: number | null
  aiReplaceable?: boolean | null
  aiReplacementNote?: string | null
  nextAction?: string | null
  notes?: string | null
}

/**
 * 更新入参。后端 PUT 是**整体替换**：文本类字段传 null 表示清空（不是「不改」，
 * 所以清空必须显式写 null，不能靠省略字段达到目的）；importance/urgency/status 传 null 表示不修改。
 * version 只在更新时有意义，带上且与库里不一致时后端返回 409 VERSION_CONFLICT。
 */
export interface TaskUpdateInput extends TaskInput {
  version?: number
}

/** 列表查询参数，对应后端 GET /api/v1/tasks 的查询串。page 从 0 开始。 */
export interface TaskListParams {
  status?: TaskStatus
  projectId?: number
  /** ISO 日期（含），形如 '2026-10-01'。 */
  dueFrom?: string
  /** ISO 日期（含）。 */
  dueTo?: string
  q?: string
  page?: number
  size?: number
}
