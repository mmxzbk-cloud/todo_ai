import { request } from './client.ts'
import type { AuthUser, LoginInput, LoginResponse, RegisterInput } from '../types/auth.ts'

/**
 * 认证相关的 5 个端点。都走 client 的 request，因此自动带 Authorization 与 X-Request-Id，
 * 只有 /refresh 例外地由 client 内部直接调用（它需要避开 401 自动刷新，否则会递归）。
 */
export const authApi = {
  /** 注册只回账号信息、不发令牌，注册后想直接进入系统需要再登录一次。 */
  register: (input: RegisterInput) =>
    request<AuthUser>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  login: (input: LoginInput) =>
    request<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(input),
    }),

  refresh: (refreshToken: string) =>
    request<LoginResponse>('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    }),

  /** 204，无响应体。需要认证，所以调用方要趁 access token 还在时先调它。 */
  logout: (refreshToken: string) =>
    request<void>('/api/v1/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    }),

  me: () => request<AuthUser>('/api/v1/auth/me'),
}
