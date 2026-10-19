package com.aiassistant.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 项目入参，新建与更新共用。
 *
 * sortOrder 允许为空：新建时只关心「建一个项目」的场景不该被迫填排序值，为空按 0 落库。
 */
public record ProjectRequest(
		@NotBlank(message = "项目名称不能为空")
		@Size(max = 100, message = "项目名称长度不能超过 100 个字符")
		String name,

		@Size(max = 16, message = "颜色值长度不能超过 16 个字符")
		String color,

		@PositiveOrZero(message = "排序值不能为负数")
		Integer sortOrder) {
}
