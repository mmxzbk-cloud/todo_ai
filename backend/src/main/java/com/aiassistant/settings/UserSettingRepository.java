package com.aiassistant.settings;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户偏好仓储。主键即 user_id，按主键取行天然隔离了用户，
 * 不存在「跨用户读写」的查询路径。
 */
public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {
}
