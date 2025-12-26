<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 transition-colors">
    <AppHeader />
    
    <main class="container mx-auto px-4 py-6">
      <div class="card">
        <h2 class="text-2xl font-bold mb-6 text-gray-800 dark:text-gray-100">⚙️ 설정</h2>
        
        <!-- Theme Settings -->
        <section class="mb-8">
          <h3 class="text-lg font-semibold mb-4 text-gray-700 dark:text-gray-200">테마 설정</h3>
          <div class="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
            <div>
              <p class="font-medium text-gray-800 dark:text-gray-100">다크 모드</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">어두운 테마로 전환합니다</p>
            </div>
            <button
              @click="toggleTheme"
              class="relative inline-flex h-8 w-14 items-center rounded-full transition-colors"
              :class="isDark ? 'bg-primary' : 'bg-gray-300'"
            >
              <span
                class="inline-block h-6 w-6 transform rounded-full bg-white transition-transform"
                :class="isDark ? 'translate-x-7' : 'translate-x-1'"
              />
            </button>
          </div>
        </section>

        <!-- WebSocket Settings -->
        <section class="mb-8">
          <h3 class="text-lg font-semibold mb-4 text-gray-700 dark:text-gray-200">연결 설정</h3>
          <div class="space-y-4">
            <div class="p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
              <div class="flex items-center justify-between">
                <div>
                  <p class="font-medium text-gray-800 dark:text-gray-100">WebSocket 상태</p>
                  <p class="text-sm" :class="wsStore.statusColor">
                    {{ wsStore.statusText }}
                  </p>
                </div>
                <button
                  @click="reconnectWS"
                  class="btn btn-primary"
                  :disabled="wsStore.isConnected"
                >
                  재연결
                </button>
              </div>
            </div>
            
            <div class="p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
              <p class="font-medium text-gray-800 dark:text-gray-100 mb-2">재연결 시도</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                {{ wsStore.reconnectAttempts }} / 5
              </p>
            </div>
          </div>
        </section>

        <!-- Data Settings -->
        <section class="mb-8">
          <h3 class="text-lg font-semibold mb-4 text-gray-700 dark:text-gray-200">데이터 설정</h3>
          <div class="p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
            <div class="flex items-center justify-between mb-4">
              <div>
                <p class="font-medium text-gray-800 dark:text-gray-100">데이터 소스</p>
                <p class="text-sm text-gray-500 dark:text-gray-400">
                  {{ marketStore.overview?.dataSource === 'KIS' ? '실시간 KIS API' : '데모 데이터' }}
                </p>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <div>
                <p class="font-medium text-gray-800 dark:text-gray-100">마지막 업데이트</p>
                <p class="text-sm text-gray-500 dark:text-gray-400">
                  {{ lastUpdateText }}
                </p>
              </div>
              <button @click="refreshData" class="btn btn-primary">
                새로고침
              </button>
            </div>
          </div>
        </section>

        <!-- Cache Settings -->
        <section>
          <h3 class="text-lg font-semibold mb-4 text-gray-700 dark:text-gray-200">캐시 설정</h3>
          <div class="p-4 bg-gray-50 dark:bg-gray-800 rounded-lg">
            <div class="flex items-center justify-between">
              <div>
                <p class="font-medium text-gray-800 dark:text-gray-100">캐시 초기화</p>
                <p class="text-sm text-gray-500 dark:text-gray-400">모든 저장된 데이터를 삭제합니다</p>
              </div>
              <button @click="clearCache" class="btn bg-red-500 text-white hover:bg-red-600">
                초기화
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import { useTheme } from '@/composables/useTheme'
import { useWebSocketStore } from '@/stores/websocket'
import { useMarketStore } from '@/stores/market'
import { useOptionStore } from '@/stores/option'

const { isDark, toggleTheme } = useTheme()
const wsStore = useWebSocketStore()
const marketStore = useMarketStore()
const optionStore = useOptionStore()

const lastUpdateText = computed(() => {
  if (!marketStore.lastUpdate) return '없음'
  return marketStore.lastUpdate.toLocaleString('ko-KR')
})

const reconnectWS = () => {
  wsStore.reset()
  wsStore.connect()
}

const refreshData = async () => {
  await Promise.all([
    marketStore.fetchOverview(),
    optionStore.fetchChainData()
  ])
}

const clearCache = () => {
  if (confirm('정말 모든 캐시를 삭제하시겠습니까?')) {
    localStorage.clear()
    sessionStorage.clear()
    alert('캐시가 삭제되었습니다. 페이지를 새로고침합니다.')
    window.location.reload()
  }
}
</script>
