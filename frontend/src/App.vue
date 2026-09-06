<script setup lang="ts">
import { navigation } from './router'
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="wordmark" to="/" aria-label="Life1000 首页">LIFE / 1000</RouterLink>
      <nav aria-label="主导航">
        <RouterLink v-for="item in navigation" :key="item.path" :to="item.path">
          {{ item.title }}
        </RouterLink>
      </nav>
    </header>
    <main id="main-content"><RouterView /></main>
    <footer>记录这一生真正想做的事情。</footer>
  </div>
</template>
