import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { OptionChainData } from '@/types'

export const useOptionStore = defineStore('option', () => {
  // State
  const chainData = ref<OptionChainData | null>(null)
  const selectedStrike = ref<number | null>(null)
  const isLoading = ref(false)

  // Getters
  const underlyingPrice = computed(() => chainData.value?.underlyingPrice ?? 0)
  const atmStrike = computed(() => chainData.value?.atmStrike ?? 0)
  const maxPain = computed(() => chainData.value?.maxPainPrice ?? 0)
  
  const strikeChain = computed(() => chainData.value?.strikeChain ?? [])

  const atmRow = computed(() => {
    return strikeChain.value.find(row => row.strikePrice === atmStrike.value)
  })

  const totalCallOI = computed(() => {
    return strikeChain.value.reduce((sum, row) => sum + row.callOpenInterest, 0)
  })

  const totalPutOI = computed(() => {
    return strikeChain.value.reduce((sum, row) => sum + row.putOpenInterest, 0)
  })

  // Greeks 집계
  const greeksSummary = computed(() => {
    if (!strikeChain.value.length) {
      return {
        avgDelta: 0,
        avgGamma: 0,
        avgTheta: 0,
        avgVega: 0,
        avgIV: 0
      }
    }

    const totalWeight = strikeChain.value.reduce((sum, row) => 
      sum + row.callOpenInterest + row.putOpenInterest, 0)

    if (totalWeight === 0) {
      return {
        avgDelta: 0,
        avgGamma: 0,
        avgTheta: 0,
        avgVega: 0,
        avgIV: 0
      }
    }

    const weightedSums = strikeChain.value.reduce((acc, row) => {
      const callWeight = row.callOpenInterest / totalWeight
      const putWeight = row.putOpenInterest / totalWeight

      return {
        delta: acc.delta + (row.callDelta * callWeight) + (row.putDelta * putWeight),
        gamma: acc.gamma + (row.callGamma * callWeight) + (row.putGamma * putWeight),
        theta: acc.theta + (row.callTheta * callWeight) + (row.putTheta * putWeight),
        vega: acc.vega + (row.callVega * callWeight) + (row.putVega * putWeight),
        iv: acc.iv + (row.callImpliedVolatility * callWeight) + (row.putImpliedVolatility * putWeight)
      }
    }, { delta: 0, gamma: 0, theta: 0, vega: 0, iv: 0 })

    return {
      avgDelta: weightedSums.delta,
      avgGamma: weightedSums.gamma,
      avgTheta: weightedSums.theta,
      avgVega: weightedSums.vega,
      avgIV: weightedSums.iv
    }
  })

  // Actions
  function updateChainData(data: OptionChainData) {
    chainData.value = data
    if (!selectedStrike.value) {
      selectedStrike.value = data.atmStrike
    }
  }

  async function fetchChainData() {
    isLoading.value = true
    try {
      const response = await fetch('/api/market/option-chain')
      if (!response.ok) throw new Error('Failed to fetch option chain')
      const data = await response.json()
      updateChainData(data)
    } catch (error) {
      console.error('[Option Store] 체인 데이터 로딩 실패:', error)
    } finally {
      isLoading.value = false
    }
  }

  function selectStrike(strike: number) {
    selectedStrike.value = strike
  }

  function reset() {
    chainData.value = null
    selectedStrike.value = null
    isLoading.value = false
  }

  return {
    // State
    chainData,
    selectedStrike,
    isLoading,
    // Getters
    underlyingPrice,
    atmStrike,
    maxPain,
    strikeChain,
    atmRow,
    totalCallOI,
    totalPutOI,
    greeksSummary,
    
    // Actions
    updateChainData,
    fetchChainData,
    selectStrike,
    reset
  }
})
