package com.aiassistant.news;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 去重判据的归一化规则：只收敛「同一篇文章的不同写法」，不做激进改写。
 */
class UrlNormalizerTest {

	@Test
	void lowercasesSchemeAndHost() {
		assertThat(UrlNormalizer.normalize("HTTPS://WWW.MOE.GOV.CN/news/2026/a.html"))
				.isEqualTo("https://www.moe.gov.cn/news/2026/a.html");
	}

	@Test
	void stripsDefaultPort() {
		assertThat(UrlNormalizer.normalize("https://example.com:443/a"))
				.isEqualTo("https://example.com/a");
		assertThat(UrlNormalizer.normalize("http://example.com:80/a"))
				.isEqualTo("http://example.com/a");
		// 非默认端口必须保留，否则两条不同服务的链接会被误判同一条
		assertThat(UrlNormalizer.normalize("http://example.com:8080/a"))
				.isEqualTo("http://example.com:8080/a");
	}

	@Test
	void stripsFragmentAndFillsRootPath() {
		assertThat(UrlNormalizer.normalize("https://example.com/news?a=1#top"))
				.isEqualTo("https://example.com/news?a=1");
		assertThat(UrlNormalizer.normalize("https://example.com"))
				.isEqualTo("https://example.com/");
	}

	@Test
	void keepsQueryAndPathAsIs() {
		// 不合并 tracking 参数、不改写路径：保守归一化不会把真不同的文章判成同一条
		assertThat(UrlNormalizer.normalize("https://example.com/b?a=2&b=1"))
				.isEqualTo("https://example.com/b?a=2&b=1");
	}

	@Test
	void fallsBackToTrimmedInputWhenUnparseable() {
		assertThat(UrlNormalizer.normalize("  not a url  ")).isEqualTo("not a url");
		assertThat(UrlNormalizer.normalize(null)).isEmpty();
	}
}
