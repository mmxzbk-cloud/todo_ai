package com.aiassistant.task;

/**
 * 紧急性：艾森豪威尔矩阵的横轴。与 {@link Importance} 同一套刻度。
 */
public enum Urgency {
	HIGH(0),
	MEDIUM(1),
	LOW(2);

	private final int weight;

	Urgency(int weight) {
		this.weight = weight;
	}

	public int weight() {
		return weight;
	}
}
