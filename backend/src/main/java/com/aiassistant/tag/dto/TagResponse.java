package com.aiassistant.tag.dto;

import com.aiassistant.tag.Tag;

/**
 * 标签出参。前端 types/tag.ts 的 Tag 与这三个字段一一对应。
 */
public record TagResponse(Long id, String name, String color) {

	public static TagResponse from(Tag tag) {
		return new TagResponse(tag.getId(), tag.getName(), tag.getColor());
	}
}
