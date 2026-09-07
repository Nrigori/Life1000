<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { formatSlot } from '../goals/slots'
defineProps<{ slot: number }>()
const emit = defineEmits<{ expired: [] }>()
let timer: ReturnType<typeof setTimeout>
onMounted(() => { timer = setTimeout(() => emit('expired'), 4000) })
onBeforeUnmount(() => clearTimeout(timer))
</script>
<template><p class="completion-feedback" role="status">第{{ formatSlot(slot) }}件，已经成为回忆。</p></template>
<style>
.completion-feedback { position: fixed; z-index: 10; bottom: 28px; left: 50%; transform: translateX(-50%); margin: 0; padding: 16px 24px; color: #70593e; background: #f8f2e7; border: 1px solid #cebea6; border-radius: 4px; box-shadow: 0 3px 12px #3e342b0c; font-size: 14px; white-space: nowrap; }
</style>
