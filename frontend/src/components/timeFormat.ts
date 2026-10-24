/**
 * 计时读数格式化。看板卡片与详情页的计时面板都用它，两处显示同一段计时不能有两种写法。
 *
 * 读数由 startedAt 与本地当前时刻算出来，不依赖服务端刷新：落库只发生在开始与结束两次请求上，
 * 所以这里的秒数天然会有几百毫秒的漂移——对「已经干了多久」这个用途来说够用，
 * 换来的是不计其数的省下的请求。
 */
export function formatElapsed(startedAt: string, now: number): string {
  const started = Date.parse(startedAt)
  if (Number.isNaN(started)) return '--:--'
  const totalSeconds = Math.max(0, Math.floor((now - started) / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}
