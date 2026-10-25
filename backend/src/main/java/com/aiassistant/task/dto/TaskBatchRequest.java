package com.aiassistant.task.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 批量导入入参（POST /api/v1/tasks/batch）。列表容量（非空、单次上限）在 Service 校验：
 * 它是业务规则而不是字段格式，报错文案里要带具体条数；这里只负责让每个元素
 * 走一遍与单条创建相同的 Bean Validation（title 必填、列宽上限等）。
 */
public record TaskBatchRequest(
		List<@Valid @NotNull TaskRequest> tasks) {
}
