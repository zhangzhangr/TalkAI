import request from './request'
import { useAuthStore } from '../stores/auth'

export function getConversations() {
  return request.get('/chat/conversations')
}

export function getConversation(id) {
  return request.get(`/chat/conversations/${id}`)
}

export function deleteConversation(id) {
  return request.delete(`/chat/conversations/${id}`)
}

export function updateTitle(id, title) {
  return request.patch(`/chat/conversations/${id}/title`, { title })
}

/**
 * Send message with SSE streaming.
 * Returns an object with:
 *   - abort(): cancel the request
 *   - onMessage(callback): called with each delta chunk
 *   - onDone(callback): called when stream completes
 *   - onError(callback): called on error
 */
export function sendMessage({ conversationId, content, model }, callbacks) {
  const auth = useAuthStore()
  const controller = new AbortController()

  fetch('/api/chat/send', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${auth.token}`,
    },
    body: JSON.stringify({ conversationId, content, model }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        const err = await response.json().catch(() => ({}))
        callbacks.onError?.(new Error(err.message || '请求失败'))
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trimStart()
            if (data.startsWith('[DONE]')) {
              callbacks.onDone?.()
              return
            }
            try {
              const parsed = JSON.parse(data)
              const content = parsed.choices?.[0]?.delta?.content || ''
              if (content) {
                callbacks.onMessage?.(content)
              }
            } catch {
              // skip unparseable chunks
            }
          }
        }
      }
      callbacks.onDone?.()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        callbacks.onError?.(err)
      }
    })

  return {
    abort: () => controller.abort(),
  }
}
