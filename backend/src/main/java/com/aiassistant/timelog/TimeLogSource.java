package com.aiassistant.timelog;

/**
 * 耗时流水的来源。取值必须与 V1 里 task_time_logs.source 的 CHECK 约束一致，
 * 新增取值需要新的迁移脚本，不允许静默写入脏值。
 */
public enum TimeLogSource {

	/** 用计时器跑出来的记录：开始与结束各一次请求，分钟数由服务端算。 */
	TIMER,

	/** 事后手工补录：回忆昨天干了多久时用，起止时间由用户填。 */
	MANUAL
}
