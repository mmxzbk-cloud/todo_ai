import { NavLink, Outlet, useNavigate } from 'react-router-dom'

import { useAuth } from '../hooks/useAuth.tsx'

/** 导航项。顺序即界面顺序：先看今天要做什么，再看整体状态，资讯是每日的信息输入，统计是回顾。 */
const NAV_ITEMS = [
  { to: '/today', label: '今日' },
  { to: '/tasks', label: '任务' },
  { to: '/board', label: '看板' },
  { to: '/news', label: '资讯' },
  { to: '/stats', label: '统计' },
  { to: '/settings', label: '设置' },
] as const

/**
 * 受保护区域的统一外壳：顶部导航 + 当前账号 + 登出，内容由子路由的 <Outlet /> 渲染。
 *
 * 当前项高亮交给 NavLink 的 isActive：它会一并写上 aria-current="page"，
 * 视觉与读屏用的是同一份判断，不会出现「高亮了但读屏不知道」的分歧。
 */
export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    // logout 已把 user 置空，RequireAuth 也会把路由拨回登录页；
    // 这里显式跳一次是为了不出现「外壳先空转一帧」的观感。
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <span className="text-sm font-semibold">AI Assistant</span>

          <nav aria-label="主导航" className="flex items-center gap-1">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `rounded-md px-3 py-1.5 text-sm ${
                    isActive
                      ? 'bg-slate-900 font-medium text-white'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            {/* displayName 理论上不会为空（注册时必填），退回用户名只是防御。 */}
            <span className="text-sm text-slate-500">{user?.displayName ?? user?.username}</span>
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-md px-2 py-1 text-sm text-slate-500 hover:bg-slate-100 hover:text-slate-900"
            >
              登出
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
