<template>
  <div class="card">
    <h3 class="text-lg font-semibold mb-4 flex items-center">
      <span class="mr-2">🎲</span>
      Greeks 요약
    </h3>

    <div v-if="!atmRow" class="text-center py-8 text-gray-400">
      데이터를 로딩 중입니다...
    </div>

    <div v-else class="grid grid-cols-2 md:grid-cols-5 gap-4">
      <!-- Delta -->
      <div class="text-center p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
        <p class="text-xs text-gray-500 mb-1">Delta (Δ)</p>
        <div class="space-y-1">
          <p class="text-sm font-semibold text-call">
            Call: {{ formatGreek(atmRow.callDelta) }}
          </p>
          <p class="text-sm font-semibold text-put">
            Put: {{ formatGreek(atmRow.putDelta) }}
          </p>
        </div>
        <p class="text-xs text-gray-400 mt-1">가격 민감도</p>
      </div>

      <!-- Gamma -->
      <div class="text-center p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
        <p class="text-xs text-gray-500 mb-1">Gamma (Γ)</p>
        <div class="space-y-1">
          <p class="text-sm font-semibold text-call">
            Call: {{ formatGreek(atmRow.callGamma) }}
          </p>
          <p class="text-sm font-semibold text-put">
            Put: {{ formatGreek(atmRow.putGamma) }}
          </p>
        </div>
        <p class="text-xs text-gray-400 mt-1">델타 변화율</p>
      </div>

      <!-- Theta -->
      <div class="text-center p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
        <p class="text-xs text-gray-500 mb-1">Theta (Θ)</p>
        <div class="space-y-1">
          <p class="text-sm font-semibold text-call">
            Call: {{ formatGreek(atmRow.callTheta) }}
          </p>
          <p class="text-sm font-semibold text-put">
            Put: {{ formatGreek(atmRow.putTheta) }}
          </p>
        </div>
        <p class="text-xs text-gray-400 mt-1">시간 가치 손실</p>
      </div>

      <!-- Vega -->
      <div class="text-center p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
        <p class="text-xs text-gray-500 mb-1">Vega (ν)</p>
        <div class="space-y-1">
          <p class="text-sm font-semibold text-call">
            Call: {{ formatGreek(atmRow.callVega) }}
          </p>
          <p class="text-sm font-semibold text-put">
            Put: {{ formatGreek(atmRow.putVega) }}
          </p>
        </div>
        <p class="text-xs text-gray-400 mt-1">변동성 민감도</p>
      </div>

      <!-- IV -->
      <div class="text-center p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
        <p class="text-xs text-gray-500 mb-1">IV (%)</p>
        <div class="space-y-1">
          <p class="text-sm font-semibold text-call">
            Call: {{ formatPercent(atmRow.callImpliedVolatility) }}
          </p>
          <p class="text-sm font-semibold text-put">
            Put: {{ formatPercent(atmRow.putImpliedVolatility) }}
          </p>
        </div>
        <p class="text-xs text-gray-400 mt-1">내재 변동성</p>
      </div>
    </div>

    <!-- Strike Info -->
    <div v-if="atmRow" class="mt-4 pt-4 border-t border-gray-200">
      <div class="flex items-center justify-between text-sm">
        <div class="text-gray-600">
          ATM 행사가: <span class="font-semibold text-strike">{{ formatNumber(atmStrike) }}</span>
        </div>
        <div class="text-gray-600">
          기초자산: <span class="font-semibold text-primary">{{ formatNumber(underlyingPrice) }}</span>
        </div>
        <div class="text-gray-600">
          Max Pain: <span class="font-semibold text-orange-600">{{ formatNumber(maxPain) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useOptionStore } from '@/stores/option'

const optionStore = useOptionStore()

// Data
const atmRow = computed(() => optionStore.atmRow)
const atmStrike = computed(() => optionStore.atmStrike)
const underlyingPrice = computed(() => optionStore.underlyingPrice)
const maxPain = computed(() => optionStore.maxPain)

// Formatters
function formatGreek(value: number | undefined): string {
  if (value === undefined || value === null) return '-'
  return value.toFixed(4)
}

function formatPercent(value: number | undefined): string {
  if (value === undefined || value === null) return '-'
  return (value * 100).toFixed(2)
}

function formatNumber(value: number): string {
  return value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })
}
</script>

<style scoped>
.text-call {
  @apply text-green-600;
}

.text-put {
  @apply text-red-600;
}

.text-strike {
  @apply text-orange-600;
}
</style>
