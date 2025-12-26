<template>
  <div class="card h-[calc(100vh-8rem)] flex flex-col">
    <h3 class="text-lg font-semibold mb-4 flex items-center justify-between flex-shrink-0">
      <span class="flex items-center">
        <span class="mr-2">📊</span>
        옵션 체인
      </span>
      <span class="text-sm font-normal text-gray-500 dark:text-gray-400">
        {{ strikeChain.length }}개 표시 / 전체 {{ allStrikes.length }}개
      </span>
    </h3>

    <div v-if="isLoading" class="text-center py-8 flex-1 flex items-center justify-center">
      <div>
        <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
        <p class="mt-2 text-gray-500 dark:text-gray-400">데이터 로딩 중...</p>
      </div>
    </div>

    <div v-else-if="strikeChain.length === 0" class="text-center py-8 flex-1 flex items-center justify-center text-gray-400 dark:text-gray-500">
      데이터가 없습니다
    </div>

    <div v-else class="flex-1 overflow-auto">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 dark:bg-gray-800 sticky top-0 z-10">
          <tr>
            <th colspan="5" class="px-3 py-2 text-center border-r-2 border-gray-300 dark:border-gray-600 bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400 font-semibold">
              CALL
            </th>
            <th class="px-3 py-2 text-center bg-orange-50 dark:bg-orange-900/20 text-orange-700 dark:text-orange-400 font-semibold">
              행사가
            </th>
            <th colspan="5" class="px-3 py-2 text-center border-l-2 border-gray-300 dark:border-gray-600 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400 font-semibold">
              PUT
            </th>
          </tr>
          <tr class="text-xs text-gray-600 dark:text-gray-400">
            <!-- Call Headers -->
            <th class="px-2 py-1 text-right">거래량</th>
            <th class="px-2 py-1 text-right">미결제</th>
            <th class="px-2 py-1 text-right">현재가</th>
            <th class="px-2 py-1 text-right">IV%</th>
            <th class="px-2 py-1 text-right border-r-2 border-gray-300 dark:border-gray-600">Delta</th>
            
            <!-- Strike -->
            <th class="px-3 py-1 text-center bg-orange-50 dark:bg-orange-900/20">Strike</th>
            
            <!-- Put Headers -->
            <th class="px-2 py-1 text-left border-l-2 border-gray-300 dark:border-gray-600">Delta</th>
            <th class="px-2 py-1 text-left">IV%</th>
            <th class="px-2 py-1 text-left">현재가</th>
            <th class="px-2 py-1 text-left">미결제</th>
            <th class="px-2 py-1 text-left">거래량</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in strikeChain"
            :key="row.strikePrice"
            class="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
            :class="{
              'bg-yellow-50 dark:bg-yellow-900/10': row.strikePrice === atmStrike,
              'bg-orange-50 dark:bg-orange-900/10': row.strikePrice === maxPain
            }"
          >
            <!-- Call Data -->
            <td class="px-2 py-2 text-right text-xs">{{ formatVolume(row.callVolume) }}</td>
            <td class="px-2 py-2 text-right text-xs">{{ formatVolume(row.callOpenInterest) }}</td>
            <td class="px-2 py-2 text-right text-xs font-semibold text-green-600 dark:text-green-400">
              {{ formatPrice(row.callPrice) }}
            </td>
            <td class="px-2 py-2 text-right text-xs">{{ formatIV(row.callImpliedVolatility) }}</td>
            <td class="px-2 py-2 text-right text-xs border-r-2 border-gray-300 dark:border-gray-600">
              {{ formatDelta(row.callDelta) }}
            </td>
            
            <!-- Strike Price -->
            <td class="px-3 py-2 text-center font-bold text-orange-700 dark:text-orange-400 bg-orange-50 dark:bg-orange-900/20">
              {{ formatNumber(row.strikePrice) }}
              <span v-if="row.strikePrice === atmStrike" class="ml-1 text-xs">🎯</span>
              <span v-if="row.strikePrice === maxPain" class="ml-1 text-xs">⚡</span>
            </td>
            
            <!-- Put Data -->
            <td class="px-2 py-2 text-left text-xs border-l-2 border-gray-300 dark:border-gray-600">
              {{ formatDelta(row.putDelta) }}
            </td>
            <td class="px-2 py-2 text-left text-xs">{{ formatIV(row.putImpliedVolatility) }}</td>
            <td class="px-2 py-2 text-left text-xs font-semibold text-red-600 dark:text-red-400">
              {{ formatPrice(row.putPrice) }}
            </td>
            <td class="px-2 py-2 text-left text-xs">{{ formatVolume(row.putOpenInterest) }}</td>
            <td class="px-2 py-2 text-left text-xs">{{ formatVolume(row.putVolume) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Summary Footer -->
    <div class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700 grid grid-cols-3 gap-4 text-sm flex-shrink-0">
      <div class="text-center">
        <p class="text-gray-500 dark:text-gray-400 mb-1">Call 미결제 합계</p>
        <p class="font-semibold text-green-600 dark:text-green-400">{{ formatVolume(totalCallOI) }}</p>
      </div>
      <div class="text-center">
        <p class="text-gray-500 dark:text-gray-400 mb-1">기초자산 가격</p>
        <p class="font-semibold text-primary-600 dark:text-primary-400">{{ formatNumber(underlyingPrice) }}</p>
      </div>
      <div class="text-center">
        <p class="text-gray-500 dark:text-gray-400 mb-1">Put 미결제 합계</p>
        <p class="font-semibold text-red-600 dark:text-red-400">{{ formatVolume(totalPutOI) }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useOptionStore } from '@/stores/option'

const optionStore = useOptionStore()

// Data - ATM 주변 15개만 표시 (±7개)
const allStrikes = computed(() => optionStore.strikeChain)
const strikeChain = computed(() => {
  const atm = atmStrike.value
  if (!atm || allStrikes.value.length === 0) return allStrikes.value

  // ATM 행사가의 인덱스 찾기
  const atmIndex = allStrikes.value.findIndex(row => row.strikePrice === atm)
  if (atmIndex === -1) return allStrikes.value.slice(0, 15) // ATM 없으면 상위 15개

  // ATM 기준 위아래 7개씩 (총 15개)
  const startIdx = Math.max(0, atmIndex - 7)
  const endIdx = Math.min(allStrikes.value.length, atmIndex + 8) // +8 = ATM 포함 8개
  
  return allStrikes.value.slice(startIdx, endIdx)
})

const isLoading = computed(() => optionStore.isLoading)
const atmStrike = computed(() => optionStore.atmStrike)
const maxPain = computed(() => optionStore.maxPain)
const underlyingPrice = computed(() => optionStore.underlyingPrice)
const totalCallOI = computed(() => optionStore.totalCallOI)
const totalPutOI = computed(() => optionStore.totalPutOI)

// Formatters
function formatNumber(value: number): string {
  return value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })
}

