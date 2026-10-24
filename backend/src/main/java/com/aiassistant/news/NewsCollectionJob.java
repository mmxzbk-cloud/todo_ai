package com.aiassistant.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日资讯采集任务（PRD US-02）。默认 06:30（app.news.collect-cron 可调），
 * 与 03:30 的耗时对账错峰。真正的抓取提交到 newsExecutor 执行，
 * 调度线程只负责触发——单线程调度器不能被几分钟的采集占住。
 */
@Component
public class NewsCollectionJob {

	private static final Logger log = LoggerFactory.getLogger(NewsCollectionJob.class);

	private final NewsCollectionService collectionService;

	public NewsCollectionJob(NewsCollectionService collectionService) {
		this.collectionService = collectionService;
	}

	@Scheduled(cron = "${app.news.collect-cron:0 30 6 * * *}")
	public void collectDaily() {
		log.info("触发每日资讯采集");
		collectionService.collectAllEnabledAsync();
	}
}
