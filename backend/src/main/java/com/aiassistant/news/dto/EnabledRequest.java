package com.aiassistant.news.dto;

import jakarta.validation.constraints.NotNull;

/** 启停来源的入参（PATCH /sources/{id}/enabled）。重新启用同时清掉连续失败标记。 */
public record EnabledRequest(@NotNull(message = "enabled 不能为空") Boolean enabled) {
}
