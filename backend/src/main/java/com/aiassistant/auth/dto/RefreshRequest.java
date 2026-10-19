package com.aiassistant.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 刷新与登出共用：两者都只认那一串刷新令牌。 */
public record RefreshRequest(
		@NotBlank(message = "刷新令牌不能为空") String refreshToken) {
}
