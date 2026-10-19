import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'

import { useAuth } from '../hooks/useAuth.tsx'

/**
 * 受保护路由的入口。登录页之外的页面都用它包住。
 *
 * state 里带上原始 location，登录成功后能跳回用户本来想看的那一页，
 * 而不是一律回今日视图。
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    // 只渲染占位，不渲染登录页：刷新页面时令牌可能仍然有效，
    // 先闪一下登录页再跳回去比多等几十毫秒难受得多。
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 text-sm text-slate-500">
        加载中…
      </div>
    )
  }

  if (user === null) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}
