package com.aiassistant.stats.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 耗时统计出参（US-05）。一次区间查询同时给按天与按项目两个维度，
 * 今日页的统计卡片一次取数就能整卡渲染，不必打两趟接口。
 */
public record TimeStatsResponse(
		LocalDate from,
		LocalDate to,
		int totalMinutes,
		List<DayUsage> days,
		List<ProjectUsage> projects,
		RunningTaskInfo running) {
}
