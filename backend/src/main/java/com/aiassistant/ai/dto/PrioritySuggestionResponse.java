package com.aiassistant.ai.dto;

import com.aiassistant.task.Priority;

/**
 * 优先级建议出参（US-02）。建议不落库：采纳与否由用户决定，
 * 采纳动作走既有的 PATCH /tasks/{id}/priority 通道，变更历史照常留痕。
 */
public record PrioritySuggestionResponse(Priority priority, String reason) {
}
