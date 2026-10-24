package com.aiassistant.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aiassistant.ai.AiClient;
import com.aiassistant.ai.AiRateLimiter;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ForbiddenException;
import com.aiassistant.common.GatewayTimeoutException;
import com.aiassistant.settings.UserSettingService;

import tools.jackson.databind.ObjectMapper;

/**
 * AI 摘要与打标（PRD US-04）：开关断路（SKIPPED / AI_DISABLED）、成功落字段、
 * 失败降级为 FAILED 而不影响条目可用性（AC 4.2 / 4.5 / 4.6）。
 */
@ExtendWith(MockitoExtension.class)
class NewsAiServiceTest {

	private static final Long OWNER = 1L;
	private static final Long ITEM_ID = 7L;

	@Mock
	private NewsItemRepository itemRepository;
	@Mock
	private UserSettingService userSettingService;
	@Mock
	private AiClient aiClient;
	@Mock
	private AiRateLimiter rateLimiter;

	private NewsAiService service;

	@BeforeEach
	void setUp() {
		service = new NewsAiService(itemRepository, userSettingService, aiClient, rateLimiter,
				new NewsProperties(), new ObjectMapper());
	}

	private NewsItem item() {
		NewsItem item = new NewsItem();
		item.setOwnerId(OWNER);
		item.setSourceName("教育部网站");
		item.setTitle("教育部发布 AI 教学指南");
		item.setUrl("https://example.com/a/1");
		item.setNormalizedUrl("https://example.com/a/1");
		item.setContentText("指南提出人工智能赋能高校教学的三条路径。");
		item.setAiStatus(NewsAiStatus.PENDING);
		return item;
	}

	@Test
	void collectionBatchIsSkippedWhenAiDisabled() {
		NewsItem existing = item();
		when(userSettingService.isAiEnabled(OWNER)).thenReturn(false);
		when(itemRepository.findByIdAndOwnerId(ITEM_ID, OWNER)).thenReturn(Optional.of(existing));

		service.processAfterCollection(OWNER, List.of(ITEM_ID));

		assertThat(existing.getAiStatus()).isEqualTo(NewsAiStatus.SKIPPED);
		verify(aiClient, never()).complete(anyString(), anyString());
	}

	@Test
	void successfulProcessingWritesSummaryCategoryAndTags() {
		NewsItem existing = item();
		when(userSettingService.isAiEnabled(OWNER)).thenReturn(true);
		when(itemRepository.findByIdAndOwnerId(ITEM_ID, OWNER)).thenReturn(Optional.of(existing));
		when(aiClient.complete(anyString(), anyString())).thenReturn("""
				```json
				{"summary":"教育部发布 AI 教学指南，明确三条赋能路径。","keyPoints":["路径一：课程智能化","路径二：考核数字化"],
				 "category":"POLICY","tags":["AI政策","课程考核"]}
				```
				""");

		service.processItem(OWNER, ITEM_ID, false);

		assertThat(existing.getAiStatus()).isEqualTo(NewsAiStatus.DONE);
		assertThat(existing.getAiSummary()).contains("AI 教学指南");
		assertThat(existing.getAiSuggestedCategory()).isEqualTo(NewsCategory.POLICY);
		assertThat(existing.getAiKeyPoints()).contains("路径一：课程智能化");
		assertThat(existing.getAiSuggestedTags()).contains("课程考核");
	}

	@Test
	void aiFailureDegradesToFailedInsteadOfThrowing() {
		NewsItem existing = item();
		when(userSettingService.isAiEnabled(OWNER)).thenReturn(true);
		when(itemRepository.findByIdAndOwnerId(ITEM_ID, OWNER)).thenReturn(Optional.of(existing));
		when(aiClient.complete(anyString(), anyString()))
				.thenThrow(new GatewayTimeoutException(ErrorCode.AI_TIMEOUT, "AI 没有及时返回"));

		service.processItem(OWNER, ITEM_ID, false);

		assertThat(existing.getAiStatus()).isEqualTo(NewsAiStatus.FAILED);
		assertThat(existing.getAiError()).isNotBlank();
	}

	@Test
	void unparseableOutputIsMarkedFailed() {
		NewsItem existing = item();
		when(userSettingService.isAiEnabled(OWNER)).thenReturn(true);
		when(itemRepository.findByIdAndOwnerId(ITEM_ID, OWNER)).thenReturn(Optional.of(existing));
		when(aiClient.complete(anyString(), anyString())).thenReturn("抱歉，我无法完成这个任务。");

		service.processItem(OWNER, ITEM_ID, false);

		assertThat(existing.getAiStatus()).isEqualTo(NewsAiStatus.FAILED);
	}

	@Test
	void regenerateRejectsWhenAiDisabled() {
		when(userSettingService.isAiEnabled(OWNER)).thenReturn(false);

		assertThatThrownBy(() -> service.regenerate(OWNER, ITEM_ID))
				.isInstanceOfSatisfying(ForbiddenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_DISABLED));
		verify(aiClient, never()).complete(anyString(), anyString());
	}
}
