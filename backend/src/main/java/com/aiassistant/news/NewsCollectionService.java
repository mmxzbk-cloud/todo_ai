package com.aiassistant.news;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.GatewayTimeoutException;
import com.aiassistant.common.RateLimitedException;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.news.dto.CollectResponse;
import com.aiassistant.news.dto.SourceRequest;
import com.aiassistant.news.dto.TrialFetchResponse;
import com.aiassistant.news.fetch.Deadline;
import com.aiassistant.news.fetch.FetchedItem;
import com.aiassistant.news.fetch.NewsHttpException;
import com.aiassistant.news.fetch.RobotsChecker;
import com.aiassistant.news.fetch.SourceFetcher;

/**
 * 采集管道（PRD US-02）：抓取 → 去重 → 入库 → 健康状态，全程后台异步。
 *
 * <p>边界约定：
 * <ul>
 * <li>逐来源独立：一个来源的任何失败都不影响其他来源（AC 2.5）；</li>
 * <li>健康状态与条目入库同事务：要么「新增 N 条 + 成功」一起落，要么整体回滚后记失败，
 * 不出现「条目进了库、健康状态还说失败」的中间态；</li>
 * <li>AI 处理不在本类：条目以 PENDING 状态入库，AI 侧按状态接管（见 NewsAiService）。</li>
 * </ul>
 */
@Service
public class NewsCollectionService {

	private static final Logger log = LoggerFactory.getLogger(NewsCollectionService.class);

	private final NewsSourceRepository sourceRepository;
	private final NewsItemRepository itemRepository;
	private final Map<NewsSourceType, SourceFetcher> fetchers;
	private final RobotsChecker robotsChecker;
	private final NewsProperties properties;
	private final ThreadPoolTaskExecutor executor;
	private final TransactionTemplate transactionTemplate;
	private final NewsSourceService sourceService;

	/** 同一来源同一时刻只跑一次采集：手动触发与定时任务撞车时，后到的一方直接跳过。 */
	private final Map<Long, Boolean> runningSources = new ConcurrentHashMap<>();

	/** 手动采集的每用户节流（PRD 3.1 / A.4 第 11 条的默认间隔）。 */
	private final Map<Long, Instant> lastManualTriggerAt = new ConcurrentHashMap<>();

	public NewsCollectionService(
			NewsSourceRepository sourceRepository,
			NewsItemRepository itemRepository,
			List<SourceFetcher> fetchers,
			RobotsChecker robotsChecker,
			NewsProperties properties,
			@Qualifier("newsExecutor") ThreadPoolTaskExecutor executor,
			PlatformTransactionManager transactionManager,
			NewsSourceService sourceService) {
		this.sourceRepository = sourceRepository;
		this.itemRepository = itemRepository;
		this.fetchers = fetchers.stream()
				.collect(Collectors.toUnmodifiableMap(SourceFetcher::type, Function.identity()));
		this.robotsChecker = robotsChecker;
		this.properties = properties;
		this.executor = executor;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.sourceService = sourceService;
	}

	// ---------------------------------------------------------------- 试抓（AC 1.3）

	/**
	 * 保存前试抓：立即抓一次，返回条数与标题预览，不写库。
	 * robots 禁止与超时是异常路径（400 / 504），其余失败走 success=false。
	 */
	public TrialFetchResponse trialFetch(SourceRequest request) {
		String url = sourceService.validateUrl(request.url());
		sourceService.assertRobotsAllowed(url);
		NewsSource probe = new NewsSource();
		probe.setOwnerId(-1L);
		probe.setName("trial");
		probe.setType(request.type());
		probe.setUrl(url);
		probe.setParseRule(sourceService.serializeParseRule(request));

		List<FetchedItem> items;
		try {
			items = fetcherFor(request.type()).fetch(probe, Deadline.within(properties.getSourceTimeout()));
		} catch (NewsHttpException ex) {
			if (ex.isTimeout()) {
				throw new GatewayTimeoutException(ErrorCode.FETCH_TIMEOUT, "试抓超时，请检查来源地址");
			}
			return TrialFetchResponse.fail("抓取失败：" + ex.getMessage());
		}
		if (items.isEmpty()) {
			return TrialFetchResponse.fail("没有解析出任何条目，请检查解析规则或源地址");
		}
		List<String> titles = items.stream().map(FetchedItem::title).limit(10).toList();
		return TrialFetchResponse.ok(items.size(), titles);
	}

	// ---------------------------------------------------------------- 手动触发（AC 2.7）

