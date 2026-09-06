<script setup lang="ts">
import { ref } from 'vue'
import { checkConnection } from '../api/health'

const checking = ref(false)
const message = ref('')

async function verify() {
  checking.value = true
  message.value = '正在检查…'
  try {
    await checkConnection()
    message.value = '前后端通信正常。'
  } catch {
    message.value = '暂时无法连接，请确认后端已启动。'
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <div class="connection-check">
    <button type="button" :disabled="checking" @click="verify">检查基础连接</button>
    <p role="status" aria-live="polite">{{ message }}</p>
  </div>
</template>
