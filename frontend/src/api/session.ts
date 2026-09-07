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
