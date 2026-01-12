<template>
  <div
    ref="wrapperRef"
    class="message-list-wrapper"
  >
    <div v-if="isLoading" class="loading-indicator">
      <div class="loading-spinner" />
      <span>{{ t('chat.loadingHistory') }}</span>
    </div>

    <div v-else-if="displayMessages.length === 0" class="empty-state">
      <div class="empty-content">
        <div class="shortcut-hints">
          <div class="shortcut-item">
            <kbd class="keyboard-key">Enter</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.sendHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Shift</kbd> + <kbd class="keyboard-key">Enter</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.newLineHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Esc</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.stopHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Tab</kbd>
            <span class="shortcut-desc">
              {{ isCodexBackend ? t('chat.welcomeScreen.toggleReasoningHint') : t('chat.welcomeScreen.toggleThinkingHint') }}
            </span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Shift</kbd> + <kbd class="keyboard-key">Tab</kbd>
            <span class="shortcut-desc">
              {{ isCodexBackend ? t('chat.welcomeScreen.switchSandboxHint') : t('chat.welcomeScreen.switchModeHint') }}
            </span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Ctrl</kbd> + <kbd class="keyboard-key">Enter</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.interruptHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Ctrl</kbd> + <kbd class="keyboard-key">J</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.newLineHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Ctrl</kbd> + <kbd class="keyboard-key">U</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.clearToLineStartHint') }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 使用 vue-virtual-scroller 的 DynamicScroller -->
    <DynamicScroller
      v-else
      ref="scrollerRef"
      class="message-list"
      :items="displayMessages"
      :min-item-size="60"
      :buffer="200"
      key-field="id"
      @scroll="handleScroll"
    >
      <template #default="{ item, index, active }">
        <DynamicScrollerItem
          :item="item"
          :active="active"
          :data-index="index"
          :size-dependencies="[
            item.content,
            item.status,
            item.result,
            item.input
          ]"
          :emit-resize="true"
        >
          <component
            :is="messageComponent"
            :source="item"
          />
        </DynamicScrollerItem>
      </template>

      <!-- Streaming 状态指示器 - 在消息列表末尾 -->
      <template #after>
        <div
          v-if="isStreaming"
          class="streaming-indicator"
        >
          <span class="generating-text">Generating</span>
          <span class="bouncing-dots">
            <span class="dot">.</span>
            <span class="dot">.</span>
            <span class="dot">.</span>
          </span>
          <span class="streaming-stats">({{ streamingStats }})</span>
        </div>
      </template>
    </DynamicScroller>

    <!-- 回到底部按钮 -->
    <transition name="fade-slide">
      <button
        v-if="showScrollToBottom"
        class="scroll-to-bottom-btn"
        :title="t('chat.scrollToBottom')"
        @click="scrollToBottom"
      >
        <span class="btn-icon">↓</span>
        <span
          v-if="newMessageCount > 0"
          class="new-message-badge"
        >{{ newMessageCount }}</span>
      </button>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { useSessionStore } from '@/stores/sessionStore'
import { useSettingsStore } from '@/stores/settingsStore'
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import type { Message } from '@/types/message'
import type { DisplayItem } from '@/types/display'
import type { ScrollState, ScrollAnchor } from '@/composables/useSessionTab'
import { DEFAULT_SCROLL_STATE } from '@/composables/useSessionTab'
import MessageDisplay from './MessageDisplay.vue'
import DisplayItemRenderer from './DisplayItemRenderer.vue'
import {
  HISTORY_TRIGGER_THRESHOLD,
  HISTORY_RESET_THRESHOLD,
  HISTORY_AUTO_LOAD_MAX
} from '@/constants/messageWindow'

const { t } = useI18n()
const sessionStore = useSessionStore()
const settingsStore = useSettingsStore()

interface Props {
  messages?: Message[]  // 保留向后兼容
  displayItems?: DisplayItem[]  // 新的 prop
  isLoading?: boolean
  isStreaming?: boolean  // 是否正在流式响应
  streamingStartTime?: number  // 流式响应开始时间
  inputTokens?: number  // 上行 token
  outputTokens?: number  // 下行 token
  contentVersion?: number  // 流式内容版本号（用于触发自动滚动）
  connectionStatus?: 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'  // 连接状态
  hasMoreHistory?: boolean  // 顶部分页可用
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
  isStreaming: false,
  streamingStartTime: 0,
  inputTokens: 0,
  outputTokens: 0,
  contentVersion: 0,
  connectionStatus: 'DISCONNECTED',
  hasMoreHistory: false
})

