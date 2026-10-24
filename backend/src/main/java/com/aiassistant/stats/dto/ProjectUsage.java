package com.aiassistant.stats.dto;

/**
 * 按项目的投入（US-05）。projectId 为 null 表示「无项目」；
 * share 是该占区间总投入的比例（0–1，保留 4 位小数），前端负责换算成百分比。
 */
public record ProjectUsage(Long projectId, String projectName, int minutes, double share) {
}
