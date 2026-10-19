package com.aiassistant.task.dto;

import java.time.LocalDate;

import com.aiassistant.task.Importance;
import com.aiassistant.task.Priority;
import com.aiassistant.task.TaskStatus;
import com.aiassistant.task.Urgency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 任务入参，新建（POST）与更新（PUT）共用一份。字段长度上限与 V1 的 tasks 列宽一致，
 * 校验放在这里是为了让「列宽溢出」在参数绑定阶段就变成 400，而不是到落库才报数据库异常。
 *
 * 语义按 HTTP 方法区分：
 * - POST：全部可选字段为空时落库默认值，priority 未带则按 importance×urgency 推导；
 * - PUT：整体替换。措辞/文本类字段传 null 表示清空；importance / urgency / status 传 null 表示不修改
 *   （这三个没有「清空」的语义，且优先级重推规则需要区分「未带」与「带了」）；
 *   priority 传 null 时只有当 importance 或 urgency 变了才重推，否则保留原值（手工覆盖不会被抹掉）。
 * - version 只在 PUT 有意义：带上且与库里不一致时返回 409 VERSION_CONFLICT。
 */
public record TaskRequest(
		@NotBlank(message = "标题不能为空")
		@Size(max = 200, message = "标题长度不能超过 200 个字符")
		String title,

		@Size(max = 4000, message = "描述长度不能超过 4000 个字符")
		String description,

		Long projectId,

		Long parentTaskId,

		@Size(max = 100, message = "来源长度不能超过 100 个字符")
		String source,

		Importance importance,

		Urgency urgency,

		Priority priority,

		TaskStatus status,

		LocalDate dueOn,

		@PositiveOrZero(message = "预计耗时为分钟数，不能为负数")
		Integer estimatedMinutes,

		Boolean aiReplaceable,

		@Size(max = 2000, message = "AI 替代方案长度不能超过 2000 个字符")
		String aiReplacementNote,

		@Size(max = 500, message = "下一步长度不能超过 500 个字符")
		String nextAction,

		@Size(max = 2000, message = "备注长度不能超过 2000 个字符")
		String notes,

		Integer version) {
}