const emit = defineEmits<{
  (e: 'load-more-history'): void
}>()

const wrapperRef = ref<HTMLElement>()
const scrollerRef = ref<InstanceType<typeof DynamicScroller>>()

// ========== 滚动状态管理（基于 ID + Offset 锚点方案） ==========

/**
 * 滚动状态（双向绑定到 sessionStore）
 */
const scrollState = computed({
  get: (): ScrollState => sessionStore.currentTab?.uiState.scrollState ?? { ...DEFAULT_SCROLL_STATE },
  set: (val: Partial<ScrollState>) => {
    if (sessionStore.currentTab) {
      sessionStore.currentTab.saveUiState({
        scrollState: { ...scrollState.value, ...val }
      })
    }
  }
})

/**
 * 是否显示"回到底部"按钮（browse 模式下显示）
 */
const showScrollToBottom = computed(() =>
  scrollState.value.mode === 'browse' && displayMessages.value.length > 0
)

/**
 * 新消息计数
 */
const newMessageCount = computed(() => scrollState.value.newMessageCount)

/**
 * 计算当前滚动锚点
 * 策略：找到视口 30% 位置的 item 作为锚点（更靠上，高度变化时更稳定）
 */
function computeScrollAnchor(): ScrollAnchor | null {
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  if (!el || displayMessages.value.length === 0) return null

  const clientHeight = el.clientHeight
  const targetPosition = clientHeight * 0.3  // 视口 30% 位置

  // 遍历已渲染的 item，找到覆盖目标位置的 item
  const items = el.querySelectorAll('[data-index]')
  let anchorItem: Element | null = null
  let anchorOffsetFromTop = 0
  let anchorIndex = -1

  for (const item of items) {
    const rect = item.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    const itemTopRelativeToViewport = rect.top - elRect.top
    const itemBottomRelativeToViewport = rect.bottom - elRect.top

    // 找到覆盖视口 30% 位置的 item
    if (itemTopRelativeToViewport <= targetPosition &&
        itemBottomRelativeToViewport >= targetPosition) {
      anchorItem = item
      anchorOffsetFromTop = itemTopRelativeToViewport
      anchorIndex = parseInt(item.getAttribute('data-index') || '-1', 10)
      break
    }
  }

  // 回退：使用第一个可见 item
  if (!anchorItem && items.length > 0) {
    anchorItem = items[0]
    const rect = anchorItem.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    anchorOffsetFromTop = rect.top - elRect.top
    anchorIndex = parseInt(anchorItem.getAttribute('data-index') || '-1', 10)
  }

  if (anchorIndex < 0 || anchorIndex >= displayMessages.value.length) return null

  const itemId = displayMessages.value[anchorIndex].id

  return {
    itemId,
    offsetFromViewportTop: anchorOffsetFromTop,
    viewportHeight: clientHeight,
    savedAt: Date.now()
  }
}

/**
 * 恢复滚动位置
 * 策略：通过 ID 找到 item -> 估算滚动 -> 等待渲染 -> 微调
 */
async function restoreScrollPosition(anchor: ScrollAnchor): Promise<void> {
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  if (!el) return

  // 1. 通过 ID 找到当前 index
  const index = displayMessages.value.findIndex(item => item.id === anchor.itemId)
  if (index === -1) {
    // ID 不存在（被清理了），回退到底部
    console.log('🔄 [Scroll] Anchor item not found, scrolling to bottom')
    scrollToBottom()
    return
  }

  // 2. 估算滚动位置（假设每个 item 平均高度 100px）
  const estimatedScrollTop = index * 100 - anchor.offsetFromViewportTop
  el.scrollTop = Math.max(0, estimatedScrollTop)

  // 3. 等待 DynamicScroller 渲染
  await nextTick()
  scrollerRef.value?.forceUpdate?.()
  await nextTick()
  await new Promise(resolve => requestAnimationFrame(resolve))

  // 4. 精确定位：找到实际渲染的 item 并微调
  const renderedItem = el.querySelector(`[data-index="${index}"]`)
  if (renderedItem) {
    const rect = renderedItem.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    const currentOffsetFromTop = rect.top - elRect.top
    const adjustment = currentOffsetFromTop - anchor.offsetFromViewportTop
    el.scrollTop += adjustment
  }

  // 5. 更新状态
  lastScrollTop.value = el.scrollTop
  const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  const nearBottom = distanceFromBottom < 50

  if (nearBottom) {
    // 恢复后发现在底部，切换到 follow 模式
    scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0 }
  }

  console.log(`🔄 [Scroll] Restored to item ${anchor.itemId} (index=${index})`)
}

