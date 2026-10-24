package com.aiassistant.news.dto;

import java.time.Instant;
import java.util.List;

import com.aiassistant.news.NewsAiStatus;
import com.aiassistant.news.NewsCategory;
import com.aiassistant.tag.dto.TagResponse;

/**
 * 资讯详情：列表公共字段 + 正文、AI 要点、标签、关联任务。
 * 类型与 ItemResponse 保持一致（强类型枚举与 Instant），前端 types 一份对应两处。
 */
public record ItemDetailResponse(
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
		List<String> aiKeyPoints,
		NewsCategory aiSuggestedCategory,
		List<String> aiSuggestedTags,
		NewsAiStatus aiStatus,
		String aiError,
		String contentText,
		List<TagResponse> tags,
		List<RelatedTaskResponse> relatedTasks) {
}
