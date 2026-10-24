package com.aiassistant.news.dto;

/**
 * 资讯详情里的关联任务。deleted=true 时前端展示「任务已删除」且不给跳转（AC 6.3）。
 */
public record RelatedTaskResponse(Long taskId, String title, boolean deleted) {
}
