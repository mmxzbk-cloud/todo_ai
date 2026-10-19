import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '../api/client.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { tasksApi } from '../api/tasks.ts'

function describeError(error: unknown): string {
  return error instanceof ApiError ? error.message : '发生未知错误，请看浏览器控制台'
}

/**
 * 已计时读数：由 startedAt 与本地当前时刻算出来，不依赖服务端刷新。
 * 落库只发生在开始与结束两次请求上，所以这里的秒数天然会有几百毫秒的漂移——
 * 对「已经干了多久」这个用途来说够用，换来的是不计其数的省下的请求。
 */
function formatElapsed(startedAt: string, now: number): string {
  const started = Date.parse(startedAt)
  if (Number.isNaN(started)) return '--:--'
  const totalSeconds = Math.max(0, Math.floor((now - started) / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

interface TimerPanelProps {
  taskId: number
}

export function TimerPanel({ taskId }: TimerPanelProps) {
  const queryClient = useQueryClient()
  const [message, setMessage] = useState<string | null>(null)
  const [markDone, setMarkDone] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  const runningQuery = useQuery({ queryKey: queryKeys.runningTimer, queryFn: tasksApi.runningTimer })
  const running = runningQuery.data ?? null
  // 计时是「一人一条」：正在跑的那条可能属于别的任务，这时本任务只能看不能开始。
  const runningLog = running !== null && running.log.taskId === taskId ? running.log : null

  useEffect(() => {
    if (!runningLog) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [runningLog])

  useEffect(() => {
    // 跨标签页以服务端未结束记录为准：别的标签页可能刚点了开始或结束，本地缓存不会知道。
    // 挂载时不必在这里额外失效——staleTime 为 0，useQuery 首次挂载本来就会取一次。
    const syncRunning = () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.runningTimer })
    }
    window.addEventListener('focus', syncRunning)
    document.addEventListener('visibilitychange', syncRunning)
    return () => {
      window.removeEventListener('focus', syncRunning)
      document.removeEventListener('visibilitychange', syncRunning)
    }
  }, [queryClient])

  const startMutation = useMutation({
    mutationFn: () => tasksApi.startTimer(taskId),
    onSuccess: () => {
      setMessage(null)
      setNow(Date.now())
      // 开始计时的副作用不止流水一条：后端还可能把 TODO 推成 DOING，所以看板与今日也要重取。
      void queryClient.invalidateQueries({ queryKey: queryKeys.runningTimer })
      void queryClient.invalidateQueries({ queryKey: queryKeys.task(taskId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'TIMER_ALREADY_RUNNING') {
        setMessage('已有计时在进行中，请先结束其他任务的计时')
        // 冲突说明本地缓存落后于服务端：拉一次真实状态，面板才知道到底在给哪个任务计时。
        void queryClient.invalidateQueries({ queryKey: queryKeys.runningTimer })
        return
      }
      setMessage(describeError(error))
    },
  })

  const stopMutation = useMutation({
    mutationFn: (params: { logId: number; markDone: boolean }) =>
      tasksApi.stopTimer(taskId, params.logId, params.markDone),
    onSuccess: () => {
      setMessage(null)
      // 先用服务端事实把本地置成「没有在跑的计时」，避免重取返回前界面还停在计时态，
      // 让人以为结束没生效而再点一次（再点会变成 TIMER_NOT_RUNNING 报错）。
      queryClient.setQueryData(queryKeys.runningTimer, null)
      void queryClient.invalidateQueries({ queryKey: queryKeys.task(taskId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.runningTimer })
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
    },
    onError: (error) => setMessage(describeError(error)),
  })

  return (
    <div className="space-y-3">
      {message && (
        <p role="alert" className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {message}
        </p>
      )}

      {runningQuery.error && (
        <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {describeError(runningQuery.error)}
        </p>
      )}

      {runningQuery.isPending && <p className="text-sm text-slate-500">正在获取计时状态…</p>}

      {!runningQuery.isPending && runningLog && (
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-3">
            <span aria-live="polite" className="text-lg font-medium tabular-nums text-slate-900">
              已计时 {formatElapsed(runningLog.startedAt, now)}
            </span>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input
                type="checkbox"
                className="h-4 w-4"
                checked={markDone}
                onChange={(event) => setMarkDone(event.target.checked)}
              />
              同时标记完成
            </label>
            <button
              type="button"
              onClick={() => stopMutation.mutate({ logId: runningLog.id, markDone })}
              disabled={stopMutation.isPending}
              className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
            >
              {stopMutation.isPending ? '结束中…' : '结束计时'}
            </button>
          </div>
          <p className="text-xs text-slate-400">
            开始于 {new Date(runningLog.startedAt).toLocaleString()}
          </p>
        </div>
      )}

      {!runningQuery.isPending && !runningLog && running && (
        <div className="space-y-2">
          <p className="text-sm text-slate-600">
            正在为其他任务计时：
            <Link to={`/tasks/${running.task.id}`} className="mx-1 text-slate-900 underline">
              {running.task.title}
            </Link>
          </p>
          <button
            type="button"
            disabled
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white opacity-50"
          >
            开始计时
          </button>
        </div>
      )}

      {!runningQuery.isPending && !runningLog && !running && (
        <button
          type="button"
          onClick={() => startMutation.mutate()}
          disabled={startMutation.isPending}
          className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {startMutation.isPending ? '开始中…' : '开始计时'}
        </button>
      )}
    </div>
  )
}
