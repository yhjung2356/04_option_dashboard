/**
 * API 유틸리티 함수
 * - 타임아웃 처리
 * - 자동 재시도
 * - 에러 처리
 */

export interface FetchOptions extends RequestInit {
  timeout?: number
  retries?: number
  retryDelay?: number
}

/**
 * 타임아웃이 적용된 fetch
 */
async function fetchWithTimeout(
  url: string,
  options: FetchOptions = {}
): Promise<Response> {
  const { timeout = 30000, ...fetchOptions } = options

  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)

  try {
    const response = await fetch(url, {
      ...fetchOptions,
      signal: controller.signal
    })
    clearTimeout(timeoutId)
    return response
  } catch (error) {
    clearTimeout(timeoutId)
    if ((error as Error).name === 'AbortError') {
      throw new Error(`요청 시간 초과 (${timeout}ms)`)
    }
    throw error
  }
}

/**
 * 재시도 로직이 적용된 fetch
 */
export async function fetchWithRetry(
  url: string,
  options: FetchOptions = {}
): Promise<Response> {
  const { retries = 3, retryDelay = 1000, ...fetchOptions } = options
  
  let lastError: Error | null = null
  
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const response = await fetchWithTimeout(url, fetchOptions)
      
      // 4xx 에러는 재시도하지 않음 (클라이언트 오류)
      if (response.status >= 400 && response.status < 500) {
        return response
      }
      
      // 5xx 에러는 재시도
      if (response.status >= 500 && attempt < retries) {
        console.warn(`[API] 서버 오류 (${response.status}), 재시도 중... (${attempt + 1}/${retries})`)
        await delay(retryDelay * Math.pow(2, attempt)) // Exponential backoff
        continue
      }
      
      return response
    } catch (error) {
      lastError = error as Error
      
      if (attempt < retries) {
        console.warn(`[API] 요청 실패, 재시도 중... (${attempt + 1}/${retries}):`, error)
        await delay(retryDelay * Math.pow(2, attempt)) // Exponential backoff
      }
    }
  }
  
  throw lastError || new Error('알 수 없는 오류')
}

/**
 * 지연 함수
 */
function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * JSON API 호출 헬퍼
 */
export async function apiCall<T>(
  url: string,
  options: FetchOptions = {}
): Promise<T> {
  try {
    const response = await fetchWithRetry(url, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers
      }
    })
    
    if (!response.ok) {
      throw new Error(`API 오류: ${response.status} ${response.statusText}`)
    }
    
    return await response.json()
  } catch (error) {
    console.error('[API] 호출 실패:', url, error)
    throw error
  }
}
