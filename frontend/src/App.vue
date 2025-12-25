<template>
  <div id="app" class="min-h-screen bg-gray-50">
    <RouterView />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useWebSocketStore } from '@/stores/websocket'
import { useTheme } from '@/composables/useTheme'

const wsStore = useWebSocketStore()
const { initTheme } = useTheme()

onMounted(() => {
  // Initialize theme
  initTheme()
  
  // Connect WebSocket on app mount
  wsStore.connect()
})

onUnmounted(() => {
  // Disconnect WebSocket on app unmount
  wsStore.disconnect()
})
</script>
