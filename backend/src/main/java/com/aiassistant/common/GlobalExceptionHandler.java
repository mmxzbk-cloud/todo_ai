package com.aiassistant.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tools.jackson.core.JacksonException.Reference;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * 把抛到接口层的异常收敛成统一的 ApiError 结构，避免前端要处理多种错误格式。
 *
 * <p>分工原则：异常类型自己带 ErrorCode 的（业务异常）原样透传；
 * 框架抛的异常在这里映射成固定的码；剩下的兜底成 500，堆栈只进日志、不进响应体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** 与 RequestIdFilter 写入 MDC 的键一致；日志格式里的 %X{requestId} 也读它。 */
	private static final String REQUEST_ID_MDC_KEY = "requestId";

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
		return error(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
		return error(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
		return error(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(RateLimitedException.class)
	public ResponseEntity<ApiError> handleRateLimited(RateLimitedException ex) {
		return error(HttpStatus.TOO_MANY_REQUESTS, ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
		return error(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
		return error(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "请求参数校验失败", fieldErrors);
	}

	/**
	 * 请求体不是合法 JSON，或枚举等字段取了不允许的值。
	 *
	 * 不加这段会落到 Spring 默认错误体，前端就要同时处理两种错误结构。
	 * 这里只回字段名，不回原始异常信息：Jackson 的默认文案里带包名、类名和解析偏移量，
	 * 属于不该出现在接口契约里的实现细节。
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
		String detail = "请确认请求体是合法 JSON，且字段类型与取值正确";
		if (ex.getMostSpecificCause() instanceof InvalidFormatException invalidFormat) {
			String field = invalidFormat.getPath().stream()
					.map(Reference::getPropertyName)
					.filter(Objects::nonNull)
					.reduce((first, second) -> second)
					.orElse(null);
			if (field != null) {
				detail = "字段 " + field + " 的取值不在允许范围内";
			}
		}
		return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "请求体解析失败：" + detail);
	}

	/**
	 * 类型不匹配与缺参：把「URL 或查询参数写错了」与「服务器出错」分开。
	 *
	 * <p>这三条不在计划 1.1 的映射表里，是有意补的：它们本可以由 Spring MVC 的默认解析器给出 4xx，
	 * 但本类下面的兜底 {@code Exception} 分支会先一步接住，于是客户端把 {@code /api/v1/tasks/abc}
	 * 或者 {@code ?status=拼错的枚举} 打成 500，还在日志里留一条 ERROR 级堆栈。
	 * 补这三条只是恢复 4xx 语义，不引入新的 ErrorCode。
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "参数 " + ex.getName() + " 的取值不在允许范围内");
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
		return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "缺少必需的请求参数 " + ex.getParameterName());
	}

	/**
	 * 路径不存在（URL 拼错、接口已被移除）。理由同上：默认的 404 会被兜底分支吃掉变成 500。
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
		return error(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "接口不存在");
	}

	/** @Version 冲突：两个请求同时读到同一个版本，谁后提交谁撞。前端按 VERSION_CONFLICT 提示刷新。 */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
		log.warn("乐观锁冲突（requestId={}）：{}", MDC.get(REQUEST_ID_MDC_KEY), ex.getMessage());
		return error(HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT, "数据已被其他请求修改，请刷新后重试");
	}

	/**
	 * 兜底。对外只说「服务器内部错误」：异常消息里可能带表名、SQL 片段或文件路径，
	 * 回给前端等于免费提供一份内网结构。排查靠日志里的 requestId 串起来的那条记录。
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		log.error("未预期的异常（requestId={}）", MDC.get(REQUEST_ID_MDC_KEY), ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "服务器内部错误，请稍后重试");
	}

	private static ResponseEntity<ApiError> error(HttpStatus status, ErrorCode code, String message) {
		return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message));
	}

	private static ResponseEntity<ApiError> error(HttpStatus status, ErrorCode code, String message,
			Map<String, String> fieldErrors) {
		return ResponseEntity.status(status).body(ApiError.withFieldErrors(status.value(), code, message, fieldErrors));
	}
}
