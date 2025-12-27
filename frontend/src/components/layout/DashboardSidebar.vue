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

    <div class="p-4 space-y-3">
      <!-- Market Status -->
      <section class="space-y-1.5">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <span>📊</span>
          <span>시장 개요</span>
        </h3>
        <div class="p-3 bg-gradient-to-br rounded-lg"
          :class="marketStatusBgClass">
          <div class="text-xs text-gray-600 dark:text-gray-400 mb-1">시장 상태</div>
          <div class="flex items-center gap-2">
            <div class="w-3 h-3 rounded-full" :class="marketStatusDotClass"></div>
            <div class="text-lg font-bold" :class="marketStatusTextClass">
              {{ marketStatusText }}
            </div>
          </div>
        </div>
      </section>

      <!-- Overview Cards (Compact) -->
      <section class="space-y-1.5">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <span>📈</span>
          <span>거래 현황</span>
        </h3>
        <div class="grid grid-cols-1 gap-1.5">
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
            <div class="text-xs text-gray-600 dark:text-gray-400 mb-1 flex items-center">
              P/C 거래량 비율
              <HelpTooltip 
                title="P/C 비율이란?"
                description="Put/Call 거래량 비율. 1.0 이상은 약세, 미만은 강세를 의미합니다."
                position="bottom"
              />
            </div>
            <div class="text-lg font-bold text-green-600 dark:text-green-400">
              {{ (marketStore.overview?.putCallRatio?.volumeRatio || 0).toFixed(2) }}
            </div>
          </div>
        </div>
      </section>

      <!-- Greeks Summary -->
      <section class="space-y-1.5">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <span>📐</span>
          <span>Greeks 요약</span>
          <HelpTooltip 
            title="Greeks란?"
            description="옵션 가격 위험 지표. Delta(가격), Gamma(델타 변화), IV(변동성)."
            position="bottom"
          />
        </h3>
        <div class="grid grid-cols-3 gap-1.5">
          <div class="p-2 bg-gradient-to-br from-blue-50 to-blue-100 dark:from-blue-900/20 dark:to-blue-800/20 rounded-lg">
            <div class="text-xs text-gray-600 dark:text-gray-400 mb-0.5 flex items-center justify-center">
              Delta
              <HelpTooltip 
                title="Delta"
                description="기초자산 1포인트 변화 시 옵션 가격 변화량. ATM은 약 0.5입니다."
                position="bottom"
              />
            </div>
            <div class="text-base font-bold text-blue-600 dark:text-blue-400">
              {{ optionStore.greeksSummary.avgDelta.toFixed(3) }}
            </div>
          </div>
          <div class="p-2 bg-gradient-to-br from-purple-50 to-purple-100 dark:from-purple-900/20 dark:to-purple-800/20 rounded-lg">
            <div class="text-xs text-gray-600 dark:text-gray-400 mb-0.5 flex items-center justify-center">
              Gamma
              <HelpTooltip 
                title="Gamma"
                description="Delta의 변화율. 기초자산 가격 변화 시 Delta 변화량을 나타냅니다."
                position="bottom"
              />
            </div>
            <div class="text-base font-bold text-purple-600 dark:text-purple-400">
              {{ optionStore.greeksSummary.avgGamma.toFixed(3) }}
            </div>
          </div>
          <div class="p-2 bg-gradient-to-br from-pink-50 to-pink-100 dark:from-pink-900/20 dark:to-pink-800/20 rounded-lg">
            <div class="text-xs text-gray-600 dark:text-gray-400 mb-0.5 flex items-center justify-center">
              IV
              <HelpTooltip 
                title="내재 변동성 (IV)"
                description="시장이 예상하는 변동성. 높을수록 옵션 가격이 비쌉니다."
                position="bottom"
              />
            </div>
            <div class="text-base font-bold text-pink-600 dark:text-pink-400">
              {{ optionStore.greeksSummary.avgIV.toFixed(1) }}%
            </div>
          </div>
        </div>
      </section>

      <!-- Sentiment Gauge -->
      <section class="space-y-1.5">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <span>🎯</span>
          <span>시장 심리</span>
          <HelpTooltip 
            title="시장 심리란?"
            description="P/C 비율 기반 강세/약세 판단. 0.7 미만은 강세, 1.5 이상은 약세입니다."
            position="bottom"
          />
        </h3>
        <div class="p-3 bg-gradient-to-br from-indigo-50 to-indigo-100 dark:from-indigo-900/20 dark:to-indigo-800/20 rounded-lg text-center">
          <div class="text-xs text-gray-600 dark:text-gray-400 mb-1">P/C 거래량 비율 기반</div>
          <div class="text-2xl font-bold mb-1" :class="marketStore.sentimentColor">
            {{ marketStore.putCallRatio.toFixed(2) }}
          </div>
          <div class="text-sm font-bold" :class="marketStore.sentimentColor">
            {{ marketStore.sentimentText }}
          </div>
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
import { ref, computed } from 'vue'
import { useMarketStore } from '@/stores/market'
import { useOptionStore } from '@/stores/option'
import HelpTooltip from '@/components/common/HelpTooltip.vue'

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
  return value.toLocaleString('ko-KR')
}

// Market status computed properties
const marketStatusText = computed(() => {
  return marketStore.overview?.marketStatus?.fullText || '알 수 없음'
})

const isMarketOpen = computed(() => {
  return marketStore.overview?.marketStatus?.isOpen || false
})

const marketStatusBgClass = computed(() => {
  if (isMarketOpen.value) {
    return 'from-green-50 to-green-100 dark:from-green-900/20 dark:to-green-800/20'
  }
  return 'from-gray-50 to-gray-100 dark:from-gray-800/50 dark:to-gray-700/50'
})

const marketStatusDotClass = computed(() => {
  if (isMarketOpen.value) {
    return 'bg-green-500 dark:bg-green-400 animate-pulse'
  }
  return 'bg-gray-400 dark:bg-gray-500'
})

const marketStatusTextClass = computed(() => {
  if (isMarketOpen.value) {
    return 'text-green-600 dark:text-green-400'
  }
  return 'text-gray-600 dark:text-gray-400'
})

// Export for parent component
defineExpose({
  openSidebar,
  closeSidebar
})
</script>