// 防抖保存锚点（减少延迟以确保 tab 切换前能保存）
let saveAnchorTimer: number | null = null
function debouncedSaveAnchor() {
  if (saveAnchorTimer) clearTimeout(saveAnchorTimer)
  saveAnchorTimer = window.setTimeout(() => {
    if (scrollState.value.mode === 'browse' && !isTabSwitching.value) {
      const anchor = computeScrollAnchor()
      if (anchor) {
        scrollState.value = { ...scrollState.value, anchor }
        console.log(`💾 [Scroll] Saved anchor: item=${anchor.itemId}`)
      }
    }
  }, 50)  // 减少到 50ms，确保快速保存
}

// 立即保存锚点（用于关键时刻，如失去焦点）
function saveAnchorImmediately() {
  if (saveAnchorTimer) {
    clearTimeout(saveAnchorTimer)
    saveAnchorTimer = null
  }
  if (scrollState.value.mode === 'browse' && !isTabSwitching.value) {
    const anchor = computeScrollAnchor()
    if (anchor) {
      scrollState.value = { ...scrollState.value, anchor }
      console.log(`💾 [Scroll] Saved anchor immediately: item=${anchor.itemId}`)
    }
  }
}

const lastScrollTop = ref(0)       // 上次滚动位置，用于检测滚动方向
const isTabSwitching = ref(false)  // Tab 切换中，阻止其他滚动逻辑
const isProgrammaticScroll = ref(false)  // 程序触发的滚动（用于区分用户手动滚动）
const historyLoadInProgress = ref(false)
const historyLoadRequested = ref(false)
const historyScrollHeightBefore = ref(0)
const historyScrollTopBefore = ref(0)
const hasLoadedHistory = ref(false)  // 标记是否已完成首次历史加载

// Streaming 计时器
const elapsedTime = ref(0)
let timerId: number | null = null

// 格式化耗时
function formatDuration(ms: number): string {
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSecs = seconds % 60
  if (minutes < 60) return `${minutes}m${remainingSecs}s`
  const hours = Math.floor(minutes / 60)
  const remainingMins = minutes % 60
  return `${hours}h${remainingMins}m${remainingSecs}s`
}

// 格式化 token 数量
function formatTokens(count: number): string {
  if (count < 1000) return `${count}`
  return `${(count / 1000).toFixed(1)}k`
}

// Streaming 状态显示
const streamingStats = computed(() => {
  const duration = formatDuration(elapsedTime.value)
  const input = formatTokens(props.inputTokens)
  const output = formatTokens(props.outputTokens)
  return `esc to interrupt · ${duration} ↑${input} ↓${output}`
})

// 格式化耗时显示（保留以备后用）
const _formattedElapsedTime = computed(() => formatDuration(elapsedTime.value))

// 启动计时器
function startTimer() {
  if (timerId !== null) return
  const startTime = props.streamingStartTime || Date.now()
  elapsedTime.value = Date.now() - startTime
  timerId = window.setInterval(() => {
    elapsedTime.value = Date.now() - startTime
  }, 100)
}

// 停止计时器
function stopTimer() {
  if (timerId !== null) {
    clearInterval(timerId)
    timerId = null
  }
}


// 监听 isStreaming 变化
watch(
  () => props.isStreaming,
  (streaming) => {
    if (streaming) {
      startTimer()
    } else {
      stopTimer()
      // 注意：流式结束时不再自动解锁，避免打断用户阅读历史消息
      // 用户需要手动滚动到底部或点击按钮才会解锁
    }
  },
  { immediate: true }
)

// 监听 streamingStartTime 变化（切换 Tab 时重启计时器）
// 解决问题：多个 Tab 同时生成时，切换 Tab 后计时器显示错误
watch(
  () => props.streamingStartTime,
  () => {
    if (props.isStreaming) {
      // 重启计时器以使用新的 startTime
      stopTimer()
      startTimer()
    }
  }
)

// 监听流式响应时的内容变化（通过 outputTokens 变化检测）
// 解决问题：消息数量不变但内容更新时，需要自动滚动
watch(
  () => props.outputTokens,
  () => {
    // 只在流式响应中、follow 模式时才自动滚动
    if (props.isStreaming && scrollState.value.mode === 'follow') {
      scrollToBottomSilent()
    }
  }
)

