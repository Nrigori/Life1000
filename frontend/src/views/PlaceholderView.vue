<script setup lang="ts">
import { useRoute } from 'vue-router'
import ConnectionCheck from '../components/ConnectionCheck.vue'

const route = useRoute()
</script>

<template>
  <section class="paper">
    <p class="eyebrow">LIFE / 1000</p>
    <h1>{{ route.meta.title }}</h1>
    <p class="placeholder-copy">这一页，留待以后写下。</p>
    <ConnectionCheck v-if="route.path === '/'" />
  </section>
</template>
