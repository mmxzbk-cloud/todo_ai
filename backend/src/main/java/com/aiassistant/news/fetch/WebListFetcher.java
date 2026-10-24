package com.aiassistant.news.fetch;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.aiassistant.news.NewsProperties;
import com.aiassistant.news.NewsSourceType;
import com.aiassistant.news.dto.NewsParseRule;

import tools.jackson.databind.ObjectMapper;

/**
 * 网页列表页抓取器（Jsoup + 用户配置的 CSS 选择器，PRD A.4 第 2 条方案 a）。
 *
 * <p>流程：抓列表页 → 按 item 选择器圈条目 → 取 title/link（absUrl 解析相对地址）→
 * 逐条抓原文页提取正文（ robots 允许且 deadline 未到时）。原文页之间保持 300ms 间隔，
 * 是对目标站点最基本的礼貌。
 */
@Component
public class WebListFetcher implements SourceFetcher {

	/** 相邻两次对同一站点正文页请求的间隔。 */
	private static final long POLITENESS_DELAY_MILLIS = 300;

	private final NewsHttpClient httpClient;
	private final NewsProperties properties;
	private final RobotsChecker robotsChecker;
	private final ObjectMapper objectMapper;

	public WebListFetcher(NewsHttpClient httpClient, NewsProperties properties, RobotsChecker robotsChecker,
			ObjectMapper objectMapper) {
		this.httpClient = httpClient;
		this.properties = properties;
		this.robotsChecker = robotsChecker;
		this.objectMapper = objectMapper;
	}

	@Override
	public NewsSourceType type() {
		return NewsSourceType.WEBPAGE;
	}

	@Override
	public List<FetchedItem> fetch(com.aiassistant.news.NewsSource source, Deadline deadline) {
		NewsParseRule rule = parseRuleOf(source);
		var response = httpClient.get(source.getUrl(), properties.getPageTimeout());
		if (response.statusCode() / 100 != 2) {
			throw new NewsHttpException("列表页返回 HTTP " + response.statusCode(), null, false);
		}
		List<ParsedEntry> entries = parseList(response.body(), response.uri().toString(), rule);
		if (entries.isEmpty()) {
			return List.of();
		}

		List<FetchedItem> items = new ArrayList<>();
		Set<String> seenUrls = new LinkedHashSet<>();
		for (ParsedEntry entry : entries) {
			if (items.size() >= properties.getMaxItemsPerSource() || deadline.expired()) {
				break;
			}
			if (!seenUrls.add(entry.url())) {
				continue;
			}
			// 列表页 robots 允许才走到这里；正文页路径可能不同，逐条再查（命中缓存，无额外网络请求）
			String content = null;
			if (robotsChecker.isAllowed(entry.url())) {
				content = fetchContent(entry.url(), deadline, rule);
			}
			items.add(new FetchedItem(entry.title(), entry.url(), entry.publishedAt(), content));
		}
		return items;
	}

	/** 抓原文页并提取正文；被 deadline / robots / 网络错误打断时正文为 null，条目仍返回。 */
	private String fetchContent(String url, Deadline deadline, NewsParseRule rule) {
		if (deadline.expired()) {
			return null;
		}
		try {
			Thread.sleep(POLITENESS_DELAY_MILLIS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return null;
		}
		try {
			var response = httpClient.get(url, properties.getPageTimeout());
			if (response.statusCode() / 100 != 2) {
				return null;
			}
			String text = extractContent(response.body(), rule.content());
			if (text != null && text.length() > properties.getContentMaxLength()) {
				text = text.substring(0, properties.getContentMaxLength());
			}
			return text;
		} catch (NewsHttpException ex) {
			// 单条正文失败不算来源失败：标题与链接来自列表页，条目照常入库
			return null;
		}
	}

	/** 静态以便单测：给列表页 HTML 与解析规则，回解析出的条目。 */
	static List<ParsedEntry> parseList(String html, String baseUri, NewsParseRule rule) {
		Document document = Jsoup.parse(html, baseUri);
		List<ParsedEntry> entries = new ArrayList<>();
		for (Element item : document.select(rule.item())) {
			Element titleEl = item.selectFirst(rule.title());
			Element linkEl = item.selectFirst(rule.link());
			if (titleEl == null || linkEl == null) {
				continue;
			}
			String title = collapse(titleEl.text());
			String link = linkEl.absUrl("href").trim();
			if (title.isEmpty() || link.isEmpty() || !link.startsWith("http")) {
				continue;
			}
			Element publishedEl = rule.published() == null ? null : item.selectFirst(rule.published());
			entries.add(new ParsedEntry(title, link, publishedEl == null ? null : parseLooseDateTime(publishedEl.text())));
		}
		return entries;
	}

	/** 正文提取：去脚本与装饰元素后取 content 选择器（缺省取 body）的纯文本。 */
	static String extractContent(String html, String contentSelector) {
		Document document = Jsoup.parse(html);
		document.select("script, style, noscript, nav, header, footer, aside, form").remove();
		Element root = contentSelector == null || contentSelector.isBlank()
				? document.body()
				: document.selectFirst(contentSelector);
		if (root == null) {
			return null;
		}
		String text = collapse(root.text());
		return text.isEmpty() ? null : text;
	}

	/** 发布时间尽量认：ISO 日期时间 → ISO 日期 → null。站点格式千奇百怪，认不出就空着。 */
	static Instant parseLooseDateTime(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String trimmed = text.trim();
		try {
			return Instant.parse(trimmed);
		} catch (Exception ignored) {
			// 继续尝试下一种格式
		}
		try {
			return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
		} catch (Exception ignored) {
			// 继续尝试下一种格式
		}
		try {
			return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(ZoneId.systemDefault()).toInstant();
		} catch (Exception ignored) {
			return null;
		}
	}

	private NewsParseRule parseRuleOf(com.aiassistant.news.NewsSource source) {
		// 来源服务已校验过网页类型必有规则；防御性兜底让错误信息可读
		if (source.getParseRule() == null) {
			throw new NewsHttpException("网页来源缺少解析规则配置", null, false);
		}
		try {
			return objectMapper.readValue(source.getParseRule(), NewsParseRule.class);
		} catch (Exception ex) {
			throw new NewsHttpException("解析规则读取失败：" + ex.getMessage(), ex, false);
		}
	}

	private static String collapse(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").trim();
	}

	record ParsedEntry(String title, String url, Instant publishedAt) {
	}
}
