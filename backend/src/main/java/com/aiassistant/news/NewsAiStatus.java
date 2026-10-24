package com.aiassistant.news;

/**
 * 条目的 AI 处理状态。与条目本身的生命周期解耦：
 * AI 关闭或失败不影响条目入库与浏览（PRD US-04 降级规则）。
 */
public enum NewsAiStatus {

	/** 新入库，等待 AI 处理。 */
	PENDING,
	/** AI 正在处理（异步进行中）。 */
	PROCESSING,
	/** 摘要与建议已生成。 */
	DONE,
	/** AI 调用失败，可在详情页重新生成。 */
	FAILED,
	/** 用户 AI 总开关关闭，跳过处理（不再自动重试）。 */
	SKIPPED
}
