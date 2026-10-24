package com.aiassistant.news;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.news.dto.ConvertTaskRequest;
import com.aiassistant.news.dto.ConvertTaskResponse;
import com.aiassistant.task.TaskService;
import com.aiassistant.task.dto.TagIdsRequest;
import com.aiassistant.task.dto.TaskRequest;
import com.aiassistant.task.dto.TaskResponse;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 资讯转任务（PRD US-06）：经现有任务创建通道创建（单一写入口），
 * 资讯侧记录关联。默认标题「研读：<资讯标题>」，默认描述带原文链接、来源与 AI 摘要要点；
 * 用户在确认框里改过的字段以提交值为准。转任务不触发任何 AI 请求（AC 6.4）。
 */
@Service
public class NewsConvertService {

	private static final int TITLE_LIMIT = 200;
	private static final int DESCRIPTION_LIMIT = 4000;

	private final NewsItemRepository itemRepository;
	private final NewsItemTaskRepository itemTaskRepository;
	private final TaskService taskService;
	private final ObjectMapper objectMapper;

	public NewsConvertService(NewsItemRepository itemRepository, NewsItemTaskRepository itemTaskRepository,
			TaskService taskService, ObjectMapper objectMapper) {
		this.itemRepository = itemRepository;
		this.itemTaskRepository = itemTaskRepository;
		this.taskService = taskService;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public ConvertTaskResponse convert(Long ownerId, Long itemId, ConvertTaskRequest request) {
		NewsItem item = itemRepository.findByIdAndOwnerId(itemId, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "资讯不存在：id=" + itemId));

		String title = request.title() == null || request.title().isBlank()
				? truncate("研读：" + item.getTitle(), TITLE_LIMIT)
				: request.title().trim();
		String description = request.description() == null || request.description().isBlank()
				? defaultDescription(item)
				: request.description();

		// TaskRequest 是 16 个分量的 record，后端内部构造只有这一处：
		// title / description / projectId / parentTaskId / source 有值，其余交默认推导
		TaskResponse task = taskService.create(ownerId, new TaskRequest(
				title,
				description,
				request.projectId(),
				null,
				"资讯",
				null, null, null, null, null, null, null, null, null, null, null));

		if (request.tagIds() != null && !request.tagIds().isEmpty()) {
			taskService.assignTags(ownerId, task.id(), new TagIdsRequest(request.tagIds()));
		}

		itemTaskRepository.save(new NewsItemTask(new NewsItemTaskId(itemId, task.id()), Instant.now()));
		return new ConvertTaskResponse(task.id(), task.title());
	}

	/** 默认描述：原文链接 + 来源 + 发布时间 + AI 摘要与要点（AI 未出结果时跳过对应段落）。 */
	private String defaultDescription(NewsItem item) {
		StringBuilder description = new StringBuilder();
		description.append("原文链接：").append(item.getUrl()).append('\n');
		description.append("来源：").append(item.getSourceName()).append('\n');
		if (item.getPublishedAt() != null) {
			description.append("发布时间：")
					.append(item.getPublishedAt().atZone(ZoneId.systemDefault()).toLocalDate()
							.format(DateTimeFormatter.ISO_LOCAL_DATE))
					.append('\n');
		}
		if (item.getAiSummary() != null && !item.getAiSummary().isBlank()) {
			description.append('\n').append("摘要：").append(item.getAiSummary()).append('\n');
		}
		List<String> keyPoints = parseList(item.getAiKeyPoints());
		if (!keyPoints.isEmpty()) {
			description.append("要点：\n");
			keyPoints.forEach(point -> description.append("- ").append(point).append('\n'));
		}
		return truncate(description.toString(), DESCRIPTION_LIMIT);
	}

	/** JSON 字符串数组的宽松解析：空/坏数据一律回空列表，转任务不该被坏摘要挡住。 */
	private List<String> parseList(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<String>>() {
			});
		} catch (Exception ex) {
			return List.of();
		}
	}

	private static String truncate(String value, int limit) {
		return value.length() <= limit ? value : value.substring(0, limit);
	}
}
