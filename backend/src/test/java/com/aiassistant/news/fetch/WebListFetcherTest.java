package com.aiassistant.news.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiassistant.news.dto.NewsParseRule;

/**
 * 网页列表解析（选择器方案 a）与正文提取：相对链接补全为绝对地址、
 * 缺标题/链接的条目跳过、正文剥脚本与装饰元素。
 */
class WebListFetcherTest {

	private static final NewsParseRule RULE = new NewsParseRule(
			".item", "h3.t", "a.lnk", ".date", null);

	private static final String LIST_HTML = """
			<html><body>
			<div class="list">
			  <div class="item">
			    <h3 class="t"><a href="/a/1">教育部发布 AI 教学指南</a></h3>
			    <a class="lnk" href="/a/1">阅读全文</a>
			    <span class="date">2026-10-24T10:00:00</span>
			  </div>
			  <div class="item">
			    <h3 class="t">缺少链接的条目</h3>
			    <span class="date">2026-10-24T11:00:00</span>
			  </div>
			  <div class="item">
			    <h3 class="t"><a href="/a/2">某省试点课程考核改革</a></h3>
			    <a class="lnk" href="/a/2">阅读全文</a>
			  </div>
			</div>
			</body></html>
			""";

	@Test
	void parsesItemsAndAbsolutizesLinks() {
		List<WebListFetcher.ParsedEntry> entries =
				WebListFetcher.parseList(LIST_HTML, "https://news.example.com/list", RULE);

		assertThat(entries).hasSize(2);
		assertThat(entries.get(0).title()).isEqualTo("教育部发布 AI 教学指南");
		assertThat(entries.get(0).url()).isEqualTo("https://news.example.com/a/1");
		// 本地时间按 UTC 解析是既定取舍：解析规则没有时区信息，宁可空着不猜
		assertThat(entries.get(0).publishedAt()).isEqualTo(Instant.parse("2026-10-24T10:00:00Z"));
		assertThat(entries.get(1).publishedAt()).isNull();
	}

	@Test
	void unparseableDateYieldsNullInsteadOfError() {
		String html = """
				<div class="item">
				  <h3 class="t">标题</h3>
				  <a class="lnk" href="/a/9">x</a>
				  <span class="date">三天前</span>
				</div>
				""";
		List<WebListFetcher.ParsedEntry> entries =
				WebListFetcher.parseList(html, "https://news.example.com/", RULE);

		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).publishedAt()).isNull();
	}

	@Test
	void extractContentStripsScriptsAndChrome() {
		String html = """
				<html><head><style>.x{color:red}</style></head>
				<body>
				<nav>首页 | 栏目</nav>
				<script>var tracking = 1;</script>
				<main><p>政策正文第一段。</p><p>正文第二段：AI 参与考核。</p></main>
				<footer>版权所有</footer>
				</body></html>
				""";

		assertThat(WebListFetcher.extractContent(html, "main"))
				.isEqualTo("政策正文第一段。 正文第二段：AI 参与考核。");
	}

	@Test
	void extractContentWithoutSelectorFallsBackToBody() {
		String html = "<html><body><script>alert(1)</script><p>只有一段正文</p></body></html>";

		assertThat(WebListFetcher.extractContent(html, null)).isEqualTo("只有一段正文");
	}

	@Test
	void extractContentReturnsNullWhenSelectorMisses() {
		String html = "<html><body><p>正文</p></body></html>";

		assertThat(WebListFetcher.extractContent(html, ".nonexistent")).isNull();
	}
}
