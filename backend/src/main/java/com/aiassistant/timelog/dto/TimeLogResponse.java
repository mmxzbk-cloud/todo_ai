package com.aiassistant.timelog.dto;

import java.time.Instant;

import com.aiassistant.timelog.TaskTimeLog;

/**
 * 出参。source 用字符串而不是枚举，前端拿到的就是库里那个原值（TIMER / MANUAL）。
 * endedAt 为 null 表示这一段还在跑。
 */
public record TimeLogResponse(
		Long id,
		Long taskId,
		Instant startedAt,
		Instant endedAt,
		int minutes,
		String source,
		String note) {

	public static TimeLogResponse from(TaskTimeLog timeLog) {
		return new TimeLogResponse(
				timeLog.getId(),
				timeLog.getTaskId(),
				timeLog.getStartedAt(),
				timeLog.getEndedAt(),
				timeLog.getMinutes(),
				timeLog.getSource().name(),
				timeLog.getNote());
	}
}
