package com.aiassistant.tag;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 标签，对应 V1 的 tags。
 *
 * 表上没有 updated_at，所以实体也没有：改名与改色不需要留修改时刻，
 * 需要追溯名称变化时由任务审计与「已删除」改名后缀来定位。
 */
@Entity
@Table(name = "tags")
public class Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false)
	private Long ownerId;

	@Column(name = "name", nullable = false, length = 50)
	private String name;

	@Column(name = "color", length = 16)
	private String color;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
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

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}
}
