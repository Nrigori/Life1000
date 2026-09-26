<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { downloadFile, fileBlob, type Attachment } from '../api/details'
import { errorMessage } from '../api/http'

const props = defineProps<{ files: Attachment[]; initialId: number }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
const images = computed(() => props.files.filter(file => file.isImage))
const initialIndex = images.value.findIndex(file => file.id === props.initialId)
const index = ref(initialIndex < 0 ? 0 : initialIndex)
const current = computed(() => images.value[index.value])
const urls = ref<Record<number, string>>({})
const currentUrl = computed(() => current.value ? urls.value[current.value.id] || '' : '')
const loading = ref(true)
const downloading = ref(false)
const error = ref('')
const mode = ref<'fit' | 'original'>('fit')
const scale = ref(1)
const pan = ref({ x: 0, y: 0 })
const dragging = ref(false)
const controllers = new Map<number, AbortController>()
let previousOverflow = ''
let dragStart = { x: 0, y: 0, panX: 0, panY: 0 }
let alive = true

const imageStyle = computed(() => ({ transform: `translate3d(${pan.value.x}px, ${pan.value.y}px, 0) scale(${scale.value})` }))
const canPan = computed(() => scale.value > 1 || mode.value === 'original')

function revoke(id: number) {
  const url = urls.value[id]
  if (!url) return
  URL.revokeObjectURL(url)
  const next = { ...urls.value }
  delete next[id]
  urls.value = next
}
function clearAll() {
  for (const controller of controllers.values()) controller.abort()
  controllers.clear()
  for (const id of Object.keys(urls.value)) URL.revokeObjectURL(urls.value[Number(id)]!)
  urls.value = {}
}
async function ensure(file: Attachment) {
  if (urls.value[file.id] || controllers.has(file.id)) return
  const controller = new AbortController()
  controllers.set(file.id, controller)
  try {
    const blob = await fileBlob(file.id, false, controller.signal)
    if (!alive || controller.signal.aborted) return
    urls.value = { ...urls.value, [file.id]: URL.createObjectURL(blob) }
  } catch (cause) {
    if (current.value?.id === file.id && !controller.signal.aborted) error.value = errorMessage(cause)
  } finally {
    controllers.delete(file.id)
    if (current.value?.id === file.id && !controller.signal.aborted) loading.value = false
  }
}
function loadAround() {
  const keep = new Set<number>()
  for (const position of [index.value - 1, index.value, index.value + 1]) {
    const file = images.value[position]
    if (file) keep.add(file.id)
  }
  for (const [id, controller] of controllers) {
    if (!keep.has(id)) { controller.abort(); controllers.delete(id) }
  }
  for (const id of Object.keys(urls.value).map(Number)) if (!keep.has(id)) revoke(id)
  const selected = current.value
  loading.value = Boolean(selected && !urls.value[selected.id])
  error.value = ''
  if (!selected) { loading.value = false; return }
  void ensure(selected)
  for (const position of [index.value - 1, index.value + 1]) {
    const file = images.value[position]
    if (file) void ensure(file)
  }
}
function resetView(nextMode: 'fit' | 'original' = 'fit') {
  mode.value = nextMode
  scale.value = 1
  pan.value = { x: 0, y: 0 }
  dragging.value = false
}
function move(step: number) {
  const next = index.value + step
  if (next < 0 || next >= images.value.length) return
  index.value = next
}
function keyboard(event: KeyboardEvent) {
  if (event.key === 'ArrowLeft') { event.preventDefault(); move(-1) }
  if (event.key === 'ArrowRight') { event.preventDefault(); move(1) }
}
function zoom(event: WheelEvent) {
  if (!currentUrl.value) return
  const next = Math.min(5, Math.max(.5, scale.value + (event.deltaY < 0 ? .18 : -.18)))
  scale.value = Number(next.toFixed(2))
  if (scale.value <= 1 && mode.value === 'fit') pan.value = { x: 0, y: 0 }
}
function pointerDown(event: PointerEvent) {
  if (event.button !== 0 || !canPan.value) return
  dragging.value = true
  dragStart = { x: event.clientX, y: event.clientY, panX: pan.value.x, panY: pan.value.y }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}
function pointerMove(event: PointerEvent) {
  if (!dragging.value) return
  pan.value = { x: dragStart.panX + event.clientX - dragStart.x, y: dragStart.panY + event.clientY - dragStart.y }
}
function pointerUp(event: PointerEvent) {
  if (!dragging.value) return
  dragging.value = false
  const target = event.currentTarget as HTMLElement
  if (target.hasPointerCapture(event.pointerId)) target.releasePointerCapture(event.pointerId)
}
async function download() {
  if (!current.value || downloading.value) return
  downloading.value = true
  error.value = ''
  try { await downloadFile(current.value) } catch (cause) { error.value = errorMessage(cause) }
  finally { downloading.value = false }
}
function cancel(event: Event) {
  event.preventDefault()
  emit('close')
}

