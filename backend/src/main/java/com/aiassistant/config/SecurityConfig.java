package com.aiassistant.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.aiassistant.auth.JwtService;
import com.aiassistant.auth.UserRepository;
import com.aiassistant.common.ApiError;
import com.aiassistant.common.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * 无状态 API 的安全配置。
 *
 * <p>三条前提，改动前先确认它们仍然成立：
 * <ol>
 * <li>认证靠 {@code Authorization: Bearer}，没有会话、没有 Cookie，
 *     所以关掉 CSRF 是安全的（没有浏览器会自动携带的凭证，也就没有可被利用的入口）。</li>
 * <li>同源部署（生产 Nginx 托管前端并把 /api 反代过来，开发期由 Vite 代理），
 *     所以<b>不配 CORS</b>。多一份 CORS 配置就多一个以后会被误放开的入口。</li>
 * <li>只有一个前端，因此没有多域名的 cookie/redirect 需求，不做 CORS 也不影响功能。</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
			ObjectMapper objectMapper) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// H2 控制台是用 iframe 渲染的，开发期放开 sameOrigin；其余安全响应头保持默认
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
				// 不用表单登录与 Basic：这两者会给未认证请求返回 HTML 登录页或 WWW-Authenticate，
				// 前端拿到的东西就不再是统一的 ApiError
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						// 拿凭证的三个入口必须匿名可达；logout 与 me 不在其中，它们要求已登录
						.requestMatchers(HttpMethod.POST,
								"/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
						// /actuator/health 给探活用，/error 给容器内部转发用，/h2-console 是开发期控制台
						.requestMatchers("/actuator/health", "/error", "/h2-console/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						// 未认证与无权限也要回统一的 ApiError：否则前端要同时处理三种错误结构
						.authenticationEntryPoint((request, response, ex) -> writeError(response, objectMapper,
								HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "未登录或登录已过期"))
						.accessDeniedHandler((request, response, ex) -> writeError(response, objectMapper,
								HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "没有访问该资源的权限")))
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * 密码哈希。cost 10：一次校验约几十毫秒，配合登录失败限流已经足够；
	 * 再提高只会让正常登录和注册也变慢，而攻击者的成本并没有线性增长那么多。
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(10);
	}

	/**
	 * 过滤器本身是 bean（依赖注入由容器给），但必须手工交给 Security 链，
	 * 所以这里不加 {@code @Component}：那样它会被当成普通 Filter bean 由容器再注册一次。
	 */
	@Bean
	JwtAuthFilter jwtAuthFilter(UserRepository userRepository, JwtService jwtService) {
		return new JwtAuthFilter(userRepository, jwtService);
	}

	/**
	 * 关掉 JwtAuthFilter 在 Servlet 容器上的自动注册。
	 *
	 * <p>Spring Boot 会把每个 Filter bean 都注册到 Servlet 容器（那是一次在 Security 链之外的执行），
	 * 而我们要的是「只在 Security 链里出现一次」。只加 {@code @Bean} 是不够的，
	 * 需要显式声明一个禁用状态的注册项来占住这个位置。
	 */
	@Bean
	FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter jwtAuthFilter) {
		FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(jwtAuthFilter);
		registration.setEnabled(false);
		return registration;
	}

	private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
			ErrorCode code, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getOutputStream(), ApiError.of(status.value(), code, message));
	}
}
