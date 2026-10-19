import { useState } from 'react'
import type { FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { ApiError } from '../api/client.ts'
import { useAuth } from '../hooks/useAuth.tsx'
import type { RegisterInput } from '../types/auth.ts'

const MODES = ['login', 'register'] as const
type Mode = (typeof MODES)[number]

const MODE_LABELS: Record<Mode, string> = {
  login: '登录',
  register: '注册',
}

interface FormError {
  banner: string
  fieldErrors: Record<string, string>
}

/**
 * 错误码 → 界面文案。
 *
 * 按 code 分支而不是按 message 做字符串匹配：message 是给人看的、随时会改文案，
 * code 才是后端对前端承诺的稳定标识。
 */
function describeError(error: unknown): FormError {
  if (!(error instanceof ApiError)) {
    return { banner: '发生未知错误，请看浏览器控制台', fieldErrors: {} }
  }
  if (error.status === 0) {
    return {
      banner: '无法连接后端服务，请确认后端已启动（默认 http://localhost:8080）',
      fieldErrors: {},
    }
  }
  switch (error.code) {
    case 'INVALID_CREDENTIALS':
      // 刻意不区分「用户名不存在」和「密码错误」：区分开等于给攻击者一个探测账号的接口。
      return { banner: '用户名或密码不正确', fieldErrors: {} }
    case 'LOGIN_RATE_LIMITED':
      return { banner: '尝试次数过多，请 15 分钟后再试', fieldErrors: {} }
    case 'USERNAME_TAKEN':
      return { banner: '用户名已被占用', fieldErrors: {} }
    case 'EMAIL_TAKEN':
      return { banner: '邮箱已被占用', fieldErrors: {} }
    case 'VALIDATION_FAILED':
      // 字段级错误交给输入框下面显示，横幅只说明「为什么没提交成功」。
      return { banner: '请检查表单填写', fieldErrors: error.fieldErrors }
    case 'FORBIDDEN':
      // 注册开关关掉时后端回 403 FORBIDDEN。
      return { banner: '当前已关闭注册，请联系管理员', fieldErrors: {} }
    default:
      return { banner: error.message, fieldErrors: error.fieldErrors }
  }
}

interface FieldProps {
  id: string
  label: string
  value: string
  autoComplete: string
  /** 传 'password' 才会遮字符，其余都是普通文本框。 */
  type?: string
  error?: string
  onChange: (value: string) => void
}

function Field({ id, label, value, autoComplete, type = 'text', error, onChange }: FieldProps) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500"
      />
      {error && (
        <p id={`${id}-error`} className="mt-1 text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}

/**
 * 登录与注册同页，用 tab 切换：两者字段重合度高，
 * 分成两个路由会让「注册完才发现用户名被占」多绕一次跳转。
 */
function LoginPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState<FormError | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // RequireAuth 挡下来时会在 state 里带上原始地址，登录后跳回那里。
  const state = location.state as { from?: { pathname?: string } } | null
  const from = state?.from?.pathname ?? '/today'

  function switchMode(next: Mode) {
    setMode(next)
    // 切换 tab 时清掉上一种模式的报错：留着「用户名已被占用」会让登录的人一头雾水。
    setError(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      if (mode === 'login') {
        await login(username, password)
      } else {
        const input: RegisterInput = { username, password, displayName }
        // 邮箱留空就整个字段省掉、不传空串：users.email 上有唯一索引，
        // 空串会让第二个不填邮箱的账号撞唯一约束。
        const trimmedEmail = email.trim()
        if (trimmedEmail !== '') {
          input.email = trimmedEmail
        }
        await register(input)
      }
      navigate(from, { replace: true })
    } catch (cause) {
      setError(describeError(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10 text-slate-900">
      <div className="w-full max-w-sm">
        <header className="mb-6 text-center">
          <h1 className="text-xl font-semibold">AI Assistant</h1>
          <p className="mt-1 text-sm text-slate-500">登录后开始管理今天要做的事</p>
        </header>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <div role="tablist" aria-label="登录或注册" className="mb-5 flex gap-1 rounded-md bg-slate-100 p-1">
            {MODES.map((value) => (
              <button
                key={value}
                type="button"
                role="tab"
                aria-selected={mode === value}
                onClick={() => switchMode(value)}
                className={`flex-1 rounded-md px-3 py-1.5 text-sm font-medium ${
                  mode === value
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                {MODE_LABELS[value]}
              </button>
            ))}
          </div>

          {error && (
            <div
              role="alert"
              className="mb-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
            >
              {error.banner}
            </div>
          )}

          {/* 不加浏览器校验：空字段由后端拦下并回 VALIDATION_FAILED，
              前端只负责把 fieldErrors 摆到对应字段下面。 */}
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <Field
              id="login-username"
              label="用户名"
              value={username}
              autoComplete="username"
              error={error?.fieldErrors.username}
              onChange={setUsername}
            />

            {mode === 'register' && (
              <Field
                id="login-display-name"
                label="显示名称"
                value={displayName}
                autoComplete="nickname"
                error={error?.fieldErrors.displayName}
                onChange={setDisplayName}
              />
            )}

            <Field
              id="login-password"
              label="密码"
              type="password"
              value={password}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              error={error?.fieldErrors.password}
              onChange={setPassword}
            />

            {mode === 'register' && (
              <Field
                id="login-email"
                label="邮箱（可选）"
                type="email"
                value={email}
                autoComplete="email"
                error={error?.fieldErrors.email}
                onChange={setEmail}
              />
            )}

            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
            >
              {submitting ? '提交中…' : MODE_LABELS[mode]}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
