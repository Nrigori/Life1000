import { reactive } from 'vue'

// Web 继续使用同源 /api；只有 Tauri 注入的原生桥存在时才连接回环后端。
export interface DesktopWindow {
  minimize(): Promise<void>
  toggleMaximize(): Promise<void>
  close(): Promise<void>
  startDragging(): Promise<void>
  isMaximized(): Promise<boolean>
}

declare global {
  interface Window {
    __TAURI__?: {
      core: { invoke<T>(command: string): Promise<T> }
      window?: { getCurrentWindow(): DesktopWindow }
    }
  }
}
export const isDesktop = () => Boolean(window.__TAURI__?.core)
export const apiBase = () => isDesktop() ? 'http://127.0.0.1:8080/api' : '/api'

export const desktopStartup = reactive({
  ready: !isDesktop(),
  starting: false,
  error: '',
})

export function currentDesktopWindow(): DesktopWindow | undefined {
  return window.__TAURI__?.window?.getCurrentWindow()
}

export async function retryDesktopStart() {
  if (!isDesktop() || desktopStartup.starting || desktopStartup.ready) return
  desktopStartup.starting = true
  desktopStartup.error = ''
  try {
    await window.__TAURI__!.core.invoke('start_backend')
    desktopStartup.ready = true
  } catch (error) {
    // 原生端只返回经过整理的提示，配置内容和子进程输出不会跨越到 WebView。
    desktopStartup.error = typeof error === 'string' ? error : '启动失败，请检查桌面运行配置。'
  } finally {
    desktopStartup.starting = false
  }
}

export function startDesktop(mount: () => void) {
  // 先挂载窗口外壳，确保无边框窗口在后端等待或报错时仍可拖动和关闭。
  mount()
  if (isDesktop()) void retryDesktopStart()
}
