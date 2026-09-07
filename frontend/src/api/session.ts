// Token 随当前标签页会话保存，不长期写入 localStorage；这里不判断有效期，真实性仍由后端校验。
const tokenKey = 'life1000.accessToken'

export function getToken(): string | null {
  return sessionStorage.getItem(tokenKey)
}

export function saveToken(token: string) {
  sessionStorage.setItem(tokenKey, token)
}

export function clearToken() {
  sessionStorage.removeItem(tokenKey)
}
