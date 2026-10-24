package com.aiassistant.settings;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 用户级偏好设置，对应 V2 的 user_settings：一个用户一行，主键就是 user_id。
 *
 * <p>表结构由 Flyway 管，{@code ddl-auto: validate} 会逐列校验。行可以不存在：
 * 没有行的用户按各字段的默认值处理（AI 开关默认关闭），第一次修改偏好时才建行，
 * 避免「注册时顺手插一行设置」这种跨模块的隐式写。
 */
@Entity
@Table(name = "user_settings")
public class UserSetting {

	@Id
	@Column(name = "user_id")
	private Long userId;

	/**
	 * AI 能力总开关。默认关闭（PRD A.4 第 1 条推荐项）：关闭时所有 AI 入口隐藏、
	 * AI 接口对本人返回 AI_DISABLED，系统行为与没有 AI 能力包时完全一致。
	 */
	@Column(name = "ai_enabled", nullable = false)
	private boolean aiEnabled = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public boolean isAiEnabled() {
		return aiEnabled;
	}

	public void setAiEnabled(boolean aiEnabled) {
		this.aiEnabled = aiEnabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
