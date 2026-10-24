package com.aiassistant.task;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.aiassistant.project.Project;
import com.aiassistant.tag.dto.TagResponse;
import com.aiassistant.task.dto.TaskResponse;

/**
 * 任务列表的映射工具。
 *
 * 单条映射放在 TaskResponse.from（计时模块也要用）；这里只解决批量回填：
 * 列表里 20 条任务可能来自 3 个项目、挂着若干标签，逐行去查就是几十次查询，
 * 所以由 Service 先把项目与标签各一次查回，再交给这里拼装。
 */
public final class TaskMapper {

	private TaskMapper() {
	}

	public static List<TaskResponse> toResponses(Collection<Task> tasks, Map<Long, Project> projectsById,
			Map<Long, List<TagResponse>> tagsByTaskId) {
		return tasks.stream()
				.map(task -> TaskResponse.from(task, projectName(task, projectsById), projectColor(task, projectsById),
						tagsFor(task, tagsByTaskId)))
				.toList();
	}

	/** projectId 为 null 时不去查 map：Map.get(null) 在不可变实现上会直接抛异常。 */
	public static String projectName(Task task, Map<Long, Project> projectsById) {
		Project project = projectOf(task, projectsById);
		return project == null ? null : project.getName();
	}

	public static String projectColor(Task task, Map<Long, Project> projectsById) {
		Project project = projectOf(task, projectsById);
		return project == null ? null : project.getColor();
	}

	private static Project projectOf(Task task, Map<Long, Project> projectsById) {
		Long projectId = task.getProjectId();
		return projectId == null ? null : projectsById.get(projectId);
	}

	/** 没有标签的任务在 map 里缺席即可，不必为它存一个空列表。 */
	private static List<TagResponse> tagsFor(Task task, Map<Long, List<TagResponse>> tagsByTaskId) {
		Long taskId = task.getId();
		return taskId == null ? List.of() : tagsByTaskId.getOrDefault(taskId, List.of());
	}
}
