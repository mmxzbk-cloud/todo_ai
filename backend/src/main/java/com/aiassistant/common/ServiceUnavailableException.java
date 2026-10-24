package com.aiassistant.common;

/**
 * 依赖的上游服务不可用或返回了不可用的结果（当前只有 AI 调用：服务没部署、
 * 拒绝了请求、或返回了空建议）。503：稍后重试可能恢复，与 5xx 的「本服务坏了」不同。
 */
public class ServiceUnavailableException extends RuntimeException {

	private final ErrorCode code;

	public ServiceUnavailableException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
