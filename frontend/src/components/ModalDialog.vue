<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const props = defineProps<{ title: string; busy?: boolean }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
let previousOverflow = ''

onMounted(() => {
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialog.value?.showModal()
})
onUnmounted(() => {
  dialog.value?.close()
  document.body.style.overflow = previousOverflow
})
function cancel(event: Event) {
  event.preventDefault()
  if (!props.busy) emit('close')
}
</script>

<template>
  <dialog ref="dialog" class="paper-dialog" aria-labelledby="dialog-title" @cancel="cancel">
    <h2 id="dialog-title">{{ title }}</h2>
    <slot />
  </dialog>
</template>
