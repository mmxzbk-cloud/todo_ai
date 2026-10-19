package com.aiassistant.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参。这里不限制长度、格式：校验放这里只会让「密码错了」变成「参数不合法」，
 * 前端要处理的分支变多，安全性一点没变——真正的防线是比对与限流。
 */
public record LoginRequest(
		@NotBlank(message = "用户名不能为空") String username,
		@NotBlank(message = "密码不能为空") String password) {
}
