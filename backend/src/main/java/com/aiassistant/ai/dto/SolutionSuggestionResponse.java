package com.aiassistant.ai.dto;

/**
 * 方案建议出参（US-03）。两个字段都可能为 null（模型只给出一半时），
 * 两者皆空的情形在服务层已被拦下，不会出现在响应里。
 */
public record SolutionSuggestionResponse(String approach, String nextStep) {
}