// 监听流式内容版本号变化（thinking/text delta 更新时触发）
// 解决问题：思考内容换行时自动滚动
watch(
  () => props.contentVersion,
  () => {
    // 只在流式响应中、follow 模式时才自动滚动
    if (props.isStreaming && scrollState.value.mode === 'follow') {
      scrollToBottomSilent()
    }
  }
)

// 监听滚动模式变化 - 切换到 follow 模式时自动滚动到底部
// 解决问题：用户发送消息后，即使后续有意外的模式切换，也能确保滚动到底部
watch(
  () => scrollState.value.mode,
  async (newMode, oldMode) => {
    if (newMode === 'follow' && oldMode === 'browse') {
      console.log('🔄 [Scroll] Mode changed to follow, scrolling to bottom')
      await nextTick()
      forceUpdateScroller()
      await nextTick()
      scrollToBottomSilent()
    }
  }
)

// 监听用户滚轮事件 - 向上滚动切换到 browse 模式
// 注意：handleScroll 也会处理模式切换，但 wheel 事件响应更快
function handleWheel(e: WheelEvent) {
  // Tab 切换中，不处理滚轮事件
  if (isTabSwitching.value) return

  // deltaY < 0 表示向上滚动，切换到 browse 模式
  if (e.deltaY < 0 && scrollState.value.mode === 'follow') {
    const anchor = computeScrollAnchor()
    scrollState.value = {
      mode: 'browse',
      anchor,
      newMessageCount: 0
    }
    console.log('🔄 [Scroll] Switched to browse mode (wheel up)')
  }
}

// 添加滚轮事件监听器（需要在 DynamicScroller 渲染后调用）
let scrollListenersAdded = false
function addScrollListeners() {
  if (scrollListenersAdded) return
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  if (el) {
    el.addEventListener('wheel', handleWheel, { passive: true })
    scrollListenersAdded = true
    console.log('🔄 [Scroll] Wheel listener added')
  }
}

onMounted(() => {
  if (props.isStreaming) {
    startTimer()
  }
  // 延迟添加事件监听，确保 scrollerRef 已挂载
  nextTick(() => {
    addScrollListeners()
  })
  // 监听页面可见性变化，在失去焦点时立即保存锚点
  document.addEventListener('visibilitychange', handleVisibilityChange)
  // 注册 Tab 切换前回调，确保在切换前保存滚动位置
  sessionStore.registerBeforeTabSwitch(handleBeforeTabSwitch)
})

// 页面可见性变化处理
function handleVisibilityChange() {
  if (document.hidden) {
    // 页面即将隐藏，立即保存锚点
    saveAnchorImmediately()
  }
}

// Tab 切换前回调：立即保存当前滚动位置
function handleBeforeTabSwitch(oldTabId: string) {
  // 确保是当前 tab 的回调
  if (sessionStore.currentTabId === oldTabId) {
    saveAnchorImmediately()
    console.log(`💾 [Scroll] Saved anchor before tab switch from ${oldTabId}`)
  }
}

