<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request, errorMessage } from '../api/http'
import { safeReturnPath } from '../router'
import { saveToken } from '../api/session'

const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function login() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const result = await request<{ accessToken: string }>('/auth/login', {
      method: 'POST', body: JSON.stringify({ username: username.value, password: password.value }),
    })
    saveToken(result.accessToken)
    password.value = ''
    await router.replace(safeReturnPath(route.query.redirect))
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="paper login-paper">
    <p class="eyebrow">LIFE / 1000</p>
    <h1>回到自己的空间</h1>
    <form class="entry-form" @submit.prevent="login">
      <label for="username">账号</label>
      <input id="username" v-model="username" required maxlength="200" autocomplete="username" :disabled="busy" />
      <label for="password">密码</label>
      <input id="password" v-model="password" required maxlength="1000" type="password" autocomplete="current-password" :disabled="busy" />
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="ink-button login-submit" type="submit" :disabled="busy">{{ busy ? '正在进入…' : '进入' }}</button>
    </form>
  </section>
</template>
