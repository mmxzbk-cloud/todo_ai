package com.aiassistant.ai;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ForbiddenException;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.common.ServiceUnavailableException;
import com.aiassistant.project.Project;
import com.aiassistant.project.ProjectRepository;
import com.aiassistant.settings.UserSettingService;
import com.aiassistant.tag.Tag;
import com.aiassistant.tag.TagRepository;
import com.aiassistant.task.TaskTagRepository;
import com.aiassistant.task.Importance;
import com.aiassistant.task.Priority;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;
import com.aiassistant.task.Urgency;
import com.aiassistant.ai.dto.PrioritySuggestionResponse;
import com.aiassistant.ai.dto.ReplaceabilityAssessmentResponse;
import com.aiassistant.ai.dto.SolutionSuggestionResponse;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 建议的业务入口（US-02 优先级 / US-03 方案 / US-04 可替代性）。
 *
 * <p>所有方法共用同一条防护链，顺序固定：
 * <ol>
 * <li>任务必须存在且属于本人（与他人任务同表现为 404，不泄露存在性）；</li>
 * <li>用户的 AI 总开关必须打开，否则 AI_DISABLED（403）——开关是断路器，
 * 关闭时后端绝不发起 AI 调用；</li>
 * <li>每用户限流，防止连击与成本失控。</li>
 * </ol>
 * 建议本身不落任何库：返回给前端展示，采纳与否、怎么写入由用户通过既有保存通道决定。
 */
@Service
public class AiSuggestionService {

	private final UserSettingService userSettingService;
	private final AiClient aiClient;
	private final AiRateLimiter rateLimiter;
	private final TaskRepository taskRepository;
	private final TaskTagRepository taskTagRepository;
	private final TagRepository tagRepository;
	private final ProjectRepository projectRepository;
	private final ObjectMapper objectMapper;

	public AiSuggestionService(
			UserSettingService userSettingService,
			AiClient aiClient,
			AiRateLimiter rateLimiter,
			TaskRepository taskRepository,
			TaskTagRepository taskTagRepository,
			TagRepository tagRepository,
			ProjectRepository projectRepository,
			ObjectMapper objectMapper) {
		this.userSettingService = userSettingService;
		this.aiClient = aiClient;
		this.rateLimiter = rateLimiter;
		this.taskRepository = taskRepository;
		this.taskTagRepository = taskTagRepository;
		this.tagRepository = tagRepository;
		this.projectRepository = projectRepository;
		this.objectMapper = objectMapper;
	}

	/** 优先级建议：返回 P0–P3 之一与一句话理由。 */
	public PrioritySuggestionResponse suggestPriority(Long ownerId, Long taskId) {
		Task task = requireTask(ownerId, taskId);
		gate(ownerId);
		rateLimiter.check(ownerId);

		Map<String, Object> parsed = callForJson(PRIORITY_SYSTEM_PROMPT, taskContext(ownerId, task));
		Priority priority = parseEnum(parsed.get("priority"), Priority.class);
		String reason = requireText(parsed.get("reason"));
		return new PrioritySuggestionResponse(priority, reason);
	}

	/** 方案建议：返回解决思路与建议的下一步行动（两者都空按失败处理）。 */
	public SolutionSuggestionResponse suggestSolution(Long ownerId, Long taskId) {
		Task task = requireTask(ownerId, taskId);
		gate(ownerId);
		rateLimiter.check(ownerId);

		Map<String, Object> parsed = callForJson(SOLUTION_SYSTEM_PROMPT, taskContext(ownerId, task));
		String approach = optionalText(parsed.get("approach"));
		String nextStep = optionalText(parsed.get("nextStep"));
		if (approach == null && nextStep == null) {
			throw emptyResult();
		}
		return new SolutionSuggestionResponse(approach, nextStep);
	}

	/** 可替代性评估：返回三态结论与说明。 */
	public ReplaceabilityAssessmentResponse assessReplaceability(Long ownerId, Long taskId) {
		Task task = requireTask(ownerId, taskId);
		gate(ownerId);
		rateLimiter.check(ownerId);

		Map<String, Object> parsed = callForJson(REPLACEABILITY_SYSTEM_PROMPT, taskContext(ownerId, task));
		ReplaceabilityVerdict verdict = parseEnum(parsed.get("verdict"), ReplaceabilityVerdict.class);
		String explanation = requireText(parsed.get("explanation"));
		return new ReplaceabilityAssessmentResponse(verdict, explanation);
	}

	// ---------------------------------------------------------------- 防护链

