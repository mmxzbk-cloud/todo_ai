package com.aiassistant.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 服务的部署级配置。密钥只从环境变量进（与 APP_AUTH_JWT_SECRET 同模式），
 * 不写死在仓库里、不下发前端。
 *
 * <p>三项（base-url / api-key / model）任一为空都视为「未配置」：此时开关打开也会得到
 * AI_UNAVAILABLE，而不是带着空参数去打一个错误的地址。
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

	/** OpenAI 兼容服务的根地址，例如 https://api.example.com/v1。 */
	private String baseUrl = "";

	private String apiKey = "";

	private String model = "";

	/** 单次调用的读取超时。建议类接口的时延指标是 P95 ≤ 15s，留出余量。 */
	private Duration timeout = Duration.ofSeconds(30);

	/** 每用户每分钟的建议请求数上限，防误点连击与成本失控（PRD A.4 第 6 条）。 */
	private int rateLimitPerMinute = 10;

	public boolean isConfigured() {
		return !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Duration getTimeout() {
		return timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	public int getRateLimitPerMinute() {
		return rateLimitPerMinute;
	}

	public void setRateLimitPerMinute(int rateLimitPerMinute) {
		this.rateLimitPerMinute = rateLimitPerMinute;
	}
}
