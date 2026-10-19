package com.aiassistant.common;

/**
 * 未认证或凭证无效。401 是前端唯一会触发「刷新令牌并重放一次」的状态码，
 * 所以这里只放「重试可能修复」的情况，权限不足走 ForbiddenException。
 */
public class UnauthorizedException extends RuntimeException {

	private final ErrorCode code;

	public UnauthorizedException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
