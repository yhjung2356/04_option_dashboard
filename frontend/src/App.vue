<template>
  <div id="app" class="min-h-screen bg-gray-50">
    <RouterView />
    <ToastNotification ref="toastRef" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, provide, ref } from 'vue'
import { useWebSocketStore } from '@/stores/websocket'
import { useTheme } from '@/composables/useTheme'
import ToastNotification from '@/components/common/ToastNotification.vue'

const wsStore = useWebSocketStore()
const { initTheme } = useTheme()
const toastRef = ref()

// Provide toast notification globally
provide('toast', toastRef)

onMounted(() => {
  // Initialize theme
  initTheme()
  
  // WebSocket은 DashboardView에서 데이터 로딩 후 연결
})

onUnmounted(() => {
  // Disconnect WebSocket on app unmount
  wsStore.disconnect()
})
</script>
