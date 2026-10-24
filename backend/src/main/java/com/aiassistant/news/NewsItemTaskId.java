package com.aiassistant.news;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * info_item_tasks 的复合主键（info_item_id, task_id）：一条资讯可多次转任务，
 * 同一条资讯与同一个任务的关联天然只可能有一行。
 */
@Embeddable
public class NewsItemTaskId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "info_item_id", nullable = false)
	private Long infoItemId;

	@Column(name = "task_id", nullable = false)
	private Long taskId;

	/** JPA 需要无参构造；业务代码用两参构造。 */
	protected NewsItemTaskId() {
	}

	public NewsItemTaskId(Long infoItemId, Long taskId) {
		this.infoItemId = infoItemId;
		this.taskId = taskId;
	}

	public Long getInfoItemId() {
		return infoItemId;
	}

	public Long getTaskId() {
		return taskId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof NewsItemTaskId that)) {
			return false;
		}
		return Objects.equals(infoItemId, that.infoItemId) && Objects.equals(taskId, that.taskId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(infoItemId, taskId);
	}
}
