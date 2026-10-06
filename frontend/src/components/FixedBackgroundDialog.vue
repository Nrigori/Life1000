<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BackgroundImage } from '../api/settings'
import { formatSlot } from '../goals/slots'
import AttachmentImage from './AttachmentImage.vue'
import ModalDialog from './ModalDialog.vue'

const props = defineProps<{ images: BackgroundImage[]; busy: boolean; selectedId?: number }>()
const emit = defineEmits<{ close: []; confirm: [image: BackgroundImage] }>()
const query = ref('')
const page = ref(1)
const selected = ref<number | undefined>(props.selectedId)
const pageSize = 12
const filtered = computed(() => {
  const value = query.value.trim().toLocaleLowerCase()
  return props.images.filter(image => image.originalName.toLocaleLowerCase().includes(value))
})
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const choice = computed(() => props.images.find(image => image.attachmentId === selected.value))
watch(query, () => { page.value = 1 })
watch(pages, value => { page.value = Math.min(page.value, value) })
</script>

<template>
  <ModalDialog title="选择固定背景" :busy="busy" class="background-library-dialog" @close="emit('close')">
    <p class="dialog-note">固定背景从所有有效图片中选择，与随机背景候选互不影响。</p>
    <label class="library-search"><span>搜索图片</span><input v-model="query" type="search" placeholder="搜索图片……" autofocus /></label>
    <p v-if="!filtered.length" class="quiet">{{ images.length ? '没有匹配的图片。' : '还没有可选图片。' }}</p>
    <div class="library-grid">
      <button v-for="image in visible" :key="image.attachmentId" type="button" class="library-choice" :class="{ selected: selected === image.attachmentId }" :data-image="image.attachmentId" :aria-pressed="selected === image.attachmentId" @click="selected = image.attachmentId">
        <span class="library-preview"><AttachmentImage :id="image.attachmentId" :alt="image.originalName" /></span>
        <strong>{{ image.originalName }}</strong><small>第 {{ formatSlot(image.slotNo) }} 件</small>
      </button>
    </div>
    <nav v-if="pages > 1" class="library-pagination" aria-label="固定背景图片分页"><button :disabled="page === 1" @click="page--">上一页</button><span>{{ page }} / {{ pages }}</span><button :disabled="page === pages" @click="page++">下一页</button></nav>
    <div class="dialog-actions"><button :disabled="busy" @click="emit('close')">取消</button><button class="ink-button" :disabled="busy || !choice" @click="choice && emit('confirm', choice)">确认选择</button></div>
  </ModalDialog>
</template>

<style scoped>
:global(.background-library-dialog){width:min(960px,calc(100vw - 32px));max-height:min(88vh,820px);overflow:auto}
.dialog-note,.quiet{color:var(--muted);font-size:13px}.library-search{display:block;margin:18px 0}.library-search span{display:block;font-size:12px;color:var(--muted);margin-bottom:6px}.library-search input{width:100%}.library-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.library-choice{display:flex;min-width:0;flex-direction:column;gap:7px;padding:8px;text-align:left;background:rgba(250,245,236,.75);border:1px solid #ded3c4}.library-choice.selected{border-color:#8f7355;box-shadow:inset 0 0 0 1px rgba(128,99,68,.25)}.library-preview{height:125px;width:100%;display:grid;place-items:center;overflow:hidden;border-radius:3px;background:#e9decd;color:var(--muted);font-size:12px}.library-preview :deep(img){width:100%;height:100%;object-fit:cover}.library-choice strong{font-size:13px;font-weight:500;overflow-wrap:anywhere}.library-choice small{color:var(--muted)}.library-pagination{display:flex;justify-content:center;align-items:center;gap:14px;margin:22px 0 4px;color:var(--muted);font-size:12px;font-variant-numeric:tabular-nums}.library-pagination button{padding:6px 10px;font-size:12px}
@media(max-width:720px){.library-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:440px){.library-grid{grid-template-columns:1fr}}
</style>
