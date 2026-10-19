package com.aiassistant.auth.dto;

/**
 * 登录/刷新成功的出参。
 *
 * @param accessToken  访问令牌（HS256 JWT），放 Authorization 头里用
 * @param refreshToken 刷新令牌明文，只在签发的这一次返回；库里只存它的 SHA-256
 * @param expiresIn    access token 的有效期（秒），前端据此提前刷新，不必解析令牌
 * @param user         当前账号信息，省掉登录后立刻再打一次 /auth/me
 */
public record AuthSessionResponse(String accessToken, String refreshToken, long expiresIn, AuthUserResponse user) {
}
