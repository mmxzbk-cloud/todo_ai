package com.aiassistant.news.dto;

import com.aiassistant.news.NewsCategory;
import com.aiassistant.news.NewsSourceType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 来源入参，新建（POST）与更新（PUT）共用。字段上限与 V3 的 info_sources 列宽一致。
 * URL 的 scheme 校验、robots 检查在服务层做（需要访问网络，Bean Validation 不该碰网络）。
 */
public record SourceRequest(
		@NotBlank(message = "来源名称不能为空")
		@Size(max = 100, message = "来源名称长度不能超过 100 个字符")
		String name,

		@NotNull(message = "来源类型不能为空")
		NewsSourceType type,

		@NotBlank(message = "来源地址不能为空")
		@Size(max = 1000, message = "来源地址长度不能超过 1000 个字符")
		String url,

		NewsParseRule parseRule,

		NewsCategory defaultCategory,

		Boolean enabled) {
}