function formatPrice(value: number | undefined): string {
  if (value === undefined || value === null) return '-'
  return value.toFixed(2)
}

function formatVolume(value: number | undefined): string {
  if (value === undefined || value === null || value === 0) return '-'
  return value.toLocaleString('ko-KR')
}

function formatIV(value: number | undefined): string {
  if (value === undefined || value === null) return '-'
  return value.toFixed(1)
}

function formatDelta(value: number | undefined): string {
  if (value === undefined || value === null) return '-'
  return value.toFixed(3)
}
</script>

<style scoped>
.text-call {
  @apply text-green-600 dark:text-green-400;
}

.text-put {
  @apply text-red-600 dark:text-red-400;
}

.text-strike {
  @apply text-orange-600 dark:text-orange-400;
}

/* Scrollbar styling */
.overflow-x-auto::-webkit-scrollbar {
  height: 6px;
}

.overflow-x-auto::-webkit-scrollbar-track {
  @apply bg-gray-100 dark:bg-gray-800;
}

.overflow-x-auto::-webkit-scrollbar-thumb {
  @apply bg-gray-300 dark:bg-gray-600 rounded;
}

.overflow-x-auto::-webkit-scrollbar-thumb:hover {
  @apply bg-gray-400 dark:bg-gray-500;
}
</style>
