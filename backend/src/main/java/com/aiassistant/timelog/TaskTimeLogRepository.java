package com.aiassistant.timelog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 耗时流水仓储。查询方法都自带 owner_id（或明确的全局语义），
 * 不在 Service 里「先取出来再比对 owner」，少一个分支就是漏一个越权口子。
 */
public interface TaskTimeLogRepository extends JpaRepository<TaskTimeLog, Long> {

	/**
	 * 当前用户有没有未结束的计时。设计上「同一个人同时只能有一段计时」，
	 * 但 H2 没有部分唯一索引，拦不住并发插入两条，所以这里取第一条即可，不做唯一性假设。
	 */
	Optional<TaskTimeLog> findFirstByOwnerIdAndEndedAtIsNull(Long ownerId);

	/** 结束计时用。带 owner_id 条件：他人的记录与不存在的记录都表现为 empty。 */
	Optional<TaskTimeLog> findByIdAndOwnerId(Long id, Long ownerId);

	/**
	 * 任务详情里的耗时明细，最近的一段排在最前面。
	 *
	 * <p>刻意只提供这一个方法、不做 Pageable 重载：详情里「最近 50 条」由 task 包在取回后截断
	 * （一次计时一段流水，50 条以上的任务极少），为此多一个重载不划算；变更历史那边不同，
	 * 条数写进了方法名，见 audit.TaskActivityLogRepository。
	 */
	List<TaskTimeLog> findByTaskIdOrderByStartedAtDesc(Long taskId);

	/**
	 * 单个任务已记录的耗时合计，没有明细时返回 0（COALESCE 而不是回 null）。
	 * 冻结契约里给 T2 预留的读法；每日对账用下面的分组聚合一次算全库，不走这里。
	 */
	@Query("SELECT COALESCE(SUM(log.minutes), 0) FROM TaskTimeLog log WHERE log.taskId = :taskId")
	long sumMinutesByTaskId(@Param("taskId") Long taskId);

	/** 兜底扫描：所有还没结束、且开始时间早于给定时刻的记录（跨用户，定时任务用）。 */
	List<TaskTimeLog> findByEndedAtIsNullAndStartedAtBefore(Instant threshold);

	/**
	 * 按明细重算每个任务的实际耗时，供每日对账使用。
	 * 返回 [taskId, SUM(minutes)]；没有明细的任务不出现在结果里，调用方按 0 处理。
	 */
	@Query("SELECT log.taskId, SUM(log.minutes) FROM TaskTimeLog log GROUP BY log.taskId")
	List<Object[]> sumMinutesGroupedByTask();
}
