package com.aiassistant.news;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 来源仓储。所有查询都带 owner 条件（或全表扫描仅限调度任务）：
 * 他人的来源与不存在的来源对外表现一致，不泄露存在性。
 */
public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {

	List<NewsSource> findByOwnerIdOrderByCreatedAtAscIdAsc(Long ownerId);

	Optional<NewsSource> findByIdAndOwnerId(Long id, Long ownerId);

	long countByOwnerId(Long ownerId);

	/** 手动「立即采集」：当前用户启用中的来源。 */
	List<NewsSource> findByOwnerIdAndEnabledTrue(Long ownerId);

	/** 每日定时采集：所有用户启用中、且未被连续失败停抓的来源。 */
	List<NewsSource> findByEnabledTrueAndAutoDisabledFalse();
}
