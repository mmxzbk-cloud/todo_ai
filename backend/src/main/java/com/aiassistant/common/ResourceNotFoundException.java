package com.aiassistant.common;

/**
 * 请求的资源不存在，由 GlobalExceptionHandler 统一转成 404 响应。
 *
 * <p>带上 ErrorCode 是因为「任务不存在」「项目不存在」在前端要提示的东西不一样，
 * 只有 404 一个状态码是不够的。所有按 id 取数的查询都带 owner 条件，
 * 因此「别人的资源」与「不存在的资源」对外表现完全一致：都是这里。
 */
public class ResourceNotFoundException extends RuntimeException {

	private final ErrorCode code;

	public ResourceNotFoundException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	/** 没有更具体的错误码时用通用 NOT_FOUND，避免调用方为了凑一个枚举去编一个语义不符的名字。 */
	public ResourceNotFoundException(String message) {
		this(ErrorCode.NOT_FOUND, message);
	}

	public ErrorCode getCode() {
		return code;
	}
}
