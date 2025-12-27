<template>
  <div class="inline-flex items-center group relative">
    <button
      ref="buttonRef"
      class="ml-1 w-4 h-4 rounded-full bg-gray-300 dark:bg-gray-600 text-gray-700 dark:text-gray-300 text-xs flex items-center justify-center hover:bg-primary-500 hover:text-white transition-colors cursor-help"
      @mouseenter="handleMouseEnter"
      @mouseleave="showTooltip = false"
      @click.stop="handleClick"
    >
      ?
    </button>
    
    <!-- Tooltip - Teleport to body to avoid overflow clipping -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-show="showTooltip"
          ref="tooltipRef"
          class="fixed z-[9999] w-72 max-w-xs p-3 bg-gray-900 dark:bg-gray-800 text-white text-xs rounded-lg shadow-xl pointer-events-none"
          :style="tooltipStyle"
        >
          <div class="font-semibold mb-1">{{ title }}</div>
          <div class="text-gray-300 dark:text-gray-400 leading-relaxed whitespace-normal break-words">
            {{ description }}
          </div>
          <!-- Arrow -->
          <div 
            class="absolute w-2 h-2 bg-gray-900 dark:bg-gray-800 transform rotate-45"
            :style="arrowStyle"
          ></div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

interface Props {
  title: string
  description: string
  position?: 'top' | 'bottom' | 'left' | 'right'
}

const props = withDefaults(defineProps<Props>(), {
  position: 'bottom'
})

const showTooltip = ref(false)
const buttonRef = ref<HTMLElement | null>(null)
const tooltipRef = ref<HTMLElement | null>(null)
const tooltipStyle = ref<Record<string, string>>({})
const arrowStyle = ref<Record<string, string>>({})

const adjustTooltipPosition = async () => {
  await nextTick()
  
  if (!tooltipRef.value || !buttonRef.value) return
  
  const tooltip = tooltipRef.value
  const button = buttonRef.value
  const buttonRect = button.getBoundingClientRect()
  const tooltipRect = tooltip.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  
  const padding = 10 // 화면 가장자리 여백
  const arrowSize = 8 // 화살표 크기
  
  let top = 0
  let left = 0
  let actualPosition = props.position
  
  // 위치별 계산
  if (props.position === 'top' || props.position === 'bottom') {
    // 기본 좌우 위치: 버튼 중앙에 툴팁 중앙 정렬
    left = buttonRect.left + (buttonRect.width / 2) - (tooltipRect.width / 2)
    
    // 왼쪽으로 짤리는 경우
    if (left < padding) {
      left = padding
    }
    
    // 오른쪽으로 짤리는 경우
    if (left + tooltipRect.width > viewportWidth - padding) {
      left = viewportWidth - tooltipRect.width - padding
    }
    
    // 상하 위치
    if (props.position === 'top') {
      top = buttonRect.top - tooltipRect.height - arrowSize
      // 위로 공간이 없으면 아래로
      if (top < padding) {
        top = buttonRect.bottom + arrowSize
        actualPosition = 'bottom'
      }
    } else {
      top = buttonRect.bottom + arrowSize
      // 아래로 공간이 없으면 위로
      if (top + tooltipRect.height > viewportHeight - padding) {
        top = buttonRect.top - tooltipRect.height - arrowSize
        actualPosition = 'top'
      }
    }
  } else {
    // left/right position
    top = buttonRect.top + (buttonRect.height / 2) - (tooltipRect.height / 2)
    
    // 위로 짤리는 경우
    if (top < padding) {
      top = padding
    }
    
    // 아래로 짤리는 경우
    if (top + tooltipRect.height > viewportHeight - padding) {
      top = viewportHeight - tooltipRect.height - padding
    }
    
    if (props.position === 'left') {
      left = buttonRect.left - tooltipRect.width - arrowSize
      if (left < padding) {
        left = buttonRect.right + arrowSize
        actualPosition = 'right'
      }
    } else {
      left = buttonRect.right + arrowSize
      if (left + tooltipRect.width > viewportWidth - padding) {
        left = buttonRect.left - tooltipRect.width - arrowSize
        actualPosition = 'left'
      }
    }
  }
  
  tooltipStyle.value = {
    top: `${top}px`,
    left: `${left}px`
  }
  
  // 화살표 위치 계산
  const arrowLeft = buttonRect.left + (buttonRect.width / 2) - left
  const arrowTop = buttonRect.top + (buttonRect.height / 2) - top
  
  if (actualPosition === 'top') {
    arrowStyle.value = {
      bottom: '-4px',
      left: `${arrowLeft}px`,
      transform: 'translateX(-50%) rotate(45deg)'
    }
  } else if (actualPosition === 'bottom') {
    arrowStyle.value = {
      top: '-4px',
      left: `${arrowLeft}px`,
      transform: 'translateX(-50%) rotate(45deg)'
    }
  } else if (actualPosition === 'left') {
    arrowStyle.value = {
      right: '-4px',
      top: `${arrowTop}px`,
      transform: 'translateY(-50%) rotate(45deg)'
    }
  } else {
    arrowStyle.value = {
      left: '-4px',
      top: `${arrowTop}px`,
      transform: 'translateY(-50%) rotate(45deg)'
    }
  }
}

const handleMouseEnter = () => {
  showTooltip.value = true
  adjustTooltipPosition()
}

const handleClick = () => {
  showTooltip.value = !showTooltip.value
  if (showTooltip.value) {
    adjustTooltipPosition()
  }
}
</script>

<style scoped>
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
