package com.aiassistant.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 账号，对应 V1 的 {@code users}：字段与列逐列对齐（含长度），
 * 表结构由 Flyway 管，{@code ddl-auto: validate} 会逐列校验，多一列少一列都起不来。
 *
 * <p>{@code created_at} / {@code updated_at} 由调用方显式赋值：V1 里这两列是 NOT NULL
 * 且没有数据库默认值，交给实体生命周期回调会让「谁负责填」这件事变得不显眼，
 * 而注册与登录是仅有的两处写入口，显式写更清楚。
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 唯一索引不排除已软删的行：用户名不回收，注销后名字仍占用。 */
	@Column(name = "username", nullable = false, length = 50)
	private String username;

	/** 可空：H2 的唯一索引允许多行 NULL，所以不填邮箱的账号不会互相冲突。 */
	@Column(name = "email", length = 255)
	private String email;

	/** bcrypt 摘要（60 字符），长度 100 是给将来换算法留的余量。 */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	/** IANA 时区名，决定「今天」「本周」怎么算；登录后随 CurrentUser 一起进请求上下文。 */
	@Column(name = "timezone", nullable = false, length = 64)
	private String timezone;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public void setLastLoginAt(Instant lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}
}
