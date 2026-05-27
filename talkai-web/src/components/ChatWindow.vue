<template>
  <main class="chat-main">
    <!-- Empty state -->
    <div v-if="!chat.currentConversation && chat.messages.length === 0" class="chat-empty">
      <h2>TalkAI</h2>
      <p>开始一段新的对话吧</p>
    </div>

    <!-- Messages -->
    <div v-else class="chat-messages" ref="messagesContainer">
      <MessageBubble
        v-for="msg in chat.messages"
        :key="msg.id"
        :message="msg"
      />
      <!-- Streaming indicator -->
      <div v-if="chat.streaming && !chat.streamingContent" class="message assistant">
        <div class="typing-indicator"><span></span><span></span><span></span></div>
      </div>
    </div>

    <!-- Input -->
    <ChatInput />
  </main>
</template>

<script setup>
import { watch, ref, nextTick } from 'vue'
import { useChatStore } from '../stores/chat'
import MessageBubble from './MessageBubble.vue'
import ChatInput from './ChatInput.vue'

const chat = useChatStore()
const messagesContainer = ref(null)

watch(
  () => chat.messages.length,
  () => nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
)

watch(
  () => chat.streamingContent,
  () => nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
)
</script>
