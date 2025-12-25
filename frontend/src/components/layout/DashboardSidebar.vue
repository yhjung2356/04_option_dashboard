<template>
  <aside 
    class="fixed lg:sticky top-0 left-0 h-screen lg:h-[calc(100vh-4rem)] w-80 bg-white dark:bg-gray-900 shadow-lg overflow-y-auto z-40 transition-transform duration-300"
    :class="{ '-translate-x-full lg:translate-x-0': !isOpen }"
  >
    <!-- Close Button (Mobile only) -->
    <button
      @click="closeSidebar"
      class="lg:hidden absolute top-4 right-4 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
    >
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
      </svg>
    </button>

    <div class="p-4 space-y-4">
      <!-- Overview Cards (Compact) -->
      <section class="space-y-2">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <span>📊</span>
          <span>시장 개요</span>
        </h3>
        <div class="grid grid-cols-1 gap-2">
          <!-- Futures Volume -->
          <div class="p-3 bg-gradient-to-br from-blue-50 to-blue-100 dark:from-blue-900/20 dark:to-blue-800/20 rounded-lg">
            <div class="text-xs text-gray-600 dark:text-gray-400 mb-1">선물 거래량</div>
            <div class="text-lg font-bold text-blue-600 dark:text-blue-400">
              {{ formatNumber(marketStore.overview?.totalFuturesVolume || 0) }}
            </div>
          </div>

          <!-- Options Volume -->
          <div class="p-3 bg-gradient-to-br from-purple-50 to-purple-100 dark:from-purple-900/20 dark:to-purple-800/20 rounded-lg">
            <div class="text-xs text-gray-600 dark:text-gray-400 mb-1">옵션 거래량</div>
            <div class="text-lg font-bold text-purple-600 dark:text-purple-400 flex items-center gap-2">
              {{ formatNumber(marketStore.overview?.totalOptionsVolume || 0) }}
              <span class="text-xs text-gray-500 dark:text-gray-400">
                (C:{{ formatNumber(marketStore.overview?.putCallRatio?.callVolume || 0) }} / P:{{ formatNumber(marketStore.overview?.putCallRatio?.putVolume || 0) }})
              </span>
            </div>
          </div>

          <!-- P/C Ratio -->
          <div class="p-3 bg-gradient-to-br from-green-50 to-green-100 dark:from-green-900/20 dark:to-green-800/20 rounded-lg">
            <div class="text-xs text-gray-600 dark:text-gray-400 mb-1">P/C 거래량 비율</div>
            <div class="text-lg font-bold text-green-600 dark:text-green-400">
              {{ (marketStore.overview?.putCallRatio?.volumeRatio || 0).toFixed(2) }}
            </div>
          </div>
        </div>
      </section>

      <!-- Greeks Summary (Compact) -->
      <section class="space-y-2">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <span>📐</span>
          <span>Greeks 요약</span>
        </h3>
        <div class="grid grid-cols-2 gap-2">
          <div class="p-2 bg-gray-50 dark:bg-gray-800 rounded">
            <div class="text-xs text-gray-600 dark:text-gray-400">Delta (Δ)</div>
            <div class="font-semibold text-sm text-gray-600 dark:text-gray-400">
              {{ optionStore.greeksSummary.avgDelta.toFixed(4) }}
            </div>
          </div>
          <div class="p-2 bg-gray-50 dark:bg-gray-800 rounded">
            <div class="text-xs text-gray-600 dark:text-gray-400">Gamma (Γ)</div>
            <div class="font-semibold text-sm text-gray-600 dark:text-gray-400">
              {{ optionStore.greeksSummary.avgGamma.toFixed(4) }}
            </div>
          </div>
          <div class="p-2 bg-gray-50 dark:bg-gray-800 rounded">
            <div class="text-xs text-gray-600 dark:text-gray-400">Theta (Θ)</div>
            <div class="font-semibold text-sm text-gray-600 dark:text-gray-400">
              {{ optionStore.greeksSummary.avgTheta.toFixed(4) }}
            </div>
          </div>
          <div class="p-2 bg-gray-50 dark:bg-gray-800 rounded">
            <div class="text-xs text-gray-600 dark:text-gray-400">Vega (ν)</div>
            <div class="font-semibold text-sm text-gray-600 dark:text-gray-400">
              {{ optionStore.greeksSummary.avgVega.toFixed(4) }}
            </div>
          </div>
          <div class="p-2 bg-gray-50 dark:bg-gray-800 rounded col-span-2">
            <div class="text-xs text-gray-600 dark:text-gray-400">IV (%)</div>
            <div class="font-semibold text-sm text-gray-600 dark:text-gray-400">
              {{ optionStore.greeksSummary.avgIV.toFixed(2) }}
            </div>
          </div>
        </div>
      </section>

      <!-- Sentiment Gauge (Compact) -->
      <section class="space-y-2">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <span>🎯</span>
          <span>시장 심리</span>
        </h3>
        <div class="p-4 bg-gradient-to-br from-purple-50 to-purple-100 dark:from-purple-900/20 dark:to-purple-800/20 rounded-lg text-center">
          <div class="text-xs text-gray-600 dark:text-gray-400 mb-1">P/C 비율 기반</div>
          <div class="text-3xl font-bold mb-1" :class="marketStore.sentimentColor">
            {{ marketStore.putCallRatio.toFixed(2) }}
          </div>
          <div class="text-sm font-semibold" :class="marketStore.sentimentColor">
            {{ marketStore.sentimentText }}
          </div>
        </div>
      </section>

      <!-- Volume Chart (Compact) -->
      <section class="space-y-2">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <span>📈</span>
          <span>거래량 추이</span>
        </h3>
        <div class="h-48">
          <VolumeChart />
        </div>
      </section>
    </div>
  </aside>

  <!-- Mobile Overlay -->
  <div
    v-if="isOpen"
    @click="closeSidebar"
    class="lg:hidden fixed inset-0 bg-black/50 z-30"
  ></div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useMarketStore } from '@/stores/market'
import { useOptionStore } from '@/stores/option'
import VolumeChart from '@/components/charts/VolumeChart.vue'

const marketStore = useMarketStore()
const optionStore = useOptionStore()
const isOpen = ref(false)

const openSidebar = () => {
  isOpen.value = true
}

const closeSidebar = () => {
  isOpen.value = false
}

const formatNumber = (value: number): string => {
  if (value >= 1000000) {
    return (value / 1000000).toFixed(1) + 'M'
  } else if (value >= 1000) {
    return (value / 1000).toFixed(1) + 'K'
  }
  return value.toString()
}

const formatGreek = (value: number): string => {
  return value.toFixed(4)
}

const getGreekColor = (value: number): string => {
  if (value > 0) return 'text-green-600 dark:text-green-400'
  if (value < 0) return 'text-red-600 dark:text-red-400'
  return 'text-gray-600 dark:text-gray-400'
}

// Export for parent component
defineExpose({
  openSidebar,
  closeSidebar
})
</script>
