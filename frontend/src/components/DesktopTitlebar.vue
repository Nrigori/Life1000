<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { navigation } from '../router'
import { currentDesktopWindow } from '../desktop'

defineProps<{ home: boolean }>()

const appWindow = currentDesktopWindow()
const maximized = ref(false)
let resizeTimer: ReturnType<typeof setTimeout> | undefined

async function readWindowState() {
  if (!appWindow) return
  try {
    maximized.value = await appWindow.isMaximized()
    document.documentElement.classList.toggle('desktop-maximized', maximized.value)
  } catch {
    // 窗口状态只影响图标表现；原生操作失败时不干扰业务页面。
  }
}

function scheduleWindowStateRead() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => void readWindowState(), 60)
}

async function minimize() {
  await appWindow?.minimize()
}

async function toggleMaximize() {
  await appWindow?.toggleMaximize()
  await readWindowState()
}

async function close() {
  await appWindow?.close()
}

function drag(event: MouseEvent) {
  if (!appWindow || event.button !== 0 || event.buttons !== 1) return
  event.preventDefault()
  if (event.detail === 2) void toggleMaximize()
  else if (event.detail === 1) void appWindow.startDragging()
}

onMounted(() => {
  void readWindowState()
  window.addEventListener('resize', scheduleWindowStateRead)
})

onBeforeUnmount(() => {
  clearTimeout(resizeTimer)
  window.removeEventListener('resize', scheduleWindowStateRead)
  document.documentElement.classList.remove('desktop-maximized')
})
</script>

<template>
  <header class="desktop-titlebar" :class="{ 'desktop-titlebar-home': home }">
    <RouterLink class="desktop-wordmark" to="/" aria-label="Life1000 首页">LIFE / 1000</RouterLink>
    <nav class="desktop-navigation" aria-label="主导航">
      <RouterLink v-for="item in navigation" :key="item.path" :to="item.path">{{ item.title }}</RouterLink>
    </nav>
    <div class="desktop-drag-region" data-testid="window-drag-region" aria-hidden="true" @mousedown="drag" />
    <div class="desktop-window-controls" aria-label="窗口控制">
      <button type="button" aria-label="最小化" title="最小化" @click="minimize">
        <svg aria-hidden="true" viewBox="0 0 12 12"><path d="M2 8.5h8" /></svg>
      </button>
      <button type="button" :aria-label="maximized ? '还原' : '最大化'" :title="maximized ? '还原' : '最大化'" @click="toggleMaximize">
        <svg v-if="!maximized" aria-hidden="true" viewBox="0 0 12 12"><rect x="2.5" y="2.5" width="7" height="7" /></svg>
        <svg v-else aria-hidden="true" viewBox="0 0 12 12"><path d="M4 3h5v5M3 4h5v5H3z" /></svg>
      </button>
      <button class="desktop-close" type="button" aria-label="关闭" title="关闭" @click="close">
        <svg aria-hidden="true" viewBox="0 0 12 12"><path d="m3 3 6 6M9 3 3 9" /></svg>
      </button>
    </div>
  </header>
</template>

<style scoped>
.desktop-titlebar {
  position: fixed;
  z-index: 50;
  inset: 0 0 auto;
  height: var(--desktop-titlebar-height);
  display: flex;
  align-items: stretch;
  color: #4b3d31;
  background: rgba(248, 242, 231, .86);
  border-bottom: 1px solid rgba(128, 99, 68, .17);
  box-shadow: 0 2px 10px rgba(62, 52, 43, .055);
  backdrop-filter: blur(14px) saturate(110%);
  -webkit-backdrop-filter: blur(14px) saturate(110%);
  user-select: none;
  -webkit-user-select: none;
}
.desktop-titlebar-home {
  color: #45382e;
  background: linear-gradient(90deg, rgba(248, 242, 231, .67), rgba(244, 235, 221, .56));
  border-bottom-color: rgba(255, 249, 239, .28);
  box-shadow: 0 2px 12px rgba(34, 27, 21, .08);
  backdrop-filter: blur(16px) saturate(112%);
  -webkit-backdrop-filter: blur(16px) saturate(112%);
}
.desktop-wordmark {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  padding: 0 22px;
  font: 16px Georgia, "Songti SC", "SimSun", serif;
  letter-spacing: .09em;
  white-space: nowrap;
}
.desktop-navigation {
  display: flex;
  flex: 0 0 auto;
  align-items: stretch;
  gap: 22px;
  color: inherit;
  font-size: 13px;
  white-space: nowrap;
}
.desktop-navigation a {
  position: relative;
  display: flex;
  align-items: center;
  padding: 1px 0 0;
  border: 0;
  opacity: .72;
}
.desktop-navigation a::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 7px;
  left: 0;
  height: 1px;
  background: currentColor;
  opacity: 0;
  transform: scaleX(.45);
  transition: opacity 120ms ease, transform 120ms ease;
}
.desktop-navigation a:hover,
.desktop-navigation a.router-link-active { opacity: 1; color: #715638; }
.desktop-navigation a:hover::after,
.desktop-navigation a.router-link-active::after { opacity: .72; transform: scaleX(1); }
.desktop-drag-region { flex: 1 1 auto; min-width: 28px; cursor: default; }
.desktop-window-controls { display: flex; flex: 0 0 auto; margin-left: 8px; }
.desktop-window-controls button {
  width: 46px;
  height: var(--desktop-titlebar-height);
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 0;
  color: inherit;
  background: transparent;
}
.desktop-window-controls button:hover { background: rgba(111, 86, 58, .1); }
.desktop-window-controls .desktop-close:hover { color: #fff8ef; background: #9a5949; }
.desktop-window-controls svg { width: 12px; height: 12px; fill: none; stroke: currentColor; stroke-width: 1.1; }
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .desktop-titlebar { background: rgba(248, 242, 231, .97); }
  .desktop-titlebar-home { background: rgba(248, 242, 231, .84); }
}
@media (max-width: 1050px) {
  .desktop-wordmark { padding: 0 16px; font-size: 15px; }
  .desktop-navigation { gap: 15px; font-size: 12px; }
  .desktop-window-controls { margin-left: 4px; }
  .desktop-window-controls button { width: 42px; }
}
@media (prefers-reduced-motion: reduce) {
  .desktop-navigation a::after { transition: none; }
}
</style>
