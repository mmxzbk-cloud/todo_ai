package com.aiassistant.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.RateLimitedException;

/**
 * 登录失败限流：key 为 {@code username|clientIp}，同一组合连续失败 5 次锁 15 分钟。
 *
 * <p>为什么不放 Redis：单实例部署（H2 文件库本来就只允许一个进程打开），
 * 为了一个计数器引入外部依赖不划算。代价是重启后计数清零——重启本身就会打断攻击脚本，
 * 这一点损失可以接受。
 *
 * <p>为什么 key 里同时有账号和 IP：只用账号会被「换用户名」绕开普查式爆破，
 * 只用 IP 会误伤同一出口 IP 下的其他人（也很容易换 IP）。
 */
@Component
public class LoginRateLimiter {

	static final int MAX_FAILURES = 5;

	static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	/** 超过这个规模就在写入时顺手清理过期条目，避免「每个新用户名一次尝试」把内存堆满。 */
	private static final int CLEANUP_THRESHOLD = 1_000;

	private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

	/** 在比对密码之前调用：被锁的组合连一次 bcrypt 都不该跑。 */
	public void checkAllowed(String username, String clientIp) {
		Instant now = Instant.now();
		Attempt attempt = attempts.get(key(username, clientIp));
		if (attempt == null || !attempt.isLockedAt(now)) {
			return;
		}
		long remainingMinutes = Math.max(1, Duration.between(now, attempt.lockedUntil()).toMinutes() + 1);
		throw new RateLimitedException(ErrorCode.LOGIN_RATE_LIMITED,
				"登录失败次数过多，请在 " + remainingMinutes + " 分钟后重试");
	}

	public void recordFailure(String username, String clientIp) {
		Instant now = Instant.now();
		attempts.compute(key(username, clientIp), (ignored, previous) -> {
			if (previous != null && previous.isLockedAt(now)) {
				// 锁定期内继续尝试不延长锁定：否则攻击者可以让一个他知道用户名的账号永远登不上
				return previous;
			}
			int failures = previous == null || previous.isStaleAt(now) ? 1 : previous.failures() + 1;
			return failures >= MAX_FAILURES
					? new Attempt(0, now, now.plus(LOCK_DURATION))
					: new Attempt(failures, now, null);
		});
		if (attempts.size() >= CLEANUP_THRESHOLD) {
			attempts.entrySet().removeIf(entry -> entry.getValue().isStaleAt(now));
		}
	}

	/** 登录成功就清空：留着旧的失败次数会让「偶尔输错一次」累积到下一次登录时突然被锁。 */
	public void recordSuccess(String username, String clientIp) {
		attempts.remove(key(username, clientIp));
	}

	/**
	 * 限流键不区分用户名大小写：{@code Admin} 与 {@code admin} 在用户看来是同一个账号，
	 * 分别计数等于把可尝试次数翻倍。
	 */
	private static String key(String username, String clientIp) {
		return String.valueOf(username).trim().toLowerCase(Locale.ROOT) + '|' + String.valueOf(clientIp);
	}

	/**
	 * 一次限流周期的记录。
	 *
	 * @param failures    锁定期开始前累计的失败次数（锁定时清零）
	 * @param lastFailureAt 最后一次失败时刻，用来判断这条记录是否还在「有效窗口」内
	 * @param lockedUntil 锁定截止时刻，null 表示没被锁
	 */
	private record Attempt(int failures, Instant lastFailureAt, Instant lockedUntil) {

		boolean isLockedAt(Instant now) {
			return this.lockedUntil != null && this.lockedUntil.isAfter(now);
		}

		/**
		 * 超过一个锁定时长没有任何失败，说明上一次尝试已经被遗忘，从 1 重新数。
		 * 用同一个时长是因为「记多久」与「锁多久」本来就该是一件事，
		 * 两套时长只会多一个要解释的常数。
		 */
		boolean isStaleAt(Instant now) {
			return this.lastFailureAt.plus(LOCK_DURATION).isBefore(now);
		}
	}
}
