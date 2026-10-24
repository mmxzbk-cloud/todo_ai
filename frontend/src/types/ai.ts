/**
 * AI 建议的类型（US-02 / 03 / 04）。建议本身不落库，采纳与否由用户决定；
 * 采纳动作走既有的保存通道（changePriority / 表单保存 / update 接口）。
 */
import type { Priority } from './task.ts'

/** 优先级建议：P0–P3 加一句理由。理由为空后端会按失败处理，不会出现空串。 */
export interface PrioritySuggestion {
  priority: Priority
  reason: string
}

/**
 * 方案建议：解决思路与建议的下一步行动。两者都可能为 null（模型只给出一半），
 * 两者皆空的情形后端已拦下，不会出现在响应里。
 */
export interface SolutionSuggestion {
  approach: string | null
  nextStep: string | null
}

/**
 * 可替代性三态结论（US-04）。与任务上的 aiReplaceable 布尔字段是两码事：
 * 写入映射为 YES→true / NO→false / PARTIAL→true + 说明标注「部分可」。
 */
export const REPLACEABILITY_VERDICTS = ['YES', 'PARTIAL', 'NO'] as const

export type ReplaceabilityVerdict = (typeof REPLACEABILITY_VERDICTS)[number]

export const REPLACEABILITY_LABELS: Record<ReplaceabilityVerdict, string> = {
  YES: '可由 AI 解决',
  PARTIAL: '部分可',
  NO: '不可',
}

export interface ReplaceabilityAssessment {
  verdict: ReplaceabilityVerdict
  explanation: string
}
