package com.aiassistant.stats.dto;

import java.time.Instant;

/**
 * 正在进行的计时。它的 minutes 为 0，不计入任何合计（PRD US-05 规则 2），
 * 由页面单独以「进行中」标识展示。
 */
public record RunningTaskInfo(Long taskId, String taskTitle, Instant startedAt) {
}
