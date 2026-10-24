/**
 * AI 能力包的查询与失效规则（US-01 / 02 / 03 / 04）。
 *
 * 开关状态收在这里供所有页面共用：同一份缓存，入口显隐在各页面按 `aiEnabled === true` 判断。
 * 建议请求本身不做成全局查询——建议是「点一次要一次」的临时展示，生命周期归组件本地 state。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { aiApi } from '../api/ai.ts'
import { queryKeys } from '../api/queryKeys.ts'
import { settingsApi } from '../api/settings.ts'

/** AI 总开关。加载中返回 false（入口隐藏比闪现更稳），切换开关后由失效刷新。 */
export function useAiStatus() {
  return useQuery({
    queryKey: queryKeys.aiSettings,
    queryFn: settingsApi.getAi,
    staleTime: 30_000,
    select: (data) => data.aiEnabled,
  })
}

/**
 * 切换总开关。成功后先用服务端返回值落缓存（开关立即翻到落库后的状态），
 * 再失效一次兜底；失败时缓存不动，受控的开关自然回弹到旧值（AC 1.6）。
 */
export function useSetAiEnabled() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (enabled: boolean) => settingsApi.setAi(enabled),
    onSuccess: (data) => {
      queryClient.setQueryData(queryKeys.aiSettings, data)
      void queryClient.invalidateQueries({ queryKey: queryKeys.aiSettings })
    },
  })
}

export { aiApi }
