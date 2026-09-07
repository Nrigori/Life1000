<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { readQuotes, saveQuote, deleteQuote, type Quote, type QuoteInput } from '../api/home'
import { errorMessage } from '../api/http'
import ModalDialog from '../components/ModalDialog.vue'
const keyword = ref('')
const quotes = ref<Quote[]>([])
const form = ref<QuoteInput & { id?: number }>()
const removing = ref<Quote>()
const error = ref('')
const formError = ref('')
const loading = ref(true)
const busy = ref(false)
let revision = 0
let controller: AbortController | undefined
let timer: ReturnType<typeof setTimeout>
async function load() {
  const current = ++revision
  controller?.abort(); controller = new AbortController()
  loading.value = true; error.value = ''
  try { const values = await readQuotes(keyword.value.trim(), controller.signal); if (current === revision) quotes.value = values }
  catch (cause) { if (current === revision) error.value = errorMessage(cause) }
  finally { if (current === revision) loading.value = false }
}
watch(keyword, () => { ++revision; controller?.abort(); clearTimeout(timer); loading.value = true; timer = setTimeout(load, 250) })
function edit(quote?: Quote) {
  formError.value = ''
  form.value = quote ? { id: quote.id, content: quote.content, source: quote.source, includeHome: quote.includeHome }
    : { content: '', source: null, includeHome: true }
}
async function save() {
  const value = form.value
  if (!value || busy.value) return
  if (!value.content.trim()) { formError.value = '请先写下金句内容。'; return }
  busy.value = true; formError.value = ''
  try {
    await saveQuote({ content: value.content.trim(), source: value.source?.trim() || null, includeHome: value.includeHome }, value.id)
    form.value = undefined; await load()
  } catch (cause) { formError.value = errorMessage(cause) }
  finally { busy.value = false }
}
async function toggle(quote: Quote, event: Event) {
  const input = event.target as HTMLInputElement
  busy.value = true; error.value = ''
  try {
    const updated = await saveQuote({ content: quote.content, source: quote.source, includeHome: !quote.includeHome }, quote.id)
    quotes.value = quotes.value.map(item => item.id === updated.id ? updated : item)
  } catch (cause) { error.value = errorMessage(cause); input.checked = quote.includeHome }
  finally { busy.value = false }
}
async function remove() {
  if (!removing.value || busy.value) return
  busy.value = true; formError.value = ''
  try { await deleteQuote(removing.value.id); removing.value = undefined; await load() }
  catch (cause) { formError.value = errorMessage(cause) }
  finally { busy.value = false }
}
onMounted(load)
onBeforeUnmount(() => { ++revision; controller?.abort(); clearTimeout(timer) })
</script>
<template>
  <section class="quotes-page">
    <div class="quotes-heading"><h1>金句收藏</h1><button @click="edit()">＋ 收藏</button></div>
    <label class="quote-search"><span class="sr-only">搜索金句</span><input v-model="keyword" type="search" maxlength="1000" placeholder="搜索金句或来源…" /></label>
    <p v-if="error" class="form-error" role="alert">{{ error }} <button @click="load">重新读取</button></p>
    <p v-if="loading" role="status">正在翻阅…</p>
    <p v-else-if="!quotes.length && !error" class="quote-empty">{{ keyword ? '没有找到符合条件的金句。' : '还没有收藏金句。' }}</p>
    <ol v-else class="quote-list">
      <li v-for="quote in quotes" :key="quote.id" class="paper quote-entry" :data-quote="quote.id">
        <blockquote>{{ quote.content }}</blockquote><p v-if="quote.source" class="quote-source">{{ quote.source }}</p>
        <div class="quote-actions"><label><input type="checkbox" :checked="quote.includeHome" :disabled="busy" @change="toggle(quote, $event)" />参与首页随机</label>
          <button :disabled="busy" @click="edit(quote)">编辑</button><button :disabled="busy" @click="removing = quote; formError = ''">删除</button>
        </div>
      </li>
    </ol>
    <ModalDialog v-if="form" :title="form.id ? '编辑金句' : '收藏金句'" :busy="busy" @close="form = undefined">
      <form class="entry-form" @submit.prevent="save">
        <label>内容<textarea v-model="form.content" required autofocus rows="5" :disabled="busy" /></label>
        <label>来源<input v-model="form.source" maxlength="1000" :disabled="busy" placeholder="可选，如：苏轼《定风波》" /></label>
        <label class="quote-home-check"><input v-model="form.includeHome" type="checkbox" :disabled="busy" />参与首页随机</label>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
        <div class="dialog-actions"><button type="button" :disabled="busy" @click="form = undefined">取消</button><button class="ink-button" :disabled="busy">保存</button></div>
      </form>
    </ModalDialog>
    <ModalDialog v-if="removing" title="删除这条金句？" :busy="busy" @close="removing = undefined">
      <p>删除后，下次进入首页将不再选到它。</p><p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <div class="dialog-actions"><button autofocus :disabled="busy" @click="removing = undefined">取消</button><button class="ink-button" :disabled="busy" @click="remove">确认删除</button></div>
    </ModalDialog>
  </section>
</template>
<style>
.quotes-page { max-width: 920px; margin: auto; }
.quotes-heading { display: flex; justify-content: space-between; align-items: baseline; gap: 20px; margin-bottom: 20px; }
.quote-search { display: block; max-width: 520px; margin-bottom: 30px; }
.quote-list { list-style: none; padding: 0; }
.quote-entry { min-height: 0; padding: 28px 36px; margin: 18px 0; }
.quote-entry blockquote { margin: 0; font: 23px/1.9 Georgia, "SimSun", serif; white-space: pre-wrap; overflow-wrap: anywhere; }
.quote-source { color: #89755d; font-size: 13px; margin-top: 14px; white-space: pre-wrap; overflow-wrap: anywhere; }
.quote-actions { display: flex; align-items: center; gap: 12px; margin-top: 22px; font-size: 12px; color: #89755d; }
.quote-actions label { margin-right: auto; }
.quote-actions button { font-size: 12px; padding: 4px 8px; border: 0; }
.quote-actions input, .quote-home-check input { width: auto; accent-color: #806344; margin-right: 8px; }
.quote-home-check { display: flex; align-items: center; }
.quote-empty { color: #89755d; padding: 40px 0; }
@media (max-width: 650px) { .quote-entry { padding: 24px; } .quote-entry blockquote { font-size: 20px; } }
</style>
