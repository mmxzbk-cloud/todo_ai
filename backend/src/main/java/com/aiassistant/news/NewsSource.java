package com.aiassistant.news;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 订阅来源（info_sources）。健康状态字段由采集管道维护，来源管理接口只读展示。
 */
@Entity
@Table(name = "info_sources")
public class NewsSource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false)
	private Long ownerId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "type", nullable = false, length = 10)
	@Enumerated(EnumType.STRING)
	private NewsSourceType type;

	@Column(name = "url", nullable = false, length = 1000)
	private String url;

	/** 网页类型的解析规则 JSON；RSS 为 null。结构见 dto/NewsParseRule。 */
	@Lob
	@Column(name = "parse_rule")
	private String parseRule;

	@Column(name = "default_category", length = 20)
	@Enumerated(EnumType.STRING)
	private NewsCategory defaultCategory;

	@Column(name = "enabled", nullable = false)
	private boolean enabled = true;

	// ---- 健康状态（采集管道写入） ----

	@Column(name = "last_fetch_at")
	private Instant lastFetchAt;

	@Column(name = "last_fetch_ok")
	private Boolean lastFetchOk;

	@Column(name = "last_fetch_message", length = 500)
	private String lastFetchMessage;

	@Column(name = "consecutive_failures", nullable = false)
	private int consecutiveFailures;

	@Column(name = "auto_disabled", nullable = false)
	private boolean autoDisabled;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(Long ownerId) {
		this.ownerId = ownerId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public NewsSourceType getType() {
		return type;
	}

	public void setType(NewsSourceType type) {
		this.type = type;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getParseRule() {
		return parseRule;
	}

	public void setParseRule(String parseRule) {
		this.parseRule = parseRule;
	}

	public NewsCategory getDefaultCategory() {
		return defaultCategory;
	}

	public void setDefaultCategory(NewsCategory defaultCategory) {
		this.defaultCategory = defaultCategory;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Instant getLastFetchAt() {
		return lastFetchAt;
	}

	public Boolean getLastFetchOk() {
		return lastFetchOk;
	}

	public String getLastFetchMessage() {
		return lastFetchMessage;
	}

	public int getConsecutiveFailures() {
		return consecutiveFailures;
	}

	public boolean isAutoDisabled() {
		return autoDisabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/** 采集结果落健康状态：成功清零失败计数，失败累加并在达阈值时置 autoDisabled。 */
	public void recordFetch(boolean ok, String message, int failureThreshold) {
		lastFetchAt = Instant.now();
		lastFetchOk = ok;
		lastFetchMessage = message == null ? null : trimToLimit(message, 500);
		if (ok) {
			consecutiveFailures = 0;
			autoDisabled = false;
			return;
		}
		consecutiveFailures++;
		if (consecutiveFailures >= failureThreshold) {
			autoDisabled = true;
		}
	}

	/** 用户手动重新启用：清掉失败标记，从零开始重新计数。 */
	public void resetFailureState() {
		consecutiveFailures = 0;
		autoDisabled = false;
	}

	private static String trimToLimit(String value, int limit) {
		return value.length() <= limit ? value : value.substring(0, limit);
	}
}
