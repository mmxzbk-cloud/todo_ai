import type { AiSettings } from '../types/settings.ts'
import { request } from './client.ts'

/**
 * 用户设置接口（当前只有 AI 总开关）。
 * 打开开关这个动作本身不触发任何 AI 请求、不改任何任务数据（PRD US-01）。
 */
export const settingsApi = {
  getAi: () => request<AiSettings>('/api/v1/settings/ai'),

  setAi: (enabled: boolean) =>
    request<AiSettings>('/api/v1/settings/ai', { method: 'PUT', body: JSON.stringify({ enabled }) }),
}