onUnmounted(() => {
  stopTimer()
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  if (el) {
    el.removeEventListener('wheel', handleWheel)
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  // 注销 Tab 切换前回调
  sessionStore.unregisterBeforeTabSwitch(handleBeforeTabSwitch)
})

// 监听 tab 切换，保存旧 tab 滚动位置并恢复新 tab 位置
// 使用 flush: 'sync' 确保在 DOM 更新之前同步执行保存逻辑
watch(
  () => sessionStore.currentTabId,
  async (newTabId, oldTabId) => {
    if (!newTabId || newTabId === oldTabId) return

    // ✅ 切换前：保存旧 tab 的滚动位置
    // 注意：此时 displayMessages 可能已经是新 tab 的数据了
    // 所以我们使用已保存的 anchor，而不是重新计算
    if (oldTabId) {
      const oldTab = sessionStore.tabs.find(t => t.tabId === oldTabId)
      if (oldTab) {
        const oldScrollState = oldTab.uiState.scrollState
        // 如果旧 tab 是 browse 模式，使用已保存的锚点（由 debouncedSaveAnchor 实时保存）
        // 不再重新计算，因为此时 displayMessages 可能已经是新 tab 的数据
        if (oldScrollState.mode === 'browse' && oldScrollState.anchor) {
          console.log(`💾 [Scroll] Using saved anchor for old tab ${oldTabId}: item=${oldScrollState.anchor.itemId}`)
        }
        // 如果是 follow 模式，无需保存（切换回来时自动滚到底部）
      }
    }

    // 标记 tab 切换中，阻止其他滚动逻辑
    isTabSwitching.value = true

    // 获取新 tab 的滚动状态
    const savedScrollState = sessionStore.currentTab?.uiState.scrollState

    // 等待 Vue 渲染 + 浏览器重绘
    await nextTick()
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))

    if (savedScrollState?.mode === 'browse') {
      // browse 模式：尝试恢复锚点位置
      if (savedScrollState.anchor) {
        await restoreScrollPosition(savedScrollState.anchor)
      } else {
        // browse 模式但无锚点：保持当前滚动位置，不强制切换模式
        // 这种情况发生在：用户刚退出跟随模式但还没滚动过
        const el = scrollerRef.value?.$el as HTMLElement | undefined
        if (el) {
          lastScrollTop.value = el.scrollTop
        }
        console.log(`🔄 [Scroll] Browse mode without anchor, keeping current position`)
      }
    } else {
      // follow 模式：滚动到底部
      // 先强制更新虚拟列表，确保所有项目都已渲染
      forceUpdateScroller()
      await nextTick()
      // 使用可靠的滚动方法，因为虚拟列表可能还没完全渲染
      await scrollToBottomReliably()
      const el = scrollerRef.value?.$el as HTMLElement | undefined
      if (el) {
        lastScrollTop.value = el.scrollTop
      }
      // 确保状态为 follow
      if (sessionStore.currentTab) {
        scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0 }
      }
    }

    await nextTick()
    isTabSwitching.value = false
    console.log(`🔄 [Scroll] Tab switched to ${newTabId}, mode=${savedScrollState?.mode ?? 'follow'}`)
  }
)

// 为虚拟列表准备数据源
// 优先使用 displayItems，如果没有则使用 messages（向后兼容）
const displayMessages = computed(() => props.displayItems || props.messages || [])

const isCodexBackend = computed(() => {
  const tabBackend = sessionStore.currentTab?.backendType?.value
  const globalBackend = settingsStore.currentBackendType
  return (tabBackend ?? globalBackend) === 'codex'
})

// 使用新的 DisplayItemRenderer 还是旧的 MessageDisplay
const messageComponent = computed(() => props.displayItems ? DisplayItemRenderer : MessageDisplay)

// 监听 displayMessages 变化，当从空变为有内容时添加事件监听器
watch(
  () => displayMessages.value.length,
  (newLen, oldLen) => {
    if (newLen > 0 && oldLen === 0) {
      // 消息从无到有，需要等待 DynamicScroller 渲染后添加事件监听器
      nextTick(() => {
        addScrollListeners()
      })
    }
  }
)

// 监听消息变化 - 基于双模式的滚动处理
watch(() => displayMessages.value.length, async (newCount, oldCount) => {
  const added = newCount - oldCount
  console.log(`📜 [Scroll] displayMessages.length changed: ${oldCount} -> ${newCount}, added=${added}, mode=${scrollState.value.mode}, isTabSwitching=${isTabSwitching.value}, historyLoadInProgress=${historyLoadInProgress.value}`)

  // Tab 切换中，不处理消息变化
  if (isTabSwitching.value) {
    console.log('📜 [Scroll] Skipped: isTabSwitching')
    return
  }

  // 首次批量加载：跳到底部
  if (oldCount === 0 && newCount > 0) {
    await nextTick()
    scrollToBottom()
    forceUpdateScroller()
    return
  }

  // 历史分页加载中：不滚动，由 isLoading watch 处理
  if (historyLoadInProgress.value) {
    await nextTick()
    forceUpdateScroller()
    return
  }

  // 新消息到达
  if (added > 0) {
    if (scrollState.value.mode === 'browse') {
      // browse 模式：保持位置（newMessageCount 由 useSessionTab 统一管理）
      const el = scrollerRef.value?.$el as HTMLElement | undefined
      const savedScrollTop = el?.scrollTop ?? 0
      await nextTick()
      forceUpdateScroller()
      // 恢复滚动位置
      await nextTick()
      if (el) el.scrollTop = savedScrollTop
    } else {
      // follow 模式：自动滚动到底部
      // 先更新虚拟列表，再滚动（顺序很重要！）
      await nextTick()
      forceUpdateScroller()
      await nextTick()
      scrollToBottomSilent()
    }
  } else {
    // 消息数量没有增加（可能是更新），正常更新 scroller
    await nextTick()
    forceUpdateScroller()
  }
})


