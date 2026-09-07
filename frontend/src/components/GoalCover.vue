<script setup lang="ts">
import { ref, watch } from 'vue'
import { readCover, type Attachment } from '../api/details'
import AttachmentImage from './AttachmentImage.vue'
const props = defineProps<{ slot: number }>()
const cover = ref<Attachment>()
let revision = 0
watch(() => props.slot, async slot => {
  const current = ++revision
  cover.value = undefined
  try { const result = await readCover(slot); if (current === revision) cover.value = result } catch { /* Keep a clean placeholder. */ }
}, { immediate: true })
</script>
<template>
  <span class="cover-empty"><AttachmentImage v-if="cover" :id="cover.id" alt="事项封面" />
    <template v-else><span class="cover-symbol" aria-hidden="true">✦</span><span>尚无影像</span></template>
  </span>
</template>
<style scoped>
.cover-empty { overflow: hidden; }
.cover-empty :deep(img) { width: 100%; height: 100%; object-fit: cover; }
</style>
