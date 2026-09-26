<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { fileBlob, downloadFile, type Attachment } from '../api/details'
import { errorMessage } from '../api/http'
import { previewKind } from '../attachments/preview'
import ModalDialog from './ModalDialog.vue'
import ImageViewer from './ImageViewer.vue'

const props = defineProps<{ file: Attachment; files?: Attachment[] }>()
const emit = defineEmits<{ close: [] }>()
const kind = computed(() => previewKind(props.file))
const imageFiles = computed(() => (props.files || [props.file]).filter(file => file.isImage))
const url = ref(''), text = ref(''), html = ref(''), error = ref('')
const loading = ref(true), downloading = ref(false)
let controller: AbortController | undefined
function clear() {
  controller?.abort()
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
}
watch(() => props.file, async file => {
  clear(); text.value = ''; html.value = ''; error.value = ''; loading.value = true
  const current = new AbortController()
  controller = current
  try {
    const type = previewKind(file)
    if (!type) return
    if (type === 'image') { loading.value = false; return }
    const blob = await fileBlob(file.id, false, current.signal)
    if (current.signal.aborted) return
    if (type === 'markdown' || type === 'text') {
      const source = await blob.text()
      if (current.signal.aborted) return
      if (type === 'text') text.value = source
      else {
        // 解析器不负责安全：仅允许排版标签，无事件、样式、脚本或外部资源属性。
        html.value = DOMPurify.sanitize(marked.parse(source, { async: false }), {
          ALLOWED_TAGS: ['h1','h2','h3','h4','h5','h6','p','blockquote','ul','ol','li','strong','em','pre','code','hr','br','del','table','thead','tbody','tr','th','td','a'],
          ALLOWED_ATTR: [],
          ALLOW_DATA_ATTR: false,
          ALLOW_ARIA_ATTR: false,
        })
      }
    } else {
      // PDF 固定为 PDF 类型，旧记录即便 MIME 是二进制也不会被 iframe 当作 HTML 执行。
      url.value = URL.createObjectURL(type === 'pdf' ? new Blob([blob], { type: 'application/pdf' }) : blob)
    }
  } catch (cause) { if (!current.signal.aborted) error.value = errorMessage(cause) }
  finally { if (!current.signal.aborted) loading.value = false }
}, { immediate: true })
async function download() {
  if (downloading.value) return
  downloading.value = true
  try { await downloadFile(props.file) } catch (cause) { error.value = errorMessage(cause) }
  finally { downloading.value = false }
}
onBeforeUnmount(clear)
</script>

<template>
  <ImageViewer v-if="kind === 'image'" :files="imageFiles" :initial-id="file.id" @close="emit('close')" />
  <ModalDialog v-else class="attachment-preview-dialog" :title="file.originalName" :aria-label="file.originalName" aria-labelledby="" @close="emit('close')">
    <div class="attachment-preview-body" :aria-busy="loading">
      <p v-if="loading" role="status">正在读取附件…</p>
      <p v-else-if="!kind">该格式暂不支持在线预览。</p>
      <template v-else>
        <iframe v-if="kind === 'pdf' && url" :src="url" :title="file.originalName + ' PDF 预览'" />
        <pre v-else-if="kind === 'text'" class="preview-text">{{ text }}</pre>
        <div v-else-if="kind === 'markdown'" class="preview-markdown" v-html="html" />
      </template>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </div>
    <div class="dialog-actions"><button autofocus @click="emit('close')">关闭预览</button><button :disabled="downloading" @click="download">{{ downloading ? '正在下载…' : '下载' }}</button></div>
  </ModalDialog>
</template>

<style>
.attachment-preview-dialog { width: min(1000px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); }
.attachment-preview-dialog h2 { overflow-wrap: anywhere; }
.attachment-preview-body { max-height: 68dvh; overflow: auto; }
.attachment-preview-body > img { display: block; max-width: 100%; max-height: 65dvh; object-fit: contain; margin: auto; }
.attachment-preview-body iframe { display: block; width: 100%; height: 65dvh; border: 1px solid #dfd3c1; }
.preview-text { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; line-height: 1.8; margin: 0; }
.preview-markdown { line-height: 1.8; overflow-wrap: anywhere; }
.preview-markdown pre { overflow: auto; background: #eee5d7; padding: 16px; border-radius: 4px; }
.preview-markdown code { font-family: Consolas, monospace; }
.preview-markdown blockquote { border-left: 2px solid #b9a58b; margin-left: 0; padding-left: 18px; color: #7a6a5b; }
.preview-markdown hr { border: 0; border-top: 1px solid #dfd3c1; }
.preview-markdown table { border-collapse: collapse; }
.preview-markdown th, .preview-markdown td { border: 1px solid #dfd3c1; padding: 6px 12px; }
</style>
