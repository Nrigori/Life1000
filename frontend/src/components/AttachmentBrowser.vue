<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Attachment } from '../api/details'
import { fileSize } from '../api/details'
import { previewKind } from '../attachments/preview'
import AttachmentImage from './AttachmentImage.vue'

const props = defineProps<{ attachments: Attachment[]; busy: boolean; coverId?: number }>()
const emit = defineEmits<{
  preview: [file: Attachment]
  download: [file: Attachment]
  remove: [file: Attachment]
  setCover: [file: Attachment]
  toggleBackground: [file: Attachment, event: Event]
}>()
type Filter = 'all' | 'images' | 'files'
const filter = ref<Filter>('all')
const query = ref('')
const imagePage = ref(1)
const filePage = ref(1)
const imagePageSize = 12
const filePageSize = 18
const normalizedQuery = computed(() => query.value.trim().toLocaleLowerCase())
const matching = computed(() => props.attachments.filter(file => file.originalName.toLocaleLowerCase().includes(normalizedQuery.value)))
const images = computed(() => matching.value.filter(file => file.isImage))
const files = computed(() => matching.value.filter(file => !file.isImage))
const imagePages = computed(() => Math.max(1, Math.ceil(images.value.length / imagePageSize)))
const filePages = computed(() => Math.max(1, Math.ceil(files.value.length / filePageSize)))
const visibleImages = computed(() => images.value.slice((imagePage.value - 1) * imagePageSize, imagePage.value * imagePageSize))
const visibleFiles = computed(() => files.value.slice((filePage.value - 1) * filePageSize, filePage.value * filePageSize))
const showImages = computed(() => filter.value !== 'files')
const showFiles = computed(() => filter.value !== 'images')
const hasMatches = computed(() => (showImages.value && images.value.length > 0) || (showFiles.value && files.value.length > 0))

watch([filter, normalizedQuery], () => { imagePage.value = 1; filePage.value = 1 })
watch(imagePages, pages => { imagePage.value = Math.min(imagePage.value, pages) })
watch(filePages, pages => { filePage.value = Math.min(filePage.value, pages) })

function extension(file: Attachment) {
  const value = file.originalName.split('.').pop()
  return value && value !== file.originalName ? value.toUpperCase() : '文件'
}
function stage(file: Attachment) {
  return file.stage === 'PROCESS' ? '过程记录' : file.stage === 'COMPLETION' ? '完成证明' : '事项附件'
}
</script>

<template>
  <div class="attachment-browser">
    <div v-if="attachments.length" class="attachment-browser-tools">
      <div class="attachment-filters" role="group" aria-label="附件分类">
        <button :class="{ active: filter === 'all' }" :aria-pressed="filter === 'all'" @click="filter = 'all'">全部 {{ attachments.length }}</button>
        <button :class="{ active: filter === 'images' }" :aria-pressed="filter === 'images'" @click="filter = 'images'">图片 {{ attachments.filter(file => file.isImage).length }}</button>
        <button :class="{ active: filter === 'files' }" :aria-pressed="filter === 'files'" @click="filter = 'files'">文件 {{ attachments.filter(file => !file.isImage).length }}</button>
      </div>
      <label class="attachment-search"><span class="sr-only">搜索附件</span><input v-model="query" type="search" placeholder="搜索附件……" /></label>
    </div>

    <p v-if="!hasMatches" class="quiet attachment-empty">{{ attachments.length ? '没有匹配的附件。' : '还没有附件。' }}</p>

    <section v-if="showImages && images.length" class="attachment-group" aria-labelledby="attachment-images-title">
      <div class="attachment-group-heading"><h3 id="attachment-images-title">图片 <small>{{ images.length }}</small></h3></div>
      <div class="attachment-image-grid">
        <article v-for="file in visibleImages" :key="file.id" class="attachment-card attachment-image-card" :data-attachment="file.id">
          <button class="attachment-thumb" :aria-label="'预览 ' + file.originalName" @click="emit('preview', file)"><AttachmentImage :id="file.id" :alt="file.originalName" /></button>
          <h3>{{ file.originalName }}</h3>
          <p class="quiet">{{ extension(file) }} · {{ fileSize(file.fileSize) }} · {{ stage(file) }}</p>
          <div class="row-actions"><button :disabled="busy" @click="emit('preview', file)">预览</button><button :disabled="busy" @click="emit('download', file)">下载</button><button :disabled="busy" @click="emit('remove', file)">删除附件</button></div>
          <button :disabled="busy || coverId === file.id" @click="emit('setCover', file)">{{ coverId === file.id ? '已选为封面' : '设为封面' }}</button>
          <label class="background-option"><input type="checkbox" :checked="file.allowHomeBackground" :disabled="busy" @change="emit('toggleBackground', file, $event)" />允许作为首页背景</label>
        </article>
      </div>
      <nav v-if="imagePages > 1" class="attachment-pagination" aria-label="图片附件分页"><button :disabled="imagePage === 1" @click="imagePage--">上一页</button><span>{{ imagePage }} / {{ imagePages }}</span><button :disabled="imagePage === imagePages" @click="imagePage++">下一页</button></nav>
    </section>

    <section v-if="showFiles && files.length" class="attachment-group" aria-labelledby="attachment-files-title">
      <div class="attachment-group-heading"><h3 id="attachment-files-title">文件 <small>{{ files.length }}</small></h3></div>
      <div class="attachment-file-list">
        <article v-for="file in visibleFiles" :key="file.id" class="attachment-card attachment-file-row" :data-attachment="file.id">
          <div class="file-mark" aria-hidden="true">{{ extension(file) }}</div>
          <div class="file-description"><h3>{{ file.originalName }}</h3><p class="quiet">{{ extension(file) }} · {{ fileSize(file.fileSize) }} · {{ stage(file) }}</p></div>
          <div class="row-actions"><button v-if="previewKind(file)" :disabled="busy" @click="emit('preview', file)">预览</button><button :disabled="busy" @click="emit('download', file)">下载</button><button :disabled="busy" @click="emit('remove', file)">删除附件</button></div>
        </article>
      </div>
      <nav v-if="filePages > 1" class="attachment-pagination" aria-label="文件附件分页"><button :disabled="filePage === 1" @click="filePage--">上一页</button><span>{{ filePage }} / {{ filePages }}</span><button :disabled="filePage === filePages" @click="filePage++">下一页</button></nav>
    </section>
  </div>
