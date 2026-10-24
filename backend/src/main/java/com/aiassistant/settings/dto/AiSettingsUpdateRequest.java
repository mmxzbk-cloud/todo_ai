package com.aiassistant.settings.dto;

import jakarta.validation.constraints.NotNull;

/** AI 开关的入参。只有这一项偏好，整个请求体就是一个布尔值。 */
public record AiSettingsUpdateRequest(@NotNull boolean enabled) {
}
