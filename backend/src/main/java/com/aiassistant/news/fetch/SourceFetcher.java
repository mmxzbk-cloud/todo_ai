package com.aiassistant.news.fetch;

import java.util.List;

import com.aiassistant.news.NewsSourceType;

/**
 * 来源抓取器：一种 NewsSourceType 一个实现。
 *
 * <p>职责边界：抓取与解析在这里，去重、落库、健康状态在 NewsCollectionService；
 * 网络异常统一抛 {@link NewsHttpException}，空结果用返回空列表表达（由调用方按
 * 「解析异常」记健康状态）。
 */
public interface SourceFetcher {

	NewsSourceType type();

	/**
	 * 抓取一个来源的候选条目，按列表页/源内出现的顺序返回。
	 * deadline 到点后尽快收手，已抓到的部分照常返回。
	 */
	List<FetchedItem> fetch(com.aiassistant.news.NewsSource source, Deadline deadline);
}
