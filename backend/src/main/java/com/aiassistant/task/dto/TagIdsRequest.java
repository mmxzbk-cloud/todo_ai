package com.aiassistant.task.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * 打标签入参：前端交上来的是该任务最终应当拥有的完整标签集合，
 * 服务端整体替换（先删后插），不做增量合并。
 */
public record TagIdsRequest(@NotNull(message = "tagIds 不能为空，清空标签请传空数组") List<Long> tagIds) {
}
