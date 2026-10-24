package com.aiassistant.news.dto;

import jakarta.validation.constraints.NotNull;

/** 星标（PATCH /items/{id}/star）。 */
public record StarRequest(@NotNull(message = "starred 不能为空") Boolean starred) {
}
