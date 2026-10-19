package com.aiassistant.task;

/**
 * 重要性：艾森豪威尔矩阵的纵轴。
 *
 * weight() 是优先级推导用的坐标，HIGH 对应 0（越重要越接近 P0）。
 * 数据库里存的是枚举名（HIGH/MEDIUM/LOW），数值只活在推导过程中，不入库，
 * 这样以后想换一套权重不需要改数据。
 */
public enum Importance {
	HIGH(0),
	MEDIUM(1),
	LOW(2);

	private final int weight;

	Importance(int weight) {
		this.weight = weight;
	}

	public int weight() {
		return weight;
	}
}
