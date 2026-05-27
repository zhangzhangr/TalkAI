import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('talkai-token') || '')
  const user = ref(JSON.parse(localStorage.getItem('talkai-user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)

  function loadToken() {
    const saved = localStorage.getItem('talkai-token')
    if (saved) {
      token.value = saved
      user.value = JSON.parse(localStorage.getItem('talkai-user') || 'null')
    }
  }

  async function login(username, password) {
    const res = await authApi.login({ username, password })
    token.value = res.data.token
    user.value = {
      userId: res.data.userId,
      username: res.data.username,
      nickname: res.data.nickname,
      avatar: res.data.avatar,
    }
    localStorage.setItem('talkai-token', token.value)
    localStorage.setItem('talkai-user', JSON.stringify(user.value))
  }

  async function register(username, password, nickname) {
    await authApi.register({ username, password, nickname })
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('talkai-token')
    localStorage.removeItem('talkai-user')
  }

  return { token, user, isLoggedIn, loadToken, login, register, logout }
})
