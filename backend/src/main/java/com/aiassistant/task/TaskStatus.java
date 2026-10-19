package com.aiassistant.task;

import java.util.List;

/**
 * 任务状态。取值必须与 V1 里 ck_tasks_status 的 CHECK 完全一致：
 * 枚举存字符串，库里加取值需要新迁移脚本，这里不能悄悄多一个。
 *
 * 在脚手架的三态（TODO/DOING/DONE）上补了 BLOCKED（等外部条件）与 CANCELLED（决定不做），
 * 它们是不同的结论：前者还会回到 DOING，后者不该再进「今日」。
 */
public enum TaskStatus {
	TODO,
	DOING,
	BLOCKED,
	DONE,
	CANCELLED;

	/** 看板固定四列。CANCELLED 不上看板，取消的任务只在列表与详情里查得到。 */
	public static final List<TaskStatus> BOARD_ORDER = List.of(TODO, DOING, BLOCKED, DONE);

	/** 「还没结束」的状态集合，今日视图用它过滤。与「不在 (DONE, CANCELLED)」等价，但能走 IN 索引。 */
	public static final List<TaskStatus> OPEN_STATUSES = List.of(TODO, DOING, BLOCKED);
}
