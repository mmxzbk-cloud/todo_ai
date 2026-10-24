package com.aiassistant.news.fetch;

/**
 * 抓取层的网络异常。不携带 ErrorCode：网络失败在采集管道里记入来源健康状态，
 * 只有「试抓超时」被接口层翻译成 FETCH_TIMEOUT（HTTP 504），翻译规则在各调用方。
 */
public class NewsHttpException extends RuntimeException {

	private final boolean timeout;

	public NewsHttpException(String message, Throwable cause, boolean timeout) {
		super(message, cause);
		this.timeout = timeout;
	}

	public boolean isTimeout() {
		return timeout;
	}
}