// 强制 DynamicScroller 重新计算所有项目尺寸
function forceUpdateScroller() {
  if (scrollerRef.value) {
    scrollerRef.value.forceUpdate?.()
  }
}

watch(() => props.isLoading, async (newValue, oldValue) => {
  // 加载开始时，如果是 follow 模式则保持在底部
  if (newValue && scrollState.value.mode === 'follow') {
    await nextTick()
    scrollToBottom()
  }

  // 加载完成
  if (!newValue && oldValue) {
    if (historyLoadInProgress.value) {
      // 历史分页加载完成：保持滚动位置
      await nextTick()
      const el = scrollerRef.value?.$el as HTMLElement | undefined
      if (el) {
        const delta = el.scrollHeight - historyScrollHeightBefore.value
        el.scrollTop = historyScrollTopBefore.value + delta
      }
      historyLoadInProgress.value = false
      // 重置懒加载请求标志，允许下次加载
      historyLoadRequested.value = false
    } else if (!hasLoadedHistory.value) {
      // 首次加载历史会话完成：自动填满视口并可靠滚动到底部
      hasLoadedHistory.value = true

      await nextTick()
      forceUpdateScroller()

      // 1. 先填满视口
      await ensureScrollable()

      // 2. 再可靠滚动
      await scrollToBottomReliably()

      // 3. 重置懒加载标志，允许后续手动触发
      historyLoadRequested.value = false
      historyLoadInProgress.value = false

      // 确保是 follow 模式
      scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0 }
    }
  }
})

// 处理滚动事件（使用 requestAnimationFrame 节流）
let scrollRAF: number | null = null
function handleScroll() {
  // 使用 RAF 节流，避免滚动时过度计算
  if (scrollRAF) return
  scrollRAF = requestAnimationFrame(() => {
    scrollRAF = null
    handleScrollCore()
  })
}

function handleScrollCore() {
  if (!scrollerRef.value) return

  // Tab 切换中，不处理滚动事件（防止模式被意外切换）
  if (isTabSwitching.value) return

  const el = scrollerRef.value.$el as HTMLElement
  if (!el) return

  const scrollTop = el.scrollTop
  const scrollHeight = el.scrollHeight
  const clientHeight = el.clientHeight
  const distanceFromBottom = scrollHeight - scrollTop - clientHeight

  // 顶部分页 - 触发加载更多历史
  const shouldTrigger = scrollTop < HISTORY_TRIGGER_THRESHOLD &&
    props.hasMoreHistory &&
    !props.isLoading &&
    !historyLoadInProgress.value &&
    !historyLoadRequested.value

  if (shouldTrigger) {
    console.log('✅ [懒加载] 触发加载更多历史')
    historyLoadRequested.value = true
    historyLoadInProgress.value = true
    historyScrollHeightBefore.value = scrollHeight
    historyScrollTopBefore.value = scrollTop
    emit('load-more-history')
  } else if (scrollTop > HISTORY_RESET_THRESHOLD) {
    // 只在加载完成后才重置，避免加载中重置
    if (!historyLoadInProgress.value) {
      historyLoadRequested.value = false
    }
  }

  // 判断是否在底部（允许 50px 的误差）
  const nearBottom = distanceFromBottom < 50
  // 判断滚动方向（必须在更新 lastScrollTop 之前计算！）
  const isScrollingUp = scrollTop < lastScrollTop.value
  // 判断是否有显著的滚动变化（避免微小抖动触发模式切换）
  const significantScroll = Math.abs(scrollTop - lastScrollTop.value) > 5

  // 更新 lastScrollTop
  lastScrollTop.value = scrollTop

  // 模式切换逻辑（基于位置判断）：
  // - follow 模式：始终保持在底部，只有用户明确向上滚动才切换（由 handleWheel 处理）
  // - browse 模式：向下滚动到底部 → 切换回 follow 模式

  if (scrollState.value.mode === 'follow') {
    // follow 模式下：检测用户向上滚动并切换到 browse
    // 必须同时满足：1) 非程序滚动 2) 向上滚动 3) 显著变化（>20px）
    // 增加阈值到 20px 以避免虚拟列表内部调整时的误判
    const isUserScrollUp = !isProgrammaticScroll.value && isScrollingUp && Math.abs(scrollTop - lastScrollTop.value) > 20
    if (isUserScrollUp) {
      // 用户向上滚动，切换到 browse 模式
      const anchor = computeScrollAnchor()
      scrollState.value = {
        mode: 'browse',
        anchor,
        newMessageCount: 0
      }
      console.log('🔄 [Scroll] Switched to browse mode (user scroll up detected)')
    }
  } else {
    // browse 模式下
    if (nearBottom && !isScrollingUp) {
      // 向下滚动到底部，切换回 follow
      scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0 }
      console.log('🔄 [Scroll] Switched to follow mode (reached bottom)')
    } else if (!nearBottom) {
      // 不在底部，保存锚点
      debouncedSaveAnchor()
    }
  }
}

