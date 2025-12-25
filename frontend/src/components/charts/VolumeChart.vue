<template>
  <div class="card">
    <h3 class="text-lg font-semibold mb-4 flex items-center">
      <span class="mr-2">📊</span>
      거래량 추이
    </h3>
    <div class="relative" style="height: 300px;">
      <Line :data="chartData" :options="chartOptions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js'
import { useMarketStore } from '@/stores/market'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
)

const marketStore = useMarketStore()

// Historical data storage
const volumeHistory = ref<Array<{
  time: string
  futures: number
  call: number
  put: number
}>>([])

// Update history when overview changes
const updateHistory = () => {
  if (!marketStore.overview) return
  
  const now = new Date()
  const timeStr = now.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
  
  volumeHistory.value.push({
    time: timeStr,
    futures: marketStore.overview.totalFuturesVolume,
    call: marketStore.overview.putCallRatio.callVolume,
    put: marketStore.overview.putCallRatio.putVolume
  })
  
  // Keep only last 20 data points
  if (volumeHistory.value.length > 20) {
    volumeHistory.value.shift()
  }
}

// Initialize with current data
onMounted(() => {
  updateHistory()
  
  // Update every time market data changes
  setInterval(() => {
    updateHistory()
  }, 3000)
})

const chartData = computed(() => {
  const labels = volumeHistory.value.map(d => d.time)
  
  return {
    labels,
    datasets: [
      {
        label: '선물',
        data: volumeHistory.value.map(d => d.futures),
        borderColor: '#667eea',
        backgroundColor: 'rgba(102, 126, 234, 0.1)',
        fill: true,
        tension: 0.4
      },
      {
        label: 'Call',
        data: volumeHistory.value.map(d => d.call),
        borderColor: '#4CAF50',
        backgroundColor: 'rgba(76, 175, 80, 0.1)',
        fill: true,
        tension: 0.4
      },
      {
        label: 'Put',
        data: volumeHistory.value.map(d => d.put),
        borderColor: '#f44336',
        backgroundColor: 'rgba(244, 67, 54, 0.1)',
        fill: true,
        tension: 0.4
      }
    ]
  }
})

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: {
    mode: 'index' as const,
    intersect: false
  },
  plugins: {
    legend: {
      position: 'top' as const
    },
    tooltip: {
      callbacks: {
        label: (context: any) => {
          const label = context.dataset.label || ''
          const value = context.parsed.y
          return `${label}: ${value.toLocaleString()}`
        }
      }
    }
  },
  scales: {
    y: {
      beginAtZero: true,
      ticks: {
        callback: (value: any) => {
          if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
          if (value >= 1000) return (value / 1000).toFixed(1) + 'K'
          return value
        }
      }
    }
  }
}
</script>
