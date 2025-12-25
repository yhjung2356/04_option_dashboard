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
          <!-- Option Chain & Top Traded -->
          <div class="grid grid-cols-1 xl:grid-cols-4 gap-6">
            <!-- Option Chain (3/4 width) -->
            <section class="xl:col-span-3">
              <OptionChainTable />
            </section>

            <!-- Top Traded (1/4 width) -->
            <section class="xl:col-span-1">
              <TopTradedTable />
            </section>
          </div>
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
import TopTradedTable from '@/components/dashboard/TopTradedTable.vue'
import { useMarketStore } from '@/stores/market'
import { useOptionStore } from '@/stores/option'

const marketStore = useMarketStore()
const optionStore = useOptionStore()
const sidebarRef = ref()

const toggleSidebar = () => {
  if (sidebarRef.value) {
    sidebarRef.value.openSidebar()
  }
}

onMounted(async () => {
  // Initial data fetch
  await Promise.all([
    marketStore.fetchOverview(),
    optionStore.fetchChainData()
  ])
})
</script>
