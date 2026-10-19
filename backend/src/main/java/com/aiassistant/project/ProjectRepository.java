package com.aiassistant.project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 项目仓储。项目没有软删，所以查询条件里只有 owner_id 与（可选的）archived_at。
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

	List<Project> findByOwnerIdOrderBySortOrderAscIdAsc(Long ownerId);

	List<Project> findByOwnerIdAndArchivedAtIsNullOrderBySortOrderAscIdAsc(Long ownerId);

	Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);

	/**
	 * 列表与看板的项目名回填用：一次取回本页涉及的所有项目，避免逐行查询。
	 * 调用方需保证 ids 非空（空集合会让 IN 生成非法 SQL）。
	 */
	List<Project> findByOwnerIdAndIdIn(Long ownerId, Collection<Long> ids);

	/** 重名校验。唯一索引是 (owner_id, name)，项目不软删，所以直接用原始名称查。 */
	Optional<Project> findByOwnerIdAndName(Long ownerId, String name);
}
