package com.aiassistant.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 登录会话（刷新令牌），对应 V1 的 {@code auth_refresh_tokens}。
 *
 * <p>只存令牌明文的 SHA-256：库文件被拿走时，拿到的是一堆不可逆的摘要，
 * 不能直接当令牌用。明文只在签发那一次返回给前端。
 *
 * <p>{@code user_id} 只存 id，不做 JPA 关联：与其它实体一致，避免跨表懒加载，
 * 也避免这个纯凭证表把账号实体的图带进事务。
 */
@Entity
@Table(name = "auth_refresh_tokens")
public class AuthRefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** SHA-256 的十六进制小写（64 字符），列长 100 够用。 */
	@Column(name = "token_hash", nullable = false, length = 100)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	/** 非空表示已吊销（登出、轮换，或该会话被判定为异常）。行保留，便于回溯。 */
	@Column(name = "revoked_at")
	private Instant revokedAt;

	/** 以下三列是审计用途：出问题时能看出「这个会话是从哪个客户端、哪个 IP 发起的」。 */
	@Column(name = "user_agent", length = 255)
	private String userAgent;

	@Column(name = "client_ip", length = 45)
	private String clientIp;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** 此刻是否还能用来换新令牌：没过期、没被吊销。 */
	public boolean isUsableAt(Instant now) {
		return this.revokedAt == null && this.expiresAt.isAfter(now);
	}

	/**
	 * 吊销。已经吊销过的不改时间：第一次吊销的时刻才是有意义的（那时这个会话真的失效了），
	 * 后面重复登出、重复轮换不该把历史改掉。
	 */
	public void revoke(Instant at) {
		if (this.revokedAt == null) {
			this.revokedAt = at;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public String getClientIp() {
		return clientIp;
	}

	public void setClientIp(String clientIp) {
		this.clientIp = clientIp;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public void setLastUsedAt(Instant lastUsedAt) {
		this.lastUsedAt = lastUsedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
