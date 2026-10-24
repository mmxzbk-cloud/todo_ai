/**
 * 资讯域的类型与常量，取值与后端 news 包完全一致（枚举同样是 const 数组 + 联合类型）。
 */
import type { Tag } from './tag.ts'

// ---------------------------------------------------------------- 枚举

export const NEWS_SOURCE_TYPES = ['RSS', 'WEBPAGE'] as const

export type NewsSourceType = (typeof NEWS_SOURCE_TYPES)[number]

export const NEWS_SOURCE_TYPE_LABELS: Record<NewsSourceType, string> = {
  RSS: 'RSS 订阅',
  WEBPAGE: '网页列表',
}

export const NEWS_CATEGORIES = ['POLICY', 'HOTSPOT', 'TECH', 'OTHER'] as const

export type NewsCategory = (typeof NEWS_CATEGORIES)[number]

export const NEWS_CATEGORY_LABELS: Record<NewsCategory, string> = {
  POLICY: '政策文件',
  HOTSPOT: '行业热点',
  TECH: '技术动态',
  OTHER: '其他',
}

export const NEWS_AI_STATUSES = ['PENDING', 'PROCESSING', 'DONE', 'FAILED', 'SKIPPED'] as const

export type NewsAiStatus = (typeof NEWS_AI_STATUSES)[number]

export const NEWS_AI_STATUS_LABELS: Record<NewsAiStatus, string> = {
  PENDING: 'AI 待处理',
  PROCESSING: 'AI 处理中',
  DONE: 'AI 已处理',
  FAILED: 'AI 失败',
  SKIPPED: 'AI 未启用',
}

// ---------------------------------------------------------------- 来源

/** 网页列表来源的 CSS 选择器解析规则（后端 NewsParseRule）。 */
export interface NewsParseRule {
  item: string
  title: string
  link: string
  published?: string | null
  content?: string | null
}

export interface NewsSource {
  id: number
  name: string
  type: NewsSourceType
  url: string
  parseRule: NewsParseRule | null
  defaultCategory: NewsCategory | null
  enabled: boolean
  lastFetchAt: string | null
  lastFetchOk: boolean | null
  lastFetchMessage: string | null
  consecutiveFailures: number
  autoDisabled: boolean
  createdAt: string
}

export interface SourceInput {
  name: string
  type: NewsSourceType
  url: string
  parseRule?: NewsParseRule | null
  defaultCategory?: NewsCategory | null
  enabled?: boolean
}

/** 试抓结果：robots 禁止与超时是 HTTP 错误，其余失败走 success=false + message。 */
export interface TrialFetchResult {
  success: boolean
  itemCount: number
  titles: string[]
  message: string | null
}

export interface CollectResult {
  queued: number
}

// ---------------------------------------------------------------- 条目

export interface NewsItem {
  id: number
  title: string
  url: string
  sourceId: number | null
  sourceName: string
  category: NewsCategory | null
  publishedAt: string | null
  createdAt: string
  readAt: string | null
  starred: boolean
  aiSummary: string | null
  aiSuggestedCategory: NewsCategory | null
  aiSuggestedTags: string[]
  aiStatus: NewsAiStatus
}

export interface RelatedTask {
  taskId: number
  title: string
  deleted: boolean
}

export interface NewsItemDetail extends NewsItem {
  aiKeyPoints: string[]
  aiError: string | null
  contentText: string | null
  tags: Tag[]
  relatedTasks: RelatedTask[]
}

export interface NewsListParams {
  category?: NewsCategory
  sourceId?: number
  /** ISO 日期（YYYY-MM-DD），后端按用户时区归界。 */
  from?: string
  to?: string
  starred?: boolean
  unread?: boolean
  q?: string
  page?: number
  size?: number
}

export interface ClassificationInput {
  category?: NewsCategory | null
  tagIds?: number[] | null
}

export interface AdoptInput {
  adoptCategory?: boolean
  adoptTags?: boolean
}

// ---------------------------------------------------------------- 简报与转任务

export interface DigestResult {
  itemCount: number
  report: string
}

export interface ConvertTaskInput {
  title?: string
  description?: string
  projectId?: number | null
  tagIds?: number[] | null
}

export interface ConvertTaskResult {
  taskId: number
  taskTitle: string
}
