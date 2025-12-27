<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-950">
    <AppHeader />
    
    <!-- Sidebar Toggle Button (Mobile) -->
    <button
      @click="toggleSidebar"
      class="lg:hidden fixed bottom-6 right-6 z-50 w-14 h-14 bg-primary-600 text-white rounded-full shadow-lg flex items-center justify-center hover:bg-primary-700 transition-colors"
    >
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
      </svg>
    </button>
    
    <div class="flex">
      <!-- Sidebar -->
      <DashboardSidebar ref="sidebarRef" />
      
      <!-- Main Content Area -->
      <main class="flex-1 lg:ml-0 w-full overflow-x-hidden">
        <div class="container mx-auto px-4 py-6 max-w-[1600px]">
          <!-- Option Chain (전체 너비) -->
          <section>
            <OptionChainTable />
          </section>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import DashboardSidebar from '@/components/layout/DashboardSidebar.vue'
import OptionChainTable from '@/components/dashboard/OptionChainTable.vue'
import { useMarketStore } from '@/stores/market'
import { useOptionStore } from '@/stores/option'
import { useWebSocketStore } from '@/stores/websocket'

const marketStore = useMarketStore()
const optionStore = useOptionStore()
const wsStore = useWebSocketStore()
const sidebarRef = ref()

const toggleSidebar = () => {
  if (sidebarRef.value) {
    sidebarRef.value.openSidebar()
  }
}

onMounted(async () => {
  // console.log('[DashboardView] ✅ 마운트됨 - 데이터 로딩 시작')
  
  try {
    // 1. 먼저 REST API로 초기 데이터 로딩
    // console.log('[DashboardView] API 호출 시작...')
    await Promise.all([
      marketStore.fetchOverview(),
      optionStore.fetchChainData()
    ])
    // console.log('[DashboardView] ✅ API 로딩 완료')
  } catch (error) {
    console.error('[DashboardView] ❌ API 로딩 실패:', error)
  }
  
  // 2. 데이터 로딩 완료 후 WebSocket 연결
  // console.log('[DashboardView] WebSocket 연결 시작...')
  wsStore.connect()
})
</script>
