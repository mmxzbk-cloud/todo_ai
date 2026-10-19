/** 后端统一错误体，对应 com.aiassistant.common.ApiError。 */
export interface ApiErrorBody {
  status: number
  /** 稳定分支标识（后端 ErrorCode 的名字）。message 会改，判分支只认它。 */
  code?: string
  error: string
  message: string
  fieldErrors?: Record<string, string>
}

/**
 * status 为 0 表示请求根本没发出去（后端没起或端口不通），与后端返回的 4xx/5xx 区分开。
 *
 * code 为空串表示这次后端没给出 code。调用方按 `code === 'XXX'` 判分支，
 * 空串自然落到「不认识这个错误」的默认分支，不需要额外判空。
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>
  /** 与后端日志里那条记录对得上的请求 ID，排查时把它贴过去即可定位。 */
  readonly requestId?: string

  constructor(
    status: number,
    message: string,
    options: { code?: string; fieldErrors?: Record<string, string>; requestId?: string } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = options.code ?? ''
    this.fieldErrors = options.fieldErrors ?? {}
    this.requestId = options.requestId
  }
}

/** 刷新令牌失败、本地已无法继续认证时由本模块派发，AuthProvider 监听它清掉内存里的 user。 */
export const AUTH_LOGOUT_EVENT = 'auth:logout'

const ACCESS_TOKEN_KEY = 'aiassistant.accessToken'
const REFRESH_TOKEN_KEY = 'aiassistant.refreshToken'
const REQUEST_ID_HEADER = 'X-Request-Id'
const AUTH_PATH_PREFIX = '/api/v1/auth'

/**
 * localStorage 在隐私模式或被策略禁用时会直接抛异常。令牌读写失败不该让整个页面崩掉，
 * 退化成「本次会话不持久化」即可。
 */
function readStored(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function writeStored(key: string, value: string | null): void {
  try {
    if (value === null) {
      window.localStorage.removeItem(key)
    } else {
      window.localStorage.setItem(key, value)
    }
  } catch {
    // 存不下就算了，见 readStored 的说明。
  }
}

export function getAccessToken(): string | null {
  return readStored(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return readStored(REFRESH_TOKEN_KEY)
}

export function setTokens(tokens: { accessToken: string; refreshToken: string } | null): void {
  writeStored(ACCESS_TOKEN_KEY, tokens?.accessToken ?? null)
  writeStored(REFRESH_TOKEN_KEY, tokens?.refreshToken ?? null)
}

/**
 * 刷新出参里只用得到这两个令牌，因此就地声明而不从业务模块 import——
 * client 是最底层，让它依赖 auth 类型会把依赖方向倒过来。
 */
interface TokenPair {
  accessToken: string
  refreshToken: string
}

async function toApiError(response: Response): Promise<ApiError> {
  const requestId = response.headers.get(REQUEST_ID_HEADER) ?? undefined
  const fallbackMessage = `请求失败（HTTP ${response.status}）`
  try {
    // 用 Partial：代理层或网关出错时回的 JSON 可能是另一种结构，缺字段不该让这里炸掉。
    const body = (await response.json()) as Partial<ApiErrorBody>
    return new ApiError(response.status, body.message ?? fallbackMessage, {
      code: body.code,
      fieldErrors: body.fieldErrors,
      requestId,
    })
  } catch {
    // 后端没返回 JSON（例如代理层自己报错），退回状态码。
    return new ApiError(response.status, fallbackMessage, { requestId })
  }
}

/**
 * 不做 401 刷新的底层请求。刷新请求本身也走这里，
 * 否则刷新失败会再触发一次刷新，变成递归。
 */
async function send<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  // 每个请求带一个 ID，后端会写进日志并原样回在响应头里。
  // 出问题时直接拿这个值捞日志，不用靠时间戳猜是哪一条请求。
  headers.set(REQUEST_ID_HEADER, crypto.randomUUID())

  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, '无法连接后端服务，请确认后端已启动（默认 http://localhost:8080）')
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  // 删除、登出接口返回 204，没有响应体。
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/**
 * 单飞刷新：模块级共享同一个 Promise，因此并发发出的 N 个请求同时撞上 401 时
 * 只会打一次 /auth/refresh，其余请求等这一次的结果。
 *
 * 没有它的话后端会收到 N 次刷新、轮换出 N 个新令牌，而服务端每次刷新都会把旧记录置为
 * revoked，于是先返回的那批令牌立刻作废，前端表现为「刷新成功却继续 401」。
 */
let refreshPromise: Promise<boolean> | null = null

function refreshSession(): Promise<boolean> {
  if (refreshPromise === null) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function performRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return false
  }
  try {
    const session = await send<TokenPair>(`${AUTH_PATH_PREFIX}/refresh`, {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    })
    setTokens({ accessToken: session.accessToken, refreshToken: session.refreshToken })
    return true
  } catch {
    // 刷新令牌也过期或被吊销了：本地令牌已无价值，清掉并通知外层登出。
    setTokens(null)
    window.dispatchEvent(new Event(AUTH_LOGOUT_EVENT))
    return false
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    return await send<T>(path, init)
  } catch (error) {
    const shouldRefresh = error instanceof ApiError && error.status === 401 && getRefreshToken() !== null
    if (!shouldRefresh) {
      throw error
    }
    // 刷新失败时 performRefresh 已经清令牌并发过登出事件，这里把原始 401 抛回去：
    // 调用方看到的仍是「未授权」，而不是一个含糊的失败。
    if (!(await refreshSession())) {
      throw error
    }
    // 只重放一次：这一枪再失败就直接抛，不再进刷新分支。
    return await send<T>(path, init)
  }
}
