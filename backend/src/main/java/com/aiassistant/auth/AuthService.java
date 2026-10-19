package com.aiassistant.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.auth.dto.AuthSessionResponse;
import com.aiassistant.auth.dto.AuthUserResponse;
import com.aiassistant.auth.dto.LoginRequest;
import com.aiassistant.auth.dto.RefreshRequest;
import com.aiassistant.auth.dto.RegisterRequest;
import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ConflictException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ForbiddenException;
import com.aiassistant.common.UnauthorizedException;

/**
 * 注册、登录、刷新、登出的唯一实现处。
 *
 * <p>四个约定贯穿全类：
 * <ol>
 * <li>「账号不存在」与「密码错误」返回同一个错误码与同一句文案，
 *     否则登录接口就成了「这批用户名里哪些存在」的探测器。</li>
 * <li>刷新令牌用一次就换新的（轮换）：旧记录置 revoked_at 而不是删除，
 *     这样「这个会话被谁在什么时候换过」查得到。</li>
 * <li>密码、刷新令牌的明文都不落库：前者 bcrypt，后者 SHA-256。</li>
 * <li>客户端 IP 与 User-Agent 只作审计，长度超限就截断——为了两个审计字段把登录搞失败不值得。</li>
 * </ol>
 */
@Service
public class AuthService {

	private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

	/** bcrypt 只处理前 72 字节，超过的部分会被静默忽略，不如直接拒绝。 */
	private static final int MAX_PASSWORD_BYTES = 72;

	/** 32 字节 = 256 位随机。刷新令牌的强度直接决定「猜中一个会话」的难度。 */
	private static final int REFRESH_TOKEN_BYTES = 32;

	/** 与 V1 的 user_agent / client_ip 列长一致。 */
	private static final int USER_AGENT_MAX_LENGTH = 255;

	private static final int CLIENT_IP_MAX_LENGTH = 45;

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;

	private final AuthRefreshTokenRepository refreshTokenRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	private final LoginRateLimiter rateLimiter;

	private final boolean registrationEnabled;

	private final Duration refreshTokenTtl;

	/**
	 * 用户名不存在时也要花一次 bcrypt 的时间，否则「响应快」就等于「这个用户名不存在」。
	 * 启动时算一次，比在代码里写死一个哈希串更不容易出错。
	 */
	private final String dummyPasswordHash;

