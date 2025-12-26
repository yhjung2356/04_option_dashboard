<template>
  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
    <!-- Card 1: Futures Volume -->
    <div class="card hover:shadow-xl transition-shadow duration-300 animate-fade-in">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm text-gray-500 mb-1">선물 거래량</p>
          <p class="text-2xl font-bold text-primary">
            {{ formatNumber(futuresVolume) }}
          </p>
        </div>
        <div class="text-4xl opacity-20">📈</div>
      </div>
    </div>

    <!-- Card 2: Options Volume -->
    <div class="card hover:shadow-xl transition-shadow duration-300 animate-fade-in" style="animation-delay: 0.1s">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm text-gray-500 mb-1">옵션 거래량</p>
          <p class="text-2xl font-bold text-primary">
            {{ formatNumber(optionsVolume) }}
          </p>
          <p class="text-xs text-gray-400 mt-1">
            Call {{ formatNumber(callVolume) }} / Put {{ formatNumber(putVolume) }}
          </p>
        </div>
        <div class="text-4xl opacity-20">🎯</div>
      </div>
    </div>

    <!-- Card 3: P/C Ratio -->
    <div class="card hover:shadow-xl transition-shadow duration-300 animate-fade-in" style="animation-delay: 0.2s">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm text-gray-500 mb-1">P/C 거래량 비율</p>
          <p class="text-2xl font-bold" :class="pcRatioColor">
            {{ pcRatio.toFixed(2) }}
          </p>
          <p class="text-xs text-gray-400 mt-1">
            OI: {{ pcOIRatio.toFixed(2) }}
          </p>
        </div>
        <div class="text-4xl opacity-20">⚖️</div>
      </div>
    </div>

    <!-- Card 4: Market Sentiment -->
    <div class="card hover:shadow-xl transition-shadow duration-300 animate-fade-in" style="animation-delay: 0.3s">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm text-gray-500 mb-1">시장 심리</p>
          <p class="text-2xl font-bold" :class="sentimentColor">
            {{ sentimentText }}
          </p>
          <p class="text-xs text-gray-400 mt-1">
            점수: {{ sentimentScore }}
          </p>
        </div>
        <div class="text-4xl opacity-20">{{ sentimentIcon }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMarketStore } from '@/stores/market'

const marketStore = useMarketStore()

// Data
const futuresVolume = computed(() => marketStore.overview?.totalFuturesVolume ?? 0)
const callVolume = computed(() => marketStore.overview?.putCallRatio?.callVolume ?? 0)
const putVolume = computed(() => marketStore.overview?.putCallRatio?.putVolume ?? 0)
const optionsVolume = computed(() => marketStore.overview?.totalOptionsVolume ?? 0)

const pcRatio = computed(() => marketStore.overview?.putCallRatio?.volumeRatio ?? 0)
const pcOIRatio = computed(() => marketStore.overview?.putCallRatio?.openInterestRatio ?? 0)

const sentiment = computed(() => marketStore.overview?.marketSentiment ?? 'NEUTRAL')
const sentimentScore = computed(() => marketStore.overview?.sentimentScore ?? 0)

// Computed styles
const pcRatioColor = computed(() => {
  const ratio = pcRatio.value
  if (ratio > 1.2) return 'text-red-600'
  if (ratio < 0.8) return 'text-green-600'
  return 'text-gray-700'
})

const sentimentColor = computed(() => marketStore.sentimentColor)
const sentimentText = computed(() => marketStore.sentimentText)

const sentimentIcon = computed(() => {
  const icons: Record<string, string> = {
    BULLISH: '🐂',
    BEARISH: '🐻',
    NEUTRAL: '➡️',
    VOLATILE: '⚡'
  }
  return icons[sentiment.value] || '➡️'
})

// Utilities
function formatNumber(num: number): string {
  return num.toLocaleString('ko-KR')
}
</script>
