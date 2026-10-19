import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'

import App from './App.tsx'
import { AuthProvider } from './hooks/useAuth.tsx'
import './index.css'

// retry 默认 1：后端没起时只重试一次就报错，避免离线状态下每个查询都白等三轮退避。
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      {/* AuthProvider 放在 Router 外层：它只管令牌与当前账号，不依赖路由钩子，
          放在外层可以保证路由切换时登录状态不被重建。 */}
      <AuthProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
