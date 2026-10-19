package com.aiassistant.timelog.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 手工补录耗时。只允许补录已经发生过的区间，所以两个时间点都必须给；
 * 「结束时间不能早于开始时间」由 Service 判断，这里只做非空与长度校验。
 */
public record ManualTimeLogRequest(
		@NotNull(message = "开始时间不能为空")
		Instant startedAt,

		@NotNull(message = "结束时间不能为空")
		Instant endedAt,

		@Size(max = 500, message = "备注长度不能超过 500 个字符")
		String note) {
}
