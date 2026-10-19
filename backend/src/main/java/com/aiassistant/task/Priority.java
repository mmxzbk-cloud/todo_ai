package com.aiassistant.task;

/**
 * 优先级 P0–P3，是存储字段而不是视图层推导值：Excel 里重要性与紧急性、优先级并存，
 * 说明确实存在「矩阵算出来是 P2 但我要先做」的情况，手工覆盖必须能留下来。
 *
 * 推导规则：重要性 + 紧急性的权重（HIGH=0 / MEDIUM=1 / LOW=2）求和后截到 3，
 * 0→P0、1→P1、2→P2、3→P3。于是 3×3 的组合分布为 1 个 P0、2 个 P1、3 个 P2、3 个 P3。
 *
 * 枚举名与 ck_tasks_priority 的 CHECK 一致，且字符串序天然就是 P0 < P1 < P2 < P3，
 * 所以列表排序可以直接按列排，不需要额外映射。
 */
public enum Priority {
	P0,
	P1,
	P2,
	P3;

	public static Priority derive(Importance importance, Urgency urgency) {
		int sum = importance.weight() + urgency.weight();
		return switch (Math.min(sum, 3)) {
			case 0 -> P0;
			case 1 -> P1;
			case 2 -> P2;
			default -> P3;
		};
	}
}
