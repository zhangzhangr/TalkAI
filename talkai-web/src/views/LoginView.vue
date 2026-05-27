<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-logo">TalkAI</h1>
      <p class="auth-subtitle">登录你的账号</p>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <input
            v-model="form.username"
            type="text"
            placeholder="用户名"
            class="form-input"
            autocomplete="username"
            required
          />
        </div>
        <div class="form-group">
          <input
            v-model="form.password"
            type="password"
            placeholder="密码"
            class="form-input"
            autocomplete="current-password"
            required
          />
        </div>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
      <p class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
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

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  if (!form.username || !form.password) return
  loading.value = true
  error.value = ''
  try {
    await auth.login(form.username, form.password)
    router.replace('/chat')
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>
