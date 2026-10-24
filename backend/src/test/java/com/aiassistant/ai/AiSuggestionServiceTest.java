package com.aiassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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

import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ForbiddenException;
import com.aiassistant.common.GatewayTimeoutException;
import com.aiassistant.common.RateLimitedException;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.common.ServiceUnavailableException;
import com.aiassistant.project.ProjectRepository;
import com.aiassistant.settings.UserSettingService;
import com.aiassistant.tag.TagRepository;
import com.aiassistant.task.Importance;
import com.aiassistant.task.Priority;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;
import com.aiassistant.task.TaskTagRepository;
import com.aiassistant.task.Urgency;
import com.aiassistant.ai.dto.PrioritySuggestionResponse;
import com.aiassistant.ai.dto.ReplaceabilityAssessmentResponse;
import com.aiassistant.ai.dto.SolutionSuggestionResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * AI 建议服务的防护链与结果校验。AI 客户端 mock 掉：这里测的是
 * 「开关门禁、owner 隔离、限流、输出解析」这些业务规则，不是 HTTP 细节。
 */
@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceTest {

	@Mock
	private UserSettingService userSettingService;
	@Mock
	private AiClient aiClient;
	@Mock
	private TaskRepository taskRepository;
	@Mock
	private TaskTagRepository taskTagRepository;
	@Mock
	private TagRepository tagRepository;
	@Mock
	private ProjectRepository projectRepository;

	private AiSuggestionService service;

	/** 实体的 id 由数据库生成、没有 setter，测试里用 mock 提供上下文读取所需的字段。 */
	private final Task task = org.mockito.Mockito.mock(Task.class);

	@BeforeEach
	void setUp() {
		AiProperties properties = new AiProperties();
		properties.setRateLimitPerMinute(5);
		AiRateLimiter rateLimiter = new AiRateLimiter(properties);

		service = new AiSuggestionService(userSettingService, aiClient, rateLimiter, taskRepository,
				taskTagRepository, tagRepository, projectRepository, new ObjectMapper());

		lenient().when(task.getId()).thenReturn(7L);
		lenient().when(task.getTitle()).thenReturn("整理季度数据");
		lenient().when(task.getImportance()).thenReturn(Importance.MEDIUM);
		lenient().when(task.getUrgency()).thenReturn(Urgency.HIGH);
		lenient().when(task.getPriority()).thenReturn(Priority.P2);
		lenient().when(task.getActualMinutes()).thenReturn(0);

		lenient().when(taskRepository.findByIdAndOwnerIdAndDeletedAtIsNull(7L, 1L))
				.thenReturn(Optional.of(task));
		lenient().when(taskTagRepository.findTagIdsByTaskId(7L)).thenReturn(List.of());
		lenient().when(userSettingService.isAiEnabled(1L)).thenReturn(true);
	}

	private void clientReturns(String content) {
		when(aiClient.complete(anyString(), anyString())).thenReturn(content);
	}

	// ---------------------------------------------------------------- 门禁与隔离

	@Test
	void rejectsWhenAiSwitchOff_withoutCallingAi() {
		when(userSettingService.isAiEnabled(1L)).thenReturn(false);

		assertThatThrownBy(() -> service.suggestPriority(1L, 7L))
				.isInstanceOfSatisfying(ForbiddenException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_DISABLED));
		verify(aiClient, never()).complete(anyString(), anyString());
	}

	@Test
	void treatsOthersTaskAsNotFound() {
		when(taskRepository.findByIdAndOwnerIdAndDeletedAtIsNull(7L, 2L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.suggestPriority(2L, 7L))
				.isInstanceOfSatisfying(ResourceNotFoundException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
		verify(aiClient, never()).complete(anyString(), anyString());
	}

	@Test
	void rateLimitsBeforeCallingAi() {
		AiProperties properties = new AiProperties();
		properties.setRateLimitPerMinute(0);
		AiSuggestionService tightService = new AiSuggestionService(userSettingService, aiClient,
				new AiRateLimiter(properties), taskRepository, taskTagRepository, tagRepository,
				projectRepository, new ObjectMapper());

		assertThatThrownBy(() -> tightService.suggestPriority(1L, 7L))
				.isInstanceOfSatisfying(RateLimitedException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_RATE_LIMITED));
		verify(aiClient, never()).complete(anyString(), anyString());
	}

	// ---------------------------------------------------------------- 优先级建议

	@Test
	void parsesPrioritySuggestion_withFencedJson() {
		clientReturns("好的，以下是建议：\n```json\n{\"priority\":\"P1\",\"reason\":\"截止日就在明天\"}\n```");

		PrioritySuggestionResponse response = service.suggestPriority(1L, 7L);

		assertThat(response.priority()).isEqualTo(Priority.P1);
		assertThat(response.reason()).isEqualTo("截止日就在明天");
	}

	@Test
	void rejectsPrioritySuggestionWithMissingReason() {
		clientReturns("{\"priority\":\"P1\",\"reason\":\"\"}");

		assertThatThrownBy(() -> service.suggestPriority(1L, 7L))
				.isInstanceOfSatisfying(ServiceUnavailableException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_EMPTY_RESULT));
	}

	@Test
	void rejectsPrioritySuggestionWithUnknownPriorityValue() {
		clientReturns("{\"priority\":\"P9\",\"reason\":\"理由\"}");

		assertThatThrownBy(() -> service.suggestPriority(1L, 7L))
				.isInstanceOf(ServiceUnavailableException.class);
	}

	// ---------------------------------------------------------------- 方案建议

	@Test
	void parsesSolutionSuggestion() {
		clientReturns("{\"approach\":\"先导出原始数据，再按季度汇总\",\"nextStep\":\"打开数据表确认列结构\"}");

		SolutionSuggestionResponse response = service.suggestSolution(1L, 7L);

		assertThat(response.approach()).isEqualTo("先导出原始数据，再按季度汇总");
		assertThat(response.nextStep()).isEqualTo("打开数据表确认列结构");
	}

	@Test
	void rejectsSolutionSuggestionWhenBothFieldsBlank() {
		clientReturns("{\"approach\":\"  \",\"nextStep\":null}");

		assertThatThrownBy(() -> service.suggestSolution(1L, 7L))
				.isInstanceOfSatisfying(ServiceUnavailableException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_EMPTY_RESULT));
	}

	// ---------------------------------------------------------------- 可替代性评估

	@Test
	void parsesReplaceabilityAssessment() {
		clientReturns("{\"verdict\":\"PARTIAL\",\"explanation\":\"数据整理可自动化，汇报沟通需人工\"}");

		ReplaceabilityAssessmentResponse response = service.assessReplaceability(1L, 7L);

		assertThat(response.verdict()).isEqualTo(ReplaceabilityVerdict.PARTIAL);
		assertThat(response.explanation()).isEqualTo("数据整理可自动化，汇报沟通需人工");
	}

	@Test
	void mapsTimeoutToGatewayTimeout() {
		when(aiClient.complete(anyString(), anyString()))
				.thenThrow(new GatewayTimeoutException(ErrorCode.AI_TIMEOUT, "AI 没有及时返回，稍后再试"));

		assertThatThrownBy(() -> service.suggestPriority(1L, 7L))
				.isInstanceOfSatisfying(GatewayTimeoutException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.AI_TIMEOUT));
	}
}
