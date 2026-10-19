package com.aiassistant.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.dto.AuthSessionResponse;
import com.aiassistant.auth.dto.AuthUserResponse;
import com.aiassistant.auth.dto.LoginRequest;
import com.aiassistant.auth.dto.RefreshRequest;
import com.aiassistant.auth.dto.RegisterRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 鉴权接口。Controller 只做协议转换：触发校验、把请求上下文里的客户端信息取出来交给 Service，
 * 业务判断（重名、限流、轮换）全部在 AuthService 里。
 *
 * <p>注册/登录/刷新是 SecurityConfig 里 permitAll 的三个端点，
 * 登出与 me 要求已登录——用 access token 都过不去的客户端，本来也不该能刷新令牌。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	private final CurrentUserProvider currentUserProvider;

	public AuthController(AuthService authService, CurrentUserProvider currentUserProvider) {
		this.authService = authService;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthUserResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public AuthSessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return authService.login(request, clientIp(httpRequest), clientUserAgent(httpRequest));
	}

	@PostMapping("/refresh")
	public AuthSessionResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
		return authService.refresh(request, clientIp(httpRequest), clientUserAgent(httpRequest));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody RefreshRequest request) {
		authService.logout(request);
	}

	/** 前端启动时用它确认本地令牌还有效；也用来在登录后补齐界面要的显示名。 */
	@GetMapping("/me")
	public AuthUserResponse me() {
		return authService.me(currentUserProvider.require().id());
	}

	/**
	 * 客户端地址，用于审计与登录限流。
	 *
	 * <p>这里只取 remoteAddr，不解析 X-Forwarded-For：那个头部是客户端可写的，
	 * 直接采信等于让攻击者给每个请求换一个「来源 IP」，把按 IP 的限流绕过去。
	 * 生产在 Nginx 之后时，正确的做法是让容器认这个头（Boot 的 server.forward-headers-strategy）
	 * 或由 Nginx 覆盖它，而不是在这里猜。
	 */
	private static String clientIp(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	private static String clientUserAgent(HttpServletRequest request) {
		return request.getHeader("User-Agent");
	}
}
