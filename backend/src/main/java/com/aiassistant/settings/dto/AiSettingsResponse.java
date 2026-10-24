package com.aiassistant.settings.dto;

/**
 * AI 开关的出参。开关状态本身对所有人可读（设置页要渲染它），
 * 真正被它拦下的是 AI 建议接口（关着时返回 AI_DISABLED）。
 */
public record AiSettingsResponse(boolean aiEnabled) {
}
