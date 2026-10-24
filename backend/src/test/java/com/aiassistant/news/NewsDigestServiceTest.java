package com.aiassistant.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.aiassistant.auth.User;
import com.aiassistant.auth.UserRepository;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ForbiddenException;
import com.aiassistant.settings.UserSettingService;

import tools.jackson.databind.ObjectMapper;

/**
 * 每日简报（PRD US-05）：开关门禁、当日无数据时空态不调 AI、三段式简报拼装（AC 5.1/5.2/5.3）。
 */
@ExtendWith(MockitoExtension.class)
class NewsDigestServiceTest {

	private static final Long OWNER = 1L;

	@Mock
	private NewsItemRepository itemRepository;
	@Mock
	private UserSettingService userSettingService;
	@Mock
	private AiClient aiClient;
	@Mock
	private UserRepository userRepository;

	private NewsDigestService service;

	@BeforeEach
	void setUp() {
		service = new NewsDigestService(itemRepository, userSettingService, aiClient,
				userRepository, new ObjectMapper());
	}

	private User user() {
		User user = org.mockito.Mockito.mock(User.class);
		when(user.getTimezone()).thenReturn("Asia/Shanghai");
		return user;
	}

	private NewsItem item(String title, NewsCategory category) {
		// AI 字段（aiSummary）与 createdAt 由实体方法/持久化维护，单测里用不到：
		// 简报上下文没有摘要时回退标题与分类，足够覆盖拼装逻辑
		NewsItem item = new NewsItem();
		item.setOwnerId(OWNER);
		item.setSourceName("教育部网站");
		item.setTitle(title);
		item.setUrl("https://example.com/a/1");
		item.setNormalizedUrl("https://example.com/a/1");
		item.setCategory(category);
		return item;
	}

	@Test
	void digestIsForbiddenWhenAiDisabled() {
		when(userSettingService.isAiEnabled(OWNER)).thenReturn(false);

		assertThatThrownBy(() -> service.generate(OWNER))
				.isInstanceOfSatisfying(ForbiddenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_DISABLED));
		verify(aiClient, never()).complete(anyString(), anyString());
	}

	@Test
	void emptyDayReturnsEmptyStateWithoutCallingAi() {
		when(userSettingService.isAiEnabled(OWNER)).thenReturn(true);
		// 实体 mock 不能在 thenReturn 参数里现场 stub（嵌套 stubbing 会报 UnfinishedStubbing）
		User user = user();
		when(userRepository.findByIdAndDeletedAtIsNull(OWNER)).thenReturn(Optional.of(user));
		when(itemRepository.findCreatedBetween(eq(OWNER), any(), any())).thenReturn(List.of());

		var result = service.generate(OWNER);

		assertThat(result.itemCount()).isZero();
		assertThat(result.report()).contains("还没有新的资讯");
		verify(aiClient, never()).complete(anyString(), anyString());
	}

	@Test
	void digestComposesThreeSections() {
		when(userSettingService.isAiEnabled(OWNER)).thenReturn(true);
		User user = user();
		when(userRepository.findByIdAndDeletedAtIsNull(OWNER)).thenReturn(Optional.of(user));
		when(itemRepository.findCreatedBetween(eq(OWNER), any(), any()))
				.thenReturn(List.of(item("教育部发布 AI 教学指南", NewsCategory.POLICY)));
		when(aiClient.complete(anyString(), anyString())).thenReturn("""
				{"policy":"教育部发布 AI 教学指南，明确考核数字化方向。","hotspot":"今日无","attention":"多省或将跟进调整课程考核标准。"}
				""");

		var result = service.generate(OWNER);

		assertThat(result.itemCount()).isEqualTo(1);
		assertThat(result.report())
				.contains("【政策动向】").contains("教育部发布 AI 教学指南")
				.contains("【热点速览】").contains("今日无")
				.contains("【值得关注】").contains("课程考核标准");
	}
}
