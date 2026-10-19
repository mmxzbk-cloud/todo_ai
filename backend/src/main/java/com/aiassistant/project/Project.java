package com.aiassistant.project;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 项目，对应 V1 的 projects。
 *
 * 只归档不删除：项目是任务的归属信息，删掉会让历史任务失去分组依据；
 * 归档（archived_at 非空）表达「不再往这里排新任务」但保留数据与统计。
 * 因此这里没有 deleted_at 字段。
 */
@Entity
@Table(name = "projects")
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false)
	private Long ownerId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "color", length = 16)
	private String color;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "archived_at")
	private Instant archivedAt;

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

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public Instant getArchivedAt() {
		return archivedAt;
	}

	public void setArchivedAt(Instant archivedAt) {
		this.archivedAt = archivedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
