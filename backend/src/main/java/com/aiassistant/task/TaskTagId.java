package com.aiassistant.task;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * task_tags 的复合主键（task_id, tag_id）。
 *
 * 复合主键必须自己实现 equals/hashCode：JPA 用它判断实体是否是「同一行」，
 * 用默认的对象标识会让同一个键的两次查找互相看不见。
 */
@Embeddable
public class TaskTagId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "task_id", nullable = false)
	private Long taskId;

	@Column(name = "tag_id", nullable = false)
	private Long tagId;

	/** JPA 需要无参构造；业务代码用下面的两参构造。 */
	protected TaskTagId() {
	}

	public TaskTagId(Long taskId, Long tagId) {
		this.taskId = taskId;
		this.tagId = tagId;
	}

	public Long getTaskId() {
		return taskId;
	}

	public Long getTagId() {
		return tagId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TaskTagId that)) {
			return false;
		}
		return Objects.equals(taskId, that.taskId) && Objects.equals(tagId, that.tagId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(taskId, tagId);
	}
}
