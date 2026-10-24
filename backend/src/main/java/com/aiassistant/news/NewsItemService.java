package com.aiassistant.news;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.PageResponse;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.news.dto.AdoptRequest;
import com.aiassistant.news.dto.ClassificationRequest;
import com.aiassistant.news.dto.ItemDetailResponse;
import com.aiassistant.news.dto.ItemResponse;
import com.aiassistant.news.dto.RelatedTaskResponse;
import com.aiassistant.tag.Tag;
import com.aiassistant.tag.TagRepository;
import com.aiassistant.tag.dto.TagResponse;
import com.aiassistant.task.TaskRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 资讯库的业务规则（PRD US-03）：筛选检索、详情、已读/星标、分类标签采纳。
 * ownerId 由 Controller 传入，查不到（他人的/不存在的）一律 404。
 */
@Service
public class NewsItemService {

	private static final int MAX_PAGE_SIZE = 100;

	private final NewsItemRepository itemRepository;
	private final NewsItemTagRepository itemTagRepository;
	private final NewsItemTaskRepository itemTaskRepository;
	private final TagRepository tagRepository;
	private final TaskRepository taskRepository;
	private final ObjectMapper objectMapper;

	public NewsItemService(NewsItemRepository itemRepository, NewsItemTagRepository itemTagRepository,
			NewsItemTaskRepository itemTaskRepository, TagRepository tagRepository,
			TaskRepository taskRepository, ObjectMapper objectMapper) {
		this.itemRepository = itemRepository;
		this.itemTagRepository = itemTagRepository;
		this.itemTaskRepository = itemTaskRepository;
		this.tagRepository = tagRepository;
		this.taskRepository = taskRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public PageResponse<ItemResponse> list(Long ownerId, NewsCategory category, Long sourceId,
			LocalDate from, LocalDate to, boolean starred, boolean unread, String q, int page, int size,
			ZoneId zoneId) {
		String keyword = (q == null || q.isBlank()) ? null : q.trim();
		// 日期区间按「入库时间」理解，边界用用户时区（与今日视图同一口径）
		Instant start = from == null ? null : from.atStartOfDay(zoneId).toInstant();
		Instant end = to == null ? null : to.plusDays(1).atStartOfDay(zoneId).toInstant();
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
		Page<NewsItem> found = itemRepository.search(
				ownerId,
				category != null, category,
				sourceId != null, sourceId,
				start != null, start,
				end != null, end,
				starred,
				unread,
				keyword != null, keyword,
				pageable);
		List<ItemResponse> items = found.getContent().stream().map(this::toResponse).toList();
		return new PageResponse<>(items, found.getTotalElements(), found.getNumber(), found.getSize());
	}

	@Transactional(readOnly = true)
	public ItemDetailResponse detail(Long ownerId, Long id) {
		NewsItem item = requireItem(ownerId, id);
		return toDetail(item, ownerId);
	}

	@Transactional
	public ItemResponse setRead(Long ownerId, Long id, boolean read) {
		NewsItem item = requireItem(ownerId, id);
		item.setRead(read);
		return toResponse(itemRepository.save(item));
	}

	@Transactional
	public ItemResponse setStarred(Long ownerId, Long id, boolean starred) {
		NewsItem item = requireItem(ownerId, id);
		item.setStarred(starred);
		return toResponse(itemRepository.save(item));
	}

	/**
	 * 分类与标签的整体修改：采纳 AI 建议与手工调整共用通道。
	 * 标签整体替换（先删后插），交上来的 id 必须全部是本人未删标签。
	 */
	@Transactional
	public ItemDetailResponse updateClassification(Long ownerId, Long id, ClassificationRequest request) {
		NewsItem item = requireItem(ownerId, id);
		if (request.category() != null) {
			item.setCategory(request.category());
		}
		if (request.tagIds() != null) {
			List<Long> distinctIds = List.copyOf(new HashSet<>(request.tagIds()));
			List<Tag> tags = distinctIds.isEmpty() ? List.of()
					: tagRepository.findByOwnerIdAndIdInAndDeletedAtIsNullOrderByNameAscIdAsc(ownerId, distinctIds);
			if (tags.size() != distinctIds.size()) {
				throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "包含不存在或已删除的标签");
			}
			itemTagRepository.deleteByItemId(id);
			Instant now = Instant.now();
			tags.forEach(tag -> itemTagRepository.save(new NewsItemTag(new NewsItemTagId(id, tag.getId()), now)));
		}
		return toDetail(itemRepository.save(item), ownerId);
	}

