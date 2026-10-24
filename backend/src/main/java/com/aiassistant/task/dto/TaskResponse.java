package com.aiassistant.task.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.aiassistant.tag.dto.TagResponse;
import com.aiassistant.task.Importance;
import com.aiassistant.task.Priority;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskStatus;
import com.aiassistant.task.Urgency;

/**
 * 任务出参。字段与前端 types/task.ts 的 TaskSummary 一一对应，顺序也保持一致：
 * 两边靠肉眼看字段名对齐，顺序一致时 diff 更容易发现漏字段。
 *
 * projectName/projectColor/tags 都不在实体上，由 Service 批量回填
 * （先收集本页的 projectId 一次查回项目，task id 一次查回标签），
 * 所以这里用工厂方法接收它们，而不是让实体自己去查。
 */
public record TaskResponse(
		Long id,
		String title,
		String description,
		Long projectId,
		String projectName,
		String projectColor,
		List<TagResponse> tags,
		Long parentTaskId,
		String source,
		Importance importance,
		Urgency urgency,
		Priority priority,
		TaskStatus status,
		LocalDate dueOn,
		Integer estimatedMinutes,
		int actualMinutes,
		Boolean aiReplaceable,
		String aiReplacementNote,
		String nextAction,
		String notes,
		Instant completedAt,
		int sortOrder,
		int version,
		Instant createdAt,
		Instant updatedAt) {

	/** 没有项目/标签上下文时的映射（如计时器返回的任务快照）。 */
	public static TaskResponse from(Task task) {
		return from(task, null, null, List.of());
	}

	public static TaskResponse from(Task task, String projectName, String projectColor, List<TagResponse> tags) {
		return new TaskResponse(
				task.getId(),
				task.getTitle(),
				task.getDescription(),
				task.getProjectId(),
				projectName,
				projectColor,
				tags,
				task.getParentTaskId(),
				task.getSource(),
				task.getImportance(),
				task.getUrgency(),
				task.getPriority(),
				task.getStatus(),
				task.getDueOn(),
				task.getEstimatedMinutes(),
				task.getActualMinutes(),
				task.getAiReplaceable(),
				task.getAiReplacementNote(),
				task.getNextAction(),
				task.getNotes(),
				task.getCompletedAt(),
				task.getSortOrder(),
				task.getVersion(),
				task.getCreatedAt(),
				task.getUpdatedAt());
	}
}