	/** 手动「立即采集」：全部启用来源，或指定一个来源。节流间隔内重复触发返回 429。 */
	public CollectResponse triggerManualCollect(Long ownerId, Long sourceId) {
		Instant now = Instant.now();
		Instant last = lastManualTriggerAt.get(ownerId);
		if (last != null && last.plus(properties.getManualCollectInterval()).isAfter(now)) {
			throw new RateLimitedException(ErrorCode.FETCH_RATE_LIMITED, "操作太频繁，请稍后再试");
		}
		List<NewsSource> sources;
		if (sourceId == null) {
			sources = sourceRepository.findByOwnerIdAndEnabledTrue(ownerId).stream()
					.filter(source -> !source.isAutoDisabled())
					.toList();
			if (sources.isEmpty()) {
				throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "没有可采集的启用来源");
			}
		} else {
			NewsSource source = sourceRepository.findByIdAndOwnerId(sourceId, ownerId)
					.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "来源不存在：id=" + sourceId));
			if (!source.isEnabled() || source.isAutoDisabled()) {
				throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "来源未启用或已因连续失败停抓，请先重新启用");
			}
			sources = List.of(source);
		}
		lastManualTriggerAt.put(ownerId, now);
		runAsync(sources);
		return new CollectResponse(sources.size());
	}

	// ---------------------------------------------------------------- 每日定时入口

	/** 定时任务入口：所有用户启用中、未被连续失败停抓的来源。 */
	public void collectAllEnabledAsync() {
		List<NewsSource> sources = sourceRepository.findByEnabledTrueAndAutoDisabledFalse();
		log.info("每日资讯采集启动，共 {} 个来源", sources.size());
		runAsync(sources);
	}

	private void runAsync(List<NewsSource> sources) {
		executor.execute(() -> {
			for (NewsSource source : sources) {
				try {
					collectSource(source);
				} catch (Exception ex) {
					// 单来源的未预期异常在这里兜住，保证后面的来源继续跑
					log.error("采集来源 {}（{}）出现未预期异常", source.getId(), source.getName(), ex);
				}
			}
		});
	}

	// ---------------------------------------------------------------- 单来源采集

	public void collectSource(NewsSource source) {
		if (runningSources.putIfAbsent(source.getId(), Boolean.TRUE) != null) {
			log.info("来源 {}（{}）正在采集中，跳过本次触发", source.getId(), source.getName());
			return;
		}
		try {
			doCollect(source);
		} finally {
			runningSources.remove(source.getId());
		}
	}

	private void doCollect(NewsSource source) {
		if (!robotsChecker.isAllowed(source.getUrl())) {
			recordFailure(source, "robots 禁止抓取（robots.txt 可能已变更）");
			return;
		}
		List<FetchedItem> items;
		try {
			items = fetcherFor(source.getType()).fetch(source, Deadline.within(properties.getSourceTimeout()));
		} catch (NewsHttpException ex) {
			recordFailure(source, (ex.isTimeout() ? "抓取超时" : "抓取失败：") + ex.getMessage());
			return;
		}
		if (items.isEmpty()) {
			recordFailure(source, "没有解析出任何条目（检查解析规则或源地址）");
			return;
		}
		try {
			transactionTemplate.executeWithoutResult(status -> insertAndRecord(source, items));
		} catch (RuntimeException ex) {
			log.error("来源 {} 条目入库失败", source.getId(), ex);
			recordFailure(source, "入库失败：" + ex.getMessage());
		}
	}

	/** 入库与健康状态同一事务（PRD 3.3：逐条事务，单条失败只跳过该条）。 */
	private void insertAndRecord(NewsSource source, List<FetchedItem> items) {
		int inserted = 0;
		int duplicates = 0;
		boolean truncated = false;
		for (FetchedItem item : items) {
			if (inserted >= properties.getMaxItemsPerSource()) {
				truncated = true;
				break;
			}
			String normalized = UrlNormalizer.normalize(item.url());
			if (normalized.isBlank()
					|| itemRepository.existsByOwnerIdAndNormalizedUrl(source.getOwnerId(), normalized)) {
				duplicates++;
				continue;
			}
			NewsItem entity = new NewsItem();
			entity.setOwnerId(source.getOwnerId());
			entity.setSourceId(source.getId());
			entity.setSourceName(source.getName());
			entity.setTitle(truncate(item.title(), 500));
			entity.setUrl(truncate(item.url(), 1000));
			entity.setNormalizedUrl(truncate(normalized, 1000));
			entity.setContentText(item.contentText() == null
					? null : truncate(item.contentText(), properties.getContentMaxLength()));
			entity.setPublishedAt(item.publishedAt());
			entity.setCategory(source.getDefaultCategory());
			entity.setAiStatus(NewsAiStatus.PENDING);
			itemRepository.save(entity);
			inserted++;
		}
		String message = "新增 " + inserted + " 条，重复跳过 " + duplicates + " 条"
				+ (truncated ? "，超出单次上限截断" : "");
		source.recordFetch(true, message, properties.getFailureThreshold());
		sourceRepository.save(source);
		log.info("来源 {}（{}）采集完成：{}", source.getId(), source.getName(), message);
	}

	private void recordFailure(NewsSource source, String message) {
		log.warn("来源 {}（{}）采集失败：{}", source.getId(), source.getName(), message);
		try {
			transactionTemplate.executeWithoutResult(status -> {
				source.recordFetch(false, message, properties.getFailureThreshold());
				sourceRepository.save(source);
				if (source.isAutoDisabled()) {
					log.warn("来源 {} 连续失败达阈值（{}），已停止自动抓取",
							source.getId(), properties.getFailureThreshold());
				}
			});
		} catch (RuntimeException ex) {
			log.error("来源 {} 健康状态记录失败", source.getId(), ex);
		}
	}

	private SourceFetcher fetcherFor(NewsSourceType type) {
		SourceFetcher fetcher = fetchers.get(type);
		if (fetcher == null) {
			throw new IllegalStateException("没有 " + type + " 类型的抓取器");
		}
		return fetcher;
	}

	private static String truncate(String value, int limit) {
		return value.length() <= limit ? value : value.substring(0, limit);
	}
}
