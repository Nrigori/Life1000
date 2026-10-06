<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BackgroundImage } from '../api/settings'
import { formatSlot } from '../goals/slots'
import AttachmentImage from './AttachmentImage.vue'
import ModalDialog from './ModalDialog.vue'

const props = defineProps<{ images: BackgroundImage[]; busy: boolean }>()
const emit = defineEmits<{ close: []; toggle: [image: BackgroundImage, event: Event] }>()
const query = ref('')
const page = ref(1)
const pageSize = 12
const filtered = computed(() => {
  const value = query.value.trim().toLocaleLowerCase()
  return props.images.filter(image => image.originalName.toLocaleLowerCase().includes(value))
})
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(query, () => { page.value = 1 })
watch(pages, value => { page.value = Math.min(page.value, value) })
</script>

<template>
  <ModalDialog title="管理随机背景候选" :busy="busy" class="background-library-dialog" @close="emit('close')">
    <p class="dialog-note">这里只管理随机背景候选，不会改变固定背景。</p>
    <label class="library-search"><span>搜索图片</span><input v-model="query" type="search" placeholder="搜索图片……" autofocus /></label>
    <p v-if="!filtered.length" class="quiet">{{ images.length ? '没有匹配的图片。' : '还没有图片。' }}</p>
    <div class="library-grid">
      <article v-for="image in visible" :key="image.attachmentId" class="library-card" :data-image="image.attachmentId">
        <div class="library-preview"><AttachmentImage :id="image.attachmentId" :alt="image.originalName" /></div>
        <p>{{ image.originalName }}</p>
        <RouterLink :to="'/goals/' + image.slotNo">第 {{ formatSlot(image.slotNo) }} 件</RouterLink>
        <label><input type="checkbox" :checked="image.allowHomeBackground" :disabled="busy" @change="emit('toggle', image, $event)" />允许作为首页背景</label>
      </article>
    </div>
    <nav v-if="pages > 1" class="library-pagination" aria-label="随机背景候选分页"><button :disabled="page === 1" @click="page--">上一页</button><span>{{ page }} / {{ pages }}</span><button :disabled="page === pages" @click="page++">下一页</button></nav>
    <div class="dialog-actions"><button :disabled="busy" @click="emit('close')">关闭</button></div>
  </ModalDialog>
</template>

<style scoped>
:global(.background-library-dialog){width:min(960px,calc(100vw - 32px));max-height:min(88vh,820px);overflow:auto}
.dialog-note,.quiet{color:var(--muted);font-size:13px}.library-search{display:block;margin:18px 0}.library-search span{display:block;font-size:12px;color:var(--muted);margin-bottom:6px}.library-search input{width:100%}.library-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.library-card{display:flex;min-width:0;flex-direction:column;gap:8px;font-size:12px}.library-preview{height:125px;display:grid;place-items:center;overflow:hidden;border:1px solid #ded1bf;border-radius:4px;background:#e9decd;color:var(--muted)}.library-preview :deep(img){width:100%;height:100%;object-fit:cover}.library-card p{margin:0;overflow-wrap:anywhere;font-size:13px}.library-card a{color:var(--muted)}.library-card label{display:flex;align-items:center;gap:5px}.library-card input{width:auto;accent-color:var(--accent)}.library-pagination{display:flex;justify-content:center;align-items:center;gap:14px;margin:22px 0 4px;color:var(--muted);font-size:12px;font-variant-numeric:tabular-nums}.library-pagination button{padding:6px 10px;font-size:12px}
@media(max-width:720px){.library-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:440px){.library-grid{grid-template-columns:1fr}}
</style>
