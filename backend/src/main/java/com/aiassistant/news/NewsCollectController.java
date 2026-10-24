package com.aiassistant.news;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.news.dto.CollectResponse;

/**
 * 手动「立即采集」（AC 2.7）。受理即返回：抓取在 newsExecutor 上异步执行，
 * 结果看来源健康状态。带 sourceId 时只采集该来源（须是本人的启用来源）。
 */
@RestController
@RequestMapping("/api/v1/news")
public class NewsCollectController {

	private final NewsCollectionService collectionService;
	private final CurrentUserProvider currentUserProvider;

	public NewsCollectController(NewsCollectionService collectionService, CurrentUserProvider currentUserProvider) {
		this.collectionService = collectionService;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping("/collect")
	public CollectResponse collect(@RequestParam(required = false) Long sourceId) {
		return collectionService.triggerManualCollect(currentUserProvider.require().id(), sourceId);
	}
}
