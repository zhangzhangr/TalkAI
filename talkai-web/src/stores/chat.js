import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as chatApi from '../api/chat'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref([])
  const currentConversation = ref(null)
  const messages = ref([])
  const streaming = ref(false)
  const streamingContent = ref('')

  async function loadConversations() {
    const res = await chatApi.getConversations()
    conversations.value = res.data
  }

  async function loadConversation(id) {
    const res = await chatApi.getConversation(id)
    currentConversation.value = res.data
    messages.value = res.data.messages || []
  }

  async function createConversation() {
    currentConversation.value = null
    messages.value = []
    streamingContent.value = ''
  }

  async function deleteConversation(id) {
    await chatApi.deleteConversation(id)
    if (currentConversation.value?.id === id) {
      createConversation()
    }
    await loadConversations()
  }

  async function updateTitle(id, title) {
    await chatApi.updateTitle(id, title)
    await loadConversations()
  }

  function sendMessage(content, model) {
    const convId = currentConversation.value?.id || null

    // add user message locally
    messages.value.push({
      id: Date.now(),
      conversationId: convId,
      role: 'user',
      content,
      createTime: new Date().toISOString(),
    })

    // add placeholder for assistant
    messages.value.push({
      id: Date.now() + 1,
      conversationId: convId,
      role: 'assistant',
      content: '',
      createTime: new Date().toISOString(),
    })
    const assistantMsg = messages.value[messages.value.length - 1]

    streaming.value = true
    streamingContent.value = ''

    let fullContent = ''

    chatApi.sendMessage(
      { conversationId: convId, content, model },
      {
        onMessage(delta) {
          fullContent += delta
          streamingContent.value = fullContent
          assistantMsg.content = fullContent
        },
        onDone() {
          streaming.value = false
          // if this is a new conversation, refresh to get the real id
          if (!convId) {
            loadConversations().then(() => {
              if (conversations.value.length > 0) {
                const latest = conversations.value[0]
                loadConversation(latest.id)
              }
            })
          }
        },
        onError(err) {
          streaming.value = false
          assistantMsg.content = assistantMsg.content || `[错误] ${err.message}`
        },
      }
    )
  }

  return {
    conversations,
    currentConversation,
    messages,
    streaming,
    streamingContent,
    loadConversations,
    loadConversation,
    createConversation,
    deleteConversation,
    updateTitle,
    sendMessage,
  }
})
