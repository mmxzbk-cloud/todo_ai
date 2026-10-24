package com.aiassistant.news.fetch;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import com.aiassistant.news.NewsProperties;
import com.aiassistant.news.NewsSourceType;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;

/**
 * RSS/Atom 抓取器（Rome）。正文取 content:encoded，退回 description；
 * 不再逐条抓原文页——政策类源以摘要为主，逐条抓页的时间成本在 P0 不划算。
 */
@Component
public class RssFetcher implements SourceFetcher {

	private final NewsHttpClient httpClient;
	private final NewsProperties properties;

	public RssFetcher(NewsHttpClient httpClient, NewsProperties properties) {
		this.httpClient = httpClient;
		this.properties = properties;
	}

	@Override
	public NewsSourceType type() {
		return NewsSourceType.RSS;
	}

	@Override
	public List<FetchedItem> fetch(com.aiassistant.news.NewsSource source, Deadline deadline) {
		var response = httpClient.get(source.getUrl(), properties.getPageTimeout());
		if (response.statusCode() / 100 != 2) {
			throw new NewsHttpException("RSS 源返回 HTTP " + response.statusCode(), null, false);
		}
		return parse(response.body());
	}

	/** 静态以便单测：给一段 RSS/Atom XML，回解析出的条目（XML 无效抛 NewsHttpException）。 */
	static List<FetchedItem> parse(String xml) {
		try {
			SyndFeed feed = new SyndFeedInput().build(new StringReader(xml));
			List<FetchedItem> items = new ArrayList<>();
			for (SyndEntry entry : feed.getEntries()) {
				String title = toPlainText(entry.getTitle());
				String link = entry.getLink() == null ? null : entry.getLink().trim();
				if (title == null || link == null || link.isEmpty()) {
					continue;
				}
				items.add(new FetchedItem(title, link, publishedOf(entry), contentOf(entry)));
			}
			return items;
		} catch (NewsHttpException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new NewsHttpException("RSS 解析失败：" + ex.getMessage(), ex, false);
		}
	}

	/** content:encoded 优先，description 兜底；都是 HTML，统一抽纯文本。 */
	private static String contentOf(SyndEntry entry) {
		String raw = null;
		for (SyndContent content : entry.getContents()) {
			if (content.getValue() != null && !content.getValue().isBlank()) {
				raw = content.getValue();
				break;
			}
		}
		if (raw == null && entry.getDescription() != null) {
			raw = entry.getDescription().getValue();
		}
		return toPlainText(raw);
	}

	private static Instant publishedOf(SyndEntry entry) {
		Date published = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
		return published == null ? null : published.toInstant();
	}

	private static String toPlainText(String html) {
		if (html == null || html.isBlank()) {
			return null;
		}
		String text = Jsoup.parse(html).text().trim();
		return text.isEmpty() ? null : text;
	}
}
