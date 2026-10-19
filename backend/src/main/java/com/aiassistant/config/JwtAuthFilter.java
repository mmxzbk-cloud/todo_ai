package com.aiassistant.config;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.aiassistant.auth.CurrentUser;
import com.aiassistant.auth.JwtService;
import com.aiassistant.auth.User;
import com.aiassistant.auth.UserRepository;
import com.aiassistant.auth.UserStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 解析 {@code Authorization: Bearer <token>}，通过校验后把 {@link CurrentUser} 放进 SecurityContext。
 *
 * <p>它刻意不是 {@code @Component}：那样 Spring Boot 会把它当作普通 Servlet 过滤器再注册一次
 * （跑在 Security 链之外），同一个请求上便会执行两遍。它只在 SecurityConfig 里作为一个 bean
 * 加进链中，并把 Servlet 容器的自动注册显式关掉。
 *
 * <p>校验通过后仍然回表读一次用户：令牌里没有 displayName / timezone（界面要用），
 * 而且账号被禁用或注销后旧令牌必须立刻失效——只看令牌签名做不到这件事。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

	private static final String BEARER_PREFIX = "Bearer ";

	/** 当前只有一个角色。授权规则都按「是不是本人」判断（见各 Service 的 owner 条件），角色只是占位。 */
	private static final String AUTHORITY_USER = "ROLE_USER";

	/** users.timezone 里存了非法时区名时的兜底值。 */
	private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

	private final UserRepository userRepository;

	private final JwtService jwtService;

	public JwtAuthFilter(UserRepository userRepository, JwtService jwtService) {
		this.userRepository = userRepository;
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)
				&& SecurityContextHolder.getContext().getAuthentication() == null) {
			// 令牌无效、账号不存在或非 ACTIVE 时不设置认证：请求继续往下走，
			// 由 Security 链按端点规则给出 401（受保护端点）或放行（公开端点）
			jwtService.parse(header.substring(BEARER_PREFIX.length()).trim())
					.flatMap(payload -> activeUser(payload.userId()))
					.ifPresent(user -> SecurityContextHolder.getContext()
							.setAuthentication(authenticationFor(user, request)));
		}
		filterChain.doFilter(request, response);
	}

	/**
	 * 令牌里的 sub 只说明「签发时这个账号存在」：注销（软删）与禁用都要在这里挡掉，
	 * 否则一个被禁用的账号能拿着旧令牌继续用到令牌过期为止。
	 */
	private Optional<User> activeUser(Long userId) {
		if (userId == null) {
			return Optional.empty();
		}
		return userRepository.findByIdAndDeletedAtIsNull(userId)
				.filter(user -> user.getStatus() == UserStatus.ACTIVE);
	}

	private static UsernamePasswordAuthenticationToken authenticationFor(User user, HttpServletRequest request) {
		CurrentUser principal = new CurrentUser(
				user.getId(),
				user.getUsername(),
				user.getDisplayName(),
				zoneIdOf(user.getTimezone()));

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(AUTHORITY_USER)));
		authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		return authentication;
	}

	/**
	 * 时区名来自数据库。存了非法值时退回默认时区并告警：宁可「今天」按默认时区算，
	 * 也不要让每一次请求都在这里抛 DateTimeException，变成一个查不出原因的 500。
	 */
	private static ZoneId zoneIdOf(String timezone) {
		if (timezone == null || timezone.isBlank()) {
			return DEFAULT_ZONE;
		}
		try {
			return ZoneId.of(timezone);
		}
		catch (DateTimeException ex) {
			log.warn("users.timezone 不是合法时区名，按 {} 处理：{}", DEFAULT_ZONE, timezone);
			return DEFAULT_ZONE;
		}
	}
}
