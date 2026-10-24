package com.aiassistant.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户级偏好的读写入口。AI 能力包（US-02/03/04）在发起建议前先到这里问开关；
 * 开关状态按用户隔离，一个用户的开关不影响其他用户。
 */
@Service
public class UserSettingService {

	private final UserSettingRepository repository;

	public UserSettingService(UserSettingRepository repository) {
		this.repository = repository;
	}

	/** 没有行的用户视为默认值（关闭）：从打开到建行之间没有「未初始化」这个中间态。 */
	@Transactional(readOnly = true)
	public boolean isAiEnabled(Long userId) {
		return repository.findById(userId)
				.map(UserSetting::isAiEnabled)
				.orElse(false);
	}

	/** upsert：第一次修改时才建行。返回落库后的最终状态，接口层原样回给前端。 */
	@Transactional
	public boolean setAiEnabled(Long userId, boolean enabled) {
		UserSetting setting = repository.findById(userId).orElseGet(() -> {
			UserSetting created = new UserSetting();
			created.setUserId(userId);
			return created;
		});
		setting.setAiEnabled(enabled);
		return repository.save(setting).isAiEnabled();
	}
}
