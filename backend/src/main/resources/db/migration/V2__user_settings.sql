-- =============================================================================
-- V2：用户级偏好设置
-- AI 能力总开关（PRD US-01）是第一个用户偏好。偏好与账号分开建表而不是在 users
-- 上加列：后续偏好项会增多，单独一张单行配置表不影响账号表的结构稳定。
--
-- 约定沿用 V1：主键直接用 user_id（一对一，不再另发 id）；
-- 枚举语义的字段用 BOOLEAN 加 NOT NULL，开关没有三态。
-- =============================================================================

CREATE TABLE user_settings (
    user_id    BIGINT      NOT NULL PRIMARY KEY,
    -- 默认关闭：首访用户看不到任何 AI 入口，主动开启才生效（PRD A.4 第 1 条推荐项）
    ai_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 偏好随账号消亡，不需要 RESTRICT
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE user_settings IS '用户级偏好设置（一个用户一行）';
COMMENT ON COLUMN user_settings.ai_enabled IS 'AI 能力总开关：关闭时全部 AI 入口隐藏、AI 接口返回 AI_DISABLED';