</template>

<style scoped>
.attachment-browser{margin-top:22px}.attachment-browser-tools{display:flex;align-items:center;justify-content:space-between;gap:18px;flex-wrap:wrap}.attachment-filters{display:flex;gap:4px;padding:3px;border:1px solid #e1d6c7;border-radius:5px;background:#f4ecdf}.attachment-filters button{border:0;background:transparent;padding:7px 11px;color:var(--muted);font-size:12px}.attachment-filters button.active{background:#fffaf1;color:#574535;box-shadow:inset 0 0 0 1px rgba(128,99,68,.16)}.attachment-search{min-width:min(260px,100%)}.attachment-search input{width:100%;padding:8px 10px}.attachment-empty{margin:24px 0}.attachment-group{margin-top:26px}.attachment-group+.attachment-group{padding-top:24px;border-top:1px solid #e6dccf}.attachment-group-heading h3{margin:0 0 14px;font-size:16px;font-weight:500}.attachment-group-heading small{color:var(--muted);font-weight:400;margin-left:6px}.attachment-image-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.attachment-card{min-width:0}.attachment-image-card{border:1px solid #e0d5c6;padding:14px;border-radius:4px}.attachment-card h3{font-size:14px;font-weight:500;overflow-wrap:anywhere;margin:12px 0 6px}.attachment-image-card>button{margin-top:12px;font-size:12px}.attachment-thumb{display:block;width:100%;height:130px;overflow:hidden;padding:0}.attachment-thumb :deep(img){width:100%;height:100%;object-fit:cover}.background-option{display:flex;align-items:center;gap:6px;font-size:12px;margin-top:14px}.background-option input{width:auto;accent-color:#806344}.attachment-file-list{border-top:1px solid #e1d6c8}.attachment-file-row{display:grid;grid-template-columns:54px minmax(0,1fr) auto;align-items:center;gap:14px;padding:13px 4px;border-bottom:1px solid #e7ddd0}.file-mark{display:grid;place-items:center;width:46px;height:38px;border:1px solid #dfd2c1;border-radius:3px;background:#f2eadf;color:#8b745c;font-size:10px;letter-spacing:.04em;overflow:hidden}.file-description{min-width:0}.file-description h3{margin:0 0 5px}.file-description p{margin:0}.attachment-pagination{display:flex;align-items:center;justify-content:center;gap:14px;margin-top:20px;color:var(--muted);font-size:12px;font-variant-numeric:tabular-nums}.attachment-pagination button{font-size:12px;padding:6px 10px}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
@media(max-width:800px){.attachment-image-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.attachment-file-row{grid-template-columns:46px minmax(0,1fr)}.attachment-file-row .row-actions{grid-column:2}}
@media(max-width:540px){.attachment-browser-tools{align-items:stretch;flex-direction:column}.attachment-search{width:100%}.attachment-image-grid{grid-template-columns:1fr}.attachment-file-row{grid-template-columns:1fr}.file-mark{display:none}.attachment-file-row .row-actions{grid-column:1}}
</style>
