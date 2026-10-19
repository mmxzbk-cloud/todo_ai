package com.aiassistant.common;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一的错误响应体。
 *
 * <p>字段分工：{@code code} 是前端判分支用的稳定标识（见 {@link ErrorCode}），
 * {@code message} 只给人看；{@code error} 保留 HTTP 原因短语，便于对照日志与浏览器网络面板。
 *
 * <p>{@code fieldErrors} 为空时不会出现在 JSON 里，前端因此可以用「有没有这个键」
 * 决定要不要做表单级错误提示，而不必先判断它是不是空对象。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
		int status,
		String code,
		String error,
		String message,
		Map<String, String> fieldErrors,
		Instant timestamp) {

	public static ApiError of(int status, ErrorCode code, String message) {
		return new ApiError(status, code.name(), reasonPhrase(status), message, Map.of(), Instant.now());
	}

	public static ApiError withFieldErrors(int status, ErrorCode code, String message, Map<String, String> fieldErrors) {
		return new ApiError(status, code.name(), reasonPhrase(status), message, fieldErrors, Instant.now());
	}

	/** 状态码从枚举来，原因短语自然也该从同一处派生，避免两处各写一份文案。 */
	private static String reasonPhrase(int status) {
		HttpStatus resolved = HttpStatus.resolve(status);
		return resolved == null ? "Error" : resolved.getReasonPhrase();
	}
}
