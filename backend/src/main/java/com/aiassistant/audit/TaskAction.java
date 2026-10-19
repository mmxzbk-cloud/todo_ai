package com.aiassistant.audit;

/**
 * 任务变更审计的动作取值，与 task_activity_logs.action 的 CHECK 取值一一对应。
 *
 * 计时相关的三个动作（TIMER_STARTED / TIMER_STOPPED / TIME_LOGGED）由计时模块写入。
 * 它们和任务自身的动作落在同一张表上，所以合并成一个枚举：
 * 拆开后「这张表到底会出现哪些取值」就要跨两个文件才能答上来。
 */
public enum TaskAction {
	CREATED,
	UPDATED,
	STATUS_CHANGED,
	DELETED,
	TIMER_STARTED,
	TIMER_STOPPED,
	TIME_LOGGED
}
