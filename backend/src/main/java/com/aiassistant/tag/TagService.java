package com.aiassistant.tag;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.common.ConflictException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.tag.dto.TagRequest;
import com.aiassistant.tag.dto.TagResponse;

/**
 * 标签的唯一写入口。ownerId 由 Controller 从当前登录用户取，任何请求体里都不带 ownerId。
 */
@Service
public class TagService {

	/** 与 tags.name 的列宽一致：改写成「已删除」名称时不能把这一列撑爆。 */
	private static final int MAX_NAME_LENGTH = 50;

	private final TagRepository repository;

	public TagService(TagRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<TagResponse> list(Long ownerId) {
		return repository.findByOwnerIdAndDeletedAtIsNullOrderByNameAscIdAsc(ownerId).stream()
				.map(TagResponse::from)
				.toList();
	}

	@Transactional
	public TagResponse create(Long ownerId, TagRequest request) {
		requireNameAvailable(ownerId, request.name(), null);
		Tag tag = new Tag();
		tag.setOwnerId(ownerId);
		tag.setName(request.name());
		tag.setColor(request.color());
		return TagResponse.from(repository.save(tag));
	}

	@Transactional
	public TagResponse update(Long ownerId, Long id, TagRequest request) {
		Tag tag = require(ownerId, id);
		requireNameAvailable(ownerId, request.name(), id);
		tag.setName(request.name());
		tag.setColor(request.color());
		return TagResponse.from(repository.save(tag));
	}

	/**
	 * 软删。
	 *
	 * 先把名称改写成「原名 (已删除 #id)」再置 deleted_at：(owner_id, name) 是普通唯一索引，
	 * 而 H2 没有部分唯一索引，不改名就永远无法用同名重建标签。
	 * 迁移到 PostgreSQL 换成 WHERE deleted_at IS NULL 的部分唯一索引后，这段改写可以删掉。
	 */
	@Transactional
	public void delete(Long ownerId, Long id) {
		Tag tag = require(ownerId, id);
		tag.setName(softDeletedName(tag.getName(), id));
		tag.setDeletedAt(Instant.now());
		repository.save(tag);
	}

	private Tag require(Long ownerId, Long id) {
		return repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TAG_NOT_FOUND, "标签不存在：id=" + id));
	}

	/** selfId 为 null 表示新建；更新时要放过自己，否则改颜色也会被判重名。 */
	private void requireNameAvailable(Long ownerId, String name, Long selfId) {
		repository.findByOwnerIdAndName(ownerId, name)
				.filter(existing -> !Objects.equals(existing.getId(), selfId))
				.ifPresent(existing -> {
					throw new ConflictException(ErrorCode.TAG_NAME_TAKEN, "标签名称已存在：" + name);
				});
	}

	private static String softDeletedName(String original, Long id) {
		String suffix = " (已删除 #" + id + ")";
		int keep = Math.max(0, MAX_NAME_LENGTH - suffix.length());
		String head = original.length() <= keep ? original : original.substring(0, keep);
		return head + suffix;
	}
}
