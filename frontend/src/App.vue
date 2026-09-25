<script setup lang="ts">
import { useRoute } from 'vue-router'
import DesktopTitlebar from './components/DesktopTitlebar.vue'
import { desktopStartup, isDesktop, retryDesktopStart } from './desktop'
import { navigation } from './router'

const route = useRoute()
const desktop = isDesktop()
</script>

<template>
  <div v-if="desktop" class="desktop-app" :class="{ 'desktop-home': route.path === '/' }">
    <DesktopTitlebar :home="route.path === '/'" />
    <main v-if="!desktopStartup.ready" id="main-content" class="desktop-startup">
      <p class="eyebrow">LIFE / 1000</p>
      <h1>正在打开记录册</h1>
      <p role="status">{{ desktopStartup.error || '正在打开你的记录册…' }}</p>
      <button v-if="desktopStartup.error" type="button" :disabled="desktopStartup.starting" @click="retryDesktopStart">
        {{ desktopStartup.starting ? '正在重试…' : '重试' }}
      </button>
    </main>
    <RouterView v-else-if="route.path === '/'" />
    <div v-else class="app-shell desktop-app-shell">
      <main id="main-content" :class="{ 'goals-main': route.path === '/goals' }"><RouterView /></main>
      <footer>记录这一生真正想做的事情。</footer>
    </div>
  </div>
  <RouterView v-else-if="route.path === '/'" />
  <div v-else class="app-shell">
    <header class="site-header">
      <RouterLink class="wordmark" to="/" aria-label="Life1000 首页">LIFE / 1000</RouterLink>
      <nav aria-label="主导航">
        <RouterLink v-for="item in navigation" :key="item.path" :to="item.path">{{ item.title }}</RouterLink>
      </nav>
    </header>
    <main id="main-content" :class="{ 'goals-main': route.path === '/goals' }"><RouterView /></main>
    <footer>记录这一生真正想做的事情。</footer>
  </div>
</template>
