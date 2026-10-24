package com.aiassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.RateLimitedException;

/**
 * 限流器的滑动窗口行为。用真实对象而不是 mock：时间窗口的逻辑本身就值得单测，
 * mock 掉它等于什么都没测。
 */
class AiRateLimiterTest {

	private AiRateLimiter limiterWithLimit(int limit) {
		AiProperties properties = new AiProperties();
		properties.setRateLimitPerMinute(limit);
		return new AiRateLimiter(properties);
	}

	@Test
	void allowsCallsUpToLimitWithinWindow() {
		AiRateLimiter limiter = limiterWithLimit(2);

		limiter.check(1L);
		limiter.check(1L);

		assertThatThrownBy(() -> limiter.check(1L))
				.isInstanceOfSatisfying(RateLimitedException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_RATE_LIMITED));
	}

	@Test
	void limitsArePerUser() {
		AiRateLimiter limiter = limiterWithLimit(1);

		limiter.check(1L);

		// 另一个用户不受影响：一个用户的连击不能把别人的额度吃掉
		limiter.check(2L);
		assertThatThrownBy(() -> limiter.check(1L))
				.isInstanceOf(RateLimitedException.class);
	}
}
