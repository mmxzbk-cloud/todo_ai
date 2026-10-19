package com.aiassistant.tag;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 标签仓储。
 *
 * 所有查询都带 owner_id：越权防护靠「查不到」，不靠「查出来再比对」——
 * 后者漏一个分支就是越权，而带条件的查询没有可漏的分支。
 */
public interface TagRepository extends JpaRepository<Tag, Long> {

	List<Tag> findByOwnerIdAndDeletedAtIsNullOrderByNameAscIdAsc(Long ownerId);

	Optional<Tag> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

	/** 任务详情与打标签校验共用；调用方需保证 ids 非空（空集合会让 IN 生成非法 SQL）。 */
	List<Tag> findByOwnerIdAndIdInAndDeletedAtIsNullOrderByNameAscIdAsc(Long ownerId, Collection<Long> ids);

	/**
	 * 按原始名称查重。唯一索引是 (owner_id, name) 且不含 deleted_at，
	 * 所以重名校验必须用原始名称：已软删的标签名称已被改写成「原名 (已删除 #id)」，不会误伤。
	 */
	Optional<Tag> findByOwnerIdAndName(Long ownerId, String name);
}
