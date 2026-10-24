package com.aiassistant.news.dto;

import jakarta.validation.constraints.NotNull;

/** 已读/未读（PATCH /items/{id}/read）。 */
public record ReadRequest(@NotNull(message = "read 不能为空") Boolean read) {
}
