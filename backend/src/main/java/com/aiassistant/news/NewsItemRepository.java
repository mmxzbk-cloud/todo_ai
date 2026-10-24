package com.aiassistant.news;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 条目仓储。列表查询的筛选条件动态组合（PRD US-03），
 * 用 JPQL + 布尔开关拼条件（与 TaskRepository.search 同一写法）。
 */
public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

	boolean existsByOwnerIdAndNormalizedUrl(Long ownerId, String normalizedUrl);

	Optional<NewsItem> findByIdAndOwnerId(Long id, Long ownerId);

	/** 每日简报的数据源：当日（用户时区）新入库条目。 */
	@Query("""
			SELECT i FROM NewsItem i
			WHERE i.ownerId = :ownerId AND i.createdAt >= :start AND i.createdAt < :end
			ORDER BY i.createdAt DESC, i.id DESC
			""")
	List<NewsItem> findCreatedBetween(@Param("ownerId") Long ownerId,
			@Param("start") Instant start, @Param("end") Instant end);

	long countByOwnerIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long ownerId, Instant start, Instant end);

	List<NewsItem> findByOwnerIdAndAiStatus(Long ownerId, NewsAiStatus aiStatus);

	@Query("""
			SELECT i FROM NewsItem i
			WHERE i.ownerId = :ownerId
			  AND (:hasCategory = false OR i.category = :category)
			  AND (:hasSource = false OR i.sourceId = :sourceId)
			  AND (:hasFrom = false OR i.createdAt >= :from)
			  AND (:hasTo = false OR i.createdAt < :to)
			  AND (:starred = false OR i.starred = true)
			  AND (:unread = false OR i.readAt IS NULL)
			  AND (:hasKeyword = false OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
			       OR LOWER(CAST(i.contentText AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')))
			ORDER BY i.createdAt DESC, i.id DESC
			""")
	Page<NewsItem> search(
			@Param("ownerId") Long ownerId,
			@Param("hasCategory") boolean hasCategory, @Param("category") NewsCategory category,
			@Param("hasSource") boolean hasSource, @Param("sourceId") Long sourceId,
			@Param("hasFrom") boolean hasFrom, @Param("from") Instant from,
			@Param("hasTo") boolean hasTo, @Param("to") Instant to,
			@Param("starred") boolean starred,
			@Param("unread") boolean unread,
			@Param("hasKeyword") boolean hasKeyword, @Param("keyword") String keyword,
			Pageable pageable);
}
