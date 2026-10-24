package com.aiassistant.news;

/**
 * 资讯分类（单选枚举，PRD A.4 第 12 条的建议集合）。
 * AI 打标从这套枚举里选（提示词用 label），展示与筛选也用它。
 */
public enum NewsCategory {

	POLICY("政策文件"),
	HOTSPOT("行业热点"),
	TECH("技术动态"),
	OTHER("其他");

	private final String label;

	NewsCategory(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** AI 返回的取值可能是中文 label 或枚举名：都能接受，认不出返回 null（按失败降级，不抛异常）。 */
	public static NewsCategory fromLooseName(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		for (NewsCategory category : values()) {
			if (category.name().equalsIgnoreCase(trimmed) || category.label.equals(trimmed)) {
				return category;
			}
		}
		return null;
	}
}
