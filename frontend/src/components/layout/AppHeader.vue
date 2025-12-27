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
          <div class="flex items-center space-x-2 px-3 py-1.5 rounded-lg bg-white/10 backdrop-blur-sm">
            <!-- Status Dot with Pulse Animation -->
            <div class="relative flex items-center justify-center">
              <div 
                class="w-2.5 h-2.5 rounded-full transition-colors duration-300"
                :class="{
                  'bg-green-400': connectionStatus === 'connected',
                  'bg-yellow-400': connectionStatus === 'connecting',
                  'bg-red-400': connectionStatus === 'disconnected' || connectionStatus === 'error',
                  'bg-orange-400': connectionStatus === 'holiday'
                }"
              ></div>
              <!-- Pulse ring for connected state -->
              <div 
                v-if="connectionStatus === 'connected'"
                class="absolute w-2.5 h-2.5 rounded-full bg-green-400 animate-ping"
              ></div>
              <!-- Spinner for connecting state -->
              <div
                v-if="connectionStatus === 'connecting'"
                class="absolute w-4 h-4 border-2 border-yellow-400 border-t-transparent rounded-full animate-spin"
              ></div>
            </div>
            
            <!-- Status Text -->
            <div class="flex flex-col">
              <span class="text-xs font-medium leading-tight">
                {{ wsStatusText }}
              </span>
              <span v-if="connectionStatus === 'connecting' && reconnectAttempts > 0" class="text-[10px] opacity-75 leading-tight">
                재시도 {{ reconnectAttempts }}회
              </span>
            </div>

            <!-- Reconnect Button (on error) -->
            <button
              v-if="connectionStatus === 'error' || connectionStatus === 'disconnected'"
              @click="handleManualReconnect"
              class="ml-1 p-1 rounded hover:bg-white/20 transition-colors"
              title="수동 재연결"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          </div>

          <!-- Refresh Button -->
          <button
            @click="handleRefresh"
            :disabled="isRefreshing"
            class="hidden md:flex items-center gap-1 px-3 py-1.5 rounded-lg bg-white/10 hover:bg-white/20 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            title="데이터 새로고침 (F5)"
          >
            <svg 
              class="w-4 h-4 transition-transform"
              :class="{ 'animate-spin': isRefreshing }"
              fill="none" 
              stroke="currentColor" 
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            <span class="text-xs font-medium">새로고침</span>
          </button>

          <!-- Last Update (Desktop only) -->
          <div class="hidden lg:flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/10">
            <svg class="w-4 h-4 opacity-75" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <div class="flex flex-col">
              <span class="text-[10px] opacity-75 leading-tight">마지막 업데이트</span>
              <span class="text-xs font-semibold leading-tight">{{ lastUpdateText }}</span>
            </div>
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
import { useOptionStore } from '@/stores/option'
import { useTheme } from '@/composables/useTheme'

const wsStore = useWebSocketStore()
const marketStore = useMarketStore()
const optionStore = useOptionStore()
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
const wsStatusText = computed(() => wsStore.statusText)
const connectionStatus = computed(() => wsStore.connectionStatus)
const reconnectAttempts = computed(() => wsStore.reconnectAttempts)

// Manual reconnect
function handleManualReconnect() {
  wsStore.reset()
  wsStore.connect()
}

// Refresh data
const isRefreshing = ref(false)
async function handleRefresh() {
  if (isRefreshing.value) return
  
  isRefreshing.value = true
  try {
    // Fetch market overview and option chain
    await Promise.all([
      marketStore.fetchOverview(),
      optionStore.fetchChainData()
    ])
  } catch (error) {
    console.error('데이터 새로고침 실패:', error)
  } finally {
    setTimeout(() => {
      isRefreshing.value = false
    }, 500) // 최소 500ms 스피너 표시
  }
}

// F5 키보드 단축키
function handleKeyDown(e: KeyboardEvent) {
  if (e.key === 'F5') {
    e.preventDefault()
    handleRefresh()
  }
}

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
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  if (timeInterval) {
    clearInterval(timeInterval)
  }
  window.removeEventListener('keydown', handleKeyDown)
})
</script>
