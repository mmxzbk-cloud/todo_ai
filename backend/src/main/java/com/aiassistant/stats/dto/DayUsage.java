package com.aiassistant.stats.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 按天的投入（US-05）。date 为用户时区口径的「流水开始日期」；
 * topTasks 是当天投入最高的前几个任务（至少前 3），供「时间花在哪」一眼可读。
 */
public record DayUsage(LocalDate date, int minutes, List<TaskUsage> topTasks) {
}
