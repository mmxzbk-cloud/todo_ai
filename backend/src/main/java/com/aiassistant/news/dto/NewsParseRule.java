package com.aiassistant.news.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 网页列表页的解析规则（PRD A.4 第 2 条方案 a：用户填 CSS 选择器）。
 *
 * <p>落库形态是 JSON 字符串（info_sources.parse_rule），接口层用本 record 收发。
 * item 必填（从列表页圈出每条资讯的根元素），title/link 必填（在 item 内取标题与链接），
 * published/content 可选（缺省时发布时间为空、正文靠整页提取兜底）。
 */
public record NewsParseRule(
		@NotBlank(message = "网页来源必须配置列表项选择器 item") String item,
		@NotBlank(message = "网页来源必须配置标题选择器 title") String title,
		@NotBlank(message = "网页来源必须配置链接选择器 link") String link,
		String published,
		String content) {
}
