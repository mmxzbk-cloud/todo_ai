package com.aiassistant.task.dto;

import com.aiassistant.task.Priority;

import jakarta.validation.constraints.NotNull;

/**
 * 改优先级入参。与 TaskStatusRequest 同形：只改这一个字段，
 * 复用 TaskRequest 会让「只想改优先级」的请求必须回传一堆用不到、还可能带脏值的字段。
 *
 * <p>走这个接口改的值就是手工覆盖值：之后即使 importance / urgency 变了也不会被推导值盖掉，
 * 除非调用方显式带 priority 或只改了 importance / urgency（见 TaskService.update）。
 */
public record TaskPriorityRequest(@NotNull(message = "优先级不能为空") Priority priority) {
}
