import { startDesktop } from './desktop'
import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import './styles/theme.css'

function preventFileDropNavigation(event: DragEvent) {
  const transfer = event.dataTransfer
  if (!transfer || !Array.from(transfer.types).includes('Files')) return
  if ([...transfer.items].some(item => item.kind === 'file') || transfer.files.length) event.preventDefault()
}

// Tauri 与浏览器都不应把拖入页面的本地文件当成导航目标；详情页会继续处理同一事件完成上传。
document.addEventListener('dragover', preventFileDropNavigation)
document.addEventListener('drop', preventFileDropNavigation)

void startDesktop(() => createApp(App).use(router).mount('#app'))
