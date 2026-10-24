package com.aiassistant.news;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aiassistant.ai.AiClient;
import com.aiassistant.ai.AiRateLimiter;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ForbiddenException;
import com.aiassistant.common.ServiceUnavailableException;
import com.aiassistant.settings.UserSettingService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 资讯的 AI 摘要与打标（PRD US-04）。
 *
 * <p>与 AiSuggestionService 同一条防护思路：总开关是断路器，关闭时绝不发起 AI 调用，
 * 条目以 SKIPPED 收场；AI 失败只影响摘要字段，条目本身照常可用（降级优先）。
 *
 * <p>两条触发路径的限流差异（A.4 第 11 条的默认处理）：
 * 采集后的批量处理是系统动作，不走每用户建议限流；详情页「重新生成」是用户动作，走。
 */
@Service
public class NewsAiService {

	private static final Logger log = LoggerFactory.getLogger(NewsAiService.class);

	private static final String SUMMARY_SYSTEM_PROMPT = """
			你是高校课程考核领域的政策情报助手。根据给定的资讯标题与正文：
			1. 给出一句话摘要；2. 提炼若干要点；3. 从给定枚举中选一个分类；4. 建议 0-3 个标签。
			分类取值：POLICY=政策文件，HOTSPOT=行业热点，TECH=技术动态，OTHER=其他。
			只输出一个 JSON 对象，不要输出任何其他文字：
			{"summary":"不超过 80 字的一句话摘要，中文","keyPoints":["要点，每条不超过 40 字，2-5 条"],"category":"POLICY|HOTSPOT|TECH|OTHER","tags":["标签，每个不超过 20 字，最多 3 个"]}""";

	private final NewsItemRepository itemRepository;
	private final UserSettingService userSettingService;
	private final AiClient aiClient;
	private final AiRateLimiter rateLimiter;
	private final NewsProperties properties;
	private final ObjectMapper objectMapper;

	public NewsAiService(NewsItemRepository itemRepository, UserSettingService userSettingService,
			AiClient aiClient, AiRateLimiter rateLimiter, NewsProperties properties, ObjectMapper objectMapper) {
		this.itemRepository = itemRepository;
		this.userSettingService = userSettingService;
		this.aiClient = aiClient;
		this.rateLimiter = rateLimiter;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	/** 采集入库后的批量处理：在采集线程上顺序执行（每日量级为个位数到几十条）。 */
	public void processAfterCollection(Long ownerId, List<Long> itemIds) {
		if (itemIds == null || itemIds.isEmpty()) {
			return;
		}
		if (!userSettingService.isAiEnabled(ownerId)) {
			itemIds.forEach(itemId -> markSkipped(ownerId, itemId));
			return;
		}
		for (Long itemId : itemIds) {
			try {
				processItem(ownerId, itemId, false);
			} catch (RuntimeException ex) {
				log.error("条目 {} AI 处理出现未预期异常", itemId, ex);
			}
		}
	}

	/** 手动重新生成（AC 4.6）：同步执行，走开关门禁与用户级限流。返回处理后的状态。 */
	public NewsAiStatus regenerate(Long ownerId, Long itemId) {
		if (!userSettingService.isAiEnabled(ownerId)) {
			throw new ForbiddenException(ErrorCode.AI_DISABLED, "AI 能力未开启");
		}
		rateLimiter.check(ownerId);
		processItem(ownerId, itemId, true);
		return itemRepository.findByIdAndOwnerId(itemId, ownerId)
				.map(NewsItem::getAiStatus)
				.orElse(NewsAiStatus.FAILED);
	}

	void processItem(Long ownerId, Long itemId, boolean manual) {
		NewsItem item = itemRepository.findByIdAndOwnerId(itemId, ownerId).orElse(null);
		if (item == null) {
			return;
		}
		// 批处理跑到一半用户关了开关：剩余条目不再发起 AI 请求
		if (!manual && !userSettingService.isAiEnabled(ownerId)) {
			item.markAiSkipped();
			itemRepository.save(item);
			return;
		}
		item.markAiProcessing();
		itemRepository.save(item);
		try {
			Map<String, Object> parsed = callForJson(item);
			// 摘要是核心产出，必须有；分类/要点/标签认不出就缺省（降级优先于失败）
			String summary = requireText(parsed.get("summary"));
			NewsCategory category = NewsCategory.fromLooseName(textOrNull(parsed.get("category")));
			List<String> keyPoints = limitStrings(listValue(parsed.get("keyPoints")), 5);
			List<String> tags = limitStrings(listValue(parsed.get("tags")), 3);
			item.applyAiResult(summary, toJson(keyPoints), category, toJson(tags));
			itemRepository.save(item);
		} catch (RuntimeException ex) {
			log.warn("条目 {} AI 处理失败：{}", itemId, ex.getMessage());
			item.markAiFailed(ex.getMessage());
			itemRepository.save(item);
		}
	}

	private void markSkipped(Long ownerId, Long itemId) {
		itemRepository.findByIdAndOwnerId(itemId, ownerId).ifPresent(item -> {
			item.markAiSkipped();
			itemRepository.save(item);
		});
	}

	private Map<String, Object> callForJson(NewsItem item) {
		String content = aiClient.complete(SUMMARY_SYSTEM_PROMPT, contextOf(item));
		Map<String, Object> parsed;
		try {
			parsed = objectMapper.readValue(extractJsonObject(content), new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception ex) {
			throw emptyResult();
		}
		if (parsed == null || parsed.isEmpty()) {
			throw emptyResult();
		}
		return parsed;
	}

	private String contextOf(NewsItem item) {
		StringBuilder context = new StringBuilder("资讯信息：\n");
		context.append("标题：").append(item.getTitle()).append('\n');
		context.append("来源：").append(item.getSourceName()).append('\n');
		String content = item.getContentText();
		if (content == null || content.isBlank()) {
			context.append("正文：（无正文，仅标题与来源）\n");
		} else {
			int limit = properties.getAiContentLimit();
			String trimmed = content.length() > limit ? content.substring(0, limit) + "…" : content;
			context.append("正文：").append(trimmed).append('\n');
		}
		return context.toString();
	}

	/** 模型输出常带代码围栏或前后缀：截取首尾大括号之间的部分（与 AiSuggestionService 同规则）。 */
	private static String extractJsonObject(String content) {
		int start = content.indexOf('{');
		int end = content.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw emptyResult();
		}
		return content.substring(start, end + 1);
	}

	private static List<String> listValue(Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		return list.stream()
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.map(String::trim)
				.filter(text -> !text.isEmpty())
				.toList();
	}

	private static List<String> limitStrings(List<String> values, int limit) {
		return values.size() > limit ? values.subList(0, limit) : values;
	}

	private static String textOrNull(Object value) {
		if (!(value instanceof String text)) {
			return null;
		}
		String trimmed = text.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String requireText(Object value) {
		String text = textOrNull(value);
		if (text == null) {
			throw emptyResult();
		}
		return text;
	}

	private String toJson(List<String> values) {
		if (values.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(values);
		} catch (Exception ex) {
			return null;
		}
	}

	private static ServiceUnavailableException emptyResult() {
		return new ServiceUnavailableException(ErrorCode.AI_EMPTY_RESULT, "AI 没有给出有效摘要，请稍后重试");
	}
}
