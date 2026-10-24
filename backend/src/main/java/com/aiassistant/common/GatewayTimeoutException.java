package com.aiassistant.common;

/**
 * 上游服务在时限内没有返回（当前只有 AI 调用）。504：问题出在「等多久都没等到」，
 * 与「对面明确回了错误」的 503 区分开，前端据此给不同的提示文案。
 */
public class GatewayTimeoutException extends RuntimeException {

	private final ErrorCode code;

	public GatewayTimeoutException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
