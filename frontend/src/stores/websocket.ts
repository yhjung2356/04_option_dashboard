import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
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

    // 장 시간 체크는 백엔드에서 하므로 여기서는 기본 체크만 수행
    const now = new Date()
    const hour = now.getHours()
    const dayOfWeek = now.getDay() // 0=일요일, 6=토요일
    
    // 주말 체크
    if (dayOfWeek === 0 || dayOfWeek === 6) {
      // console.log('[WebSocket] 주말에는 연결하지 않습니다')
      connectionStatus.value = 'holiday'
      return
    }
    
    // 간단한 장 시간 체크 (상세한 체크는 백엔드에서)
    // 주간장: 08:45~15:45 (15:35~15:45 동시호가)
    // 야간장: 18:00~익일 05:00
    const isDaySession = (hour >= 8 && hour < 16)
    const isNightSession = (hour >= 18 || hour < 5)
    
    if (!isDaySession && !isNightSession) {
      // console.log('[WebSocket] 장 시간이 아니므로 연결하지 않습니다')
      connectionStatus.value = 'disconnected'
      return
    }

    connectionStatus.value = 'connecting'

    try {
      // 개발 환경에서는 백엔드 포트(8080) 사용, 프로덕션에서는 같은 호스트 사용
      const backendUrl = import.meta.env.DEV 
        ? 'http://localhost:8080/ws'
        : `http://${window.location.host}/ws`
      
      const stompClient = new Client({
        webSocketFactory: () => new SockJS(backendUrl),
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
        // 데이터 유효성 체크: 거래량이 0이면 스킵 (빈 데이터)
        if (data.data && (data.data.totalFuturesVolume > 0 || data.data.totalOptionsVolume > 0)) {
          marketStore.updateOverview(data.data)
        }
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
