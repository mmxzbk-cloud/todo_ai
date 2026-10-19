package com.aiassistant.common;

/**
 * 稳定的错误码。
 *
 * <p>{@code message} 是给人看的文案，会随版本调整；前端判分支必须依赖这里不变的名字，
 * 所以错误码只增不改：改名等于让所有已发布的判断分支静默失效。
 * 取值与实施计划 1.1 节冻结的清单一一对应，新增取值要先确认前端有没有对应的分支。
 */
public enum ErrorCode {
	VALIDATION_FAILED,
	BAD_REQUEST,
	NOT_FOUND,
	UNAUTHORIZED,
	FORBIDDEN,
	INTERNAL_ERROR,
	USERNAME_TAKEN,
	EMAIL_TAKEN,
	INVALID_CREDENTIALS,
	LOGIN_RATE_LIMITED,
	TASK_NOT_FOUND,
	PROJECT_NOT_FOUND,
	TAG_NOT_FOUND,
	VERSION_CONFLICT,
	TIMER_ALREADY_RUNNING,
	TIMER_NOT_RUNNING,
	PROJECT_NAME_TAKEN,
	TAG_NAME_TAKEN,
	PROJECT_HAS_TASKS
}
