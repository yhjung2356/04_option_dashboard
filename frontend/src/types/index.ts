// 선물 데이터 타입
export interface FuturesData {
  id?: number
  symbol: string
  name: string
  currentPrice: number
  openPrice: number
  highPrice: number
  lowPrice: number
  changeAmount: number
  changePercent: number
  volume: number
  tradingValue: number
  openInterest: number
  bidPrice: number
  bidVolume: number
  askPrice: number
  askVolume: number
  timestamp: string
}

// 옵션 데이터 타입
export interface OptionData {
  id?: number
  symbol: string
  name: string
  optionType: 'CALL' | 'PUT'
  strikePrice: number
  expiryDate: string
  underlyingPrice: number
  currentPrice: number
  volume: number
  tradingValue: number
  openInterest: number
  bidPrice: number
  bidVolume: number
  askPrice: number
  askVolume: number
  delta: number
  gamma: number
  theta: number
  vega: number
  impliedVolatility: number
  timestamp: string
}

// 옵션 체인 행 타입
export interface OptionChainRow {
  strikePrice: number
  callPrice: number
  callVolume: number
  callOpenInterest: number
  callDelta: number
  callGamma: number
  callTheta: number
  callVega: number
  callImpliedVolatility: number
  callBidPrice: number
  callAskPrice: number
  putPrice: number
  putVolume: number
  putOpenInterest: number
  putDelta: number
  putGamma: number
  putTheta: number
  putVega: number
  putImpliedVolatility: number
  putBidPrice: number
  putAskPrice: number
  totalVolume: number
  totalOpenInterest: number
}

// 옵션 체인 전체 데이터
export interface OptionChainData {
  underlyingPrice: number
  atmStrike: number
  maxPainPrice: number
  strikeChain: OptionChainRow[]
}

// 시장 개요 데이터
export interface MarketOverview {
  // 선물 데이터
  totalFuturesVolume: number
  totalFuturesTradingValue: number
  totalFuturesOpenInterest: number
  
  // 옵션 데이터
  totalOptionsVolume: number
  totalOptionsTradingValue: number
  totalOptionsOpenInterest: number
  
  // Put/Call 비율
  putCallRatio: {
    callVolume: number
    putVolume: number
    volumeRatio: number
    callOpenInterest: number
    putOpenInterest: number
    openInterestRatio: number
    callTradingValue: number
    putTradingValue: number
    tradingValueRatio: number
  }
  
  // 시장 심리
  marketSentiment: 'BULLISH' | 'NEUTRAL' | 'BEARISH'
  sentimentScore: number
  
  // 데이터 소스
  dataSource: 'KIS' | 'MOCK'
  
  // 상위 거래 종목
  topByVolume: Array<{
    symbol: string
    name: string
    type: string
    currentPrice: number
    volume: number
    tradingValue: number
    openInterest: number
    changePercent: number | undefined
  }>
  topByOpenInterest: Array<{
    symbol: string
    name: string
    type: string
    currentPrice: number
    volume: number
    tradingValue: number
    openInterest: number
    changePercent: number | undefined
  }>
  
  // 시장 상태
  marketStatus?: {
    displayName: string     // "주간장", "야간장", "휴장"
    description: string     // "거래중", "주말", "공휴일"
    isOpen: boolean         // true/false
    fullText: string        // "주간장 거래중", "휴장"
  }
}

// WebSocket 메시지 타입
export interface WebSocketMessage<T = any> {
  type: 'MARKET_OVERVIEW' | 'OPTION_CHAIN' | 'PRICE_UPDATE'
  timestamp: string
  data: T
}

// 시스템 상태
export interface SystemState {
  dataSource: 'KIS' | 'MOCK'
  demoMode: boolean
  marketHoursEnabled: boolean
  currentTimestamp: number
  serverTime: string
}
