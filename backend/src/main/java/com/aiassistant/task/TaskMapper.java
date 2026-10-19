package com.aiassistant.task;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.aiassistant.task.dto.TaskResponse;

/**
 * 任务列表的映射工具。
 *
 * 单条映射放在 TaskResponse.from（计时模块也要用）；这里只解决批量回填：
 * 列表里 20 条任务可能来自 3 个项目，逐行去查项目名就是 20 次查询，
 * 所以由 Service 先收集 projectId 一次查回，再交给这里拼装。
 */
public final class TaskMapper {

	private TaskMapper() {
	}

	public static List<TaskResponse> toResponses(Collection<Task> tasks, Map<Long, String> projectNames) {
		return tasks.stream()
				.map(task -> TaskResponse.from(task, projectName(task, projectNames)))
				.toList();
	}

	/** projectId 为 null 时不去查 map：Map.get(null) 在不可变实现上会直接抛异常。 */
	public static String projectName(Task task, Map<Long, String> projectNames) {
		Long projectId = task.getProjectId();
		return projectId == null ? null : projectNames.get(projectId);
	}
}
