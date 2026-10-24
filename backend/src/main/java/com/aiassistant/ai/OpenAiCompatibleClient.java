package com.aiassistant.ai;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.GatewayTimeoutException;
import com.aiassistant.common.ServiceUnavailableException;

import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI 兼容接口的 {@link AiClient} 实现（POST {base-url}/chat/completions）。
 *
 * <p>选「OpenAI 兼容」而不是绑死某家 SDK：市面上的大模型服务（含自托管网关）几乎都提供
 * 这套协议，换供应商只改三个环境变量，不加依赖。
 *
 * <p>异常语义（PRD 第五章 5.1）在本类收口：
 * <ul>
 * <li>没配置 → AI_UNAVAILABLE（配置缺失对用户的表现就是「服务不可用」）</li>
 * <li>超时 → AI_TIMEOUT（504），其他网络故障 → AI_UNAVAILABLE（503）</li>
 * <li>响应不含有效文本 → AI_EMPTY_RESULT</li>
 * </ul>
 */
@Component
public class OpenAiCompatibleClient implements AiClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

	private final AiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public OpenAiCompatibleClient(AiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		// 构造期只建壳不判配置：没配置时 restClient 不该被调用（complete 里先拦），建出来仅为依赖完整
		java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getTimeout());
		this.restClient = RestClient.builder()
				.baseUrl(properties.getBaseUrl())
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.requestFactory(requestFactory)
				.build();
	}

	@Override
	public String complete(String systemPrompt, String userPrompt) {
		if (!properties.isConfigured()) {
			// 不带密钥信息进日志：只说缺了哪一项
			log.warn("AI 服务未配置，拒绝调用（base-url / api-key / model 需通过环境变量提供）");
			throw new ServiceUnavailableException(ErrorCode.AI_UNAVAILABLE,
					"AI 服务暂时不可用，请确认服务端已配置 AI_BASE_URL / AI_API_KEY / AI_MODEL");
		}

		Map<String, Object> body = Map.of(
				"model", properties.getModel(),
				"temperature", 0.2,
				"messages", List.of(
						Map.of("role", "system", "content", systemPrompt),
						Map.of("role", "user", "content", userPrompt)));

		String responseBody;
		try {
			responseBody = restClient.post()
					.uri("/chat/completions")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
					.body(body)
					.retrieve()
					.body(String.class);
		} catch (ResourceAccessException ex) {
			throw translateNetworkFailure(ex);
		}

		if (responseBody == null || responseBody.isBlank()) {
			throw new ServiceUnavailableException(ErrorCode.AI_EMPTY_RESULT, "AI 没有给出有效建议，请重试");
		}
		return extractContent(responseBody);
	}

	/** 超时与其余网络故障分家：前端一个提示「没及时返回」，一个提示「服务不可用」。 */
	private static RuntimeException translateNetworkFailure(ResourceAccessException ex) {
		Throwable cause = ex.getCause();
		if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
			return new GatewayTimeoutException(ErrorCode.AI_TIMEOUT, "AI 没有及时返回，稍后再试");
		}
		if (cause instanceof ConnectException) {
			return new ServiceUnavailableException(ErrorCode.AI_UNAVAILABLE, "AI 服务暂时不可用，稍后再试");
		}
		log.info("AI 调用网络异常：{}", ex.getMessage());
		return new ServiceUnavailableException(ErrorCode.AI_UNAVAILABLE, "AI 服务暂时不可用，稍后再试");
	}

	/**
	 * OpenAI 兼容响应里只取 choices[0].message.content。
	 * 响应结构由服务端保证，但兼容实现鱼龙混杂：缺字段、空内容一律按 AI_EMPTY_RESULT 处理，
	 * 不让 NPE 或结构变化变成 500。
	 */
	private String extractContent(String responseBody) {
		try {
			ChatCompletionResponse parsed = objectMapper.readValue(responseBody, ChatCompletionResponse.class);
			if (parsed.choices() == null || parsed.choices().isEmpty()) {
				throw emptyResult();
			}
			String content = parsed.choices().get(0).message() == null ? null : parsed.choices().get(0).message().content();
			if (content == null || content.isBlank()) {
				throw emptyResult();
			}
			return content;
		} catch (ServiceUnavailableException ex) {
			throw ex;
		} catch (Exception ex) {
			log.info("AI 响应不是预期的 chat/completions 结构：{}", ex.getMessage());
			throw emptyResult();
		}
	}

	private static ServiceUnavailableException emptyResult() {
		return new ServiceUnavailableException(ErrorCode.AI_EMPTY_RESULT, "AI 没有给出有效建议，请重试");
	}

	/** 只映射用得到的三个字段，其余全部忽略。 */
	private record ChatCompletionResponse(List<Choice> choices) {
	}

	private record Choice(Message message) {
	}

	private record Message(String content) {
	}
}
