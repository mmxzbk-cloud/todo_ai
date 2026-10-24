package com.aiassistant.news.dto;

import java.time.Instant;
import java.util.List;

import com.aiassistant.news.NewsAiStatus;
import com.aiassistant.news.NewsCategory;

/**
 * 资讯列表行 / 详情的公共字段。列表不带正文与要点（列表页只需要扫一眼的信息），
 * 详情见 ItemDetailResponse。
 */
public record ItemResponse(
		Long id,
		String title,
		String url,
		Long sourceId,
		String sourceName,
		NewsCategory category,
		Instant publishedAt,
		Instant createdAt,
		Instant readAt,
		boolean starred,
		String aiSummary,
		NewsCategory aiSuggestedCategory,
		List<String> aiSuggestedTags,
		NewsAiStatus aiStatus) {
}
