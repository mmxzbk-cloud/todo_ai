package com.aiassistant.ai;

/**
 * AI 可替代性的三态结论（PRD US-04）。与任务上的 ai_replaceable 布尔字段是两码事：
 * 落库映射为「YES→是 / NO→否 / PARTIAL→是 + 说明标注部分可」（A.4 第 3 条方案 a），
 * 枚举本身只活在接口出参里，不新增任何列。
 */
public enum ReplaceabilityVerdict {
	YES,
	PARTIAL,
	NO
}
