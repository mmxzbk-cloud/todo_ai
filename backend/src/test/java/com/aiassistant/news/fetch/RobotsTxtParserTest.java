package com.aiassistant.news.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * robots.txt 解析的可测逻辑：分组选择、最长匹配、通配与锚点、空规则。
 * 网络行为（404/403/超时）在 RobotsChecker，不在这里。
 */
class RobotsTxtParserTest {

	private static final String TOKEN = RobotsChecker.PRODUCT_TOKEN;

	@Test
	void emptyOrMissingRobotsMeansAllowed() {
		assertThat(RobotsTxtParser.isAllowed(null, TOKEN, "/news")).isTrue();
		assertThat(RobotsTxtParser.isAllowed("", TOKEN, "/news")).isTrue();
	}

	@Test
	void starGroupDisallowBlocks() {
		String robots = """
				User-agent: *
				Disallow: /
				""";
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/news/list")).isFalse();
	}

	@Test
	void disallowRootWithoutSlashDoesNothing() {
		// 空值 Disallow 表示本组无限制，不等于封全站
		String robots = """
				User-agent: *
				Disallow:
				""";
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/anything")).isTrue();
	}

	@Test
	void specificGroupOverridesStar() {
		String robots = """
				User-agent: *
				Disallow: /

				User-agent: AIAssistantNews
				Allow: /public/
				Disallow: /private/
				""";
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/public/a")).isTrue();
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/private/a")).isFalse();
		// 指名组没覆盖到的路径回到「无规则 = 允许」，而不是继承通配组的全站禁令
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/other")).isTrue();
	}

	@Test
	void longestMatchWinsAndAllowWinsTies() {
		String robots = """
				User-agent: *
				Disallow: /news
				Allow: /news/free
				""";
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/news/pay/a")).isFalse();
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/news/free/a")).isTrue();
		// 前缀相同的 Allow/Disallow 同时命中时 Allow 胜出
		String tie = """
				User-agent: *
				Disallow: /a
				Allow: /a
				""";
		assertThat(RobotsTxtParser.isAllowed(tie, TOKEN, "/a/b")).isTrue();
	}

	@Test
	void wildcardAndEndAnchor() {
		String robots = """
				User-agent: *
				Disallow: /*.pdf$
				Allow: /docs/*.html
				""";
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/files/report.pdf")).isFalse();
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/docs/guide.html")).isTrue();
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/docs/guide")).isTrue();
	}

	@Test
	void commentsAndUnknownDirectivesIgnored() {
		String robots = """
				# 站点说明
				Sitemap: https://example.com/sitemap.xml
				Crawl-delay: 10

				User-agent: *   # 所有爬虫
				Disallow: /admin # 后台不开放
				""";
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/admin/login")).isFalse();
		assertThat(RobotsTxtParser.isAllowed(robots, TOKEN, "/news")).isTrue();
	}
}
