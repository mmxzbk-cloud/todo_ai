package com.aiassistant.common;

/**
 * 请求本身不合法（如子任务再挂子任务、时间区间反了），由 GlobalExceptionHandler 统一转成 400。
 *
 * <p>与 {@code VALIDATION_FAILED} 的区别：那种是 Bean Validation 拦下的字段级问题，
 * 这里是没有请求 DTO 可挂的业务规则。
 */
public class BadRequestException extends RuntimeException {

	private final ErrorCode code;

	public BadRequestException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
