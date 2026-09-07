<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { readTimeline, readYear, type TimelineYear, type TimelineEntry } from '../api/completion'
import { errorMessage } from '../api/http'
import { formatSlot } from '../goals/slots'
const years = ref<TimelineYear[]>([])
// 默认只展开当前年，其余年份按需查询；重新展开时再取数据，避免长期使用旧的完成日期。
const open = ref(new Set<number>([new Date().getFullYear()]))
const entries = ref<Record<number, TimelineEntry[]>>({})
const errors = ref<Record<number, string>>({})
const pending = ref(new Set<number>())
const loading = ref(true)
const error = ref('')
async function loadYear(year: number) {
  if (pending.value.has(year)) return
  pending.value.add(year); errors.value[year] = ''
  try { entries.value[year] = await readYear(year) }
  catch (cause) { errors.value[year] = errorMessage(cause) }
  finally { pending.value.delete(year) }
}
async function load() {
  loading.value = true; error.value = ''
  try {
    years.value = await readTimeline()
    await Promise.all(years.value.filter(year => open.value.has(year.year)).map(year => loadYear(year.year)))
  } catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}
function toggle(year: number) {
  if (open.value.has(year)) open.value.delete(year)
  else { open.value.add(year); void loadYear(year) }
}
onMounted(load)
</script>
<template>
  <section class="timeline-page">
    <h1>时间轴</h1>
    <p class="timeline-intro">这一年的哪一天，完成了什么。</p>
    <p v-if="loading" role="status">正在翻阅…</p>
    <div v-else-if="error"><p role="alert">{{ error }}</p><button @click="load">重新读取</button></div>
    <article v-for="year in years" v-else :key="year.year" class="timeline-year paper" :data-year="year.year">
      <button class="year-toggle" :aria-expanded="open.has(year.year)" :aria-controls="'year-' + year.year" @click="toggle(year.year)">
        <span class="year-number">{{ year.year }}</span><span>完成 {{ year.count }} 件</span><span aria-hidden="true">{{ open.has(year.year) ? '⌃' : '⌄' }}</span>
      </button>
      <div v-if="open.has(year.year)" :id="'year-' + year.year" class="year-content">
        <p v-if="pending.has(year.year)" role="status">正在翻阅…</p>
        <div v-else-if="errors[year.year]"><p role="alert">{{ errors[year.year] }}</p><button @click="loadYear(year.year)">重新读取</button></div>
        <p v-else-if="!entries[year.year]?.length" class="empty-year">这一年没有完成记录。</p>
        <ol v-else>
          <li v-for="entry in entries[year.year]" :key="entry.slotNo">
            <RouterLink :to="'/goals/' + entry.slotNo"><time :datetime="entry.completedDate">{{ entry.completedDate.slice(5).replace('-', '.') }}</time><span>第{{ formatSlot(entry.slotNo) }}件 · {{ entry.title }}</span></RouterLink>
          </li>
        </ol>
      </div>
    </article>
  </section>
</template>
<style scoped>
.timeline-page { max-width: 900px; margin: 0 auto; }
.timeline-intro { margin-bottom: 38px; color: #7a6a5b; font-size: 14px; }
.timeline-year { min-height: 0; margin: 18px 0; padding: 0 32px; }
.year-toggle { display: flex; width: 100%; align-items: center; gap: 22px; padding: 25px 0; border: 0; }
.year-number { margin-right: auto; font: 28px Georgia, serif; color: #5e4b39; }
.year-toggle > span:not(.year-number) { color: #8c775f; font-size: 13px; }
.year-content { border-top: 1px solid #e2d7c7; padding: 16px 0 26px; }
.year-content ol { list-style: none; padding: 0; margin: 0; }
.year-content a { display: flex; align-items: baseline; gap: 30px; padding: 14px 0; line-height: 1.8; }
.year-content a:hover { color: #806344; text-decoration: underline; text-underline-offset: 5px; }
.year-content time { color: #8c775f; font-variant-numeric: tabular-nums; flex-shrink: 0; }
.year-content a span { overflow-wrap: anywhere; }
.empty-year { color: #8c775f; font-size: 14px; padding: 10px 0; }
@media (max-width: 600px) { .timeline-year { padding: 0 18px; } .year-content a { gap: 16px; font-size: 14px; } }
</style>
