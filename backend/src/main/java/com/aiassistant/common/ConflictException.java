package com.aiassistant.common;

/**
 * 与现有数据冲突：重名、乐观锁版本过期、计时已在跑。
 * 这类错误的共同点是「换一个时间点或换一个值再试就可能成功」，因此统一 409。
 */
public class ConflictException extends RuntimeException {

	private final ErrorCode code;

	public ConflictException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
