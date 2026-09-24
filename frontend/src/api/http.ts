import { apiBase } from '../desktop'
import { clearToken, getToken } from './session'

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
  }
}

export async function fetchApi(path: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers)
  const token = getToken()
  if (token && path !== '/auth/login') headers.set('Authorization', `Bearer ${token}`)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  // Web 使用同源代理，Desktop 使用回环 API；FormData 的边界由浏览器生成。
  const response = await fetch(`${apiBase()}${path}`, {
    ...options,
    headers,
    signal: options.signal
      ? AbortSignal.any([options.signal, AbortSignal.timeout(path === '/backup/export' ? 600000 : options.body instanceof FormData ? 120000 : 15000)])
      : AbortSignal.timeout(path === '/backup/export' ? 600000 : options.body instanceof FormData ? 120000 : 15000),
  })
  if (!response.ok) {
    // 失效认证统一清理并通知路由回登录；登录接口自身的 401 只作为账号密码错误显示。
    if (response.status === 401 && path !== '/auth/login') {
      clearToken()
      window.dispatchEvent(new Event('life1000:unauthorized'))
    }
    const body = await response.json().catch(() => null) as { message?: string } | null
    const message = typeof body?.message === 'string' ? body.message : ''
    throw new ApiError(response.status, !message || /SQLException|AxiosError|NullPointerException|StackTrace/.test(message) ? '暂时无法读取，请稍后再试。' : message)
  }
  return response
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetchApi(path, options)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '暂时无法连接，请确认服务已启动后重试。'
}
