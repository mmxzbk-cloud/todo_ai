package com.aiassistant.news.dto;

import java.util.List;

import com.aiassistant.news.NewsCategory;

import jakarta.validation.constraints.Size;

/**
 * 分类与标签的整体修改（PUT /items/{id}/classification）。
 * 采纳 AI 建议、用户手改共用这一个通道（AC 4.3 / 4.4）。
 *
 * 语义：category 传 null 表示不修改分类（没有「清空分类」的诉求，AI 打标后总有归属）；
 * tagIds 传 null 表示不动标签，非 null 时整体替换（与任务 assignTags 同语义）。
 */
public record ClassificationRequest(
		NewsCategory category,

		@Size(max = 10, message = "标签数量不能超过 10 个")
		List<Long> tagIds) {
}