	/**
	 * 采纳 AI 建议的分类与标签（AC 4.3）。标签采纳时把建议标签名解析为本人的标签，
	 * 不存在的自动创建——否则建议标签对没建过标签的用户毫无用处。
	 */
	@Transactional
	public ItemDetailResponse adoptAiSuggestions(Long ownerId, Long id, AdoptRequest request) {
		NewsItem item = requireItem(ownerId, id);
		if (Boolean.TRUE.equals(request.adoptCategory()) && item.getAiSuggestedCategory() != null) {
			item.setCategory(item.getAiSuggestedCategory());
		}
		if (Boolean.TRUE.equals(request.adoptTags())) {
			List<String> names = parseStringList(item.getAiSuggestedTags());
			if (!names.isEmpty()) {
				List<Tag> tags = names.stream()
						.map(name -> resolveOrCreateTag(ownerId, name))
						.toList();
				itemTagRepository.deleteByItemId(id);
				Instant now = Instant.now();
				tags.forEach(tag -> itemTagRepository.save(new NewsItemTag(new NewsItemTagId(id, tag.getId()), now)));
			}
		}
		return toDetail(itemRepository.save(item), ownerId);
	}

	private Tag resolveOrCreateTag(Long ownerId, String name) {
		String trimmed = name.trim();
		return tagRepository.findByOwnerIdAndName(ownerId, trimmed)
				.filter(tag -> tag.getDeletedAt() == null)
				.orElseGet(() -> {
					Tag tag = new Tag();
					tag.setOwnerId(ownerId);
					tag.setName(trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed);
					try {
						return tagRepository.save(tag);
					} catch (DataIntegrityViolationException ex) {
						// 并发建同名标签撞唯一索引：回头读已有行
						return tagRepository.findByOwnerIdAndName(ownerId, trimmed).orElseThrow(() -> ex);
					}
				});
	}

	// ---------------------------------------------------------------- 映射

	private NewsItem requireItem(Long ownerId, Long id) {
		return itemRepository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "资讯不存在：id=" + id));
	}

	private ItemResponse toResponse(NewsItem item) {
		return new ItemResponse(item.getId(), item.getTitle(), item.getUrl(), item.getSourceId(),
				item.getSourceName(), item.getCategory(), item.getPublishedAt(), item.getCreatedAt(),
				item.getReadAt(), item.isStarred(), item.getAiSummary(), item.getAiSuggestedCategory(),
				parseStringList(item.getAiSuggestedTags()), item.getAiStatus());
	}

	private ItemDetailResponse toDetail(NewsItem item, Long ownerId) {
		List<Long> tagIds = itemTagRepository.findTagIdsByItemId(item.getId());
		List<TagResponse> tags = tagIds.isEmpty() ? List.of()
				: tagRepository.findByOwnerIdAndIdInAndDeletedAtIsNullOrderByNameAscIdAsc(ownerId, tagIds)
						.stream().map(TagResponse::from).toList();
		List<Long> taskIds = itemTaskRepository.findTaskIdsByItemId(item.getId());
		List<RelatedTaskResponse> relatedTasks = taskIds.isEmpty() ? List.of()
				: taskRepository.findByIdInAndOwnerId(taskIds, ownerId).stream()
						.map(task -> new RelatedTaskResponse(task.getId(), task.getTitle(),
								task.getDeletedAt() != null))
						.toList();
		return new ItemDetailResponse(item.getId(), item.getTitle(), item.getUrl(), item.getSourceId(),
				item.getSourceName(), item.getCategory(), item.getPublishedAt(), item.getCreatedAt(),
				item.getReadAt(), item.isStarred(), item.getAiSummary(), parseStringList(item.getAiKeyPoints()),
				item.getAiSuggestedCategory(), parseStringList(item.getAiSuggestedTags()), item.getAiStatus(),
				item.getAiError(), item.getContentText(), tags, relatedTasks);
	}

	/** JSON 字符串数组的宽松解析：空/坏数据一律回空列表，不挡浏览。 */
	private List<String> parseStringList(String json) {
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
}
