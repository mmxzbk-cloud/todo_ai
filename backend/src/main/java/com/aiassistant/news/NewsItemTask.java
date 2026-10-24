package com.aiassistant.news;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 资讯 → 任务关联行（info_item_tasks，PRD US-06）。
 * 任务是软删的：详情展示「任务已删除」靠查询时看 deleted_at，关联行本身保留。
 */
@Entity
@Table(name = "info_item_tasks")
public class NewsItemTask {

	@EmbeddedId
	private NewsItemTaskId id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** JPA 需要无参构造。 */
	protected NewsItemTask() {
	}

	public NewsItemTask(NewsItemTaskId id, Instant createdAt) {
		this.id = id;
		this.createdAt = createdAt;
	}

	public NewsItemTaskId getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
