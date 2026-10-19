/**
 * 账号相关出参的类型，字段与后端 `auth/dto` 里的 record 一一对应。
 *
 * 后端刻意不返回 passwordHash / status / lastLoginAt，这里也不多声明——
 * 前端类型比后端宽只会让人以为有字段可用。
 */

/** 对应 AuthUserResponse。email 可空：注册时可以不填，登录只认用户名。 */
export interface AuthUser {
  id: number
  username: string
  email: string | null
  displayName: string
  /** IANA 时区名，后端用它算「今天」；本轮界面不做时区换算，先留着。 */
  timezone: string
}

/** 对应 AuthSessionResponse。refreshToken 只在签发这一次返回，前端要负责存下来。 */
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  /** access token 的有效期（秒）。本轮不解析令牌，留着给将来「提前刷新」用。 */
  expiresIn: number
  /** 登录出参里就带账号信息，省掉登录后立刻再打一次 /auth/me。 */
  user: AuthUser
}

/** 对应 LoginRequest。 */
export interface LoginInput {
  username: string
  password: string
}

/** 对应 RegisterRequest。 */
export interface RegisterInput {
  username: string
  password: string
  displayName: string
  /**
   * 可选。不填就整个字段省掉、不要传空串：`users.email` 上有唯一索引，
   * 空串而非 NULL 会让第二个不填邮箱的账号直接撞唯一约束。
   */
  email?: string
}