	private Task requireTask(Long ownerId, Long taskId) {
		return taskRepository.findByIdAndOwnerIdAndDeletedAtIsNull(taskId, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TASK_NOT_FOUND, "任务不存在：id=" + taskId));
	}

	private void gate(Long ownerId) {
		if (!userSettingService.isAiEnabled(ownerId)) {
			throw new ForbiddenException(ErrorCode.AI_DISABLED, "AI 能力未开启");
		}
	}

	// ---------------------------------------------------------------- 提示词

	private static final String PRIORITY_SYSTEM_PROMPT = """
			你是个人任务管理系统里的优先级助手。根据给定的任务信息判断优先级：
			P0 最紧急重要，P1 次之，P2 一般，P3 最不紧急。
			综合考虑任务内容、重要性与紧急性、截止日远近、已有投入。尊重用户已填的重要性/紧急性，
			但如果任务内容明显与之矛盾，可以给出不同的优先级。
			只输出一个 JSON 对象，不要输出任何其他文字：
			{"priority":"P0|P1|P2|P3","reason":"不超过 60 字的一句话理由，用中文"}""";

	private static final String SOLUTION_SYSTEM_PROMPT = """
			你是个人任务管理系统的规划助手。用户会给你一个任务，请给出解决思路与建议的下一步行动。
			下一步行动必须具体到可以直接开工，不要复述任务标题。
			只输出一个 JSON 对象，不要输出任何其他文字：
			{"approach":"解决思路，不超过 200 字，用中文","nextStep":"建议的下一步行动，一句话，不超过 80 字，用中文"}""";

	private static final String REPLACEABILITY_SYSTEM_PROMPT = """
			你是个人任务管理系统的评估助手。判断用户给的任务是否适合交给 AI（大语言模型）完成：
			YES 表示可以整体交给 AI 完成；PARTIAL 表示 AI 能完成其中一部分、仍需要人参与；
			NO 表示基本无法交给 AI（例如需要线下操作、人际沟通、物理执行或涉密环境）。
			只输出一个 JSON 对象，不要输出任何其他文字：
			{"verdict":"YES|PARTIAL|NO","explanation":"不超过 100 字的说明，用中文"}""";

	/**
	 * 任务上下文。发给 AI 的内容仅限任务本身的信息（标题/描述/备注/字段值/项目与标签名），
	 * 不包含任何账号信息——这是 PRD 3.1 的发送边界。
	 */
	private String taskContext(Long ownerId, Task task) {
		StringBuilder context = new StringBuilder("任务信息：\n");
		context.append("标题：").append(task.getTitle()).append('\n');
		if (notBlank(task.getDescription())) {
			context.append("描述：").append(task.getDescription()).append('\n');
		}
		if (notBlank(task.getNotes())) {
			context.append("备注：").append(task.getNotes()).append('\n');
		}
		if (notBlank(task.getSource())) {
			context.append("来源：").append(task.getSource()).append('\n');
		}
		context.append("重要性：").append(label(task.getImportance()))
				.append("，紧急性：").append(label(task.getUrgency()))
				.append("，当前优先级：").append(task.getPriority().name()).append('\n');
		if (task.getDueOn() != null) {
			long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), task.getDueOn());
			context.append("截止日：").append(task.getDueOn())
					.append(daysLeft >= 0 ? "（还有 " + daysLeft + " 天）" : "（已逾期 " + Math.abs(daysLeft) + " 天）")
					.append('\n');
		}
		if (task.getEstimatedMinutes() != null) {
			context.append("预计耗时：").append(task.getEstimatedMinutes()).append(" 分钟\n");
		}
		context.append("实际耗时：").append(task.getActualMinutes()).append(" 分钟\n");

		String projectName = task.getProjectId() == null ? null
				: projectRepository.findByIdAndOwnerId(task.getProjectId(), ownerId).map(Project::getName).orElse(null);
		if (projectName != null) {
			context.append("所属项目：").append(projectName).append('\n');
		}

		List<Long> tagIds = taskTagRepository.findTagIdsByTaskId(task.getId());
		if (!tagIds.isEmpty()) {
			String tagNames = tagRepository.findByOwnerIdAndIdInAndDeletedAtIsNullOrderByNameAscIdAsc(ownerId, tagIds)
					.stream()
					.map(Tag::getName)
					.reduce((a, b) -> a + "、" + b)
					.orElse(null);
			if (tagNames != null) {
				context.append("标签：").append(tagNames).append('\n');
			}
		}
		return context.toString();
	}

	private static String label(Importance importance) {
		return switch (importance) {
			case HIGH -> "高";
			case MEDIUM -> "中";
			case LOW -> "低";
		};
	}

	private static String label(Urgency urgency) {
		return switch (urgency) {
			case HIGH -> "高";
			case MEDIUM -> "中";
			case LOW -> "低";
		};
	}

	// ---------------------------------------------------------------- 结果解析

	/** 发起调用并解析出 JSON 对象；解析不出结构、缺必要字段一律按 AI_EMPTY_RESULT 处理。 */
	private Map<String, Object> callForJson(String systemPrompt, String userPrompt) {
		String content = aiClient.complete(systemPrompt, userPrompt);
		Map<String, Object> parsed;
		try {
			parsed = objectMapper.readValue(extractJsonObject(content), new TypeReference<Map<String, Object>>() {
			});
		} catch (ServiceUnavailableException ex) {
			throw ex;
		} catch (Exception ex) {
			throw emptyResult();
		}
		if (parsed == null || parsed.isEmpty()) {
			throw emptyResult();
		}
		return parsed;
	}

	/** 模型输出常带代码围栏或前后缀：截取首尾大括号之间的部分，交由上层解析。 */
	private static String extractJsonObject(String content) {
		int start = content.indexOf('{');
		int end = content.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw emptyResult();
		}
		return content.substring(start, end + 1);
	}

	private static <E extends Enum<E>> E parseEnum(Object value, Class<E> type) {
		if (!(value instanceof String text)) {
			throw emptyResult();
		}
		try {
			return Enum.valueOf(type, text.trim());
		} catch (IllegalArgumentException ex) {
			throw emptyResult();
		}
	}

	/** 必填文本：缺失、空串、纯空白、或类型不对都算「没给出有效建议」。 */
	private static String requireText(Object value) {
		String text = optionalText(value);
		if (text == null) {
			throw emptyResult();
		}
		return text;
	}

	private static String optionalText(Object value) {
		if (!(value instanceof String text)) {
			return null;
		}
		String trimmed = text.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static ServiceUnavailableException emptyResult() {
		return new ServiceUnavailableException(ErrorCode.AI_EMPTY_RESULT, "AI 没有给出有效建议，请重试");
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}
