package com.aiassistant.news.fetch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/**
 * 抓取层共用的 HTTP 客户端。统一两件事：
 * 1. User-Agent 标明身份（对目标站点的礼貌，也便于对方站长联系我们）；
 * 2. 把「超时」与「其他网络失败」分开，调用方按 isTimeout() 决定提示文案。
 */
@Component
public class NewsHttpClient {

	private final HttpClient client;

	public NewsHttpClient() {
		this.client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	/**
	 * GET 一个 URL，返回原始响应（状态码 + UTF-8 文本）。
	 * 非 2xx 不算异常：robots 检查要按 404/403 区分语义，由调用方解释状态码。
	 */
	public HttpResponse<String> get(String url, Duration timeout) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(timeout)
				.header("User-Agent", "AIAssistantNews/0.1 (personal information assistant)")
				.GET()
				.build();
		try {
			return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (HttpTimeoutException ex) {
			throw new NewsHttpException("请求超时：" + url, ex, true);
		} catch (IOException ex) {
			throw new NewsHttpException("请求失败：" + url, ex, false);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new NewsHttpException("请求被中断：" + url, ex, false);
		}
	}
}
