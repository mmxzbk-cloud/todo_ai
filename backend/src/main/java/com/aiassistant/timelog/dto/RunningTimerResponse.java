package com.aiassistant.timelog.dto;

import com.aiassistant.task.dto.TaskResponse;

/**
 * 当前正在跑的计时。带上任务，前端不必为了显示「在给哪个任务计时」再请求一次详情。
 */
public record RunningTimerResponse(TimeLogResponse log, TaskResponse task) {
}
