package com.aiassistant.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

/**
 * 自签 HS256 JWT。不引第三方 JWT 库：这里只用到「签一段 JSON、验一段 JSON」两件事，
 * 引入一个库换来的是它的历史漏洞面、版本升级负担，以及下面这些必须自己写死的判断
 * 被库的默认行为悄悄改掉的风险。
 *
 * <p>三条安全约定，改动前请先读完：
 * <ol>
 * <li><b>验签恒定用 HMAC-SHA256，从不读 token 头部里的 alg。</b>
 *     读头部的算法等于让攻击者选算法（{@code alg: none}、把 RS256 降级成 HS256 都是这么来的）。</li>
 * <li><b>签名比对用 {@link MessageDigest#isEqual}。</b>普通数组相等会提前返回，
 *     比对耗时会泄露「前几个字节猜对了」，这就是可用的签名伪造信号。</li>
 * <li><b>解析失败一律返回空，不抛异常。</b>调用方只有「能用」和「不能用」两种结局，
 *     不需要为「过期」「签名错」「格式错」写三条分支（也别让它们出现在响应里）。</li>
 * </ol>
 */
@Service
public class JwtService {

	private static final Logger log = LoggerFactory.getLogger(JwtService.class);

	/** 头部是固定常量：不读它，也就没必要让它可变。 */
	private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	/** JWT 规范要求 base64url 且不带 padding。 */
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

	/** HS256 的密钥短于 32 字节就可以被离线爆破，而爆破成功后能自签任意用户的令牌。 */
	private static final int MIN_SECRET_BYTES = 32;

	private static final int JWT_PART_COUNT = 3;

	private final ObjectMapper objectMapper;

	private final byte[] secret;

	private final Duration accessTokenTtl;

	public JwtService(
			ObjectMapper objectMapper,
			@Value("${app.auth.jwt-secret}") String jwtSecret,
			@Value("${app.auth.access-token-ttl:PT2H}") Duration accessTokenTtl) {
		this.objectMapper = objectMapper;
		this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
		if (this.secret.length < MIN_SECRET_BYTES) {
			// 弱密钥能签出「完全合法」的令牌，等发现时已经无法区分哪些令牌是伪造的；
			// 启动就失败是唯一能保证它被换掉的地方
			throw new IllegalStateException(
					"app.auth.jwt-secret 至少需要 " + MIN_SECRET_BYTES + " 字节，当前 " + this.secret.length + " 字节");
		}
		this.accessTokenTtl = accessTokenTtl;
	}

	/** access token 的有效期，登录响应里的 expiresIn 由它算出来，避免两处各存一份。 */
	public Duration accessTokenTtl() {
		return accessTokenTtl;
	}

	/** 签发。载荷只放 sub / username / iat / exp，时刻用 epoch 秒（JWT 的规定，不是自选）。 */
	public String issue(Long userId, String username) {
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("sub", userId);
		claims.put("username", username);
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", now.plus(accessTokenTtl).getEpochSecond());

		String signingInput = encode(HEADER.getBytes(StandardCharsets.UTF_8)) + '.' + encodedPayload(claims);
		return signingInput + '.' + encode(sign(signingInput));
	}

	/**
	 * 校验并取出载荷。签名错、过期、格式错、载荷不合规，对外都是同一个结果：empty。
	 *
	 * <p>验签在任何载荷解析之前完成：未通过验签的内容不进入 JSON 解析器，
	 * 于是「构造一个畸形载荷让解析器崩掉」这条路也走不通。
	 */
	public Optional<JwtPayload> parse(String token) {
		try {
			String[] parts = token == null ? new String[0] : token.split("\\.");
			if (parts.length != JWT_PART_COUNT) {
				return Optional.empty();
			}

			byte[] expected = sign(parts[0] + '.' + parts[1]);
			byte[] actual = BASE64_URL_DECODER.decode(parts[2]);
			if (!MessageDigest.isEqual(expected, actual)) {
				return Optional.empty();
			}

			Map<?, ?> claims = objectMapper.readValue(BASE64_URL_DECODER.decode(parts[1]), Map.class);
			return toPayload(claims);
		}
		catch (RuntimeException ex) {
			// 走到这里的可能是签名 base64 解不开（IllegalArgumentException）、载荷不是 JSON
			// （JacksonException）、exp 超出 Instant 范围（DateTimeException）。
			// 连异常消息都只进 debug：一个畸形的令牌不值得在两边的日志里各留一条噪音
			log.debug("令牌解析失败：{}", ex.getMessage());
			return Optional.empty();
		}
	}

	private Optional<JwtPayload> toPayload(Map<?, ?> claims) {
		Object subject = claims.get("sub");
		Object expiration = claims.get("exp");
		if (!(subject instanceof Number userId) || !(expiration instanceof Number exp)) {
			return Optional.empty();
		}

		Instant expiresAt = Instant.ofEpochSecond(exp.longValue());
		if (!expiresAt.isAfter(Instant.now())) {
			// 过期令牌也是「不可用」，不单独说原因：前端只有刷新一条路，多一个分支没有用处
			return Optional.empty();
		}

		Instant issuedAt = claims.get("iat") instanceof Number iat ? Instant.ofEpochSecond(iat.longValue()) : null;
		String username = claims.get("username") instanceof String name ? name : null;
		return Optional.of(new JwtPayload(userId.longValue(), username, issuedAt, expiresAt));
	}

	private String encodedPayload(Map<String, Object> claims) {
		return encode(objectMapper.writeValueAsBytes(claims));
	}

	private static String encode(byte[] value) {
		return BASE64_URL_ENCODER.encodeToString(value);
	}

	private byte[] sign(String signingInput) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			// HmacSHA256 是 JDK 必备算法，走到这里说明运行环境坏了；没有降级方案，
			// 也绝对不能「跳过验签继续」，所以直接失败
			throw new IllegalStateException("无法计算 HMAC-SHA256 签名", ex);
		}
	}
}
