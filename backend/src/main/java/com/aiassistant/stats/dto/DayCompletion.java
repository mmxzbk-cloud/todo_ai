package com.aiassistant.stats.dto;

import java.time.LocalDate;

/**
 * 按天的完成数。date 为用户时区口径的「完成日期」；区间内没有完成的日子也会返回（count 为 0），
 * 前端画趋势不用自己补空日期——与耗时统计的按天列表同一约定。
 */
public record DayCompletion(LocalDate date, long count) {
}
