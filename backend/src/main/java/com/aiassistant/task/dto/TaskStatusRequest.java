package com.aiassistant.task.dto;

import com.aiassistant.task.TaskStatus;

import jakarta.validation.constraints.NotNull;

/**
 * 改状态入参。只改状态这一个字段，所以单独一个 DTO：
 * 复用 TaskRequest 会让「只想改状态」的请求必须回传一堆用不到、还可能带脏值的字段。
 */
public record TaskStatusRequest(@NotNull(message = "状态不能为空") TaskStatus status) {
}
