<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { readStats, type Stats } from '../api/home'
import { errorMessage } from '../api/http'
const stats = ref<Stats>()
const loading = ref(true)
const error = ref('')
async function load() {
  loading.value = true; error.value = ''
  try { stats.value = await readStats() } catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}
onMounted(load)
</script>
<template>
  <section class="paper stats-page">
    <p class="eyebrow">数据统计</p><h1>LIFE / 1000</h1>
    <p v-if="loading" role="status">正在翻阅…</p>
    <div v-else-if="error"><p role="alert">{{ error }}</p><button @click="load">重新读取</button></div>
    <dl v-else-if="stats" class="quiet-stats">
      <div><dt>已写下</dt><dd>{{ stats.writtenCount }}</dd></div>
      <div><dt>已完成</dt><dd>{{ stats.completedCount }}</dd></div>
      <div><dt>进行中</dt><dd>{{ stats.inProgressCount }}</dd></div>
      <div><dt>空白</dt><dd>{{ stats.blankCount }}</dd></div>
      <div><dt>今年完成</dt><dd>{{ stats.completedThisYear }} <small>件</small></dd></div>
      <div><dt>累计上传</dt><dd class="upload-total">{{ stats.imageCount }} <small>张图片 ·</small> {{ stats.documentCount }} <small>个文档</small></dd></div>
      <div><dt>收藏金句</dt><dd>{{ stats.quoteCount }} <small>条</small></dd></div>
    </dl>
  </section>
</template>
<style scoped>
.stats-page { max-width: 860px; margin: auto; }
.quiet-stats { margin: 40px 0 0; }
.quiet-stats > div { display: flex; align-items: baseline; justify-content: space-between; padding: 19px 0; border-bottom: 1px solid #e1d6c6; gap: 20px; }
.quiet-stats > div:last-child { border: 0; }
dt { color: #88735b; font-size: 14px; }
dd { font: 28px Georgia, serif; margin: 0; color: #5c4935; text-align: right; }
dd small { font: 12px "Microsoft YaHei", sans-serif; color: #8b765f; }
.upload-total { font-size: 24px; }
</style>
