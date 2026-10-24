package com.aiassistant.news.dto;

/**
 * 转任务结果：任务建到了本人名下，资讯详情从此展示这条关联（AC 6.2 / 6.3）。
 */
public record ConvertTaskResponse(Long taskId, String taskTitle) {
}
