package com.aiassistant.project.dto;

import java.time.Instant;

import com.aiassistant.project.Project;

/**
 * 项目出参。前端 types/project.ts 的 Project 与这五个字段一一对应。
 */
public record ProjectResponse(Long id, String name, String color, int sortOrder, Instant archivedAt) {

	public static ProjectResponse from(Project project) {
		return new ProjectResponse(
				project.getId(),
				project.getName(),
				project.getColor(),
				project.getSortOrder(),
				project.getArchivedAt());
	}
}
