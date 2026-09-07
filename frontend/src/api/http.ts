import { clearToken, getToken } from './session'

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
  }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  const token = getToken()
  if (token && path !== '/auth/login') headers.set('Authorization', `Bearer ${token}`)
  if (options.body) headers.set('Content-Type', 'application/json')
  const response = await fetch(`/api${path}`, {
    ...options,
    headers,
    signal: options.signal
      ? AbortSignal.any([options.signal, AbortSignal.timeout(15000)])
      : AbortSignal.timeout(15000),
  })
  if (!response.ok) {
    if (response.status === 401 && path !== '/auth/login') {
      clearToken()
      window.dispatchEvent(new Event('life1000:unauthorized'))
    }
    const body = await response.json().catch(() => null) as { message?: string } | null
    throw new ApiError(response.status, body?.message || '暂时无法读取，请稍后再试。')
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '暂时无法连接，请确认服务已启动后重试。'
}
