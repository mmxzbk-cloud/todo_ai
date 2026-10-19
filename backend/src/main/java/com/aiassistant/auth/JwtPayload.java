package com.aiassistant.auth;

import java.time.Instant;

/**
 * 从 access token 里解出来的内容。只有这四项：令牌里放得越少，
 * 令牌泄露时能拿走的信息越少，改字段时要动的地方也越少。
 *
 * <p>{@code username} 只是给日志与排查用的展示值，鉴权的依据是 {@code userId}
 * （过滤器还要按它回表确认账号仍然存在、仍然 ACTIVE）。
 */
public record JwtPayload(Long userId, String username, Instant issuedAt, Instant expiresAt) {
}
