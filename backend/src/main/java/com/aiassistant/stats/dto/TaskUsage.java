package com.aiassistant.stats.dto;

/** 单个任务在某口径下的投入分钟数。title 由服务端回填，软删任务也保留原标题。 */
public record TaskUsage(Long taskId, String title, int minutes) {
}
