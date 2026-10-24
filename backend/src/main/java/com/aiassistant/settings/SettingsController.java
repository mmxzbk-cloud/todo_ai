package com.aiassistant.settings;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUser;
import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.settings.dto.AiSettingsResponse;
import com.aiassistant.settings.dto.AiSettingsUpdateRequest;

import jakarta.validation.Valid;

/**
 * 用户设置接口。当前只有 AI 能力总开关；后续偏好项挂到同一个 /settings 前缀下。
 *
 * 打开开关这个动作本身不触发任何 AI 请求、不改任何任务数据（PRD US-01 核心规则 2），
 * 它只改一行配置，副作用由各页面的 AI 入口在下一次显式点击时才有。
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

	private final UserSettingService userSettingService;
	private final CurrentUserProvider currentUserProvider;

	public SettingsController(UserSettingService userSettingService, CurrentUserProvider currentUserProvider) {
		this.userSettingService = userSettingService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/ai")
	public AiSettingsResponse ai() {
		return new AiSettingsResponse(userSettingService.isAiEnabled(currentUserId()));
	}

	@PutMapping("/ai")
	public AiSettingsResponse updateAi(@Valid @RequestBody AiSettingsUpdateRequest request) {
		boolean enabled = userSettingService.setAiEnabled(currentUserId(), request.enabled());
		return new AiSettingsResponse(enabled);
	}

	private Long currentUserId() {
		CurrentUser user = currentUserProvider.require();
		return user.id();
	}
}
