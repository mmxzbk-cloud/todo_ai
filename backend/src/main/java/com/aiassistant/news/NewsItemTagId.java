package com.aiassistant.news;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * info_item_tags 的复合主键（info_item_id, tag_id）。
 * 复合主键必须自己实现 equals/hashCode：JPA 用它判断实体是否是「同一行」。
 */
@Embeddable
public class NewsItemTagId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "info_item_id", nullable = false)
	private Long infoItemId;

	@Column(name = "tag_id", nullable = false)
	private Long tagId;

	/** JPA 需要无参构造；业务代码用两参构造。 */
	protected NewsItemTagId() {
	}

	public NewsItemTagId(Long infoItemId, Long tagId) {
		this.infoItemId = infoItemId;
		this.tagId = tagId;
	}

	public Long getInfoItemId() {
		return infoItemId;
	}

	public Long getTagId() {
		return tagId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof NewsItemTagId that)) {
			return false;
		}
		return Objects.equals(infoItemId, that.infoItemId) && Objects.equals(tagId, that.tagId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(infoItemId, tagId);
	}
}
