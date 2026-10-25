import { useState } from 'react'

import { BatchTaskImport } from '../components/BatchTaskImport.tsx'
import { QuickTaskForm } from '../components/QuickTaskForm.tsx'
import { TaskCard } from '../components/TaskCard.tsx'
import { TimeStatsCard } from '../components/TimeStatsCard.tsx'
import { describeError, useTodayTasks } from '../hooks/useTasks.ts'

/**
 * 今日视图。
 *
 * 「今天」的口径由服务端按用户时区算（未删 + 截止日 ≤ 今天 + 未完成/未取消），
 * 前端不自己过滤：浏览器时区与账号时区可能不一致，两处各算一次必然出现同一天看到不同列表。
 */
function TodayPage() {
  const today = useTodayTasks()
  // 改状态与开始计时的失败提示集中在页面顶端一条，卡片上不再各自弹一个。
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // 顺序直接用服务端给的（截止日 → 优先级 → id）：前端再排一次，两套口径迟早会不一致。
  const tasks = today.data ?? []

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold">今日</h1>
        <p className="mt-1 text-sm text-slate-500">
          截止到今天还没结束的任务。先记下来，细节以后再补。
        </p>
      </header>

      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <QuickTaskForm />
        <BatchTaskImport />
      </section>

      {/* 耗时统计（US-05）：只读、不受 AI 总开关影响。 */}
      <TimeStatsCard />

      {errorMessage && (
        <div
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700"
        >
          {errorMessage}
        </div>
      )}

      <section>
        <h2 className="mb-3 text-base font-medium">
          任务
          <span className="ml-2 text-sm font-normal text-slate-400">{tasks.length} 条</span>
        </h2>

        {today.isPending ? (
          <p className="text-sm text-slate-500">加载中…</p>
        ) : today.isError ? (
          <p role="alert" className="text-sm text-red-600">
            {describeError(today.error)}
          </p>
        ) : tasks.length === 0 ? (
          <p className="text-sm text-slate-500">今天没有到期的任务。</p>
        ) : (
          <ul className="space-y-3">
            {tasks.map((task) => (
              <li key={task.id}>
                <TaskCard task={task} onError={setErrorMessage} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

export default TodayPage
