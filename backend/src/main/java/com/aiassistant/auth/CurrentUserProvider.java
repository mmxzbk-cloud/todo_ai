package com.aiassistant.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.UnauthorizedException;

/**
 * 取当前用户的唯一入口：Service 与 Controller 都从这里拿 ownerId，
 * 谁都不直接读 SecurityContextHolder，也就不会出现「某个方法忘了取、结果查了全库」。
 */
@Component
public class CurrentUserProvider {

	/**
	 * 取不到就抛 401。
	 *
	 * <p>正常路径上这里不该失败：Security 配置要求除注册/登录/刷新以外的所有端点都已认证。
	 * 抛 401 是因为这是唯一诚实的答案——继续执行等于用 null 当 ownerId 去查数据。
	 */
	public CurrentUser require() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
			throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
		}
		return currentUser;
	}
}
