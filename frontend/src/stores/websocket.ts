import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Client } from '@stomp/stompjs'
import { useMarketStore } from './market'
import { useOptionStore } from './option'
import type { WebSocketMessage } from '@/types'

export const useWebSocketStore = defineStore('websocket', () => {
  // State
  const client = ref<Client | null>(null)
  const isConnected = ref(false)
  const connectionStatus = ref<'disconnected' | 'connecting' | 'connected' | 'error' | 'holiday'>('disconnected')
  const reconnectAttempts = ref(0)
  const maxReconnectAttempts = 5
  const reconnectDelay = 3000

  // Getters
  const statusText = computed(() => {
    const texts = {
      disconnected: '연결 끊김',
      connecting: '연결 중...',
      connected: '연결됨',
      error: '오류',
      holiday: '휴장'
    }
    return texts[connectionStatus.value]
  })

  const statusColor = computed(() => {
    const colors = {
      disconnected: 'text-gray-500',
      connecting: 'text-yellow-500',
      connected: 'text-green-500',
      error: 'text-red-500',
      holiday: 'text-orange-500'
    }
    return colors[connectionStatus.value]
  })

  // Actions
  function connect() {
    if (client.value && isConnected.value) {
      console.log('[WebSocket] 이미 연결되어 있습니다')
      return
    }

    // 장 시간 체크 (주간: 09:00~15:45, 야간: 18:00~05:00)
    const now = new Date()
    const year = now.getFullYear()
    const hour = now.getHours()
    const minute = now.getMinutes()
    const dayOfWeek = now.getDay() // 0=일요일, 6=토요일
    const month = now.getMonth() + 1 // 1-12
    const date = now.getDate()
    
    // 주말 체크
    if (dayOfWeek === 0 || dayOfWeek === 6) {
      console.log('[WebSocket] 주말에는 연결하지 않습니다')
      connectionStatus.value = 'holiday'
      return
    }
    
    // 한국 공휴일 체크 (2025-2026년)
    const holidays2025 = [
      [1, 1],   // 신정
      [1, 28],  // 설날 연휴
      [1, 29],  // 설날
      [1, 30],  // 설날 연휴
      [3, 1],   // 삼일절
      [5, 5],   // 어린이날
      [5, 6],   // 어린이날 대체공휴일
      [6, 6],   // 현충일
      [8, 15],  // 광복절
      [9, 6],   // 추석 연휴
      [9, 7],   // 추석 연휴
      [9, 8],   // 추석
      [9, 9],   // 추석 연휴
      [10, 3],  // 개천절
      [10, 9],  // 한글날
      [12, 25], // 크리스마스
    ]
    
    const holidays2026 = [
      [1, 1],   // 신정
      [1, 24],  // 설날 연휴
      [1, 25],  // 설날 연휴
      [1, 26],  // 설날
      [3, 1],   // 삼일절
      [3, 2],   // 삼일절 대체공휴일
      [5, 5],   // 어린이날
      [5, 25],  // 석가탄신일
      [6, 6],   // 현충일
      [8, 15],  // 광복절
      [9, 24],  // 추석 연휴
      [9, 25],  // 추석
      [9, 26],  // 추석 연휴
      [10, 3],  // 개천절
      [10, 5],  // 개천절 대체공휴일
      [10, 9],  // 한글날
      [12, 25], // 크리스마스
    ]
    
    const holidays = year === 2025 ? holidays2025 : year === 2026 ? holidays2026 : []
    const isHoliday = holidays.some(([m, d]) => m === month && d === date)
    
    if (isHoliday) {
      console.log('[WebSocket] 오늘은 휴장일입니다')
      connectionStatus.value = 'holiday'
      return
    }
    
    // 특수 거래일 체크 (1월 첫 거래일, 수능날: 10시 시작)
    // 1월 2일 또는 3일 (1일이 주말인 경우)
    const isFirstTradingDay = (month === 1 && (date === 2 || (date === 3 && new Date(year, 0, 2).getDay() === 0)))
    // 수능날 (11월 둘째 목요일 또는 셋째 목요일)
    const isCollegeExamDay = (month === 11 && dayOfWeek === 4 && date >= 8 && date <= 21)
    
    // 장 시작 시간 결정
    const marketStartHour = (isFirstTradingDay || isCollegeExamDay) ? 10 : 9
    const isDaySession = (hour > marketStartHour || (hour === marketStartHour && minute >= 0)) && (hour < 15 || (hour === 15 && minute <= 45))
    const isNightSession = hour >= 18 || hour < 5
    
    if (!isDaySession && !isNightSession) {
      console.log('[WebSocket] 장 시간이 아니므로 연결하지 않습니다')
      connectionStatus.value = 'disconnected'
      return
    }

    connectionStatus.value = 'connecting'

    try {
      // 개발 환경에서는 백엔드 포트(8080) 사용, 프로덕션에서는 같은 호스트 사용
      const backendUrl = import.meta.env.DEV 
        ? 'ws://localhost:8080/ws'
        : `ws://${window.location.host}/ws`
      
      const stompClient = new Client({
        brokerURL: backendUrl,
        reconnectDelay: reconnectDelay,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        
        onConnect: () => {
          console.log('[WebSocket] 연결 성공')
          isConnected.value = true
          connectionStatus.value = 'connected'
          reconnectAttempts.value = 0
          subscribeToTopics()
        },
        
        onStompError: (frame) => {
          console.error('[WebSocket] 오류:', frame)
          connectionStatus.value = 'error'
          handleReconnect()
        },
        
        onWebSocketClose: () => {
          console.log('[WebSocket] 연결 종료')
          isConnected.value = false
          connectionStatus.value = 'disconnected'
          handleReconnect()
        },
        
        onWebSocketError: (error) => {
          console.error('[WebSocket] WebSocket 오류:', error)
          connectionStatus.value = 'error'
        }
      })

      stompClient.activate()
      client.value = stompClient
      
    } catch (error) {
      console.error('[WebSocket] 연결 생성 실패:', error)
      connectionStatus.value = 'error'
      handleReconnect()
    }
  }

  function subscribeToTopics() {
    if (!client.value || !isConnected.value) return

    const marketStore = useMarketStore()
    const optionStore = useOptionStore()

    // 시장 개요 구독
    client.value.subscribe('/topic/market-overview', (message) => {
      try {
        const data: WebSocketMessage = JSON.parse(message.body)
        marketStore.updateOverview(data.data)
      } catch (error) {
        console.error('[WebSocket] 시장 개요 파싱 오류:', error)
      }
    })

    // 옵션 체인 구독
    client.value.subscribe('/topic/option-chain', (message) => {
      try {
        const data: WebSocketMessage = JSON.parse(message.body)
        optionStore.updateChainData(data.data)
      } catch (error) {
        console.error('[WebSocket] 옵션 체인 파싱 오류:', error)
      }
    })

    console.log('[WebSocket] 토픽 구독 완료')
  }

  function handleReconnect() {
    if (reconnectAttempts.value >= maxReconnectAttempts) {
      console.error('[WebSocket] 최대 재연결 시도 횟수 초과')
      connectionStatus.value = 'error'
      return
    }

    reconnectAttempts.value++
    console.log(`[WebSocket] 재연결 시도 ${reconnectAttempts.value}/${maxReconnectAttempts}`)
    
    setTimeout(() => {
      connect()
    }, reconnectDelay)
  }

  function disconnect() {
    if (client.value) {
      client.value.deactivate()
      client.value = null
      isConnected.value = false
      connectionStatus.value = 'disconnected'
      console.log('[WebSocket] 연결 종료됨')
    }
  }

  function reset() {
    disconnect()
    reconnectAttempts.value = 0
  }

  return {
    // State
    isConnected,
    connectionStatus,
    reconnectAttempts,
    // Getters
    statusText,
    statusColor,
    // Actions
    connect,
    disconnect,
    reset
  }
})
