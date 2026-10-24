import type { PageResponse } from '../types/task.ts'
import type {
  AdoptInput,
  ClassificationInput,
  CollectResult,
  ConvertTaskInput,
  ConvertTaskResult,
  DigestResult,
  NewsItem,
  NewsItemDetail,
  NewsListParams,
  NewsSource,
  SourceInput,
  TrialFetchResult,
} from '../types/news.ts'
import { request } from './client.ts'

const NEWS = '/api/v1/news'
const SOURCES = `${NEWS}/sources`
const ITEMS = `${NEWS}/items`

/** 查询参数拼装规则与 tasks.ts 同一套：undefined / 空串不进 query string。 */
function toQueryString(params: NewsListParams): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === '') continue
    search.set(key, String(value))
  }
  const query = search.toString()
  return query === '' ? '' : `?${query}`
}

/**
 * 资讯域接口（PRD US-01 ～ US-06）。后端按用户隔离：他人资源一律 404。
 */
export const newsApi = {
  // ---- 来源管理（US-01） ----
  listSources: () => request<NewsSource[]>(SOURCES),

  createSource: (input: SourceInput) =>
    request<NewsSource>(SOURCES, { method: 'POST', body: JSON.stringify(input) }),

  updateSource: (id: number, input: SourceInput) =>
    request<NewsSource>(`${SOURCES}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  setSourceEnabled: (id: number, enabled: boolean) =>
    request<NewsSource>(`${SOURCES}/${id}/enabled`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled }),
    }),

  /** 后端返回 204。已抓取的历史资讯保留。 */
  removeSource: (id: number) => request<void>(`${SOURCES}/${id}`, { method: 'DELETE' }),

  /** 保存前试抓：立即抓一次返回预览，不写库（AC 1.3）。 */
  trialSource: (input: SourceInput) =>
    request<TrialFetchResult>(`${SOURCES}/trial`, { method: 'POST', body: JSON.stringify(input) }),

  // ---- 采集（US-02） ----
  collect: (sourceId?: number) =>
    request<CollectResult>(`${NEWS}/collect${sourceId == null ? '' : `?sourceId=${sourceId}`}`, {
      method: 'POST',
    }),

  // ---- 条目（US-03 / US-04） ----
  listItems: (params: NewsListParams) =>
    request<PageResponse<NewsItem>>(`${ITEMS}${toQueryString(params)}`),

  getItem: (id: number) => request<NewsItemDetail>(`${ITEMS}/${id}`),

  setRead: (id: number, read: boolean) =>
    request<NewsItem>(`${ITEMS}/${id}/read`, { method: 'PATCH', body: JSON.stringify({ read }) }),

  setStarred: (id: number, starred: boolean) =>
    request<NewsItem>(`${ITEMS}/${id}/star`, { method: 'PATCH', body: JSON.stringify({ starred }) }),

  updateClassification: (id: number, input: ClassificationInput) =>
    request<NewsItemDetail>(`${ITEMS}/${id}/classification`, {
      method: 'PUT',
      body: JSON.stringify(input),
    }),

  /** 重新生成 AI 摘要与建议（AC 4.6）。开关关闭时后端返回 AI_DISABLED。 */
  regenerateAi: (id: number) => request<NewsItemDetail>(`${ITEMS}/${id}/ai/regenerate`, { method: 'POST' }),

  /** 采纳 AI 建议：后端把建议标签名解析/建成本人标签。 */
  adoptAi: (id: number, input: AdoptInput) =>
    request<NewsItemDetail>(`${ITEMS}/${id}/ai/adopt`, { method: 'POST', body: JSON.stringify(input) }),

  // ---- 简报（US-05） ----
  digest: () => request<DigestResult>(`${NEWS}/digest`, { method: 'POST' }),

  // ---- 转任务（US-06） ----
  convertToTask: (id: number, input: ConvertTaskInput) =>
    request<ConvertTaskResult>(`${ITEMS}/${id}/convert-task`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
}
