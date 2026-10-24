package com.aiassistant.news.fetch;

import java.time.Duration;
import java.time.Instant;

/**
 * 单来源采集的时间预算（PRD 3.2：单来源 ≤ source-timeout）。
 * 抓取器在每个条目动作前检查，到点就收手——宁可少抓几条，不挂死一个来源。
 */
public final class Deadline {

	private final Instant expiresAt;

	private Deadline(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public static Deadline within(Duration budget) {
		return new Deadline(Instant.now().plus(budget));
	}

	public boolean expired() {
		return remaining().isZero() || remaining().isNegative();
	}

	public Duration remaining() {
		return Duration.between(Instant.now(), expiresAt);
	}
}
