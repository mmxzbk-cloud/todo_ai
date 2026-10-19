package com.aiassistant.timelog;

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
 * 任务耗时流水。列与 V1__init_schema.sql 的 task_time_logs 逐列对齐，
 * 由 spring.jpa.hibernate.ddl-auto: validate 在启动时校验，漏一列或写错长度都会当场报错。
 *
 * <p>这张表是「实际耗时」的权威数据，tasks.actual_minutes 只是它的冗余累计值；
 * 两者在同一事务里维护，另有每日对账任务按本表重算纠偏。
 */
@Entity
@Table(name = "task_time_logs")
public class TaskTimeLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "task_id", nullable = false)
	private Long taskId;

	/**
	 * 从 tasks 冗余过来的拥有者。按人统计耗时可不必回表，
	 * 更重要的是「同一个人同时只能有一段计时」的校验只查本表就够了。
	 */
	@Column(name = "owner_id", nullable = false)
	private Long ownerId;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	/** NULL 表示正在计时；超过 12 小时未结束的记录由 TimerSweeper 兜底截断。 */
	@Column(name = "ended_at")
	private Instant endedAt;

	/** 落库时算好的分钟数，避免每次聚合都做时间差。未结束的计时为 0。 */
	@Column(name = "minutes", nullable = false)
	private int minutes;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 20)
	private TimeLogSource source = TimeLogSource.TIMER;

	@Column(name = "note", length = 500)
	private String note;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		// 只为兜住「没显式赋值」的情况：测试与手工构造对象时不必自己记着填创建时间。
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getTaskId() {
		return taskId;
	}

	public void setTaskId(Long taskId) {
		this.taskId = taskId;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(Long ownerId) {
		this.ownerId = ownerId;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public void setEndedAt(Instant endedAt) {
		this.endedAt = endedAt;
	}

	public int getMinutes() {
		return minutes;
	}

	public void setMinutes(int minutes) {
		this.minutes = minutes;
	}

	public TimeLogSource getSource() {
		return source;
	}

	public void setSource(TimeLogSource source) {
		this.source = source;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