	public AuthService(
			UserRepository userRepository,
			AuthRefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			LoginRateLimiter rateLimiter,
			@Value("${app.auth.registration-enabled:true}") boolean registrationEnabled,
			@Value("${app.auth.refresh-token-ttl:P30D}") Duration refreshTokenTtl) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.rateLimiter = rateLimiter;
		this.registrationEnabled = registrationEnabled;
		this.refreshTokenTtl = refreshTokenTtl;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/** 注册成功不直接发令牌：前端接着走一次登录，令牌签发就只在一个地方。 */
	@Transactional
	public AuthUserResponse register(RegisterRequest request) {
		if (!registrationEnabled) {
			// 接口保留（前端靠它判断能不能注册），但不再接受新账号
			throw new ForbiddenException(ErrorCode.FORBIDDEN, "注册功能已关闭");
		}

		String username = request.username().trim();
		if (userRepository.existsByUsername(username)) {
			throw new ConflictException(ErrorCode.USERNAME_TAKEN, "用户名已被占用");
		}
		// 空邮箱存 null：H2 的唯一索引允许多行 NULL，不填邮箱的账号因此不会互相冲突
		String email = normalizeNullable(request.email());
		if (email != null && userRepository.existsByEmail(email)) {
			throw new ConflictException(ErrorCode.EMAIL_TAKEN, "邮箱已被占用");
		}
		// @Size 数的是字符，bcrypt 数的是字节：30 个中文字符就超过 72 字节，
		// 而 Spring 的 BCryptPasswordEncoder 遇到超长密码会直接抛 IllegalArgumentException（500）
		if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "密码不能超过 " + MAX_PASSWORD_BYTES + " 字节");
		}

		Instant now = Instant.now();
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setDisplayName(request.displayName().trim());
		user.setTimezone(DEFAULT_TIMEZONE);
		user.setStatus(UserStatus.ACTIVE);
		// created_at / updated_at 在 V1 里是 NOT NULL 且没有默认值，数据库不会帮忙填
		user.setCreatedAt(now);
		user.setUpdatedAt(now);

		return AuthUserResponse.from(userRepository.save(user));
	}

	@Transactional
	public AuthSessionResponse login(LoginRequest request, String clientIp, String userAgent) {
		String username = request.username().trim();
		// 限流放在最前面：被锁的组合连一次 bcrypt 都不该跑，否则锁账号这件事本身就成了 CPU 放大器
		rateLimiter.checkAllowed(username, clientIp);

		Optional<User> found = userRepository.findByUsernameAndDeletedAtIsNull(username);
		if (found.isEmpty()) {
			// 账号不存在也走一次密码比对，让两条路径的耗时接近（见 dummyPasswordHash）
			passwordEncoder.matches(request.password(), dummyPasswordHash);
			rateLimiter.recordFailure(username, clientIp);
			throw invalidCredentials();
		}

		User user = found.get();
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			rateLimiter.recordFailure(username, clientIp);
			throw invalidCredentials();
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			// 密码是对的，所以不记失败次数：这不是猜密码，锁账号解决不了任何问题
			throw new ForbiddenException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系管理员");
		}
		rateLimiter.recordSuccess(username, clientIp);

		Instant now = Instant.now();
		user.setLastLoginAt(now);
		user.setUpdatedAt(now);
		userRepository.save(user);

		return issueSession(user, clientIp, userAgent, now);
	}

	/**
	 * 用刷新令牌换一组新令牌（轮换）。
	 *
	 * <p>已吊销、已过期、查不到一律 401：其中「已吊销」是重点——正常的轮换总是先吊销旧的，
	 * 所以一个已被吊销的令牌再次出现，要么是重放攻击，要么是客户端保存了旧值，两者都不该放行。
	 */
	@Transactional
	public AuthSessionResponse refresh(RefreshRequest request, String clientIp, String userAgent) {
		Instant now = Instant.now();
		AuthRefreshToken stored = refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
				.filter(token -> token.isUsableAt(now))
				.orElseThrow(AuthService::invalidRefreshToken);

		User user = userRepository.findByIdAndDeletedAtIsNull(stored.getUserId())
				.filter(found -> found.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(AuthService::invalidRefreshToken);

		stored.revoke(now);
		stored.setLastUsedAt(now);
		refreshTokenRepository.save(stored);

		return issueSession(user, clientIp, userAgent, now);
	}

	/**
	 * 登出。按 hash 找到记录置 revoked_at；找不到也当成功（幂等）。
	 * 前端因此不需要为「重复登出」「旧令牌已被轮换掉」写分支。
	 */
	@Transactional
	public void logout(RefreshRequest request) {
		Instant now = Instant.now();
		refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
				.ifPresent(token -> {
					token.revoke(now);
					refreshTokenRepository.save(token);
				});
	}

	/**
	 * 当前账号。这里回表而不是直接用 CurrentUser：出参里的 email 不在 principal 上，
	 * 而且账号被注销后（软删）这个接口应当立刻失效。
	 */
	@Transactional(readOnly = true)
	public AuthUserResponse me(Long userId) {
		return userRepository.findByIdAndDeletedAtIsNull(userId)
				.map(AuthUserResponse::from)
				.orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED, "账号不存在或已注销"));
	}

	private AuthSessionResponse issueSession(User user, String clientIp, String userAgent, Instant now) {
		String refreshToken = newRefreshToken();

		AuthRefreshToken token = new AuthRefreshToken();
		token.setUserId(user.getId());
		token.setTokenHash(hash(refreshToken));
		token.setExpiresAt(now.plus(refreshTokenTtl));
		token.setUserAgent(truncate(userAgent, USER_AGENT_MAX_LENGTH));
		token.setClientIp(truncate(clientIp, CLIENT_IP_MAX_LENGTH));
		token.setCreatedAt(now);
		refreshTokenRepository.save(token);

		return new AuthSessionResponse(
				jwtService.issue(user.getId(), user.getUsername()),
				refreshToken,
				jwtService.accessTokenTtl().toSeconds(),
				AuthUserResponse.from(user));
	}

	/** 不用 UUID：UUID v4 只有 122 位随机且格式可预测，这里要的是「猜不中」。 */
	private static String newRefreshToken() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** 库里只存 SHA-256：库文件泄露时，拿到的摘要不能直接当令牌换新令牌。 */
	private static String hash(String refreshToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			// SHA-256 是 JDK 必备算法，缺失说明运行环境坏了，没有降级方案
			throw new IllegalStateException("无法计算 SHA-256", ex);
		}
	}

	private static String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private static UnauthorizedException invalidCredentials() {
		return new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码不正确");
	}

	private static UnauthorizedException invalidRefreshToken() {
		return new UnauthorizedException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期，请重新登录");
	}
}
