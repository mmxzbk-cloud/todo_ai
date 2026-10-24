/**
 * 耗时统计（US-05），对应后端 stats/dto 的一组 record。
 * 一切口径与后端一致：流水为权威数据、按用户时区归日、进行中的计时不进合计。
 */
import type { Priority, TaskStatus } from './task.ts'

/** 单个任务在某口径下的投入。 */
export interface StatsTaskUsage {
  taskId: number
  title: string
  minutes: number
}

/** 按天的投入。date 形如 '2026-10-24'，区间内 0 分钟的日子也会返回。 */
export interface StatsDayUsage {
  date: string
  minutes: number
  topTasks: StatsTaskUsage[]
}

/** 按项目的投入。projectId 为 null 表示「无项目」；share 为 0–1 的小数。 */
export interface StatsProjectUsage {
  projectId: number | null
  projectName: string
  minutes: number
  share: number
}

/** 正在进行的计时。它的 minutes 为 0，不计入任何合计，页面单独展示。 */
export interface StatsRunningTask {
  taskId: number
  taskTitle: string
  startedAt: string
}

export interface TimeStats {
  from: string
  to: string
  totalMinutes: number
  days: StatsDayUsage[]
  projects: StatsProjectUsage[]
  running: StatsRunningTask | null
}

// ---------------------------------------------------------------- 任务概览统计

/** 按天的完成数。date 形如 '2026-10-24'，区间内 0 完成的日子也会返回。 */
export interface StatsDayCompletion {
  date: string
  count: number
}

/**
 * 任务概览统计，对应后端 TaskStatsResponse。
 * byStatus / byPriority 的键是全量的（后端已补 0），可以直接取值；
 * 状态/优先级分布与逾期是「当前快照」，新建/完成数按区间统计。
 */
export interface TaskStats {
  from: string
  to: string
  byStatus: Record<TaskStatus, number>
  byPriority: Record<Priority, number>
  overdue: number
  createdInRange: number
  completedInRange: number
  completedByDay: StatsDayCompletion[]
}
