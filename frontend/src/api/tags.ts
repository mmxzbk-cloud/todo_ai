import { request } from './client.ts'
import type { Tag } from '../types/tag.ts'

/** 新建与更新共用一份入参，与后端 TagRequest 的两个字段对应。 */
export interface TagInput {
  name: string
  /** 不传表示不要颜色（后端落 null），而不是空字符串。 */
  color?: string
}

export const tagsApi = {
  list: (): Promise<Tag[]> => request<Tag[]>('/api/v1/tags'),

  create: (input: TagInput): Promise<Tag> =>
    request<Tag>('/api/v1/tags', { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: TagInput): Promise<Tag> =>
    request<Tag>(`/api/v1/tags/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** 后端返回 204，request 会把空响应体转成 undefined。 */
  remove: (id: number): Promise<void> =>
    request<void>(`/api/v1/tags/${id}`, { method: 'DELETE' }),
}
