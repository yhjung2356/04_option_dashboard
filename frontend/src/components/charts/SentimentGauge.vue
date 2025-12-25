<template>
  <div class="card">
    <h3 class="text-lg font-semibold mb-4 flex items-center">
      <span class="mr-2">🎯</span>
      시장 심리 게이지
    </h3>
    <div class="relative" style="height: 250px;">
      <Doughnut :data="chartData" :options="chartOptions" />
    </div>
    <div class="mt-4 text-center">
      <p class="text-sm text-gray-600">심리 점수</p>
      <p class="text-3xl font-bold" :class="sentimentColor">
        {{ sentimentScore }}
      </p>
      <p class="text-sm text-gray-500 mt-1">{{ sentimentText }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'
import { useMarketStore } from '@/stores/market'

ChartJS.register(ArcElement, Tooltip, Legend)

const marketStore = useMarketStore()

// P/C 비율로 시장 심리 점수 계산 (-100 ~ +100)
// P/C < 0.7 = 강한 강세 (+80), 0.7~1.0 = 약한 강세 (+40)
// P/C 1.0~1.5 = 중립 (0), P/C > 1.5 = 약한 약세 (-40), P/C > 2.0 = 강한 약세 (-80)
const sentimentScore = computed(() => {
  const pcRatio = marketStore.overview?.putCallRatio?.volumeRatio ?? 1.0
  if (pcRatio < 0.7) return 80  // 강한 강세
  if (pcRatio < 1.0) return 40  // 약한 강세
  if (pcRatio <= 1.5) return 0  // 중립
  if (pcRatio <= 2.0) return -40  // 약한 약세
  return -80  // 강한 약세
})
const sentimentText = computed(() => marketStore.sentimentText)
const sentimentColor = computed(() => marketStore.sentimentColor)

const chartData = computed(() => {
  const score = sentimentScore.value
  const remaining = 100 - Math.abs(score)
  
  return {
    labels: ['심리 점수', ''],
    datasets: [
      {
        data: [Math.abs(score), remaining],
        backgroundColor: [
          score > 0 ? '#4CAF50' : score < 0 ? '#f44336' : '#9E9E9E',
          '#E0E0E0'
        ],
        borderWidth: 0
      }
    ]
  }
})

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  circumference: 180,
  rotation: -90,
  cutout: '75%',
  plugins: {
    legend: {
      display: false
    },
    tooltip: {
      enabled: true
    }
  }
}
</script>
