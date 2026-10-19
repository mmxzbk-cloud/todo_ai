package com.aiassistant.audit.dto;

import java.time.Instant;

import com.aiassistant.audit.TaskActivityLog;

/**
 * 变更历史的读模型。
 *
 * action 用字符串而不是 TaskAction：前端只做展示，返回原值可以让后端新增动作类型时不必同步发前端版本。
 * 放在 audit 包下（而不是 task/dto）是因为它描述的是审计表本身，计时模块也要用它。
 */
public record ActivityResponse(
		Long id,
		String action,
		String field,
		String oldValue,
		String newValue,
		Long actorId,
		Instant createdAt) {

	public static ActivityResponse from(TaskActivityLog log) {
		return new ActivityResponse(
				log.getId(),
				log.getAction().name(),
				log.getField(),
				log.getOldValue(),
				log.getNewValue(),
				log.getActorId(),
				log.getCreatedAt());
	}
}
