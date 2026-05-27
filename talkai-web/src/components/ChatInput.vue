<template>
  <div class="chat-input-area">
    <div class="chat-input-row">
      <textarea
        v-model="input"
        class="chat-input"
        placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
        rows="1"
        :disabled="chat.streaming"
        @keydown="handleKeydown"
        @input="autoResize"
        ref="inputEl"
      ></textarea>
      <button
        class="btn btn-send"
        :disabled="!input.trim() || chat.streaming"
        @click="handleSend"
      >
        {{ chat.streaming ? '...' : '发送' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useChatStore } from '../stores/chat'

const chat = useChatStore()
const input = ref('')
const inputEl = ref(null)

function handleSend() {
  const content = input.value.trim()
  if (!content || chat.streaming) return
  input.value = ''
  nextTick(() => autoResize())
  chat.sendMessage(content)
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function autoResize() {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
}
</script>
