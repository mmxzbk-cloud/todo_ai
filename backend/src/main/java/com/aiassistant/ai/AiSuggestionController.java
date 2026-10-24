package com.aiassistant.ai;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.ai.dto.PrioritySuggestionResponse;
import com.aiassistant.ai.dto.ReplaceabilityAssessmentResponse;
import com.aiassistant.ai.dto.SolutionSuggestionResponse;
import com.aiassistant.auth.CurrentUser;
import com.aiassistant.auth.CurrentUserProvider;

/**
 * AI 建议接口。挂在 /tasks/{taskId}/ai 下，与任务模块共用 owner 语义：
 * taskId 只能是本人的任务，他人的任务按 404 处理（见 AiSuggestionService 的防护链）。
 *
 * <p>三个接口都是「请求 → 建议」，不写任何数据：POST 表达的是一次调用而非资源创建。
 * 耗时统计不受开关控制，不在这里；开关门禁在 Service 的防护链里，Controller 不重复。
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/ai")
public class AiSuggestionController {

	private final AiSuggestionService service;
	private final CurrentUserProvider currentUserProvider;

	public AiSuggestionController(AiSuggestionService service, CurrentUserProvider currentUserProvider) {
		this.service = service;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping("/priority-suggestion")
	public PrioritySuggestionResponse priority(@PathVariable Long taskId) {
		return service.suggestPriority(ownerId(), taskId);
	}

	@PostMapping("/solution-suggestion")
	public SolutionSuggestionResponse solution(@PathVariable Long taskId) {
		return service.suggestSolution(ownerId(), taskId);
	}

	@PostMapping("/replaceability-assessment")
	public ReplaceabilityAssessmentResponse replaceability(@PathVariable Long taskId) {
		return service.assessReplaceability(ownerId(), taskId);
	}

	private Long ownerId() {
		CurrentUser user = currentUserProvider.require();
		return user.id();
	}
}
