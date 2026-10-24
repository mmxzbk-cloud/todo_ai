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
 * 资讯条目（info_items）。采集管道负责「入什么」，用户动作（已读/星标/分类采纳）
 * 与 AI 补写都收敛到本类的方法上，避免散落在各服务里各写一套时间戳。
 */
@Entity
@Table(name = "info_items")
public class NewsItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false)
	private Long ownerId;

	/** 来源删除后置 NULL（外键 ON DELETE SET NULL），展示靠 sourceName 快照。 */
	@Column(name = "source_id")
	private Long sourceId;

	@Column(name = "source_name", nullable = false, length = 100)
	private String sourceName;

	@Column(name = "title", nullable = false, length = 500)
	private String title;

	@Column(name = "url", nullable = false, length = 1000)
	private String url;

	/** 去重判据（同用户内唯一），归一化规则见 UrlNormalizer。 */
	@Column(name = "normalized_url", nullable = false, length = 1000)
	private String normalizedUrl;

	@Lob
	@Column(name = "content_text")
	private String contentText;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "category", length = 20)
	@Enumerated(EnumType.STRING)
	private NewsCategory category;

	@Column(name = "ai_summary", length = 1000)
	private String aiSummary;

	/** AI 要点，JSON 字符串数组。 */
	@Lob
	@Column(name = "ai_key_points")
	private String aiKeyPoints;

	@Column(name = "ai_suggested_category", length = 20)
	@Enumerated(EnumType.STRING)
	private NewsCategory aiSuggestedCategory;

	/** AI 建议标签名，JSON 字符串数组（采纳时映射到 tags 表）。 */
	@Column(name = "ai_suggested_tags", length = 500)
	private String aiSuggestedTags;

	@Column(name = "ai_status", nullable = false, length = 10)
	@Enumerated(EnumType.STRING)
	private NewsAiStatus aiStatus = NewsAiStatus.PENDING;

	@Column(name = "ai_error", length = 500)
	private String aiError;

	@Column(name = "read_at")
	private Instant readAt;

	@Column(name = "starred", nullable = false)
	private boolean starred;

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

	// ---------------------------------------------------------------- 用户动作

	/** 已读/未读是用户动作：读状态用时间戳记，NULL 即未读（与 V3 注释一致）。 */
	public void setRead(boolean read) {
		readAt = read ? Instant.now() : null;
	}

	public void setStarred(boolean starred) {
		this.starred = starred;
	}

	// ---------------------------------------------------------------- AI 补写

	public void markAiProcessing() {
		aiStatus = NewsAiStatus.PROCESSING;
		aiError = null;
	}

	public void applyAiResult(String summary, String keyPointsJson, NewsCategory suggestedCategory,
			String suggestedTagsJson) {
		aiSummary = summary;
		aiKeyPoints = keyPointsJson;
		aiSuggestedCategory = suggestedCategory;
		aiSuggestedTags = suggestedTagsJson;
		aiStatus = NewsAiStatus.DONE;
		aiError = null;
	}

	public void markAiFailed(String error) {
		aiStatus = NewsAiStatus.FAILED;
		aiError = error == null ? null : error.length() <= 500 ? error : error.substring(0, 500);
	}

	public void markAiSkipped() {
		aiStatus = NewsAiStatus.SKIPPED;
	}

	// ---------------------------------------------------------------- getters/setters

	public Long getId() {
		return id;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(Long ownerId) {
		this.ownerId = ownerId;
	}

	public Long getSourceId() {
		return sourceId;
	}

	public void setSourceId(Long sourceId) {
		this.sourceId = sourceId;
	}

	public String getSourceName() {
		return sourceName;
	}

	public void setSourceName(String sourceName) {
		this.sourceName = sourceName;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getNormalizedUrl() {
		return normalizedUrl;
	}

	public void setNormalizedUrl(String normalizedUrl) {
		this.normalizedUrl = normalizedUrl;
	}

	public String getContentText() {
		return contentText;
	}

	public void setContentText(String contentText) {
		this.contentText = contentText;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

	public NewsCategory getCategory() {
		return category;
	}

	public void setCategory(NewsCategory category) {
		this.category = category;
	}

	public String getAiSummary() {
		return aiSummary;
	}

	public String getAiKeyPoints() {
		return aiKeyPoints;
	}

	public NewsCategory getAiSuggestedCategory() {
		return aiSuggestedCategory;
	}

	public String getAiSuggestedTags() {
		return aiSuggestedTags;
	}

	public NewsAiStatus getAiStatus() {
		return aiStatus;
	}

	public void setAiStatus(NewsAiStatus aiStatus) {
		this.aiStatus = aiStatus;
	}

	public String getAiError() {
		return aiError;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public boolean isStarred() {
		return starred;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
