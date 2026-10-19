package com.aiassistant.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 任务变更审计，对应 V1 的 task_activity_logs。
 *
 * 只追加不修改：表上没有 updated_at，实体也不做 JPA 关联（外键一律存 id），
 * 这样审计记录不会因为任务被改动而跟着变。
 */
@Entity
@Table(name = "task_activity_logs")
public class TaskActivityLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "task_id", nullable = false)
	private Long taskId;

	/**
	 * 动作发起人。列可空，因为系统侧写入（如超时兜底截断）没有人类发起人；
	 * 用户触发的写入一律填当前用户 id。
	 */
	@Column(name = "actor_id")
	private Long actorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 30)
	private TaskAction action;

	/** 被改动的字段名；CREATED / DELETED / TIMER_* 这类整体动作不填。 */
	@Column(name = "field", length = 50)
	private String field;

	@Column(name = "old_value", length = 500)
	private String oldValue;

	@Column(name = "new_value", length = 500)
	private String newValue;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getTaskId() {
		return taskId;
	}

	public void setTaskId(Long taskId) {
		this.taskId = taskId;
	}

	public Long getActorId() {
		return actorId;
	}

	public void setActorId(Long actorId) {
		this.actorId = actorId;
	}

	public TaskAction getAction() {
		return action;
	}

	public void setAction(TaskAction action) {
		this.action = action;
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public String getOldValue() {
		return oldValue;
	}

	public void setOldValue(String oldValue) {
		this.oldValue = oldValue;
	}

	public String getNewValue() {
		return newValue;
	}

	public void setNewValue(String newValue) {
		this.newValue = newValue;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
