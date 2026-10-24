/**
 * 任务域的查询与失效规则。
 *
 * 放在一处的理由：一次写操作要失效哪几个缓存（例如改状态要同时刷今日、看板、详情），
 * 这是服务端数据的口径问题，不是页面渲染问题。散到页面里的话，同一个规则会被写三遍，
 * 而且总会漏掉一处——症状是「在 A 页面改完，B 页面还是旧数据」。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '../api/client.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { tasksApi } from '../api/tasks.ts'
import type {
  BoardColumn,
  Priority,
  TaskInput,
  TaskListParams,
  TaskStatus,
  TaskSummary,
} from '../types/task.ts'

/**
 * 把任意异常翻译成能给用户看的一句话。
 * 放在这里而不是新建一个 utils 模块：调用方只有本文件里的几个 mutation 和它们所在的页面，
 * 为四行代码单开一个文件不划算。
 */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '发生未知错误，请看浏览器控制台'
}

/**
 * 读后端的稳定错误码（如 TIMER_ALREADY_RUNNING）。用属性探测而不是直接写 error.code：
 * client.ts 由另一个任务提供，code 字段晚于本次改动合入时这里也不会编译不过，
 * 代价只是这一处看起来绕了一层。
 */
export function errorCode(error: unknown): string | undefined {
  return error instanceof ApiError ? (error as ApiError & { code?: string }).code : undefined
}

/** 今日视图的口径是「还没结束的任务」，改到这两个状态后它就该从今日列表里消失。 */
const CLOSED_STATUSES: readonly TaskStatus[] = ['DONE', 'CANCELLED']

// ---------------------------------------------------------------- 查询

export function useTodayTasks() {
  return useQuery({ queryKey: queryKeys.today, queryFn: tasksApi.today })
}

export function useBoard() {
  return useQuery({ queryKey: queryKeys.board, queryFn: tasksApi.board })
}

/**
 * 任务列表（GET /api/v1/tasks）。
 * enabled 给「按需才拉」的界面用：看板的已取消折叠区收起时不发请求，展开才拉。
 */
export function useTaskList(params: TaskListParams, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: queryKeys.tasks(params),
    queryFn: () => tasksApi.list(params),
    enabled: options?.enabled,
  })
}

/** 计时状态。data 为 null 表示没有计时在跑。 */
export function useRunningTimer() {
  return useQuery({ queryKey: queryKeys.runningTimer, queryFn: tasksApi.runningTimer })
}

// ---------------------------------------------------------------- 变更

export function useCreateTask() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: TaskInput) => tasksApi.create(input),
    onSuccess: () => {
      // 新建的任务可能同时出现在今日与看板里，两个都刷；列表也可以直接建成已关闭状态，
      // 所以列表缓存（含看板的「已取消」折叠区）一并按前缀失效。
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
      void queryClient.invalidateQueries({ queryKey: queryKeys.taskLists })
    },
  })
}

export function useStartTimer() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (taskId: number) => tasksApi.startTimer(taskId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.runningTimer })
      // 开始计时可能把任务从 TODO 顺带改成 DOING，列表口径跟着变。
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
    },
    onError: (error) => {
      // 409 说明服务端已经有一条在跑，本地却以为没有（多半是另一个标签页开的）。
      // 不重新拉一次的话按钮会一直可点、点了就一直失败。
      if (errorCode(error) === 'TIMER_ALREADY_RUNNING') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.runningTimer })
      }
    },
  })
}

/**
 * 改优先级：与改状态同一套乐观更新规则。
 * 优先级只影响排序与徽标，不改变「这条属于哪一列 / 还在不在今日」，所以回滚时不需要搬动任何元素，
 * 把缓存里那条任务的 priority 换回去即可。
 */
export function useChangeTaskPriority() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, priority }: { id: number; priority: Priority }) =>
      tasksApi.changePriority(id, priority),

    onMutate: async ({ id, priority }) => {
      await Promise.all([
        queryClient.cancelQueries({ queryKey: queryKeys.today }),
        queryClient.cancelQueries({ queryKey: queryKeys.board }),
      ])

      const previousToday = queryClient.getQueryData<TaskSummary[]>(queryKeys.today)
      const previousBoard = queryClient.getQueryData<BoardColumn[]>(queryKeys.board)

      if (previousToday) {
        queryClient.setQueryData<TaskSummary[]>(
          queryKeys.today,
          previousToday.map((task) => (task.id === id ? { ...task, priority } : task)),
        )
      }
      if (previousBoard) {
        queryClient.setQueryData<BoardColumn[]>(
          queryKeys.board,
          previousBoard.map((column) => ({
            ...column,
            tasks: column.tasks.map((task) => (task.id === id ? { ...task, priority } : task)),
          })),
        )
      }

      return { previousToday, previousBoard }
    },

    onError: (_error, _variables, context) => {
      if (context?.previousToday) queryClient.setQueryData(queryKeys.today, context.previousToday)
      if (context?.previousBoard) queryClient.setQueryData(queryKeys.board, context.previousBoard)
    },

    onSettled: (_data, _error, variables) => {
      // 服务端改了 updatedAt / version，以它为准重取一次。
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
      void queryClient.invalidateQueries({ queryKey: queryKeys.task(variables.id) })
    },
  })
}

