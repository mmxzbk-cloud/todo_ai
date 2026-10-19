package com.aiassistant.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 给每个请求一个 ID：日志格式里的 {@code %X{requestId}} 读的就是它。
 *
 * <p>它必须跑在鉴权之前（{@link Ordered#HIGHEST_PRECEDENCE}）：令牌无效、账号被禁用这类
 * 最需要排查的请求，恰恰是 Security 链直接拦掉、不会进 Controller 的那些。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	/** 与 logging.pattern 里的 %X{...} 键名一致，改名要同时改 application.yml。 */
	public static final String REQUEST_ID_MDC_KEY = "requestId";

	/**
	 * 只接受字母数字与 {@code - _ .} 组成、长度 1-64 的请求 ID。
	 * 它会被写进日志和响应头，放任换行或超长内容进来就是一条日志注入通道
	 * （伪造的换行能在日志里造出一条看起来像别的请求的记录）。
	 */
	private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = resolve(request.getHeader(REQUEST_ID_HEADER));
		MDC.put(REQUEST_ID_MDC_KEY, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			// 线程会被复用：不清理就会把上一个请求的 id 带到下一个请求的日志里
			MDC.remove(REQUEST_ID_MDC_KEY);
		}
	}

	/** 前端每请求都会带一个（用它才能把浏览器网络面板里的一条请求和后端日志对上）；没带或不可信就自己生成。 */
	private static String resolve(String incoming) {
		if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
			return incoming;
		}
		return UUID.randomUUID().toString();
	}
}