/**
 * 程序调用的滚动到底部（follow 模式下使用）
 * 设置 isProgrammaticScroll 标志，避免触发模式切换
 */
let programmaticScrollCount = 0  // 使用计数器替代布尔值，支持并发调用
function scrollToBottomSilent() {
  programmaticScrollCount++
  isProgrammaticScroll.value = true
  if (scrollerRef.value) {
    scrollerRef.value.scrollToBottom()
  } else if (wrapperRef.value) {
    wrapperRef.value.scrollTop = wrapperRef.value.scrollHeight
  }
  // 延迟重置标志：等待多帧确保 DynamicScroller 的 scroll 事件处理完成
  // 虚拟滚动可能需要多帧才能触发 scroll 事件
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        programmaticScrollCount--
        if (programmaticScrollCount <= 0) {
          programmaticScrollCount = 0
          isProgrammaticScroll.value = false
        }
      })
    })
  })
}

/**
 * 用户主动点击"回到底部"按钮（切换到 follow 模式）
 */
function scrollToBottom() {
  scrollToBottomSilent()
  // 用户主动操作，切换到 follow 模式
  scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0, isNearBottom: true }
  console.log('🔄 [Scroll] User clicked scroll to bottom')
}

/**
 * 检查是否有滚动条（视口是否被填满）
 */
function hasScrollbar(): boolean {
  if (!scrollerRef.value) return false
  const el = scrollerRef.value.$el as HTMLElement
  return el.scrollHeight > el.clientHeight
}

/**
 * 滚动到底部（可靠版本，用于 tab 切换等关键场景）
 */
async function scrollToBottomReliably(): Promise<void> {
  if (!scrollerRef.value) return
  // 设置程序滚动标志，防止被误判为用户滚动
  programmaticScrollCount++
  isProgrammaticScroll.value = true
  scrollerRef.value.scrollToBottom()
  await nextTick()
  // 延迟重置标志：等待多帧确保 scroll 事件处理完成
  await new Promise<void>(resolve => {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          programmaticScrollCount--
          if (programmaticScrollCount <= 0) {
            programmaticScrollCount = 0
            isProgrammaticScroll.value = false
          }
          resolve()
        })
      })
    })
  })
}

/**
 * 自动加载直到填满视口或达到上限
 */
async function ensureScrollable(): Promise<void> {
  // 等待虚拟滚动器渲染
  await nextTick()
  await nextTick()

  let attempts = 0
  const MAX_ATTEMPTS = 10  // 防御性限制
  let totalLoaded = 0  // 记录自动加载的总消息数

  while (attempts < MAX_ATTEMPTS) {
    // 1️⃣ 先检查：视口是否已填满
    if (hasScrollbar()) {
      console.log('✅ 视口已填满，停止自动加载')
      break
    }

    // 2️⃣ 再判断：是否还有更多历史消息
    if (!props.hasMoreHistory) {
      console.log('📭 没有更多历史消息，停止加载（消息数量不足以填满视口）')
      break
    }

    // 3️⃣ 检查：是否超过自动加载上限
    if (totalLoaded >= HISTORY_AUTO_LOAD_MAX) {
      console.log(`📊 已自动加载 ${totalLoaded} 条消息，达到上限 ${HISTORY_AUTO_LOAD_MAX}，停止加载`)
      break
    }

    // 4️⃣ 继续加载
    console.log(`📏 视口未填满且有更多历史，自动加载第 ${attempts + 1} 批...`)
    emit('load-more-history')
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 300))  // 等待加载完成
    totalLoaded += 50  // 假设每次加载50条
    attempts++
  }

  if (attempts >= MAX_ATTEMPTS) {
    console.warn('⚠️ 达到最大尝试次数，停止自动加载')
  }
}
</script>

