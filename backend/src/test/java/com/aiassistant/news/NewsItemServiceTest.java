package com.aiassistant.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.news.dto.ClassificationRequest;
import com.aiassistant.news.dto.ItemDetailResponse;
import com.aiassistant.tag.Tag;
import com.aiassistant.tag.TagRepository;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * 资讯库服务层：owner 隔离（404）、分类采纳、标签整体替换与无效标签校验、
 * 软删任务的「已删除」展示（AC 3.7 / 4.3 / 6.3）。
 */
@ExtendWith(MockitoExtension.class)
class NewsItemServiceTest {

	private static final Long OWNER = 1L;

	@Mock
	private NewsItemRepository itemRepository;
	@Mock
	private NewsItemTagRepository itemTagRepository;
	@Mock
	private NewsItemTaskRepository itemTaskRepository;
	@Mock
	private TagRepository tagRepository;
	@Mock
	private TaskRepository taskRepository;

	private NewsItemService service;

	@BeforeEach
	void setUp() {
		service = new NewsItemService(itemRepository, itemTagRepository, itemTaskRepository,
				tagRepository, taskRepository, new ObjectMapper());
	}

	private NewsItem item() {
		NewsItem item = new NewsItem();
		item.setOwnerId(OWNER);
		item.setSourceName("教育部网站");
		item.setTitle("AI 教学指南");
		item.setUrl("https://example.com/a/1");
		item.setNormalizedUrl("https://example.com/a/1");
		item.setAiStatus(NewsAiStatus.DONE);
		return item;
	}

	@Test
	void detailOfOtherUsersItemIsNotFound() {
		when(itemRepository.findByIdAndOwnerId(9L, OWNER)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.detail(OWNER, 9L))
				.isInstanceOfSatisfying(ResourceNotFoundException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void updateClassificationAdoptsCategoryAndReplacesTags() {
		NewsItem existing = item();
		when(itemRepository.findByIdAndOwnerId(7L, OWNER)).thenReturn(Optional.of(existing));
		Tag tag = org.mockito.Mockito.mock(Tag.class);
		when(tag.getId()).thenReturn(3L);
		when(tagRepository.findByOwnerIdAndIdInAndDeletedAtIsNullOrderByNameAscIdAsc(OWNER, List.of(3L)))
				.thenReturn(List.of(tag));
		when(itemRepository.save(any(NewsItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ItemDetailResponse response = service.updateClassification(OWNER, 7L,
				new ClassificationRequest(NewsCategory.POLICY, List.of(3L)));

		assertThat(existing.getCategory()).isEqualTo(NewsCategory.POLICY);
		verify(itemTagRepository).deleteByItemId(7L);
		assertThat(response.tags()).isEmpty();
		assertThat(response.category()).isEqualTo(NewsCategory.POLICY);
	}

	@Test
	void updateClassificationRejectsUnknownTag() {
		NewsItem existing = item();
		when(itemRepository.findByIdAndOwnerId(7L, OWNER)).thenReturn(Optional.of(existing));
		when(tagRepository.findByOwnerIdAndIdInAndDeletedAtIsNullOrderByNameAscIdAsc(OWNER, List.of(99L)))
				.thenReturn(List.of());

		assertThatThrownBy(() -> service.updateClassification(OWNER, 7L,
				new ClassificationRequest(null, List.of(99L))))
				.isInstanceOfSatisfying(BadRequestException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		// 校验失败时不允许碰关联表
		verify(itemTagRepository, org.mockito.Mockito.never()).deleteByItemId(7L);
	}

	@Test
	void detailMarksSoftDeletedTaskAsDeleted() {
		// 实体的 id 由数据库生成、没有 setter：需要 id 的用例用 mock 提供只读字段
		NewsItem existing = org.mockito.Mockito.mock(NewsItem.class);
		when(existing.getId()).thenReturn(7L);
		when(itemRepository.findByIdAndOwnerId(7L, OWNER)).thenReturn(Optional.of(existing));
		when(itemTagRepository.findTagIdsByItemId(7L)).thenReturn(List.of());
		when(itemTaskRepository.findTaskIdsByItemId(7L)).thenReturn(List.of(55L));
		Task deletedTask = org.mockito.Mockito.mock(Task.class);
		when(deletedTask.getId()).thenReturn(55L);
		when(deletedTask.getTitle()).thenReturn("研读旧政策");
		when(deletedTask.getDeletedAt()).thenReturn(Instant.now());
		when(taskRepository.findByIdInAndOwnerId(List.of(55L), OWNER)).thenReturn(List.of(deletedTask));

		ItemDetailResponse response = service.detail(OWNER, 7L);

		assertThat(response.relatedTasks()).hasSize(1);
		assertThat(response.relatedTasks().get(0).taskId()).isEqualTo(55L);
		assertThat(response.relatedTasks().get(0).deleted()).isTrue();
	}
}
