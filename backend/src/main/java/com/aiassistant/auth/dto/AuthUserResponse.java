package com.aiassistant.auth.dto;

import com.aiassistant.auth.User;

/**
 * 账号出参。刻意不含 password_hash / status / last_login_at：
 * 接口按「界面要什么给什么」裁剪，将来加字段时也不用担心顺手把凭证字段带出去。
 *
 * <p>{@code timezone} 给前端用（例如把 ISO 时刻按用户时区展示），
 * 前端不做时区换算时也可以忽略它，不影响其它字段。
 */
public record AuthUserResponse(Long id, String username, String email, String displayName, String timezone) {

	public static AuthUserResponse from(User user) {
		return new AuthUserResponse(
				user.getId(),
				user.getUsername(),
				user.getEmail(),
				user.getDisplayName(),
				user.getTimezone());
	}
}
