package com.aiassistant.news.dto;

/**
 * 采纳 AI 建议的入参（POST /items/{id}/ai/adopt）。
 * null 视为不采纳；采纳标签时后端把建议标签名解析/建成本人标签后整体替换。
 */
public record AdoptRequest(Boolean adoptCategory, Boolean adoptTags) {
}
