package com.aiassistant.news.fetch;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.aiassistant.news.NewsProperties;

/**
 * robots 协议检查（PRD 3.1 抓取范围）。保存来源与每次采集前都会走这里。
 *
 * <p>状态码语义：robots.txt 404/410 = 没有禁令（允许）；401/403 = 站方明确拒绝一切抓取（禁止）；
 * 其余失败（5xx、超时、网络不可达）按「无禁令」处理并留日志——用一次网络抖动把用户的来源
 * 永久判死，比暂时多请求一个页面伤害更大。内容按 host 缓存 1 小时，避免每条 URL 都打一次。
 */
@Component
public class RobotsChecker {

	public static final String PRODUCT_TOKEN = "AIAssistantNews";

	private static final Logger log = LoggerFactory.getLogger(RobotsChecker.class);
	private static final Duration CACHE_TTL = Duration.ofHours(1);

	private final NewsHttpClient httpClient;
	private final NewsProperties properties;
	private final Map<String, CachedRobots> cacheByHost = new ConcurrentHashMap<>();

	private record CachedRobots(Instant expiresAt, String content) {
	}

	public RobotsChecker(NewsHttpClient httpClient, NewsProperties properties) {
		this.httpClient = httpClient;
		this.properties = properties;
	}

	/** 目标 URL 是否允许抓取。URL 解析不了时返回 true——合法性校验是来源管理的事，不是这里的职责。 */
	public boolean isAllowed(String url) {
		URI uri;
		try {
			uri = URI.create(url.trim());
		} catch (IllegalArgumentException ex) {
			return true;
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
		String host = uri.getHost();
		if (!("http".equals(scheme) || "https".equals(scheme)) || host == null) {
			return true;
		}
		int port = uri.getPort();
		String base = scheme + "://" + host + (port == -1 ? "" : ":" + port);
		String robotsUrl = base + "/robots.txt";

		String content = cachedOrFetch(base, robotsUrl);
		if (content == null) {
			return true;
		}
		String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
		if (uri.getRawQuery() != null) {
			path = path + "?" + uri.getRawQuery();
		}
		return RobotsTxtParser.isAllowed(content, PRODUCT_TOKEN, path);
	}

	private String cachedOrFetch(String base, String robotsUrl) {
		CachedRobots cached = cacheByHost.get(base);
		Instant now = Instant.now();
		if (cached != null && cached.expiresAt().isAfter(now)) {
			return cached.content();
		}
		String content;
		try {
			var response = httpClient.get(robotsUrl, properties.getPageTimeout());
			int status = response.statusCode();
			if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.GONE.value()) {
				content = null;
			} else if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
				// 站方拒绝读取 robots.txt：按全站禁止处理
				content = "User-agent: *\nDisallow: /";
			} else if (response.statusCode() / 100 == 2) {
				content = response.body();
			} else {
				log.warn("robots.txt 返回非预期状态 {}（{}），本次按允许处理", status, robotsUrl);
				content = null;
			}
		} catch (NewsHttpException ex) {
			log.warn("robots.txt 获取失败（{}），本次按允许处理：{}", robotsUrl, ex.getMessage());
			content = null;
		}
		cacheByHost.put(base, new CachedRobots(now.plus(CACHE_TTL), content));
		return content;
	}
}
