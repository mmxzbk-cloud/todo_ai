package com.aiassistant.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册入参。
 *
 * <p>password 的上限 72 来自 bcrypt 的硬限制（它只用前 72 字节）：不拦的话，
 * 长密码会被静默截断，用户以为设了 100 位密码、实际生效的只有 72 字节。
 *
 * <p>timezone 不在入参里：新账号一律用 {@code Asia/Shanghai}，
 * 改时区属于个人设置，不该在注册这一步决定（此刻用户还没看到任何时间视图）。
 */
public record RegisterRequest(
		@NotBlank(message = "用户名不能为空")
		@Size(min = 3, max = 50, message = "用户名长度需在 3-50 之间")
		String username,

		@NotBlank(message = "密码不能为空")
		@Size(min = 8, max = 72, message = "密码长度需在 8-72 之间")
		String password,

		@NotBlank(message = "显示名称不能为空")
		@Size(max = 100, message = "显示名称不能超过 100 个字符")
		String displayName,

		/** 可选：留空表示不填，登录只认用户名。格式与长度按 V1 的 email 列定。 */
		@Email(message = "邮箱格式不正确")
		@Size(max = 255, message = "邮箱不能超过 255 个字符")
		String email) {
}
