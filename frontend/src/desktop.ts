// Web 继续使用同源 /api；只有 Tauri 注入的原生桥存在时才连接回环后端。
declare global {
  interface Window {
    __TAURI__?: { core: { invoke<T>(command: string): Promise<T> } }
  }
}
export const isDesktop = () => Boolean(window.__TAURI__?.core)
export const apiBase = () => isDesktop() ? 'http://127.0.0.1:8080/api' : '/api'

export async function startDesktop(mount: () => void) {
  if (!isDesktop()) { mount(); return }
  const root = document.getElementById('app')!
  const panel = document.createElement('main')
  panel.className = 'desktop-startup'
  const title = document.createElement('h1')
  title.textContent = 'Life1000'
  const message = document.createElement('p')
  message.setAttribute('role', 'status')
  const retry = document.createElement('button')
  retry.textContent = '重试'
  panel.append(title, message, retry)
  root.replaceChildren(panel)
  async function start() {
    retry.hidden = true
    message.textContent = '正在打开你的记录册…'
    try {
      await window.__TAURI__!.core.invoke('start_backend')
      mount()
    } catch (error) {
      // 原生端只返回经过整理的提示，配置内容和子进程输出不会跨越到 WebView。
      message.textContent = typeof error === 'string' ? error : '启动失败，请检查桌面运行配置。'
      retry.hidden = false
    }
  }
  retry.addEventListener('click', () => void start())
  await start()
}
