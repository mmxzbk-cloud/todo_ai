package com.aiassistant.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.aiassistant.project.Project;
import com.aiassistant.tag.dto.TagResponse;
import com.aiassistant.task.dto.TaskResponse;

/**
 * 列表映射的回填验证：项目名/项目色/标签都来自调用方给的批量查询结果，
 * 缺项目、缺标签的任务必须映射成 null / 空列表，而不是抛错或漏字段。
 */
class TaskMapperTest {

	private static Task task(Long id, Long projectId) {
		Task task = Mockito.mock(Task.class);
		Mockito.lenient().when(task.getId()).thenReturn(id);
		Mockito.lenient().when(task.getProjectId()).thenReturn(projectId);
		return task;
	}

	@Test
	void fillsProjectNameColorAndTags() {
		Project project = Mockito.mock(Project.class);
		Mockito.lenient().when(project.getName()).thenReturn("AI 助手");
		Mockito.lenient().when(project.getColor()).thenReturn("#6366f1");
		TagResponse urgent = new TagResponse(9L, "紧急", "#f43f5e");
		TagResponse waiting = new TagResponse(7L, "等回复", "#0ea5e9");

		List<TaskResponse> responses = TaskMapper.toResponses(
				List.of(task(1L, 5L), task(2L, null)),
				Map.of(5L, project),
				Map.of(1L, List.of(urgent, waiting)));

		assertThat(responses).hasSize(2);
		assertThat(responses.get(0).projectName()).isEqualTo("AI 助手");
		assertThat(responses.get(0).projectColor()).isEqualTo("#6366f1");
		// 标签顺序沿用上游（按名字排序）给定的顺序，映射不再重排
		assertThat(responses.get(0).tags()).containsExactly(urgent, waiting);
		assertThat(responses.get(1).projectName()).isNull();
		assertThat(responses.get(1).projectColor()).isNull();
		assertThat(responses.get(1).tags()).isEmpty();
	}

	@Test
	void projectMissingFromMapMapsToNulls() {
		// 项目被并行走删/查不到时：名字与颜色都回 null，卡片显示成无项目，而不是 500
		List<TaskResponse> responses = TaskMapper.toResponses(
				List.of(task(3L, 99L)), Map.of(), Map.of());

		assertThat(responses.get(0).projectName()).isNull();
		assertThat(responses.get(0).projectColor()).isNull();
		assertThat(responses.get(0).tags()).isEmpty();
	}
}