/**
 * 结束计时。卡片与详情页的计时面板都走这里：结束的副作用（累加实际耗时、可能置为已完成）
 * 由后端在一个事务里做，前端只负责把受影响的缓存刷掉。
 */
export function useStopTimer() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, logId, markDone }: { taskId: number; logId: number; markDone: boolean }) =>
      tasksApi.stopTimer(taskId, logId, markDone),
    onSuccess: (_result, { taskId }) => {
      // 先用服务端事实把本地置成「没有在跑的计时」，避免重取返回前界面还停在计时态，
      // 让人以为结束没生效而再点一次（再点会变成 TIMER_NOT_RUNNING 报错）。
      queryClient.setQueryData(queryKeys.runningTimer, null)
      void queryClient.invalidateQueries({ queryKey: queryKeys.runningTimer })
      void queryClient.invalidateQueries({ queryKey: queryKeys.task(taskId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
    },
  })
}

/**
 * 改状态：乐观更新。
 *
 * 用户点一下下拉就立刻看到结果，网络往返的那几百毫秒里界面不是「卡住」的样子；
 * 失败时用 onMutate 留下的快照整体回滚——注意回滚是回写缓存，
 * 因此受控的 <select> 值（来自 task.status）会自己退回服务端状态，组件里不必再写一遍。
 */
export function useChangeTaskStatus() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: TaskStatus }) =>
      tasksApi.changeStatus(id, status),

    onMutate: async ({ id, status }) => {
      // 先取消正在飞行的重取：它要是晚于这次乐观写入落地，就会把界面刷回旧状态，
      // 看起来像「改了又自己跳回去」。
      await Promise.all([
        queryClient.cancelQueries({ queryKey: queryKeys.today }),
        queryClient.cancelQueries({ queryKey: queryKeys.board }),
      ])

      const previousToday = queryClient.getQueryData<TaskSummary[]>(queryKeys.today)
      const previousBoard = queryClient.getQueryData<BoardColumn[]>(queryKeys.board)

      // 要挪动的那条任务，从任一缓存里找；两个都没有就不做乐观更新（后面 onSettled 会重取）。
      const moved =
        previousToday?.find((task) => task.id === id) ??
        previousBoard?.flatMap((column) => column.tasks).find((task) => task.id === id)

      if (moved) {
        const next: TaskSummary = { ...moved, status }

        if (previousToday) {
          queryClient.setQueryData<TaskSummary[]>(
            queryKeys.today,
            CLOSED_STATUSES.includes(status)
              ? previousToday.filter((task) => task.id !== id)
              : previousToday.map((task) => (task.id === id ? next : task)),
          )
        }

        if (previousBoard) {
          // 看板是「列 → 任务」的结构，改状态等于把这条从原列摘掉、插进目标列。
          // 统一先过滤再插入，它才不会同时出现在两列里。
          queryClient.setQueryData<BoardColumn[]>(
            queryKeys.board,
            previousBoard.map((column) => ({
              ...column,
              tasks:
                column.status === status
                  ? [next, ...column.tasks.filter((task) => task.id !== id)]
                  : column.tasks.filter((task) => task.id !== id),
            })),
          )
        }
      }

      return { previousToday, previousBoard }
    },

    onError: (_error, _variables, context) => {
      // 只在真有快照时回写：写 undefined 会被 TanStack 当成「这个缓存不存在」。
      if (context?.previousToday) queryClient.setQueryData(queryKeys.today, context.previousToday)
      if (context?.previousBoard) queryClient.setQueryData(queryKeys.board, context.previousBoard)
    },

    onSettled: (_data, _error, variables) => {
      // 乐观更新只是让界面先动；服务端可能顺带改了别的字段（比如 completedAt），
      // 所以无论成功失败都以服务端结果为准重取一次。
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
      void queryClient.invalidateQueries({ queryKey: queryKeys.task(variables.id) })
      // 改状态会改变任务属于哪些列表（取消/恢复都会进出 CANCELLED 列表），
      // 按前缀失效所有任务列表，展开的「已取消」折叠区才不会停在旧数据上。
      void queryClient.invalidateQueries({ queryKey: queryKeys.taskLists })
    },
  })
}
