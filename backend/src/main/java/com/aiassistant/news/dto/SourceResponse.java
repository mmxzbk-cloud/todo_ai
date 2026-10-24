package com.aiassistant.news.dto;

import java.time.Instant;

import com.aiassistant.news.NewsCategory;
import com.aiassistant.news.NewsSourceType;

/**
 * 来源出参：配置字段 + 健康状态。健康状态由采集管道维护，前端只读展示（AC 1.5）。
 */
public record SourceResponse(
		Long id,
		String name,
		NewsSourceType type,
		String url,
		NewsParseRule parseRule,
		NewsCategory defaultCategory,
		boolean enabled,
		Instant lastFetchAt,
		Boolean lastFetchOk,
		String lastFetchMessage,
		int consecutiveFailures,
		boolean autoDisabled,
		Instant createdAt) {
}
