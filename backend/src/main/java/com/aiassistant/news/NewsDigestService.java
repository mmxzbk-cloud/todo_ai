package com.aiassistant.news;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aiassistant.ai.AiClient;
import com.aiassistant.auth.UserRepository;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ForbiddenException;
import com.aiassistant.common.ServiceUnavailableException;
import com.aiassistant.settings.UserSettingService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 每日简报（PRD US-05）：把当日新入库的资讯汇总成一页三段式简报。
 * 只读展示、不落库；当日无新条目时空态返回，不调用 AI（AC 5.2 / 5.6）。
 */
@Service
public class NewsDigestService {

	private static final Logger log = LoggerFactory.getLogger(NewsDigestService.class);

	/** 进入简报正文的条目数上限：再多 AI 上下文与成本都不划算，超出部分只计入条数。 */
	private static final int MAX_ITEMS_IN_PROMPT = 30;

	private static final String DIGEST_SYSTEM_PROMPT = """
			你是高校课程考核领域的政策情报助手。把用户当天入库的资讯汇总成一页简报，分三段：
			1. policy 政策动向：政策文件与官方动作；2. hotspot 热点速览：行业与院校动态；
			3. attention 值得关注：对高校课程考核工作可能产生影响的信号。
			某一段没有素材就写「今日无」。只输出一个 JSON 对象，不要输出任何其他文字：
			{"policy":"...","hotspot":"...","attention":"..."}""";

	private final NewsItemRepository itemRepository;
	private final UserSettingService userSettingService;
	private final AiClient aiClient;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	public NewsDigestService(NewsItemRepository itemRepository, UserSettingService userSettingService,
			AiClient aiClient, UserRepository userRepository, ObjectMapper objectMapper) {
		this.itemRepository = itemRepository;
		this.userSettingService = userSettingService;
		this.aiClient = aiClient;
		this.userRepository = userRepository;
		this.objectMapper = objectMapper;
	}

	public DigestResult generate(Long ownerId) {
		if (!userSettingService.isAiEnabled(ownerId)) {
			throw new ForbiddenException(ErrorCode.AI_DISABLED, "AI 能力未开启");
		}
		ZoneId zone = userRepository.findByIdAndDeletedAtIsNull(ownerId)
				.map(user -> safeZone(user.getTimezone()))
				.orElse(ZoneId.systemDefault());
		LocalDate today = LocalDate.now(zone);
		// 「今日」按用户时区算（与今日视图同一口径），简报基于生成时刻已入库的条目
		var start = today.atStartOfDay(zone).toInstant();
		var end = today.plusDays(1).atStartOfDay(zone).toInstant();
		List<NewsItem> items = itemRepository.findCreatedBetween(ownerId, start, end);
		if (items.isEmpty()) {
			return new DigestResult(0, "今天还没有新的资讯");
		}

		Map<String, Object> parsed = callForJson(items);
		String report = "【政策动向】\n" + textOr(parsed.get("policy"))
				+ "\n\n【热点速览】\n" + textOr(parsed.get("hotspot"))
				+ "\n\n【值得关注】\n" + textOr(parsed.get("attention"));
		return new DigestResult(items.size(), report);
	}

	private Map<String, Object> callForJson(List<NewsItem> items) {
		String content = aiClient.complete(DIGEST_SYSTEM_PROMPT, contextOf(items));
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

	private String contextOf(List<NewsItem> items) {
		StringBuilder context = new StringBuilder("今日入库资讯（共 " + items.size() + " 条）：\n");
		items.stream().limit(MAX_ITEMS_IN_PROMPT).forEach(item -> {
			context.append("- 标题：").append(item.getTitle())
					.append("｜分类：").append(item.getCategory() == null ? "未分类" : item.getCategory().getLabel())
					.append("｜来源：").append(item.getSourceName()).append('\n');
			if (item.getAiSummary() != null && !item.getAiSummary().isBlank()) {
				context.append("  摘要：").append(item.getAiSummary()).append('\n');
			} else if (item.getContentText() != null && !item.getContentText().isBlank()) {
				String content = item.getContentText();
				context.append("  摘要：").append(content.length() > 120 ? content.substring(0, 120) + "…" : content)
						.append('\n');
			}
		});
		return context.toString();
	}

	private static String extractJsonObject(String content) {
		int start = content.indexOf('{');
		int end = content.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw emptyResult();
		}
		return content.substring(start, end + 1);
	}

	private static String textOr(Object value) {
		if (value instanceof String text && !text.isBlank()) {
			return text.trim();
		}
		return "今日无";
	}

	private static ZoneId safeZone(String timezone) {
		try {
			return ZoneId.of(timezone);
		} catch (DateTimeParseException | NullPointerException ex) {
			return ZoneId.systemDefault();
		}
	}

	private static ServiceUnavailableException emptyResult() {
		return new ServiceUnavailableException(ErrorCode.AI_EMPTY_RESULT, "AI 没有生成简报，请稍后重试");
	}

	/** 简报结果：itemCount 当日入库条数（含未进提示词的），report 简报文本。 */
	public record DigestResult(int itemCount, String report) {
	}
}
