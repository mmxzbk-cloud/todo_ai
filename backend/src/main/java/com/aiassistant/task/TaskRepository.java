package com.aiassistant.task;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 任务仓储。
 *
 * 两条硬规则：
 * 1. 按 id 取数一律「owner 条件 + deleted_at IS NULL」写在查询里，不做「取出来再比对」——后者漏一个分支就是越权；
 * 2. 排序只有一套（截止日近的在前、无截止日的排最后、同截止日按优先级、再按 id 兜底），
 *    列表 / 今日 / 看板都用它，避免三个页面给出三种顺序。
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

	Optional<Task> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

	List<Task> findByOwnerIdAndDeletedAtIsNullAndParentTaskIdOrderByIdDesc(Long ownerId, Long parentTaskId);

	/**
	 * 列表搜索。
	 *
	 * 可选条件用 hasXxx 布尔开关而不是 {@code :param IS NULL}：后者 Hibernate 无法推断参数类型，
	 * 会在启动或首次执行时报错；开关版本参数类型永远明确。
	 * 排序写在查询里而不是交给 Pageable：Sort 表达不了「dueOn 为空的排最后」这个 CASE。
	 * Spring Data 会剥掉 ORDER BY 生成 count 查询，分页总数不受影响。
	 */
	@Query("""
			SELECT t FROM Task t
			WHERE t.ownerId = :ownerId AND t.deletedAt IS NULL
			  AND (:hasStatus = FALSE OR t.status = :status)
			  AND (:hasProject = FALSE OR t.projectId = :projectId)
			  AND (:hasDueFrom = FALSE OR t.dueOn >= :dueFrom)
			  AND (:hasDueTo = FALSE OR t.dueOn <= :dueTo)
			  AND (:hasQ = FALSE OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')))
			ORDER BY CASE WHEN t.dueOn IS NULL THEN 1 ELSE 0 END, t.dueOn ASC, t.priority ASC, t.id DESC
			""")
	Page<Task> search(
			@Param("ownerId") Long ownerId,
			@Param("hasStatus") boolean hasStatus,
			@Param("status") TaskStatus status,
			@Param("hasProject") boolean hasProject,
			@Param("projectId") Long projectId,
			@Param("hasDueFrom") boolean hasDueFrom,
			@Param("dueFrom") LocalDate dueFrom,
			@Param("hasDueTo") boolean hasDueTo,
			@Param("dueTo") LocalDate dueTo,
			@Param("hasQ") boolean hasQ,
			@Param("q") String q,
			Pageable pageable);

	/**
	 * 今日视图：未删 + 截止日不晚于今天 + 状态未结束。
	 *
	 * 「今天」由调用方按 users.timezone 算好再传进来：用服务器时区算会让出国或换机器的用户
	 * 看到整体位移的一天。状态条件写成 IN（而不是 NOT IN）：同样走 ix_tasks_owner_alive_due，
	 * 且与「不在 (DONE, CANCELLED)」等价。
	 */
	@Query("""
			SELECT t FROM Task t
			WHERE t.ownerId = :ownerId AND t.deletedAt IS NULL
			  AND t.dueOn IS NOT NULL AND t.dueOn <= :today
			  AND t.status IN :statuses
			ORDER BY CASE WHEN t.dueOn IS NULL THEN 1 ELSE 0 END, t.dueOn ASC, t.priority ASC, t.id DESC
			""")
	List<Task> findToday(
			@Param("ownerId") Long ownerId,
			@Param("today") LocalDate today,
			@Param("statuses") Collection<TaskStatus> statuses);

	/**
	 * 看板：一次取回固定四列的全部任务，在服务层按状态分组。
	 * 分四次查询会让四列看到不同的数据快照，也会多打三次库。
	 */
	@Query("""
			SELECT t FROM Task t
			WHERE t.ownerId = :ownerId AND t.deletedAt IS NULL
			  AND t.status IN :statuses
			ORDER BY CASE WHEN t.dueOn IS NULL THEN 1 ELSE 0 END, t.dueOn ASC, t.priority ASC, t.id DESC
			""")
	List<Task> findBoard(@Param("ownerId") Long ownerId, @Param("statuses") Collection<TaskStatus> statuses);

	/**
	 * 批量按 id 取任务（不筛 deleted_at）：耗时统计要展示流水归属的任务标题，
	 * 软删任务的流水仍在统计口径里，标题一并给出来而不是显示成空。
	 */
	List<Task> findByIdInAndOwnerId(Collection<Long> ids, Long ownerId);
}
