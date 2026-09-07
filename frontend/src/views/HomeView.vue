<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { navigation } from '../router'
import { calendar } from '../home/calendar'
import { randomBackground, randomQuote, readStats, type Quote, type Stats } from '../api/home'
import { fileBlob } from '../api/details'
const background = ref('')
const quote = ref<Quote>()
const stats = ref<Stats>()
const today = ref(calendar(new Date()))
const errors = ref<string[]>([])
const loading = ref(true)
let controller: AbortController | undefined
let revision = 0
let midnight: ReturnType<typeof setTimeout>
function updateDate() {
  const now = new Date()
  today.value = calendar(now)
  clearTimeout(midnight)
  midnight = setTimeout(updateDate, new Date(now.getFullYear(), now.getMonth(), now.getDate()+1).getTime()-now.getTime()+100)
}
function revoke() {
  if (background.value) URL.revokeObjectURL(background.value)
  background.value = ''
}
async function load() {
  const current = ++revision
  controller?.abort(); controller = new AbortController()
  const signal = controller.signal
  errors.value = []; loading.value = true
  const results = await Promise.allSettled([
    (async () => {
      const value = await randomBackground(signal)
      let url = ''
      if (value) {
        const blob = await fileBlob(value.id, false, signal)
        url = URL.createObjectURL(blob)
        try { const image = new Image(); image.src = url; await image.decode() }
        catch (error) { URL.revokeObjectURL(url); throw error }
      }
      if (current !== revision) { if (url) URL.revokeObjectURL(url); return }
      revoke(); background.value = url
    })(),
    randomQuote(signal).then(value => { if (current === revision) quote.value = value }),
    readStats(signal).then(value => { if (current === revision) stats.value = value }),
  ])
  if (current !== revision) return
  results.forEach((result, index) => { if (result.status === 'rejected') errors.value.push(['照片暂时无法读取，先以纸张为封面。','金句暂时无法读取。','数字暂时无法读取。'][index]!) })
  loading.value = false
}
onMounted(() => { updateDate(); void load() })
onBeforeUnmount(() => { ++revision; controller?.abort(); clearTimeout(midnight); revoke() })
</script>
<template>
  <div class="home-cover" :class="{ 'with-photo': background }">
    <img v-if="background" class="home-photo" :src="background" alt="" />
    <div v-if="background" class="home-shade" aria-hidden="true" />
    <header class="home-header"><nav aria-label="主导航">
      <RouterLink v-for="item in navigation" :key="item.path" :to="item.path">{{ item.title }}</RouterLink>
    </nav></header>
    <main id="main-content" class="home-body">
      <h1>LIFE <span>/</span> 1000</h1>
      <blockquote v-if="quote" class="home-quote">
        <p>{{ quote.content }}</p><cite v-if="quote.source">{{ quote.source }}</cite>
      </blockquote>
      <div v-if="errors.length" class="home-errors" role="alert"><p v-for="message in errors" :key="message">{{ message }}</p><button @click="load">重新读取</button></div>
      <p v-if="loading" class="home-loading" role="status">正在打开封面…</p>
    </main>
    <footer class="home-footer">
      <time class="home-date">{{ today.date }}</time>
      <div class="home-counts"><p>{{ stats ? stats.completedCount + ' / ' + stats.writtenCount : '— / —' }}</p><span>已完成 / 已写下</span></div>
      <div class="home-year"><p>{{ today.year }} 已走过 {{ today.percent.toFixed(1) }}%</p><div class="year-hairline" aria-hidden="true"><span :style="{ width: today.percent + '%' }" /></div></div>
    </footer>
  </div>
</template>
<style>
.home-cover { position: relative; isolation: isolate; min-height: 100svh; height: 100svh; display: flex; flex-direction: column; color: #574532; }
.home-photo, .home-shade { position: absolute; inset: 0; width: 100%; height: 100%; z-index: -2; }
.home-photo { object-fit: cover; }
.home-shade { z-index: -1; background: linear-gradient(180deg, #241e185e 0%, #241e183d 50%, #241e1873 100%); }
.home-cover.with-photo { color: #fff5e7; text-shadow: 0 1px 6px #17100c80; }
.home-header { padding: 38px 5vw; }
.home-header nav { justify-content: flex-end; gap: 34px; color: inherit; }
.home-header a { opacity: .76; padding: 7px 0; border-bottom: 1px solid transparent; }
.home-header a:hover { opacity: 1; border-color: currentColor; }
.home-body { flex: 1; padding: 2vh 8vw 7vh; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 0; text-align: center; }
.home-body h1 { font-size: clamp(46px, 7.2vw, 112px); letter-spacing: .075em; font-weight: 400; white-space: nowrap; margin: 0 0 34px; }
.home-body h1 span { font-weight: 400; opacity: .55; padding: 0 .07em; }
.home-quote { max-width: 900px; margin: 0; max-height: 32vh; overflow-y: auto; }
.home-quote p { font-family: Georgia, "Songti SC", "SimSun", serif; font-size: clamp(20px, 2.1vw, 32px); line-height: 1.9; margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.home-quote cite { display: block; margin-top: 20px; font-style: normal; font-size: 13px; opacity: .72; white-space: pre-wrap; overflow-wrap: anywhere; }
.home-footer { display: grid; grid-template-columns: 1fr 1fr 1fr; align-items: end; padding: 0 6vw 6vh; color: inherit; gap: 24px; }
.home-date { font-size: 14px; letter-spacing: .18em; opacity: .82; }
.home-counts { text-align: center; }
.home-counts p { margin: 0 0 7px; font: 28px Georgia, serif; letter-spacing: .12em; }
.home-counts span { font-size: 11px; opacity: .72; letter-spacing: .08em; }
.home-year { justify-self: end; width: min(100%, 220px); opacity: .8; }
.home-year p { font-size: 12px; letter-spacing: .06em; margin: 0 0 12px; }
.year-hairline { height: 1px; background: #a18f7938; }
.with-photo .year-hairline { background: #fff5e738; }
.year-hairline span { display: block; height: 1px; background: currentColor; }
.home-errors { font-size: 12px; opacity: .85; margin-top: 18px; }
.home-errors p { margin: 5px; }
.home-errors button { color: inherit; border: 0; padding: 3px 0; text-decoration: underline; }
.home-loading { font-size: 12px; opacity: .6; }
@media (max-width: 650px) {
 .home-header { padding: 22px; } .home-header nav { gap: 16px; justify-content: center; font-size: 12px; }
 .home-body { padding: 16px 24px 32px; } .home-body h1 { font-size: clamp(32px, 8vw, 52px); }
 .home-footer { padding: 0 24px 32px; grid-template-columns: 1fr 1fr; }
 .home-counts { text-align: right; } .home-year { grid-column: 1/-1; justify-self: stretch; width: 100%; }
}
@media (max-height: 600px) { .home-header { padding-top: 16px; padding-bottom: 16px; } .home-body h1 { font-size: 44px; margin-bottom: 18px; } }
</style>
