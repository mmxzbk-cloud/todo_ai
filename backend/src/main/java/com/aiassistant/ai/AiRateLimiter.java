package com.aiassistant.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.RateLimitedException;

/**
 * AI 建议类接口的每用户滑动窗口限流（默认每分钟 N 次，见 AiProperties）。
 *
 * <p>内存实现足够：单实例个人系统，重启丢窗口只意味着「重启后能多打几次」，无伤大雅；
 * 换成 Redis 的那天再换，接口不变。每个用户一个窗口，互不影响。
 */
@Component
public class AiRateLimiter {

	private final int limitPerMinute;
	private final Map<Long, Deque<Instant>> hitsByUser = new ConcurrentHashMap<>();

	public AiRateLimiter(AiProperties properties) {
		this.limitPerMinute = properties.getRateLimitPerMinute();
	}

	/** 通过则记一次调用；超限抛 AI_RATE_LIMITED（HTTP 429）。 */
	public void check(Long userId) {
		Instant now = Instant.now();
		Deque<Instant> window = hitsByUser.computeIfAbsent(userId, key -> new ArrayDeque<>());
		synchronized (window) {
			Instant cutoff = now.minus(Duration.ofMinutes(1));
			while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
				window.removeFirst();
			}
			if (window.size() >= limitPerMinute) {
				throw new RateLimitedException(ErrorCode.AI_RATE_LIMITED, "请求太频繁，请稍后再试");
			}
			window.addLast(now);
		}
	}
}
