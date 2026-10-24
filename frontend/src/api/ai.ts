import type { PrioritySuggestion, ReplaceabilityAssessment, SolutionSuggestion } from '../types/ai.ts'
import { request } from './client.ts'

/**
 * AI 建议接口（US-02 / 03 / 04）。三个都是「请求 → 建议」：
 * 建议不落任何数据，采纳走各自的既有保存通道。
 * 关闭总开关时这些入口根本不渲染，这里也不会发出请求（AC 2.6 / 3.6 / 4.7）。
 */
export const aiApi = {
  suggestPriority: (taskId: number) =>
    request<PrioritySuggestion>(`/api/v1/tasks/${taskId}/ai/priority-suggestion`, { method: 'POST' }),

  suggestSolution: (taskId: number) =>
    request<SolutionSuggestion>(`/api/v1/tasks/${taskId}/ai/solution-suggestion`, { method: 'POST' }),

  assessReplaceability: (taskId: number) =>
    request<ReplaceabilityAssessment>(`/api/v1/tasks/${taskId}/ai/replaceability-assessment`, {
      method: 'POST',
    }),
}
