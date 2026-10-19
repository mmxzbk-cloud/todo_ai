package com.aiassistant.task;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 任务，对应 V1 的 tasks：字段与列逐列对齐（含 {@code version}），
 * 表结构由 Flyway 管，{@code ddl-auto: validate} 会逐列校验，多一列少一列都起不来。
 *
 * 外键（owner_id / project_id / parent_task_id）一律只存 id，不做 JPA 关联：
 * 一是避免懒加载在事务边界外炸掉，二是避免跨包（project / tag）耦合。
 * 需要项目名等展示字段时，在 Service 里批量回填，而不是靠 @ManyToOne 逐行去查。
 */
@Entity
@Table(name = "tasks")
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false)
	private Long ownerId;

	@Column(name = "project_id")
	private Long projectId;

	/** 自引用，只做父子两层；层级约束在服务层，数据库的 CHECK 只兜住「自己不能是自己的父任务」。 */
	@Column(name = "parent_task_id")
	private Long parentTaskId;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", length = 4000)
	private String description;

	/** 来源是自由文本（会议、邮件、临时插单），与 project_id 的项目维度互补。 */
	@Column(name = "source", length = 100)
	private String source;

	@Enumerated(EnumType.STRING)
	@Column(name = "importance", nullable = false, length = 10)
	private Importance importance = Importance.MEDIUM;

	@Enumerated(EnumType.STRING)
	@Column(name = "urgency", nullable = false, length = 10)
	private Urgency urgency = Urgency.MEDIUM;

	/** 优先级是存储字段：手工值优先，只有调用方明确要求重推时才用推导值覆盖。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "priority", nullable = false, length = 10)
	private Priority priority = Priority.P2;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private TaskStatus status = TaskStatus.TODO;

	@Column(name = "due_on")
	private LocalDate dueOn;

	/** 可空：新建时经常估不出来，留空比随便填一个数更诚实。 */
	@Column(name = "estimated_minutes")
	private Integer estimatedMinutes;

	/** 实际耗时的冗余累计列，权威数据在 task_time_logs，同事务累加（见 addActualMinutes）。 */
	@Column(name = "actual_minutes", nullable = false)
	private int actualMinutes;

	/** 三态：null=未评估 / TRUE / FALSE。null 与 false 语义不同，所以不能设默认值。 */
	@Column(name = "ai_replaceable")
	private Boolean aiReplaceable;

	@Column(name = "ai_replacement_note", length = 2000)
	private String aiReplacementNote;

	@Column(name = "next_action", length = 500)
	private String nextAction;

	@Column(name = "notes", length = 2000)
	private String notes;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	/**
	 * 乐观锁。初始值留给 Hibernate 在插入时填 0（Integer 的种子值），
	 * 这里不写 {@code = 0} 是为了让「还没落库」这件事在实体上也是显式的：
	 * 版本号的含义是「数据库里的那一行改到第几版」，新建的实体还没有版本。
	 */
	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

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

	/**
	 * 累加实际耗时。停止计时、手工补录与每日对账都走这里，保证只有一处改冗余列。
	 *
	 * 允许负数：对账在「累计值比明细多」时要能往下修正（见 ReconciliationJob），
	 * 只加不减会让漂移单向累积，重算出来的差额永远修不掉。
	 * 结果夹到 0 以上，因为 tasks.actual_minutes 上有 {@code actual_minutes >= 0} 的 CHECK。
	 */
	public void addActualMinutes(int minutes) {
		this.actualMinutes = Math.max(0, this.actualMinutes + minutes);
	}

	/**
	 * 标记为已完成。计时器「结束并标记完成」直接调这里，不让调用方自己拼状态与完成时刻。
	 * 已经是 DONE 时不覆盖 completed_at：完成时刻记的是第一次完成的时间，
	 * 后面再补一次计时不该把「哪天完成的」改掉。
	 */
	public void markDone(Instant at) {
		this.status = TaskStatus.DONE;
		if (this.completedAt == null) {
			this.completedAt = at;
		}
	}

	/** 软删只置时间戳，行还在，查询侧靠 deleted_at IS NULL 过滤，误删可找回。 */
	public void softDelete(Instant at) {
		this.deletedAt = at;
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

	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public Long getParentTaskId() {
		return parentTaskId;
	}

	public void setParentTaskId(Long parentTaskId) {
		this.parentTaskId = parentTaskId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public Importance getImportance() {
		return importance;
	}

	public void setImportance(Importance importance) {
		this.importance = importance;
	}

	public Urgency getUrgency() {
		return urgency;
	}

	public void setUrgency(Urgency urgency) {
		this.urgency = urgency;
	}

	public Priority getPriority() {
		return priority;
	}

	public void setPriority(Priority priority) {
		this.priority = priority;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public void setStatus(TaskStatus status) {
		this.status = status;
	}

	public LocalDate getDueOn() {
		return dueOn;
	}

	public void setDueOn(LocalDate dueOn) {
		this.dueOn = dueOn;
	}

	public Integer getEstimatedMinutes() {
		return estimatedMinutes;
	}

	public void setEstimatedMinutes(Integer estimatedMinutes) {
		this.estimatedMinutes = estimatedMinutes;
	}

	public int getActualMinutes() {
		return actualMinutes;
	}

	public Boolean getAiReplaceable() {
		return aiReplaceable;
	}

	public void setAiReplaceable(Boolean aiReplaceable) {
		this.aiReplaceable = aiReplaceable;
	}

	public String getAiReplacementNote() {
		return aiReplacementNote;
	}

	public void setAiReplacementNote(String aiReplacementNote) {
		this.aiReplacementNote = aiReplacementNote;
	}

	public String getNextAction() {
		return nextAction;
	}

	public void setNextAction(String nextAction) {
		this.nextAction = nextAction;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public Integer getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}
}
