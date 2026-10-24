package com.aiassistant.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.news.dto.ConvertTaskRequest;
import com.aiassistant.news.dto.ConvertTaskResponse;
import com.aiassistant.task.TaskService;
import com.aiassistant.task.dto.TaskRequest;
import com.aiassistant.task.dto.TaskResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * 资讯转任务（PRD US-06）：走现有任务通道、记录双向关联、默认标题带「研读：」前缀（AC 6.2）。
 */
@ExtendWith(MockitoExtension.class)
class NewsConvertServiceTest {

	private static final Long OWNER = 1L;
	private static final Long ITEM_ID = 7L;

	@Mock
	private NewsItemRepository itemRepository;
	@Mock
	private NewsItemTaskRepository itemTaskRepository;
	@Mock
	private TaskService taskService;

	private NewsConvertService service;

	@BeforeEach
	void setUp() {
		service = new NewsConvertService(itemRepository, itemTaskRepository, taskService, new ObjectMapper());
	}

	private NewsItem item() {
		NewsItem item = new NewsItem();
		item.setOwnerId(OWNER);
		item.setSourceName("教育部网站");
		item.setTitle("教育部发布 AI 教学指南");
		item.setUrl("https://example.com/a/1");
		item.setNormalizedUrl("https://example.com/a/1");
		item.setCategory(NewsCategory.POLICY);
		return item;
	}

	@Test
	void convertCreatesTaskViaTaskServiceAndRecordsLink() {
		NewsItem existing = item();
		when(itemRepository.findByIdAndOwnerId(ITEM_ID, OWNER)).thenReturn(Optional.of(existing));
		// TaskResponse 是 23 个分量的 record：测试只关心 id 与 title，其余按类型补零/空
		when(taskService.create(eq(OWNER), any(TaskRequest.class)))
				.thenReturn(new TaskResponse(101L, "研读：教育部发布 AI 教学指南", null, null, null,
						null, null, null, null, null, null, null, null, 0,
						null, null, null, null, null, 0, 0, null, null));

		ConvertTaskResponse response = service.convert(OWNER, ITEM_ID, new ConvertTaskRequest(null, null, null, null));

		ArgumentCaptor<TaskRequest> captor = ArgumentCaptor.forClass(TaskRequest.class);
		verify(taskService).create(eq(OWNER), captor.capture());
		assertThat(captor.getValue().title()).isEqualTo("研读：教育部发布 AI 教学指南");
		assertThat(captor.getValue().source()).isEqualTo("资讯");
		assertThat(captor.getValue().description()).contains("原文链接：https://example.com/a/1").contains("来源：教育部网站");
		assertThat(response.taskId()).isEqualTo(101L);
		// 双向关联：资讯侧必须记录任务 id（AC 6.2）
		verify(itemTaskRepository).save(any(NewsItemTask.class));
	}

	@Test
	void convertOfOtherUsersItemIsNotFound() {
		when(itemRepository.findByIdAndOwnerId(9L, OWNER)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.convert(OWNER, 9L, new ConvertTaskRequest(null, null, null, null)))
				.isInstanceOfSatisfying(ResourceNotFoundException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
