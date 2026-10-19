package com.aiassistant.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 变更历史的读取出口，任务详情（task 包）与 /api/v1/tasks/{id}/activities（timelog 包）共用同一个方法。
 *
 * 只保留这一个「最近 50 条」的查询，而不是给两个消费方各配一个：同样的「最近 50 条变更历史」
 * 出现两份定义，早晚会在某一处改了排序或条数，让两个入口对同一件事给出不同的答案。
 *
 * 排序固定「新的在前」，没有 id 兜底：created_at 是微秒精度的时刻，同一事务里连写几条也会各自取
 * Instant.now()，实际不会相同时刻。如果以后改成同一批次共用一个时刻，需要在这里补 id 排序。
 */
public interface TaskActivityLogRepository extends JpaRepository<TaskActivityLog, Long> {

	List<TaskActivityLog> findTop50ByTaskIdOrderByCreatedAtDesc(Long taskId);
}
