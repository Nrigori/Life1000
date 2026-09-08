<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { LifeGoal } from '../api/goals'
import { readCompletion, saveCompletion, localToday } from '../api/completion'
import { readAttachments, fileSize, type Attachment } from '../api/details'
import { errorMessage } from '../api/http'
import { formatSlot } from '../goals/slots'
import ModalDialog from './ModalDialog.vue'
import AttachmentImage from './AttachmentImage.vue'
import AttachmentPreviewDialog from './AttachmentPreviewDialog.vue'
import { previewKind } from '../attachments/preview'
const preview = ref<Attachment>()
const props = defineProps<{ goal: LifeGoal; editing?: boolean }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const date = ref(localToday())
const note = ref('')
const rating = ref<number | null>(null)
const selected = ref<File[]>([])
const existing = ref<Attachment[]>([])
const loading = ref(true)
const busy = ref(false)
const loaded = ref(false)
const error = ref('')
let alive = true
onBeforeUnmount(() => { alive = false })
// 撤销后的档案和证明会保留并重新载入；再次完成默认今天，只有编辑现有档案才沿用原完成日期。
async function load() {
  loading.value = true; error.value = ''
  try {
    const [archive, files] = await Promise.all([readCompletion(props.goal.slotNo), readAttachments(props.goal.slotNo)])
    if (!alive) return
    if (archive) {
      note.value = archive.completionNote || ''; rating.value = archive.rating
      if (props.editing) date.value = archive.completedDate
    }
    loaded.value = true
    existing.value = files.filter(file => file.stage === 'COMPLETION')
  } catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}
function choose(event: Event) {
  const input = event.target as HTMLInputElement
  selected.value.push(...(input.files || [])); input.value = ''
}
// 快速入口与详情入口共用提交逻辑，证明随档案一次发送；后端确认保存后才通知页面刷新完成状态。
async function submit() {
  if (busy.value || loading.value || !loaded.value) return
  if (!date.value) { error.value = '请填写完成日期。'; return }
  if (selected.value.reduce((sum, file) => sum + file.size, 0) > 50 * 1024 * 1024) {
    error.value = '本次证明附件合计不能超过 50 MB，可以完成后再补充。'; return
  }
  busy.value = true; error.value = ''
  try {
    await saveCompletion(props.goal.slotNo, { completedDate: date.value, completionNote: note.value || null, rating: rating.value }, selected.value, !!props.editing)
    if (alive) emit('saved')
  } catch (cause) { error.value = errorMessage(cause) }
  finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <ModalDialog class="completion-dialog" :title="'第 ' + formatSlot(goal.slotNo) + ' 件'" :busy="busy" @close="emit('close')">
    <p class="completion-title">{{ goal.title }}</p>
    <p class="completion-caption">{{ editing ? '完成之后' : '完成了' }}</p>
    <p v-if="loading" role="status">正在翻阅完成档案…</p>
    <div v-else-if="!loaded"><p role="alert">{{ error }}</p><button @click="load">重新读取</button></div>
    <form v-else class="entry-form" @submit.prevent="submit">
      <label>完成日期<input v-model="date" type="date" min="1000-01-01" max="9999-12-31" required autofocus :disabled="busy" /></label>
      <label>完成感想<textarea v-model="note" rows="4" :disabled="busy" placeholder="可选，留下一点此刻的感受。" /></label>
      <fieldset class="star-rating" :disabled="busy"><legend>体验评分 <small>可选</small></legend>
        <button v-for="star in 5" :key="star" type="button" :aria-label="star + ' 星'" :aria-pressed="rating === star" @click="rating = star">{{ rating && star <= rating ? '★' : '☆' }}</button>
        <button type="button" class="no-rating" :aria-pressed="rating === null" @click="rating = null">不评分</button>
      </fieldset>
      <label>完成证明<input type="file" multiple :disabled="busy" @change="choose" /></label>
      <p class="quiet">可选。图片或文档，本次合计最多 50 MB。</p>
      <ul v-if="selected.length" class="proof-selection">
        <li v-for="(file, index) in selected" :key="index">{{ file.name }} · {{ fileSize(file.size) }}<button type="button" :disabled="busy" :aria-label="'移除 ' + file.name" @click="selected.splice(index, 1)">移除</button></li>
      </ul>
      <div v-if="existing.length" class="retained-proofs"><p class="quiet">已保留的完成证明</p>
        <div v-for="file in existing" :key="file.id"><AttachmentImage v-if="file.isImage" :id="file.id" :alt="file.originalName" /><span>{{ file.originalName }}</span><button v-if="previewKind(file)" type="button" @click="preview = file">预览</button></div>
      </div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="dialog-actions"><button type="button" :disabled="busy" @click="emit('close')">取消</button><button class="ink-button" :disabled="busy">{{ busy ? '正在保存…' : editing ? '保存完成档案' : '确认完成' }}</button></div>
    </form>
    <AttachmentPreviewDialog v-if="preview" :file="preview" @close="preview = undefined" />
  </ModalDialog>
</template>
<style>
.completion-dialog { width: min(580px, calc(100vw - 32px)); }
.completion-dialog h2 { font-size: 15px; letter-spacing: .13em; color: #897057; }
.completion-title { font-family: Georgia, "SimSun", serif; font-size: 24px; margin-bottom: 4px; overflow-wrap: anywhere; }
.completion-caption { font-size: 18px; color: #897057; margin-top: 0; padding-bottom: 20px; border-bottom: 1px solid #dfd3c1; }
.star-rating { padding: 12px 0; border: 0; margin: 0; }
.star-rating legend { font-size: 14px; }
.star-rating small { color: #897057; }
.star-rating button { border: 0; padding: 4px 7px; font-size: 27px; }
.star-rating .no-rating { font-size: 12px; margin-left: 14px; text-decoration: underline; text-underline-offset: 4px; }
.proof-selection { padding-left: 18px; font-size: 13px; overflow-wrap: anywhere; }
.proof-selection button { margin-left: 8px; padding: 3px 8px; font-size: 12px; }
.retained-proofs { font-size: 12px; }
.retained-proofs > div { display: flex; gap: 10px; align-items: center; margin-top: 8px; }
.retained-proofs img { width: 64px; height: 48px; object-fit: cover; }
</style>
