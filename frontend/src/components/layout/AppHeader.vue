<template>
  <header class="bg-gradient-to-r from-primary to-primary-dark text-white shadow-lg">
    <div class="container mx-auto px-3 py-2 md:px-4 md:py-3">
      <div class="flex items-center justify-between">
        <!-- Logo & Title -->
        <div class="flex items-center space-x-2 md:space-x-3">
          <div class="text-xl md:text-2xl font-bold">📊</div>
          <div>
            <h1 class="text-sm md:text-xl font-bold">선물/옵션 모니터</h1>
            <p class="text-xs text-primary-light hidden md:block">{{ currentTime }}</p>
          </div>
        </div>

        <!-- Status Indicators & Actions -->
        <div class="flex items-center space-x-2 md:space-x-6">
          <!-- Navigation (Desktop only) -->
          <nav class="hidden md:flex items-center space-x-2">
            <RouterLink to="/" class="px-3 py-1 text-sm rounded hover:bg-white/20 transition-colors" :class="{ 'bg-white/30': $route.path === '/' }">대시보드</RouterLink>
            <RouterLink to="/settings" class="px-3 py-1 text-sm rounded hover:bg-white/20 transition-colors" :class="{ 'bg-white/30': $route.path === '/settings' }">설정</RouterLink>
            <RouterLink to="/about" class="px-3 py-1 text-sm rounded hover:bg-white/20 transition-colors" :class="{ 'bg-white/30': $route.path === '/about' }">정보</RouterLink>
          </nav>

          <!-- Dark Mode Toggle (Desktop only) -->
          <button @click="toggleTheme" class="hidden md:block p-2 rounded-lg hover:bg-white/20 transition-colors" title="다크 모드 전환">
            <span v-if="isDark">🌙</span>
            <span v-else>☀️</span>
          </button>

          <!-- WebSocket Status -->
          <div class="flex items-center space-x-1 md:space-x-2">
            <div 
              class="w-2 h-2 rounded-full animate-pulse"
              :class="isConnected ? 'bg-green-400' : 'bg-red-400'"
            ></div>
            <span class="text-xs md:text-sm hidden sm:inline">
              {{ wsStatusText }}
            </span>
          </div>

          <!-- Last Update (Desktop only) -->
          <div class="hidden lg:block text-sm">
            <span class="opacity-75">업데이트:</span>
            <span class="font-semibold ml-1">{{ lastUpdateText }}</span>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useWebSocketStore } from '@/stores/websocket'
import { useMarketStore } from '@/stores/market'
import { useTheme } from '@/composables/useTheme'

const wsStore = useWebSocketStore()
const marketStore = useMarketStore()
const { isDark, toggleTheme } = useTheme()

// Current time
const currentTime = ref('')
let timeInterval: number

function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// Computed
const isConnected = computed(() => wsStore.isConnected)
const wsStatusText = computed(() => wsStore.statusText)

// const dataSourceText = computed(() => {
//   return marketStore.overview?.dataSource === 'LIVE' ? '실시간' : '데모'
// })

const lastUpdateText = computed(() => {
  if (!marketStore.lastUpdate) return '-'
  const diff = Date.now() - marketStore.lastUpdate.getTime()
  if (diff < 1000) return '방금 전'
  if (diff < 60000) return `${Math.floor(diff / 1000)}초 전`
  return `${Math.floor(diff / 60000)}분 전`
})

// Lifecycle
onMounted(() => {
  updateTime()
  timeInterval = window.setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timeInterval) {
    clearInterval(timeInterval)
  }
})
</script>
