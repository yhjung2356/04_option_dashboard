import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { MarketOverview } from '@/types'

export const useMarketStore = defineStore('market', () => {
  // State
  const overview = ref<MarketOverview | null>(null)
  const lastUpdate = ref<Date | null>(null)
  const isLoading = ref(false)

  // Getters
  const totalVolume = computed(() => {
    if (!overview.value) return 0
    return (overview.value.totalFuturesVolume ?? 0) + (overview.value.totalOptionsVolume ?? 0)
  })

  const putCallRatio = computed(() => {
    if (!overview.value?.putCallRatio) return 0
    return overview.value.putCallRatio.volumeRatio ?? 0
  })

  const marketSentiment = computed(() => {
    // 간단한 시장 심리 판단 로직
    if (!overview.value?.putCallRatio) return 'NEUTRAL'
    const ratio = overview.value.putCallRatio.volumeRatio ?? 1
    if (ratio > 1.5) return 'BEARISH'  // Put이 Call보다 1.5배 많으면 약세
    if (ratio < 0.7) return 'BULLISH'  // Call이 Put보다 많으면 강세
    return 'NEUTRAL'
  })

  const sentimentColor = computed(() => {
    const sentiment = marketSentiment.value
    const colors = {
      BULLISH: 'text-green-600',
      BEARISH: 'text-red-600',
      NEUTRAL: 'text-gray-600',
      VOLATILE: 'text-orange-600'
    }
    return colors[sentiment] || colors.NEUTRAL
  })

  const sentimentText = computed(() => {
    const sentiment = marketSentiment.value
    const texts = {
      BULLISH: '강세장',
      BEARISH: '약세장',
      NEUTRAL: '보합',
      VOLATILE: '변동성 높음'
    }
    return texts[sentiment] || texts.NEUTRAL
  })

  // Actions
  function updateOverview(data: MarketOverview) {
    console.log('[Market Store] 데이터 업데이트:', {
      futuresVolume: data?.totalFuturesVolume,
      optionsVolume: data?.totalOptionsVolume,
      putCallRatio: data?.putCallRatio?.volumeRatio,
      marketStatus: data?.marketStatus
    })
    overview.value = data
    lastUpdate.value = new Date()
  }

  async function fetchOverview() {
    isLoading.value = true
    try {
      // console.log('[Market Store] API 호출 시작...')
      const response = await fetch('/api/market/overview')
      // console.log('[Market Store] 응답 상태:', response.status, response.statusText)
      
      if (!response.ok) throw new Error('Failed to fetch overview')
      
      const data = await response.json()
      console.log('[Market Store] JSON 파싱 완료:', data)
      
      updateOverview(data)
    } catch (error) {
      console.error('[Market Store] ❌ 개요 로딩 실패:', error)
    } finally {
      isLoading.value = false
    }
  }

  function reset() {
    overview.value = null
    lastUpdate.value = null
    isLoading.value = false
  }

  return {
    // State
    overview,
    lastUpdate,
    isLoading,
    // Getters
    totalVolume,
    putCallRatio,
    marketSentiment,
    sentimentColor,
    sentimentText,
    // Actions
    updateOverview,
    fetchOverview,
    reset
  }
})
