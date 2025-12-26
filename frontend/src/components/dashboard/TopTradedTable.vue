<template>
  <div class="card h-[calc(100vh-8rem)] flex flex-col overflow-hidden">
    <h3 class="text-lg font-semibold mb-4 flex items-center flex-shrink-0">
      <span class="mr-2">🏆</span>
      거래량 상위 종목
    </h3>

    <div v-if="!topByVolume || topByVolume.length === 0" class="text-center py-8 text-gray-400 dark:text-gray-500">
      데이터가 없습니다
    </div>

    <div v-else class="space-y-2 mb-6 flex-shrink-0">
      <div
        v-for="(item, index) in topByVolume"
        :key="item.symbol"
        class="flex items-center p-2 bg-gray-50 dark:bg-gray-800 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
      >
        <!-- Rank -->
        <div class="flex-shrink-0 w-6 h-6 flex items-center justify-center rounded-full font-bold text-xs"
             :class="getRankClass(index)">
          {{ index + 1 }}
        </div>

        <!-- Name & Type -->
        <div class="ml-2 flex-grow min-w-0">
          <p class="font-semibold text-sm text-gray-800 dark:text-gray-200 truncate">{{ formatItemName(item) }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ getOptionType(item) }}</p>
        </div>

        <!-- Volume & Price -->
        <div class="text-right ml-2">
          <p class="font-semibold text-sm text-primary-600 dark:text-primary-400">{{ formatVolume(item.volume) }}</p>
          <p class="text-xs" :class="getPriceChangeClass(item.changePercent)">
            {{ formatPrice(item.currentPrice) }}
          </p>
        </div>
      </div>
    </div>

    <!-- Divider -->
    <div class="my-4 border-t border-gray-200 dark:border-gray-700 flex-shrink-0"></div>

    <h3 class="text-lg font-semibold mb-4 flex items-center flex-shrink-0">
      <span class="mr-2">📌</span>
      미결제약정 상위 종목
    </h3>

    <div v-if="!topByOI || topByOI.length === 0" class="text-center py-8 text-gray-400 dark:text-gray-500">
      데이터가 없습니다
    </div>

    <div v-else class="space-y-2 flex-1 overflow-y-auto">
      <div
        v-for="(item, index) in topByOI"
        :key="item.symbol"
        class="flex items-center p-2 bg-gray-50 dark:bg-gray-800 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
      >
        <!-- Rank -->
        <div class="flex-shrink-0 w-6 h-6 flex items-center justify-center rounded-full font-bold text-xs"
             :class="getRankClass(index)">
          {{ index + 1 }}
        </div>

        <!-- Name & Type -->
        <div class="ml-2 flex-grow min-w-0">
          <p class="font-semibold text-sm text-gray-800 dark:text-gray-200 truncate">{{ formatItemName(item) }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ getOptionType(item) }}</p>
        </div>

        <!-- Open Interest & Price -->
        <div class="text-right ml-2">
          <p class="font-semibold text-sm text-orange-600 dark:text-orange-400">{{ formatVolume(item.openInterest) }}</p>
          <p class="text-xs" :class="getPriceChangeClass(item.changePercent)">
            {{ formatPrice(item.currentPrice) }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMarketStore } from '@/stores/market'

const marketStore = useMarketStore()

// Data
const topByVolume = computed(() => marketStore.overview?.topByVolume ?? [])
const topByOI = computed(() => marketStore.overview?.topByOpenInterest ?? [])

// Utilities
function getRankClass(index: number): string {
  if (index === 0) return 'bg-yellow-400 dark:bg-yellow-500 text-white'
  if (index === 1) return 'bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-200'
  if (index === 2) return 'bg-amber-600 dark:bg-amber-700 text-white'
  return 'bg-gray-200 dark:bg-gray-700 text-gray-600 dark:text-gray-300'
}

function getOptionType(item: any): string {
  // item이 null/undefined인 경우 처리
  if (!item) return '-'
  
  // name에서 타입 추출: "C 202601 575.0" → Call, "P 202601 560.0" → Put
  if (item.name) {
    if (item.name.startsWith('C ')) return 'Call 옵션'
    if (item.name.startsWith('P ')) return 'Put 옵션'
  }
  // type 필드 체크
  if (item.type === 'OPTIONS') return '옵션'
  if (item.type === 'FUTURES') return '선물'
  return '선물'
}

function getPriceChangeClass(changePercent: number | undefined): string {
  if (!changePercent) return 'text-gray-600 dark:text-gray-400'
  if (changePercent > 0) return 'text-green-600 dark:text-green-400'
  if (changePercent < 0) return 'text-red-600 dark:text-red-400'
  return 'text-gray-600 dark:text-gray-400'
}

function formatVolume(value: number | undefined): string {
  if (value === undefined || value === null) return '-'
  return value.toLocaleString('ko-KR')
}

function formatPrice(value: number | undefined): string {
  if (value === undefined || value === null) return '-'
  return value.toFixed(2)
}

function formatItemName(item: any): string {
  if (!item || !item.name) return item?.symbol || '-'
  
  // "C 202601 575.0" → "Call 575.0"
  // "P 202601 560.0" → "Put 560.0"
  const parts = item.name.split(' ')
  if (parts.length >= 3) {
    const type = parts[0] === 'C' ? 'Call' : parts[0] === 'P' ? 'Put' : ''
    const strike = parts[2]
    return `${type} ${strike}`
  }
  
  return item.name
}
</script>
