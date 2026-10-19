package com.aiassistant.timelog.dto;

import com.aiassistant.task.dto.TaskResponse;

/**
 * 结束计时的出参：闭合后的那一段流水 + 更新后的任务。
 * 带上任务是为了让前端用服务端结果覆盖本地乐观更新（任务可能同时被置为已完成）。
 */
public record TimerStopResponse(TimeLogResponse log, TaskResponse task) {
}
