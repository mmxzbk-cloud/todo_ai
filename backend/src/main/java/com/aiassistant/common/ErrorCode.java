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
	PROJECT_HAS_TASKS,
	// ---- AI 能力包（PRD 第五章 5.3）：错误码只增不改，前端按 code 判分支 ----
	/** 用户级 AI 总开关处于关闭状态（HTTP 403）。 */
	AI_DISABLED,
	/** AI 服务在超时时限内没有返回（HTTP 504）。 */
	AI_TIMEOUT,
	/** AI 服务不可用或调用失败（HTTP 503）。 */
	AI_UNAVAILABLE,
	/** 建议类接口触发每用户限流（HTTP 429）。 */
	AI_RATE_LIMITED,
	/** AI 返回内容为空或缺少必要字段，按失败处理（HTTP 503）。 */
	AI_EMPTY_RESULT
}
