import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import './style.css'

const app = createApp(App)

// Global Error Handler
app.config.errorHandler = (err, instance, info) => {
  console.error('🚨 [Global Error Handler]', {
    error: err,
    component: instance?.$options.name || 'Unknown',
    errorInfo: info
  })
  
  // 사용자에게 친화적인 에러 메시지 표시
  // Toast 알림으로 표시 가능
}

// Global Warning Handler (개발 환경에서만)
if (import.meta.env.DEV) {
  app.config.warnHandler = (msg, instance, trace) => {
    console.warn('⚠️ [Vue Warning]', {
      message: msg,
      component: instance?.$options.name || 'Unknown',
      trace
    })
  }
}

app.use(createPinia())
app.use(router)

app.mount('#app')

// Register Service Worker (PWA) - 프로덕션에서만 활성화
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
      .then(registration => {
        console.log('[PWA] Service Worker registered:', registration)
      })
      .catch(error => {
        console.error('[PWA] Service Worker registration failed:', error)
      })
  })
}
