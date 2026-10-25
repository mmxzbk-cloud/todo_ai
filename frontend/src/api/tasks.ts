import type {
  BoardColumn,
  PageResponse,
  Priority,
  RunningTimer,
  TaskDetail,
  TaskInput,
  TaskListParams,
  TaskStatus,
  TaskSummary,
  TaskUpdateInput,
  TimeLog,
  TimerStopResult,
} from '../types/task.ts'
import { request } from './client.ts'

const TASKS = '/api/v1/tasks'

/**
 * 把查询参数拼成 query string，跳过没值的字段。
 * 不能把 undefined 直接交给 URLSearchParams：它会被序列化成字面量 "undefined"，
 * 后端的 LocalDate / Long 绑定拿到这个字符串会直接 400。
 */
function toQueryString(params: TaskListParams): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === '') continue
    search.set(key, String(value))
  }
  const query = search.toString()
  return query === '' ? '' : `?${query}`
}

/**
 * 任务接口。函数名是跨任务冻结的契约（详情页与设置页会 import），不要改名或改语义。
 */
export const tasksApi = {
  /** 分页列表。后端默认 page=0、size=20。 */
  list: (params: TaskListParams) =>
    request<PageResponse<TaskSummary>>(`${TASKS}${toQueryString(params)}`),

  /** 今日视图：未删 + 截止日 ≤ 今天（按用户时区）+ 未完成/未取消，服务端已按截止日与优先级排好。 */
  today: () => request<TaskSummary[]>(`${TASKS}/today`),

  /** 看板：固定四列，空列也在。 */
  board: () => request<BoardColumn[]>(`${TASKS}/board`),

  get: (id: number) => request<TaskDetail>(`${TASKS}/${id}`),

  create: (input: TaskInput) =>
    request<TaskSummary>(TASKS, { method: 'POST', body: JSON.stringify(input) }),

  /**
   * 批量导入：一次提交多条，后端在同一个事务里逐条创建（任一条失败整批回滚），
   * 返回按输入顺序排列的创建结果。上限与后端 TaskService.MAX_BATCH_SIZE 一致（50）。
   */
  batchCreate: (inputs: TaskInput[]) =>
    request<TaskSummary[]>(`${TASKS}/batch`, { method: 'POST', body: JSON.stringify({ tasks: inputs }) }),

  /** 整体替换；带 version 时做乐观锁校验，冲突返回 409 VERSION_CONFLICT。 */
  update: (id: number, input: TaskUpdateInput) =>
    request<TaskSummary>(`${TASKS}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  changeStatus: (id: number, status: TaskStatus) =>
    request<TaskSummary>(`${TASKS}/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  /** 只改优先级；与 changeStatus 同形，落库的是手工覆盖值。 */
  changePriority: (id: number, priority: Priority) =>
    request<TaskSummary>(`${TASKS}/${id}/priority`, {
      method: 'PATCH',
      body: JSON.stringify({ priority }),
    }),

  /** 交上去的是该任务最终应有的完整标签集合，服务端整体替换，不是增量。 */
  assignTags: (id: number, tagIds: number[]) =>
    request<TaskDetail>(`${TASKS}/${id}/tags`, {
      method: 'PUT',
      body: JSON.stringify({ tagIds }),
    }),

  /** 软删。后端返回 204 无响应体。 */
  remove: (id: number) => request<void>(`${TASKS}/${id}`, { method: 'DELETE' }),

  /**
   * 当前有没有计时在跑。后端没有时回 204，而 client 把 204 映射成 undefined；
   * 这里统一成 null，调用方只判断「有没有」，不必区分 undefined 与 null 两种「没有」。
   */
  runningTimer: (): Promise<RunningTimer | null> =>
    request<RunningTimer | undefined>('/api/v1/timers/running').then((value) => value ?? null),

  /** 已有计时在跑时后端返回 409 TIMER_ALREADY_RUNNING。 */
  startTimer: (id: number) => request<TimeLog>(`${TASKS}/${id}/timers`, { method: 'POST' }),

  /** markDone=true 时后端在同一个事务里把任务置为已完成。 */
  stopTimer: (id: number, logId: number, markDone: boolean) =>
    request<TimerStopResult>(`${TASKS}/${id}/timers/${logId}/stop?markDone=${markDone}`, {
      method: 'POST',
    }),

  /** 手工补录耗时，只允许补已发生过的区间。 */
  addTimeLog: (id: number, input: { startedAt: string; endedAt: string; note?: string }) =>
    request<TimeLog>(`${TASKS}/${id}/time-logs`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
}
