package com.aiassistant.news.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * RSS/Atom 解析：正文取 content:encoded 退 description、缺链接的条目跳过、
 * Atom 用 updated 兜底发布时间。解析失败抛 NewsHttpException（采集侧按来源失败记健康状态）。
 */
class RssFetcherTest {

	@Test
	void parsesRss2EntriesAndStripsHtml() {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<rss version="2.0"><channel>
				  <title>政策速递</title>
				  <link>https://example.com</link>
				  <description>t</description>
				  <item>
				    <title>教育部发布 &lt;em&gt;AI 教学&lt;/em&gt;指南</title>
				    <link>https://example.com/a/1</link>
				    <pubDate>Sat, 24 Oct 2026 02:00:00 GMT</pubDate>
				    <description>&lt;p&gt;指南要点：&lt;b&gt;AI 赋能&lt;/b&gt;课程考核&lt;/p&gt;</description>
				  </item>
				  <item>
				    <title>没有链接的条目</title>
				    <description>应被跳过</description>
				  </item>
				</channel></rss>
				""";

		List<FetchedItem> items = RssFetcher.parse(xml);

		assertThat(items).hasSize(1);
		// Jsoup text() 直接拼接文本节点，元素边界不产生空格
		assertThat(items.get(0).title()).isEqualTo("教育部发布 AI 教学指南");
		assertThat(items.get(0).url()).isEqualTo("https://example.com/a/1");
		assertThat(items.get(0).publishedAt()).isEqualTo(Instant.parse("2026-10-24T02:00:00Z"));
		assertThat(items.get(0).contentText()).isEqualTo("指南要点：AI 赋能课程考核");
	}

	@Test
	void parsesAtomUsingUpdatedAsPublishedFallback() {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<feed xmlns="http://www.w3.org/2005/Atom">
				  <title>热点</title>
				  <entry>
				    <title>某省启动课程考核改革试点</title>
				    <link href="https://example.org/b/2"/>
				    <updated>2026-10-24T08:30:00Z</updated>
				    <content>试点方案覆盖 12 所高校。</content>
				  </entry>
				</feed>
				""";

		List<FetchedItem> items = RssFetcher.parse(xml);

		assertThat(items).hasSize(1);
		assertThat(items.get(0).url()).isEqualTo("https://example.org/b/2");
		assertThat(items.get(0).publishedAt()).isEqualTo(Instant.parse("2026-10-24T08:30:00Z"));
		assertThat(items.get(0).contentText()).isEqualTo("试点方案覆盖 12 所高校。");
	}

	@Test
	void invalidXmlThrowsNewsHttpException() {
		assertThatThrownBy(() -> RssFetcher.parse("<rss><channel><item>残缺"))
				.isInstanceOf(NewsHttpException.class)
				.hasMessageContaining("RSS 解析失败");
	}
}
