package com.aiassistant.news.dto;

import java.util.List;

/**
 * 试抓结果（AC 1.3）：只预览不落库。robots 禁止与超时是异常路径
 * （分别 400 ROBOTS_DISALLOWED / 504 FETCH_TIMEOUT），其余失败用 success=false + message 表达。
 */
public record TrialFetchResponse(
		boolean success,
		int itemCount,
		List<String> titles,
		String message) {

	public static TrialFetchResponse ok(int itemCount, List<String> titles) {
		return new TrialFetchResponse(true, itemCount, titles, null);
	}

	public static TrialFetchResponse fail(String message) {
		return new TrialFetchResponse(false, 0, List.of(), message);
	}
}
