<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-logo">TalkAI</h1>
      <p class="auth-subtitle">创建新账号</p>
      <form @submit.prevent="handleRegister">
        <div class="form-group">
          <input
            v-model="form.username"
            type="text"
            placeholder="用户名（3-32位）"
            class="form-input"
            autocomplete="username"
            required
          />
        </div>
        <div class="form-group">
          <input
            v-model="form.nickname"
            type="text"
            placeholder="昵称（选填）"
            class="form-input"
          />
        </div>
        <div class="form-group">
          <input
            v-model="form.password"
            type="password"
            placeholder="密码（至少6位）"
            class="form-input"
            autocomplete="new-password"
            required
          />
        </div>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>
      <p class="auth-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({ username: '', password: '', nickname: '' })
const loading = ref(false)
const error = ref('')

async function handleRegister() {
  if (!form.username || !form.password) return
  if (form.password.length < 6) {
    error.value = '密码至少6位'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await auth.register(form.username, form.password, form.nickname)
    await auth.login(form.username, form.password)
    router.replace('/chat')
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>
