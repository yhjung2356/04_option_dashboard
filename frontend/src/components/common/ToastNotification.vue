<script setup lang="ts">
import { ref, watch } from 'vue'
import { useWebSocketStore } from '@/stores/websocket'

const wsStore = useWebSocketStore()
const notifications = ref<Array<{ id: number; message: string; type: 'success' | 'error' | 'info' | 'warning' }>>([])
let notificationId = 0

// WebSocket 상태 변화 감지
watch(() => wsStore.connectionStatus, (newStatus, oldStatus) => {
  if (oldStatus && oldStatus !== newStatus) {
    if (newStatus === 'connected') {
      showNotification('WebSocket 연결 성공', 'success')
    } else if (newStatus === 'error') {
      showNotification('WebSocket 연결 오류가 발생했습니다', 'error')
    } else if (newStatus === 'disconnected' && oldStatus === 'connected') {
      showNotification('WebSocket 연결이 끊어졌습니다', 'warning')
    } else if (newStatus === 'connecting') {
      if (wsStore.reconnectAttempts > 0) {
        showNotification(`재연결 시도 중... (${wsStore.reconnectAttempts}회)`, 'info')
      }
    }
  }
})

function showNotification(message: string, type: 'success' | 'error' | 'info' | 'warning' = 'info') {
  const id = notificationId++
  notifications.value.push({ id, message, type })
  
  // 5초 후 자동 제거
  setTimeout(() => {
    removeNotification(id)
  }, 5000)
}

function removeNotification(id: number) {
  const index = notifications.value.findIndex(n => n.id === id)
  if (index !== -1) {
    notifications.value.splice(index, 1)
  }
}

// 전역으로 사용할 수 있도록 provide
defineExpose({ showNotification })
</script>

<template>
  <!-- Toast Container -->
  <Teleport to="body">
    <div class="fixed top-20 right-4 z-[9999] space-y-2 w-80 max-w-[calc(100vw-2rem)]">
      <TransitionGroup name="toast">
        <div
          v-for="notification in notifications"
          :key="notification.id"
          class="flex items-center gap-3 p-4 rounded-lg shadow-lg backdrop-blur-sm"
          :class="{
            'bg-green-500/90 text-white': notification.type === 'success',
            'bg-red-500/90 text-white': notification.type === 'error',
            'bg-blue-500/90 text-white': notification.type === 'info',
            'bg-yellow-500/90 text-white': notification.type === 'warning'
          }"
        >
          <!-- Icon -->
          <div class="text-2xl flex-shrink-0">
            <span v-if="notification.type === 'success'">✓</span>
            <span v-else-if="notification.type === 'error'">✕</span>
            <span v-else-if="notification.type === 'info'">ℹ</span>
            <span v-else-if="notification.type === 'warning'">⚠</span>
          </div>
          
          <!-- Message -->
          <div class="flex-1 text-sm font-medium">
            {{ notification.message }}
          </div>
          
          <!-- Close Button -->
          <button
            @click="removeNotification(notification.id)"
            class="flex-shrink-0 text-white/80 hover:text-white transition-colors"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active {
  transition: all 0.3s ease-out;
}

.toast-leave-active {
  transition: all 0.2s ease-in;
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100%);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(100%);
}
</style>
