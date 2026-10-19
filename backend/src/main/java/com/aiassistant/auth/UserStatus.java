package com.aiassistant.auth;

/**
 * 账号状态。取值必须与 V1 的 {@code ck_users_status} 完全一致（存字符串 + CHECK）。
 *
 * <p>「注销」用 {@code users.deleted_at} 表达，不是一种状态：注销的账号不参与登录，
 * 而 DISABLED 是「账号还在、但暂时不许用」，两者在查询条件里出现的位置不同。
 */
public enum UserStatus {
	ACTIVE,
	DISABLED
}
