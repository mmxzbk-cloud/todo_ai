import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { authApi } from '../api/auth.ts'
import { AUTH_LOGOUT_EVENT, getAccessToken, getRefreshToken, setTokens } from '../api/client.ts'
import type { AuthUser, RegisterInput } from '../types/auth.ts'

interface AuthContextValue {
  user: AuthUser | null
  loading: boolean
  login(username: string, password: string): Promise<void>
  register(input: RegisterInput): Promise<void>
  logout(): Promise<void>
}

/**
 * 未登录时是 null。useAuth 拿到的永远非 null，靠抛错而不是回退一个空对象来暴露
 * 「忘了包 AuthProvider」——回退空对象会让这种错误表现成「登录后 user 一直是 null」，
 * 排查成本高得多。
 */
const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  // 初值为 true：挂载时还不知道本地令牌是否有效，先按「未知」处理，
  // 否则未登录用户会先看到受保护页面再被踢回登录页。
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    async function loadCurrentUser() {
      try {
        // 没有令牌就不打这一枪，直接让 finally 结束 loading。
        if (!getAccessToken()) {
          return
        }
        const current = await authApi.me()
        if (!cancelled) {
          setUser(current)
        }
      } catch {
        // 取不到当前用户就当未登录。令牌确实不可用时留着只会让后续每个请求都白跑一次，
        // 所以顺手清掉。这里不派发 AUTH_LOGOUT_EVENT：本来就没有已登录状态需要撤销。
        if (!cancelled) {
          setTokens(null)
          setUser(null)
        }
      } finally {
        // 无论成功、失败还是没令牌，都要结束 loading，不能把页面卡在占位上。
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadCurrentUser()

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    // client 层刷新令牌失败时派发这个事件。令牌已经由 client 清掉，
    // 这里只负责把内存里的 user 清空，跳转交给 RequireAuth。
    function handleLogoutEvent() {
      setUser(null)
      setLoading(false)
    }

    window.addEventListener(AUTH_LOGOUT_EVENT, handleLogoutEvent)
    return () => {
      window.removeEventListener(AUTH_LOGOUT_EVENT, handleLogoutEvent)
    }
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const session = await authApi.login({ username, password })
    setTokens({ accessToken: session.accessToken, refreshToken: session.refreshToken })
    setUser(session.user)
  }, [])

  const register = useCallback(async (input: RegisterInput) => {
    await authApi.register(input)
    // 注册接口只回账号信息、不发令牌，所以在这里补一次登录。
    // 不补的话注册成功仍然是未登录，用户刚填完一遍表单就被送回登录页。
    const session = await authApi.login({ username: input.username, password: input.password })
    setTokens({ accessToken: session.accessToken, refreshToken: session.refreshToken })
    setUser(session.user)
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken()
    try {
      // 顺序要紧：logout 端点需要认证，必须趁 access token 还在时先通知后端吊销刷新令牌。
      // 先清本地令牌的话，服务端那条刷新令牌会一直有效到 30 天后。
      if (refreshToken) {
        await authApi.logout(refreshToken)
      }
    } catch {
      // 后端没响应也要让用户退得出去：卡在「点了登出没反应」比多留一条待吊销的令牌更糟。
    } finally {
      setTokens(null)
      setUser(null)
    }
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, register, logout }),
    [user, loading, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth 必须在 AuthProvider 内使用')
  }
  return context
}
