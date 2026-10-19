package com.aiassistant.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 标签入参，新建与更新共用。长度上限与 tags.name / tags.color 的列宽一致。
 */
public record TagRequest(
		@NotBlank(message = "标签名称不能为空")
		@Size(max = 50, message = "标签名称长度不能超过 50 个字符")
		String name,

		@Size(max = 16, message = "颜色值长度不能超过 16 个字符")
		String color) {
}
