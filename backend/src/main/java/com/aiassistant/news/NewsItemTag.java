package com.aiassistant.news;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 资讯与标签的关联行（info_item_tags），与 TaskTag 同构：
 * 主键就是 (info_item_id, tag_id)，整体替换用先删后插。
 */
@Entity
@Table(name = "info_item_tags")
public class NewsItemTag {

	@EmbeddedId
	private NewsItemTagId id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** JPA 需要无参构造。 */
	protected NewsItemTag() {
	}

	public NewsItemTag(NewsItemTagId id, Instant createdAt) {
		this.id = id;
		this.createdAt = createdAt;
	}

	public NewsItemTagId getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
