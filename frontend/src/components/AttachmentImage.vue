<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { fileBlob } from '../api/details'
const props = defineProps<{ id: number; alt: string }>()
const url = ref('')
const failed = ref(false)
let controller: AbortController | undefined
// 图片需先携带 JWT 获取 Blob，不能把 Token 放入图片 URL；换图或卸载时取消请求并释放对象 URL。
function clear() {
  controller?.abort()
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
}
watch(() => props.id, async id => {
  clear(); failed.value = false
  const current = new AbortController()
  controller = current
  try {
    const blob = await fileBlob(id, false, current.signal)
    if (!current.signal.aborted) url.value = URL.createObjectURL(blob)
  } catch { if (!current.signal.aborted) failed.value = true }
}, { immediate: true })
onBeforeUnmount(clear)
</script>
<template>
  <img v-if="url && !failed" :src="url" :alt="alt" loading="lazy" @error="failed = true" />
  <span v-else role="status">{{ failed ? '影像暂时无法读取' : '正在展开影像…' }}</span>
</template>
