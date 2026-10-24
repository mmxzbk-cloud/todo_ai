package com.aiassistant.news;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 资讯采集的可调参数。PRD A.4 第 3/4/5/11 条的建议值都落在这里：
 * 评审拍板改数值时动配置，不动代码。
 */
@ConfigurationProperties(prefix = "app.news")
public class NewsProperties {

	/** 每日定时采集（默认 06:30，与 03:30 的耗时对账错峰）。 */
	private String collectCron = "0 30 6 * * *";

	/** 单来源单次采集的时间预算，超出的条目放弃、健康状态如实记录。 */
	private Duration sourceTimeout = Duration.ofSeconds(60);

	/** 单个页面（列表页 / 正文页 / RSS 源）的请求超时。 */
	private Duration pageTimeout = Duration.ofSeconds(10);

	/** 单来源单次入库条数上限，超出丢弃并在健康状态注明。 */
	private int maxItemsPerSource = 50;

	/** 每用户来源数上限。 */
	private int maxSourcesPerUser = 20;

	/** 连续失败达到该次数后停止自动抓取并标异常。 */
	private int failureThreshold = 3;

	/** 发送给 AI 的正文截断上限（字符）。 */
	private int aiContentLimit = 5000;

	/** 落库正文的最大长度（字符），超出截断——只用于检索与 AI，不需要全文。 */
	private int contentMaxLength = 20000;

	/** 手动「立即采集」的最小触发间隔（防连击）。 */
	private Duration manualCollectInterval = Duration.ofMinutes(5);

	public String getCollectCron() {
		return collectCron;
	}

	public void setCollectCron(String collectCron) {
		this.collectCron = collectCron;
	}

	public Duration getSourceTimeout() {
		return sourceTimeout;
	}

	public void setSourceTimeout(Duration sourceTimeout) {
		this.sourceTimeout = sourceTimeout;
	}

	public Duration getPageTimeout() {
		return pageTimeout;
	}

	public void setPageTimeout(Duration pageTimeout) {
		this.pageTimeout = pageTimeout;
	}

	public int getMaxItemsPerSource() {
		return maxItemsPerSource;
	}

	public void setMaxItemsPerSource(int maxItemsPerSource) {
		this.maxItemsPerSource = maxItemsPerSource;
	}

	public int getMaxSourcesPerUser() {
		return maxSourcesPerUser;
	}

	public void setMaxSourcesPerUser(int maxSourcesPerUser) {
		this.maxSourcesPerUser = maxSourcesPerUser;
	}

	public int getFailureThreshold() {
		return failureThreshold;
	}

	public void setFailureThreshold(int failureThreshold) {
		this.failureThreshold = failureThreshold;
	}

	public int getAiContentLimit() {
		return aiContentLimit;
	}

	public void setAiContentLimit(int aiContentLimit) {
		this.aiContentLimit = aiContentLimit;
	}

	public int getContentMaxLength() {
		return contentMaxLength;
	}

	public void setContentMaxLength(int contentMaxLength) {
		this.contentMaxLength = contentMaxLength;
	}

	public Duration getManualCollectInterval() {
		return manualCollectInterval;
	}

	public void setManualCollectInterval(Duration manualCollectInterval) {
		this.manualCollectInterval = manualCollectInterval;
	}
}
