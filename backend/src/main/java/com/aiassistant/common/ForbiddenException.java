package com.aiassistant.common;

/**
 * 身份已知但不允许做这件事（注册已关闭、账号被禁用）。403 与 401 的区别是
 * 「重新登录也解决不了」，前端不该拿它去触发刷新令牌。
 */
public class ForbiddenException extends RuntimeException {

	private final ErrorCode code;

	public ForbiddenException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