watch(index, () => { resetView(); loadAround() })
watch(currentUrl, url => { if (url && current.value) loading.value = false })
onMounted(() => {
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialog.value?.showModal()
  window.addEventListener('keydown', keyboard)
  loadAround()
})
onBeforeUnmount(() => {
  alive = false
  window.removeEventListener('keydown', keyboard)
  dialog.value?.close()
  document.body.style.overflow = previousOverflow
  clearAll()
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="image-viewer" :aria-label="current ? `查看图片：${current.originalName}` : '查看图片'" @cancel="cancel">
      <header class="image-viewer-bar">
        <div class="image-viewer-identity">
          <strong>{{ current?.originalName }}</strong>
          <span>{{ images.length ? index + 1 : 0 }} / {{ images.length }}</span>
        </div>
        <div class="image-viewer-actions">
          <button type="button" :disabled="mode === 'fit' && scale === 1" @click="resetView('fit')">适应窗口</button>
          <button type="button" :disabled="mode === 'original' && scale === 1" @click="resetView('original')">原始大小</button>
          <button type="button" :disabled="downloading || !current" @click="download">{{ downloading ? '正在下载…' : '下载' }}</button>
          <button class="image-viewer-close" type="button" aria-label="关闭图片查看器" title="关闭" @click="emit('close')">×</button>
        </div>
      </header>
      <button class="image-viewer-previous" type="button" aria-label="上一张图片" :disabled="index === 0" @click="move(-1)">‹</button>
      <div class="image-viewer-stage" :class="{ 'is-dragging': dragging, 'can-pan': canPan }"
        @wheel.prevent="zoom" @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="pointerUp">
        <p v-if="loading" role="status">正在展开影像…</p>
        <p v-else-if="error" class="image-viewer-error" role="alert">{{ error }}</p>
        <img v-if="currentUrl" :src="currentUrl" :alt="current?.originalName" :class="mode" :style="imageStyle" draggable="false"
          @error="error = '图片暂时无法预览，请尝试下载。'" />
      </div>
      <button class="image-viewer-next" type="button" aria-label="下一张图片" :disabled="index >= images.length - 1" @click="move(1)">›</button>
      <p v-if="error && currentUrl" class="image-viewer-floating-error" role="alert">{{ error }}</p>
    </dialog>
  </Teleport>
</template>

<style>
.image-viewer { width: 100vw; height: 100dvh; max-width: none; max-height: none; margin: 0; padding: 0; overflow: hidden; border: 0; color: #f8efe2; background: rgba(37, 30, 25, .94); }
.image-viewer::backdrop { background: rgba(37, 30, 25, .72); }
.image-viewer-bar { position: absolute; z-index: 3; inset: 0 0 auto; min-height: 58px; display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 8px 14px 8px 22px; background: rgba(69, 56, 46, .66); border-bottom: 1px solid rgba(255, 244, 229, .13); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); }
.image-viewer-identity { min-width: 0; display: flex; align-items: baseline; gap: 16px; }
.image-viewer-identity strong { max-width: min(48vw, 620px); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 400; }
.image-viewer-identity span { color: rgba(248, 239, 226, .66); font-size: 12px; white-space: nowrap; }
.image-viewer-actions { display: flex; align-items: center; gap: 5px; }
.image-viewer-actions button { padding: 7px 10px; border-color: rgba(255, 244, 229, .18); color: #f8efe2; font-size: 12px; }
.image-viewer-actions button:hover { background: rgba(255, 244, 229, .09); border-color: rgba(255, 244, 229, .32); }
.image-viewer-actions button:disabled { opacity: .4; }
.image-viewer-actions .image-viewer-close { width: 38px; height: 38px; margin-left: 4px; padding: 0; border: 0; font-size: 25px; font-weight: 200; }
.image-viewer-stage { position: absolute; inset: 58px 62px 0; display: grid; place-items: center; overflow: hidden; touch-action: none; }
.image-viewer-stage.can-pan { cursor: grab; }
.image-viewer-stage.is-dragging { cursor: grabbing; }
.image-viewer-stage img { display: block; flex: none; user-select: none; will-change: transform; transform-origin: center; }
.image-viewer-stage img.fit { max-width: calc(100vw - 148px); max-height: calc(100dvh - 90px); width: auto; height: auto; }
.image-viewer-stage img.original { max-width: none; max-height: none; width: auto; height: auto; }
.image-viewer-stage > p { color: rgba(248, 239, 226, .72); }
.image-viewer-previous, .image-viewer-next { position: absolute; z-index: 2; top: 58px; bottom: 0; width: 62px; padding: 0; border: 0; border-radius: 0; color: rgba(248, 239, 226, .74); font: 42px/1 Georgia, serif; }
.image-viewer-previous { left: 0; }
.image-viewer-next { right: 0; }
.image-viewer-previous:hover, .image-viewer-next:hover { color: #fff8ec; background: rgba(255, 244, 229, .045); }
.image-viewer-previous:disabled, .image-viewer-next:disabled { opacity: .18; cursor: default; }
.image-viewer-floating-error { position: absolute; z-index: 3; right: 22px; bottom: 18px; left: 22px; margin: 0; color: #f1c9ba; text-align: center; font-size: 13px; }
@media (max-width: 700px) {
  .image-viewer-bar { align-items: flex-start; flex-direction: column; gap: 6px; padding: 8px 12px; }
  .image-viewer-identity strong { max-width: 72vw; }
  .image-viewer-stage { top: 92px; }
}
</style>
