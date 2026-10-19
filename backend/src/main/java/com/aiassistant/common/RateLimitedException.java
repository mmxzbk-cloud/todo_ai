package com.aiassistant.common;

/**
 * 触发限流（当前只有登录失败次数）。429 而不是 401：
 * 前端要能区分「密码错了」和「暂时不许试」，后者的提示是「稍后再试」而不是「检查密码」。
 */
public class RateLimitedException extends RuntimeException {

	private final ErrorCode code;

	public RateLimitedException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
