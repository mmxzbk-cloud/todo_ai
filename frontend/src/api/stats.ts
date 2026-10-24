import type { TaskStats, TimeStats } from '../types/stats.ts'
import { request } from './client.ts'

/**
 * 统计接口。只读、不受 AI 总开关影响。
 * 耗时统计的 from/to 是 ISO 日期（含两端），区间最长 31 天，超了后端返回 400；
 * 任务概览统计用天数（今天往前 N 天，含今天），同样上限 31 天。
 */
export const statsApi = {
  time: (from: string, to: string) => request<TimeStats>(`/api/v1/stats/time?from=${from}&to=${to}`),
  tasks: (days: number) => request<TaskStats>(`/api/v1/stats/tasks?days=${days}`),
}
