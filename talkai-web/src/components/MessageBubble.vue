<template>
  <div class="message" :class="message.role">
    <div class="message-avatar">
      {{ message.role === 'user' ? '👤' : '🤖' }}
    </div>
    <div class="message-body">
      <div class="message-content" v-text="message.content"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  message: { type: Object, required: true },
})
</script>
