/**
 * 全项目唯一的查询坐标。
 *
 * 收在一个文件里的理由：TanStack 的失效（invalidate）按 key 前缀匹配，「改了 A 页面要顺带刷新 B 页面」
 * 这类规则一旦散落在各页面里就地拼字符串，重复的 key 拼写迟早会漂移，脏数据就是这么来的。
 * 阶段二的页面（机会、Skill、复盘）直接往这里加，不要再另起一套 key。
 */
export const queryKeys = {
  /** 列表带筛选参数：参数是 key 的一部分，换了筛选条件就是另一份缓存。 */
  tasks: (p: unknown) => ['tasks', 'list', p] as const,
  today: ['tasks', 'today'] as const,
  board: ['tasks', 'board'] as const,
  task: (id: number) => ['tasks', 'detail', id] as const,
  runningTimer: ['timers', 'running'] as const,
  projects: ['projects'] as const,
  tags: ['tags'] as const,
}
