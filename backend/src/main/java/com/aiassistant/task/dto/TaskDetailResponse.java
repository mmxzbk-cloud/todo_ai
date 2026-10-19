package com.aiassistant.task.dto;

import java.util.List;

import com.aiassistant.audit.dto.ActivityResponse;
import com.aiassistant.tag.dto.TagResponse;
import com.aiassistant.timelog.dto.TimeLogResponse;

/**
 * 任务详情：一次请求把详情页要的东西全给前端。
 *
 * 这是唯一一个跨包组装点（task → tag、audit、timelog）。耗时明细与变更历史各取最近 50 条：
 * 详情页是给人看的，长任务的全部流水没有展示价值，却会把响应体撑到几百 KB。
 */
public record TaskDetailResponse(
		TaskResponse task,
		List<TagResponse> tags,
		List<TaskResponse> children,
		List<TimeLogResponse> timeLogs,
		List<ActivityResponse> activities) {
}
