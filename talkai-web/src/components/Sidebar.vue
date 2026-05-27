<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <h2 class="sidebar-logo">TalkAI</h2>
      <button class="btn btn-new-chat" @click="handleNewChat">+ 新对话</button>
    </div>

    <div class="sidebar-list">
      <div
        v-for="conv in chat.conversations"
        :key="conv.id"
        class="sidebar-item"
        :class="{ active: chat.currentConversation?.id === conv.id }"
        @click="handleSelect(conv.id)"
      >
        <span class="sidebar-item-title">{{ conv.title }}</span>
        <button
          class="sidebar-item-delete"
          title="删除"
          @click.stop="handleDelete(conv.id)"
        >&times;</button>
      </div>
      <p v-if="chat.conversations.length === 0" class="sidebar-empty">
        暂无对话
      </p>
    </div>

    <div class="sidebar-footer">
      <span class="sidebar-user">{{ auth.user?.nickname || auth.user?.username }}</span>
      <button class="btn btn-logout" @click="handleLogout">退出</button>
    </div>
  </aside>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'

const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()

onMounted(() => {
  chat.loadConversations()
})

async function handleNewChat() {
  chat.createConversation()
}

async function handleSelect(id) {
  await chat.loadConversation(id)
}

async function handleDelete(id) {
  if (confirm('确定删除该对话？')) {
    await chat.deleteConversation(id)
  }
}

function handleLogout() {
  auth.logout()
  chat.createConversation()
  router.replace('/login')
}
</script>
