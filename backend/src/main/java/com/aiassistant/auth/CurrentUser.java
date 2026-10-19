package com.aiassistant.auth;

import java.time.ZoneId;

/**
 * 当前登录用户，作为 SecurityContext 里的 principal 往下传。
 *
 * <p>带上 {@code displayName} 与 {@code zoneId} 是因为它们几乎每个请求都要用：
 * 显示名要出现在界面上，「今天」「本周」必须按用户所在时区算（用服务器时区算，
 * 出国或换机器后历史统计会整体位移）。每处都回表查一次不如认证时查好。
 */
public record CurrentUser(Long id, String username, String displayName, ZoneId zoneId) {
}