<style scoped>
.message-list-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0; /* 关键：防止 flex 子元素溢出 */
  background: var(--theme-background, #ffffff);
  position: relative; /* 为 scroll-to-bottom 按钮提供定位上下文 */
}

.message-list {
  flex: 1;
  min-height: 0; /* 关键：防止 flex 子元素溢出 */
  overflow-y: auto !important;
  overflow-x: hidden;
  padding: 4px 6px 4px 6px; /* 减少底部留白 */
  /* 滚动优化 */
  -webkit-overflow-scrolling: touch; /* iOS 惯性滚动 */
  overscroll-behavior: contain; /* 防止滚动穿透 */
  scrollbar-color: var(--theme-scrollbar-thumb, #d1d5da) transparent;
}

/* 修复 vue-virtual-scroller 的默认样式可能导致的内容截断 */
.message-list :deep(.vue-recycle-scroller__item-wrapper) {
  overflow: visible !important;
}

.message-list :deep(.vue-recycle-scroller__item-view) {
  overflow: visible !important;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px;
  color: var(--theme-foreground, #24292e);
}

.empty-content {
  max-width: 520px;
  text-align: center;
}

.shortcut-hints {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}

.shortcut-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  color: var(--theme-secondary-foreground, #6a737d);
}

.shortcut-desc {
  min-width: 80px;
  text-align: left;
}

.keyboard-key {
  display: inline-block;
  padding: 3px 6px;
  font-size: 11px;
  font-family: var(--theme-editor-font-family);
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  color: var(--theme-foreground, #24292e);
  font-weight: 600;
}

/* Streaming 状态指示器 - 左对齐，宽度自适应 */
.streaming-indicator {
  display: inline-flex;
  align-items: center;
  gap: 0;
  padding: 4px 10px;
  margin: 8px 0 4px -6px;
  font-size: 12px;
  font-family: var(--theme-editor-font-family);
  color: var(--theme-secondary-foreground);
}

.generating-text {
  color: #D97706;
  font-weight: 500;
}

.bouncing-dots {
  display: inline-flex;
  margin-right: 4px;
}

.bouncing-dots .dot {
  color: #D97706;
  font-weight: bold;
  animation: bounce 1.4s ease-in-out infinite;
}

.bouncing-dots .dot:nth-child(1) {
  animation-delay: 0s;
}

.bouncing-dots .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.bouncing-dots .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes bounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 1;
  }
  30% {
    transform: translateY(-3px);
    opacity: 0.6;
  }
}

.streaming-stats {
  color: var(--theme-foreground, #24292e);
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  margin: 0 8px 8px 8px;
  background: var(--theme-card-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  color: var(--theme-text-secondary, #586069);
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--theme-border, #e1e4e8);
  border-top-color: var(--theme-primary, #0366d6);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 滚动条样式 */
.message-list::-webkit-scrollbar {
  width: 8px;
}

.message-list::-webkit-scrollbar-track {
  background: transparent;
}

.message-list-wrapper:hover .message-list::-webkit-scrollbar-track,
.message-list::-webkit-scrollbar-track:hover {
  background: var(--theme-scrollbar-track-hover, rgba(0, 0, 0, 0.06));
}

.message-list::-webkit-scrollbar-thumb {
  background: var(--theme-scrollbar-thumb, #d1d5da);
  border-radius: 4px;
}

.message-list::-webkit-scrollbar-thumb:hover {
  background: var(--theme-scrollbar-thumb-hover, #959da5);
}

.message-list-wrapper:hover .message-list {
  scrollbar-color: var(--theme-scrollbar-thumb-hover, #959da5)
    var(--theme-scrollbar-track-hover, rgba(0, 0, 0, 0.06));
}

/* 回到底部按钮 */
.scroll-to-bottom-btn {
  position: absolute;
  bottom: 80px;
  right: 24px;
  width: 48px;
  height: 48px;
  background: var(--theme-panel-background, #f6f8fa);
  color: var(--theme-foreground, #24292e);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.2s ease;
  z-index: 10;
}

.scroll-to-bottom-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  background: var(--theme-hover-background, #f0f0f0);
  border-color: var(--theme-accent, #0366d6);
}

.scroll-to-bottom-btn:active {
  transform: translateY(0);
}

.scroll-to-bottom-btn .btn-icon {
  font-size: 20px;
  font-weight: bold;
}

.new-message-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  background: var(--theme-error, #d73a49);
  color: white;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

/* 过渡动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
