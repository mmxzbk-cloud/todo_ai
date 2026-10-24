package com.aiassistant.news.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * 资讯转任务的入参（POST /items/{id}/convert-task）。
 * 前端确认框预填标题与描述（含原文链接），用户可改后提交；
 * 后端对缺省字段给默认值：标题「研读：<资讯标题>」，描述含链接、来源与 AI 摘要要点。
 */
public record ConvertTaskRequest(
		@Size(max = 200, message = "任务标题长度不能超过 200 个字符")
		String title,

		@Size(max = 4000, message = "任务描述长度不能超过 4000 个字符")
		String description,

		Long projectId,

		List<Long> tagIds) {
}
