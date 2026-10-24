import { Navigate, Route, Routes } from 'react-router-dom'

import { AppLayout } from './components/AppLayout.tsx'
import { RequireAuth } from './components/RequireAuth.tsx'
import BoardPage from './pages/BoardPage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import NewsPage from './pages/NewsPage.tsx'
import SettingsPage from './pages/SettingsPage.tsx'
import StatsPage from './pages/StatsPage.tsx'
import TaskDetailPage from './pages/TaskDetailPage.tsx'
import TasksPage from './pages/TasksPage.tsx'
import TodayPage from './pages/TodayPage.tsx'

/**
 * 只放路由表：页面级的状态（筛选条件、查询、表单草稿）都在各自的页面里，
 * 这里多写一行状态，路由文件就会变成第二个「藏状态的地方」。
 *
 * 页面一律按默认导出引入：新增的 pages/ 都这么写，路由文件就不必记住
 * 每个页面导出的是哪个名字。
 *
 * 保护方式是把 RequireAuth 包在 AppLayout 外面而不是给每个子路由各加一层，
 * 这样以后加页面只需在下面再加一行 <Route>，忘了保护的可能性更低。
 */
function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/today" element={<TodayPage />} />
        <Route path="/tasks" element={<TasksPage />} />
        <Route path="/board" element={<BoardPage />} />
        <Route path="/tasks/:id" element={<TaskDetailPage />} />
        <Route path="/news" element={<NewsPage />} />
        <Route path="/stats" element={<StatsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>

      {/* 根路径与未匹配路径都落到今日视图；未登录时再由 RequireAuth 转到登录页。 */}
      <Route path="/" element={<Navigate to="/today" replace />} />
      <Route path="*" element={<Navigate to="/today" replace />} />
    </Routes>
  )
}

export default App
