package com.aiassistant.task;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 任务与标签的关联行，对应 V1 的 task_tags。
 *
 * 没有独立的 id 列：主键就是 (task_id, tag_id)，所以「同一个标签被挂两次」在数据库层就不可能发生。
 * 关联表用「先删后插」整体替换（见 TaskService.assignTags），不做增量比对——
 * 前端交上来的是一份完整标签列表，增量比对多出来的分支只会多出不一致的可能。
 */
@Entity
@Table(name = "task_tags")
public class TaskTag {

	@EmbeddedId
	private TaskTagId id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** JPA 需要无参构造。 */
	protected TaskTag() {
	}

	public TaskTag(TaskTagId id, Instant createdAt) {
		this.id = id;
		this.createdAt = createdAt;
	}

	public TaskTagId getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
