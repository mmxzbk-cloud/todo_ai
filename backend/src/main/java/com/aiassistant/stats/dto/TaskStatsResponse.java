package com.aiassistant.stats.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.aiassistant.task.Priority;
import com.aiassistant.task.TaskStatus;

/**
 * 任务概览统计出参。一次区间查询同时给「当前快照」（状态/优先级分布、逾期）
 * 与「区间内动向」（新建、完成趋势），页面一次取数就能整卡渲染。
 *
 * <p>byStatus / byPriority 是全量键：服务层已把没有任务的枚举值补成 0，
 * 前端可以直接取值，不必再处理「键缺失」的分支。
 */
public record TaskStatsResponse(
		LocalDate from,
		LocalDate to,
		Map<TaskStatus, Long> byStatus,
		Map<Priority, Long> byPriority,
		long overdue,
		long createdInRange,
		long completedInRange,
		List<DayCompletion> completedByDay) {
}
