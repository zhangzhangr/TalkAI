<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const router = useRouter()
const auth = useAuthStore()

onMounted(() => {
  auth.loadToken()
  if (!auth.isLoggedIn && router.currentRoute.value.path !== '/login'
      && router.currentRoute.value.path !== '/register') {
    router.replace('/login')
  }
})
</script>
