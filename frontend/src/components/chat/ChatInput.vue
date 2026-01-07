<template>
  <div
    class="unified-chat-input-container"
    :class="{ focused: isFocused, generating: isGenerating, 'inline-mode': inline }"
    :style="{
      height: containerHeight ? containerHeight + 'px' : undefined,
      '--input-max-height': INPUT_MAX_HEIGHT + 'px'
    }"
  >
    <!-- 顶部拖拽条 -->
    <div
      class="resize-handle"
      @mousedown="startResize"
    >
      <div class="resize-handle-bar" />
    </div>

    <!-- Pending Task Bar (任务队列显示) -->
    <div
      v-if="visibleTasks.length > 0"
      class="pending-task-bar"
    >
      <div class="task-header">
        {{ t('chat.taskQueueCount', { count: visibleTasks.length }) }}
      </div>
      <div
        v-for="task in visibleTasks"
        :key="task.id"
        class="task-row"
      >
        <span class="task-label">{{ getTaskLabel(task) }}</span>
        <span
          class="task-status"
          :class="`status-${task.status.toLowerCase()}`"
        >
          {{ getTaskStatusText(task.status) }}
        </span>
      </div>
    </div>

    <!-- Top Toolbar (上下文管理工具栏) -->
    <div
      v-if="showContextControls && (contexts.length > 0 || enabled)"
      class="top-toolbar"
    >
      <!-- Add Context 按钮 -->
      <button
        ref="addContextButtonRef"
        class="add-context-btn"
        :disabled="!enabled"
        @click="handleAddContextClick"
      >
        <span class="btn-text">{{ t('chat.addContext') }}</span>
      </button>

      <!-- Active File Tag (当前打开的文件 - 由 IDEA 推送) -->
      <el-tooltip
        v-if="shouldShowActiveFile"
        :content="activeFileTooltip"
        placement="top"
        :show-after="300"
      >
        <div
          class="context-tag active-file-tag"
        >
          <span class="tag-file-name">{{ activeFileName }}</span>
          <span v-if="activeFileLineRange" class="tag-line-range">{{ activeFileLineRange }}</span>
          <button
            class="tag-remove"
            :title="t('common.remove')"
            @click.stop="dismissActiveFile"
          >
            ×
          </button>
        </div>
      </el-tooltip>

      <!-- Context Tags (上下文标签) - 全部显示，自然换行 -->
      <el-tooltip
        v-for="(context, index) in contexts"
        :key="`context-${index}`"
        :content="getContextTooltip(context)"
        placement="top"
        :show-after="300"
      >
        <div
          class="context-tag"
          :class="{
            'image-tag': isImageContext(context),
            'context-disabled': context.disabled
          }"
          @click.stop="handleContextClick(context)"
        >
          <!-- 图片：只显示缩略图，点击可预览 -->
          <template v-if="isImageContext(context)">
            <img
              :src="getContextImagePreviewUrl(context)"
              class="tag-image-preview"
              alt="图片"
            >
          </template>
          <!-- 非图片：显示图标和文字 -->
          <template v-else>
            <span class="tag-icon">{{ getContextIcon(context) }}</span>
            <span class="tag-text">{{ getContextDisplay(context) }}</span>
          </template>
          <button
            class="tag-remove"
            :title="t('common.remove')"
            @click.stop="removeContext(context)"
          >
            ×
          </button>
        </div>
      </el-tooltip>
    </div>

    <!-- 拖放区域提示 -->
    <div
      v-if="isDragging"
      class="drop-zone-overlay"
      @drop.prevent="handleDrop"
      @dragover.prevent
      @dragleave="handleDragLeave"
    >
      <div class="drop-zone-content">
        <span class="drop-icon">📁</span>
        <span class="drop-text">{{ t('chat.dropFileToAddContext') }}</span>
      </div>
    </div>

    <!-- 错误提示区域 -->
    <div
      v-if="currentError"
      class="error-banner"
      @click="handleClearError"
    >
      <span class="error-icon">⚠️</span>
      <span class="error-text">{{ currentError }}</span>
      <button class="error-dismiss" title="关闭">×</button>
    </div>

    <!-- 输入区域 -->
    <div
      class="input-area"
      :class="{ 'generating-state': isGenerating }"
      @click="focusInput"
      @drop.prevent="handleDrop"
      @dragover.prevent="handleDragOver"
      @dragleave="handleDragLeave"
    >

      <RichTextInput
        ref="richTextInputRef"
        v-model="inputText"
        class="message-textarea"
        :placeholder="placeholderText"
        :disabled="!enabled"
        @focus="isFocused = true"
        @blur="isFocused = false"
        @keydown="handleKeydown"
        @paste-image="handlePasteImage"
        @preview-image="handleInputImagePreview"
        @submit="handleRichTextSubmit"
      />
    </div>

    <!-- Bottom Toolbar (底部工具栏) -->
    <div class="bottom-toolbar">
      <!-- 左侧控件组 - Cursor 风格紧凑布局 -->
      <div class="toolbar-left">
        <div class="cursor-style-selectors">
          <!-- Claude 权限模式选择器 - Cursor 风格（带灰色背景） -->
          <el-select
            v-if="showPermissionControls && backendType === 'claude'"
            v-model="selectedPermissionValue"
            class="cursor-selector mode-selector"
            :disabled="!enabled"
            placement="top-start"
            :teleported="true"
            popper-class="chat-input-select-dropdown mode-dropdown claude-mode-dropdown"
            :popper-options="{
              modifiers: [
                {
                  name: 'preventOverflow',
                  options: { boundary: 'viewport' }
                },
                {
                  name: 'flip',
                  options: {
                    fallbackPlacements: ['top-start', 'top'],
                  }
                }
              ]
            }"
            @change="setPermissionMode(selectedPermissionValue)"
          >
            <template #prefix>
              <span class="mode-prefix-icon">{{ getModeIcon(selectedPermissionValue) }}</span>
            </template>
            <el-option value="default" label="Default">
              <span class="mode-option-label">
                <span class="mode-icon">?</span>
                <span>Default</span>
              </span>
            </el-option>
            <el-option value="acceptEdits" label="Accept Edits">
              <span class="mode-option-label">
                <span class="mode-icon">✎</span>
                <span>Accept Edits</span>
              </span>
            </el-option>
            <el-option value="bypassPermissions" label="Bypass">
              <span class="mode-option-label">
                <span class="mode-icon">∞</span>
                <span>Bypass</span>
              </span>
            </el-option>
            <el-option value="plan" label="Plan">
              <span class="mode-option-label">
                <span class="mode-icon">☰</span>
                <span>Plan</span>
              </span>
            </el-option>
          </el-select>

          <!-- ========== Claude 后端控件组 ========== -->
          <template v-if="showModelSelector && backendType === 'claude'">
            <!-- Claude 模型选择器 -->
            <el-select
              v-model="selectedModelValue"
              class="cursor-selector model-selector"
              :disabled="!enabled"
              placement="top-start"
              :teleported="true"
              popper-class="chat-input-select-dropdown claude-model-dropdown"
              :popper-options="{
                modifiers: [
                  {
                    name: 'preventOverflow',
                    options: { boundary: 'viewport' }
                  },
                  {
                    name: 'flip',
                    options: {
                      fallbackPlacements: ['top-start', 'top'],
                    }
                  }
                ]
              }"
              @change="handleBaseModelChange"
            >
              <el-option
                v-for="model in claudeModelOptions"
                :key="model.modelId"
                :value="model.modelId"
                :label="model.displayName"
              >
                <span class="model-option-label">
                  {{ model.displayName }}
                </span>
              </el-option>
            </el-select>

            <!-- Claude 思考级别控件 -->
            <ThinkingToggle
              :thinking-mode="currentThinkingMode"
              :thinking-tokens="thinkingLevel"
              :thinking-levels="thinkingLevels"
              @change="handleThinkingLevelChange"
            />
          </template>

        </div>

        <!-- ========== Codex 工具栏（独立组件） ========== -->
        <CodexToolbar
          v-if="backendType === 'codex'"
          :model="codexModel"
          :sandbox-mode="codexSandboxMode"
          :reasoning-effort="codexReasoningEffort"
          :disabled="!enabled"
          @update:model="handleCodexModelChange"
          @update:sandbox-mode="handleCodexSandboxModeChange"
          @update:reasoning-effort="handleCodexReasoningEffortChange"
        />

        <!-- Skip Permissions 开关（Claude 和 Codex 共用） -->
        <StatusToggle
          v-if="showPermissionControls"
          :label="t('permission.mode.bypass')"
          :enabled="skipPermissionsValue"
          :disabled="!enabled"
          :tooltip="t('permission.mode.bypassTooltip')"
          @toggle="handleSkipPermissionsChange"
        />
      </div>

      <!-- 右侧按钮组 -->
      <div class="toolbar-right">
        <!-- 上下文使用量指示器 -->
        <ContextUsageIndicator
          :session-token-usage="sessionTokenUsage"
        />

        <StatusToggle
          v-if="showContextControls && !inline"
          class="auto-cleanup-toggle"
          :label="t('chat.autoCleanupContext')"
          :enabled="autoCleanupContexts"
          :disabled="!enabled"
          :show-icon="false"
          :tooltip="t('chat.autoCleanupContextTooltip')"
          @toggle="handleAutoCleanupChange"
        />

        <!-- 统计信息 -->
        <div
          v-if="tokenUsage"
          class="token-stats"
          :title="getTokenTooltip()"
        >
          {{ formatTokenUsage(tokenUsage) }}
        </div>

        <!-- 图片上传按钮 - 简洁图标 -->
        <button
          class="icon-btn attach-btn"
          :disabled="!enabled"
          :title="t('chat.uploadImage')"
          @click="handleImageUploadClick"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
          </svg>
        </button>
        <input
          ref="imageInputRef"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/bmp,image/webp"
          multiple
          style="display: none"
          @change="handleImageFileSelect"
        >

        <!-- 发送按钮 - 简洁图标 (三角形播放图标) -->
        <button
          v-if="!isGenerating"
          class="icon-btn send-icon-btn"
          :class="{ active: canSend }"
          :disabled="!canSend"
          :title="t('chat.sendMessageShortcut')"
          @click="handleSend"
          @contextmenu="handleSendButtonContextMenu"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5.14v14l11-7-11-7z"/>
          </svg>
        </button>

        <!-- 停止按钮 - 简洁图标 -->
        <button
          v-else
          class="icon-btn stop-icon-btn"
          :title="t('chat.stopGenerating')"
          @click="$emit('stop')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <rect x="4" y="4" width="16" height="16" rx="2"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Thinking Config Panel (思考配置面板) - Codex 后端使用 -->
    <div
      v-if="showThinkingConfig && backendType === 'codex'"
      class="thinking-config-dropdown"
    >
      <ThinkingConfigPanel
        :backend-type="backendType"
        :model-value="currentThinkingConfig"
        @update:model-value="handleThinkingConfigUpdate"
      />
    </div>

    <!-- Send Button Context Menu (发送按钮右键菜单) -->
    <div
      v-if="showSendContextMenu"
      ref="sendContextMenuRef"
      class="send-context-menu"
      :style="{
        left: sendContextMenuPosition.x + 'px',
        top: sendContextMenuPosition.y + 'px'
      }"
      @click.stop
    >
      <div
        class="context-menu-item"
        @click="handleSendFromContextMenu"
      >
        <span class="menu-icon">📤</span>
        <span class="menu-text">{{ t('common.send') }}</span>
      </div>
      <div
        v-if="isGenerating && hasInput"
        class="context-menu-item"
        @click="handleForceSendFromContextMenu"
      >
        <span class="menu-icon">⚡</span>
        <span class="menu-text">{{ t('chat.interruptAndSend') }}</span>
      </div>
    </div>

    <!-- Context menu：通过全局监听关闭（不使用全屏遮罩） -->
    <!-- Context Selector Popup (上下文选择器弹窗) - 使用统一组件 -->
    <FileSelectPopup
      :visible="showContextSelectorPopup"
      :files="contextSearchResults"
      :anchor-element="addContextButtonRef ?? null"
      :show-search-input="true"
      :placeholder="t('tools.search')"
      :is-indexing="contextIsIndexing"
      @select="handleContextSelect"
      @dismiss="handleContextDismiss"
      @search="handleContextSearch"
    />

    <!-- @ Symbol File Popup (@ 符号文件选择弹窗) -->
    <AtSymbolFilePopup
      :visible="showAtSymbolPopup"
      :files="atSymbolSearchResults"
      :anchor-element="richTextInputElement"
      :at-position="atSymbolPosition"
      :is-indexing="atSymbolIsIndexing"
      @select="handleAtSymbolFileSelect"
      @dismiss="dismissAtSymbolPopup"
    />

    <!-- Slash Command Popup (斜杠命令弹窗) -->
    <SlashCommandPopup
      :visible="showSlashCommandPopup"
      :query="slashCommandQuery"
      :anchor-element="richTextInputElement"
      @select="handleSlashCommandSelect"
      @dismiss="dismissSlashCommandPopup"
    />

    <!-- 图片预览模态框 -->
    <ImagePreviewModal
      :visible="previewVisible"
      :image-src="previewImageSrc"
      image-alt="图片预览"
      @close="closeImagePreview"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { AiModel, type PermissionMode, type EnhancedMessage, type TokenUsage as EnhancedTokenUsage, type ImageReference } from '@/types/enhancedMessage'
import type { ContextReference } from '@/types/display'
import type { ContentBlock } from '@/types/message'
import { jetbrainsRSocket, type ActiveFileInfo } from '@/services/jetbrainsRSocket'
export type { ActiveFileInfo }  // 重新导出供父组件使用
import AtSymbolFilePopup from '@/components/input/AtSymbolFilePopup.vue'
import FileSelectPopup from '@/components/input/FileSelectPopup.vue'
import SlashCommandPopup from '@/components/input/SlashCommandPopup.vue'
import ContextUsageIndicator from './ContextUsageIndicator.vue'
import ImagePreviewModal from '@/components/common/ImagePreviewModal.vue'
import RichTextInput from './RichTextInput.vue'
import ThinkingToggle from './ThinkingToggle.vue'
import StatusToggle from './StatusToggle.vue'
import ThinkingConfigPanel from '@/components/settings/ThinkingConfigPanel.vue'
import CodexToolbar from './CodexToolbar.vue'
import { fileSearchService, type IndexedFileInfo } from '@/services/fileSearchService'
import { jetbrainsBridge } from '@/services/jetbrainsApi'
import { isInAtQuery } from '@/utils/atSymbolDetector'
import { useSessionStore } from '@/stores/sessionStore'
import { useSettingsStore } from '@/stores/settingsStore'
// Composables
import { useImageHandling } from '@/composables/useImageHandling'
import { useDragAndDrop } from '@/composables/useDragAndDrop'
import { useInputResize, INPUT_MAX_HEIGHT } from '@/composables/useInputResize'
import { useModelSelection } from '@/composables/useModelSelection'
import { useContextMenu } from '@/composables/useContextMenu'
// Multi-backend types
import type { BackendType } from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'
import { isCodexThinking, getCodexEffortLevels } from '@/types/thinking'
// Codex types
import type { CodexSandboxMode, CodexReasoningEffort } from '@/types/codex'
import { DEFAULT_CODEX_CONFIG, REASONING_EFFORT_OPTIONS, SANDBOX_MODE_OPTIONS } from '@/types/codex'

interface PendingTask {
  id: string
  type: 'SWITCH_MODEL' | 'QUERY'
  text: string
  alias?: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  realModelId?: string
  error?: string
}

interface TokenUsage {
  inputTokens: number
  outputTokens: number
  cacheCreationTokens: number
  cacheReadTokens: number
  totalTokens: number
}

interface Props {
  pendingTasks?: PendingTask[]
  contexts?: ContextReference[]
  isGenerating?: boolean
  enabled?: boolean
  selectedModel?: AiModel
  actualModelId?: string  // 实际模型ID
  selectedPermission?: PermissionMode
  skipPermissions?: boolean
  showContextControls?: boolean
  autoCleanupContexts?: boolean
  showModelSelector?: boolean
  showPermissionControls?: boolean
  showSendButton?: boolean
  tokenUsage?: TokenUsage
  placeholderText?: string
  messageHistory?: EnhancedMessage[]  // 消息历史（用于Token计算）
  sessionTokenUsage?: EnhancedTokenUsage | null  // 会话级Token使用量
  // 内嵌编辑模式相关
  inline?: boolean           // 是否为内嵌模式（用于编辑消息）
  editDisabled?: boolean     // 是否禁用发送（当前阶段用于编辑模式）
  // Toast 函数
  showToast?: (message: string, duration?: number) => void
  // v-model 支持：输入框文本（用于 Tab 切换时保持状态）
  modelValue?: string
  // Multi-backend support
  backendType?: BackendType  // 后端类型
  thinkingConfig?: ThinkingConfig  // 思考配置
}

interface SendOptions {
  /** 是否是斜杠命令（斜杠命令不发送 contexts） */
  isSlashCommand?: boolean
  /** IDE 上下文（当前打开的文件信息，结构化数据，发送后端时才转换为 XML） */
  ideContext?: ActiveFileInfo | null
}

interface Emits {
  (e: 'send', contents: ContentBlock[], options?: SendOptions): void
  (e: 'force-send', contents: ContentBlock[], options?: SendOptions): void
  (e: 'stop'): void
  (e: 'context-add', context: ContextReference): void
  (e: 'context-remove', context: ContextReference): void
  (e: 'context-toggle', context: ContextReference): void  // 切换启用/禁用
  (e: 'auto-cleanup-change', value: boolean): void
  (e: 'skip-permissions-change', skip: boolean): void
  (e: 'cancel'): void  // 取消编辑（仅 inline 模式）
  (e: 'update:modelValue', value: string): void  // v-model 支持
  (e: 'update:thinkingConfig', value: ThinkingConfig): void  // 思考配置更新
}

const props = withDefaults(defineProps<Props>(), {
  pendingTasks: () => [],
  contexts: () => [],
  isGenerating: false,
  enabled: true,
  selectedModel: AiModel.SONNET,
  selectedPermission: 'default',
  skipPermissions: false,
  showContextControls: true,
  autoCleanupContexts: false,
  showModelSelector: true,
  showPermissionControls: true,
  showSendButton: true,
  placeholderText: '',
  inline: false,
  editDisabled: false,
  modelValue: '',
  backendType: 'claude'  // 默认为 Claude 后端
})

const emit = defineEmits<Emits>()

// i18n & stores
const { t } = useI18n()
const sessionStore = useSessionStore()
const settingsStore = useSettingsStore()

// ========== 初始化 Composables ==========

// 模型选择 composable
const {
  currentModel,
  currentThinkingMode,
  canToggleThinkingComputed: _canToggleThinkingComputed,
  thinkingLevel,
  thinkingLevels,
  thinkingEnabled: _thinkingEnabled,
  selectedPermissionValue,
  skipPermissionsValue,
  baseModelOptions,
  claudeModelOptions,  // Claude 模型列表
  getBaseModelLabel,
  getModeIcon,
  handleBaseModelChange,
  handleThinkingLevelChange,
  toggleThinkingEnabled,
  handleSkipPermissionsChange,
  cyclePermissionMode,
  setPermissionMode,
  updatePermission,
  updateSkipPermissions
} = useModelSelection({
  initialPermission: props.selectedPermission,
  initialSkipPermissions: props.skipPermissions,
  onSkipPermissionsChange: (skip) => emit('skip-permissions-change', skip)
})

// ========== Codex 配置（从 settingsStore 读取，因为 sessionStore 可能没有完整实现） ==========
const codexModel = computed(() => {
  return sessionStore.currentTab?.modelId?.value ?? settingsStore.settings.codexModel ?? DEFAULT_CODEX_CONFIG.model
})

const codexSandboxMode = computed(() => {
  const stored = (settingsStore.settings as any).codexSandboxMode ?? DEFAULT_CODEX_CONFIG.sandboxMode
  return stored === 'full-access' ? 'danger-full-access' : stored
})

const codexReasoningEffort = computed<CodexReasoningEffort>(() => {
  const stored = (settingsStore.settings as any).codexReasoningEffort
  if (stored === undefined || stored === null) {
    return DEFAULT_CODEX_CONFIG.reasoningEffort
  }
  return stored
})

// Codex 事件处理函数
function handleCodexModelChange(model: string) {
  console.log(`🔄 [Codex] 切换模型: ${model}`)
  const tab = sessionStore.currentTab
  if (tab) {
    tab.updateSettings({ model })
  }
  settingsStore.saveSettings({ codexModel: model })
}

function handleCodexSandboxModeChange(mode: CodexSandboxMode) {
  console.log(`🔄 [Codex] 切换沙盒模式: ${mode}`)
  settingsStore.saveSettings({ codexSandboxMode: mode } as any)
  const tab = sessionStore.currentTab
  if (tab?.backendType.value === 'codex' && tab.isConnected.value && !tab.isGenerating.value) {
    tab.reconnect({
      continueConversation: true,
      resumeSessionId: tab.sessionId.value || undefined
    })
  }
}

function handleCodexReasoningEffortChange(effort: CodexReasoningEffort) {
  console.log(`🔄 [Codex] 切换推理深度: ${effort}`)
  settingsStore.saveSettings({ codexReasoningEffort: effort } as any)
}

function cycleCodexReasoningEffort() {
  const options = REASONING_EFFORT_OPTIONS.map(option => option.value)
  const current = codexReasoningEffort.value
  const currentIndex = options.indexOf(current)
  const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % options.length
  handleCodexReasoningEffortChange(options[nextIndex])
}

function cycleCodexSandboxMode() {
  const options = SANDBOX_MODE_OPTIONS.map(option => option.value)
  const rawCurrent = codexSandboxMode.value as unknown as string
  const normalizedCurrent = rawCurrent === 'full-access' ? 'danger-full-access' : rawCurrent
  const currentIndex = options.indexOf(normalizedCurrent as CodexSandboxMode)
  const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % options.length
  handleCodexSandboxModeChange(options[nextIndex])
}

// ========== Multi-Backend Thinking Config ==========

// Thinking config panel visibility
const showThinkingConfig = ref(false)

// Current thinking config (from props or default)
const currentThinkingConfig = computed<ThinkingConfig>(() => {
  if (props.thinkingConfig) {
    return props.thinkingConfig
  }
  // Default config based on backend type
  if (props.backendType === 'codex') {
    return {
      type: 'codex',
      effort: 'medium',
      summary: 'auto'
    }
  } else {
    return {
      type: 'claude',
      enabled: true,
      tokenBudget: 8096
    }
  }
})

// Codex thinking helpers
const codexThinkingEnabled = computed(() => {
  if (isCodexThinking(currentThinkingConfig.value)) {
    return true
  }
  return false
})

const codexEffortLabel = computed(() => {
  if (!isCodexThinking(currentThinkingConfig.value)) return DEFAULT_CODEX_CONFIG.reasoningEffort
  const effort = currentThinkingConfig.value.effort
  if (effort === null) return DEFAULT_CODEX_CONFIG.reasoningEffort

  const levels = getCodexEffortLevels()
  const level = levels.find(l => l.id === effort)
  return level?.label || effort
})

const codexThinkingTooltip = computed(() => {
  if (!isCodexThinking(currentThinkingConfig.value)) return '配置推理强度'
  const effort = currentThinkingConfig.value.effort
  if (effort === null) return '使用默认推理强度'

  const levels = getCodexEffortLevels()
  const level = levels.find(l => l.id === effort)
  return level?.description || '配置推理强度'
})

function toggleThinkingConfigPanel() {
  showThinkingConfig.value = !showThinkingConfig.value
}

function handleThinkingConfigUpdate(config: ThinkingConfig) {
  emit('update:thinkingConfig', config)
}

// Close thinking config panel when clicking outside
function handleGlobalThinkingConfigClick(event: MouseEvent) {
  if (!showThinkingConfig.value) return

  const target = event.target as HTMLElement
  if (
    !target.closest('.thinking-config-dropdown') &&
    !target.closest('.thinking-config-btn')
  ) {
    showThinkingConfig.value = false
  }
}

// 当前错误信息（从 sessionStore 读取）
const currentError = computed(() => sessionStore.currentLastError)

// 清除错误
function handleClearError() {
  sessionStore.clearCurrentError()
}

// Refs
const richTextInputRef = ref<InstanceType<typeof RichTextInput>>()
// 获取 RichTextInput 的 DOM 元素用于 @ 符号弹窗定位
const richTextInputElement = computed(() => {
  return richTextInputRef.value?.$el as HTMLElement | null
})
const addContextButtonRef = ref<HTMLButtonElement>()
const imageInputRef = ref<HTMLInputElement>()

// State
// inputText 使用 computed 支持 v-model，实现多 Tab 输入框状态隔离
const inputText = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value)
})
const isFocused = ref(false)
const showContextSelectorPopup = ref(false)
const contextSearchResults = ref<IndexedFileInfo[]>([])
const contextIsIndexing = ref(false)  // Add Context 弹窗的索引状态

// @ Symbol File Popup State
const showAtSymbolPopup = ref(false)
const atSymbolPosition = ref(0)
const atSymbolSearchResults = ref<IndexedFileInfo[]>([])
const atSymbolIsIndexing = ref(false)  // 是否正在索引

// Slash Command Popup State
const showSlashCommandPopup = ref(false)
const slashCommandQuery = ref('')

// Active File State (当前打开的文件 - 由 IDEA 推送)
const currentActiveFile = ref<ActiveFileInfo | null>(null)
// activeFileDisabled 从 sessionStore 获取，每个 Tab 独立
const activeFileDisabled = computed({
  get: () => {
    const state = sessionStore.currentTab?.uiState as any
    return state?.activeFileDisabled ?? state?.activeFileDismissed ?? false
  },
  set: (value: boolean) => {
    const state = sessionStore.currentTab?.uiState as any
    if (state) {
      state.activeFileDisabled = value
    }
  }
})
let activeFileUnsubscribe: (() => void) | null = null

// 输入框大小调整 composable
const { containerHeight, startResize } = useInputResize()

// 右键菜单 composable
const {
  showSendContextMenu,
  sendContextMenuPosition,
  handleSendButtonContextMenu,
  handleSendFromContextMenu,
  handleForceSendFromContextMenu,
  closeSendContextMenu
} = useContextMenu({
  onSend: () => handleSend(),
  onForceSend: () => handleForceSend()
})

// 图片处理 composable
const {
  previewVisible,
  previewImageSrc,
  readImageAsBase64,
  addImageToContext,
  handlePasteImage: _handlePasteImage,
  handleImageFileSelect: _handleImageFileSelect,
  openImagePreview,
  closeImagePreview,
  getImagePreviewUrl
} = useImageHandling({
  onContextAdd: (ctx) => emit('context-add', ctx),
  onInsertToEditor: (base64, mimeType) => richTextInputRef.value?.insertImage(base64, mimeType),
  isCursorAtStart: () => richTextInputRef.value?.isCursorAtStart() ?? true,
  showToast: props.showToast
})

// 拖放 composable
const {
  isDragging,
  handleDragOver,
  handleDragLeave,
  handleDrop: _handleDrop
} = useDragAndDrop({
  onContextAdd: (ctx) => emit('context-add', ctx),
  onImageAdd: addImageToContext,
  onInsertImageToEditor: (base64, mimeType) => richTextInputRef.value?.insertImage(base64, mimeType),
  isCursorAtStart: () => richTextInputRef.value?.isCursorAtStart() ?? true,
  readImageAsBase64
})

// Local state for props
// selectedModelValue 直接绑定 currentModel（响应会话切换）
const selectedModelValue = computed({
  get: () => currentModel.value,
  set: (_val) => {
    // setter 由 handleBaseModelChange 处理
  }
})

// Computed
const sendContextMenuRef = ref<HTMLElement | null>(null)

function handleGlobalSendContextMenuMouseDown(event: MouseEvent) {
  if (!showSendContextMenu.value) return

  const targetNode = event.target instanceof Node ? event.target : null
  if (targetNode && sendContextMenuRef.value?.contains(targetNode)) {
    return
  }

  // 不吞事件：让本次点击继续传递给真实目标
  closeSendContextMenu()
}

function handleGlobalSendContextMenuContextMenu(event: MouseEvent) {
  if (!showSendContextMenu.value) return

  const targetNode = event.target instanceof Node ? event.target : null
  if (targetNode && sendContextMenuRef.value?.contains(targetNode)) {
    return
  }

  closeSendContextMenu()
}

function bindSendContextMenuGlobalHandlers() {
  document.addEventListener('mousedown', handleGlobalSendContextMenuMouseDown, true)
  document.addEventListener('contextmenu', handleGlobalSendContextMenuContextMenu, true)
  window.addEventListener('blur', closeSendContextMenu)
}

function unbindSendContextMenuGlobalHandlers() {
  document.removeEventListener('mousedown', handleGlobalSendContextMenuMouseDown, true)
  document.removeEventListener('contextmenu', handleGlobalSendContextMenuContextMenu, true)
  window.removeEventListener('blur', closeSendContextMenu)
}

watch(showSendContextMenu, (isOpen) => {
  if (isOpen) {
    bindSendContextMenuGlobalHandlers()
  } else {
    unbindSendContextMenuGlobalHandlers()
  }
})

const visibleTasks = computed(() => {
  return props.pendingTasks.filter(
    task => task.status === 'PENDING' || task.status === 'RUNNING'
  )
})

const hasInput = computed(() => inputText.value.trim().length > 0)

const canSend = computed(() => {
  // 如果是编辑模式且禁用发送，则不能发送
  if (props.editDisabled) return false
  const hasContent = richTextInputRef.value?.getText()?.trim() ||
                     (richTextInputRef.value?.extractContentBlocks()?.length ?? 0) > 0
  // 🔧 移除 isGenerating 限制，允许生成期间发送（消息会加入队列）
  return hasContent && props.enabled
})


const placeholderText = computed(() => {
  return props.placeholderText || ''
})

// 是否应该显示当前打开的文件标签（当有活跃文件且未被关闭时显示）
const shouldShowActiveFile = computed(() => {
  return currentActiveFile.value !== null && !activeFileDisabled.value
})

// 当前活跃文件是否启用（显示时即启用）
const activeFileEnabled = computed(() => {
  return shouldShowActiveFile.value
})

// 从路径中提取文件名
function getFileName(filePath: string): string {
  const parts = filePath.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || filePath
}

// 获取活跃文件的文件名（可能被截断）
const activeFileName = computed(() => {
  if (!currentActiveFile.value) return ''
  return getFileName(currentActiveFile.value.relativePath)
})

// 获取活跃文件的行号范围（包含列信息）
const activeFileLineRange = computed(() => {
  if (!currentActiveFile.value) return ''
  const file = currentActiveFile.value
  if (file.hasSelection && file.startLine && file.endLine) {
    // 选区：显示起始行:列-结束行:列
    const startCol = file.startColumn || 1
    const endCol = file.endColumn || 1
    return `:${file.startLine}:${startCol}-${file.endLine}:${endCol}`
  } else if (file.line) {
    // 光标：显示行:列
    const col = file.column || 1
    return `:${file.line}:${col}`
  }
  return ''
})

// 获取活跃文件的显示文本（只显示文件名和范围）- 保留以兼容其他地方
const activeFileDisplayText = computed(() => {
  if (!currentActiveFile.value) return ''
  const file = currentActiveFile.value
  const fileName = getFileName(file.relativePath)
  if (file.hasSelection && file.startLine && file.endLine) {
    // 有选区时显示行号范围
    return `${fileName}:${file.startLine}-${file.endLine}`
  } else if (file.line) {
    // 有光标位置时显示行号
    return `${fileName}:${file.line}`
  }
  return fileName
})

// XML 转换逻辑已移至 userMessageBuilder.ts 的 ideContextToContentBlocks 函数
// ChatInput 现在传递结构化的 ideContext，由 useSessionMessages 在发送时转换为 XML

// Watch props changes
// Model selection is now driven by settingsStore (UiModelOption)，不再直接依赖 props.selectedModel
watch(() => props.selectedPermission, (newValue) => {
  updatePermission(newValue)
})

watch(() => props.skipPermissions, (newValue) => {
  updateSkipPermissions(newValue)
})

// Watch input text for @ symbol and slash command detection
// 光标位置变化通过 keydown 事件触发检测
watch(inputText, () => {
  checkAtSymbol()
  checkSlashCommand()
})

// Methods
function focusInput() {
  richTextInputRef.value?.focus()
}

function adjustHeight() {
  // RichTextInput 自动处理高度，这里保留空实现以兼容现有调用
}

/**
 * 处理 RichTextInput 的图片粘贴事件
 */
async function handlePasteImage(file: File) {
  await _handlePasteImage(file)
}

/**
 * 检测文本是否以斜杠命令开头
 */
function isSlashCommandText(text: string): boolean {
  const trimmed = text.trim()
  return trimmed.startsWith('/') && /^\/\w+/.test(trimmed)
}

/**
 * 处理 RichTextInput 的提交事件
 * 注意：即使正在生成，也允许发送（父组件会自动将消息加入队列）
 */
async function handleRichTextSubmit(_content: { text: string; images: { id: string; data: string; mimeType: string; name: string }[] }) {
  if (!props.enabled) return

  // 使用新方法提取有序内容块
  const contents = richTextInputRef.value?.extractContentBlocks() || []

  if (contents.length === 0) return

  // 检测是否是斜杠命令
  const text = richTextInputRef.value?.getText() || ''
  const isSlashCommand = isSlashCommandText(text)

  // 关闭斜杠命令弹窗
  dismissSlashCommandPopup()

  // 获取 IDE 上下文（当前打开的文件，结构化数据）
  // 斜杠命令不需要 IDE 上下文
  const ideContext = (!isSlashCommand && activeFileEnabled.value) ? currentActiveFile.value : null

  // 发送消息（父组件的 enqueueMessage 会自动处理队列逻辑）
  // 注意：不再转换 XML，传递结构化的 ideContext
  emit('send', contents, { isSlashCommand, ideContext })

  // 清理
  richTextInputRef.value?.clear()
  inputText.value = ''
}

// @ Symbol File Reference Functions
async function checkAtSymbol() {
  // 使用 RichTextInput 的光标位置
  const cursorPosition = richTextInputRef.value?.getCursorPosition() ?? 0
  // 使用纯文本进行 @ 符号检测（inputText 现在是 HTML 格式）
  const plainText = richTextInputRef.value?.getText() ?? ''
  const atResult = isInAtQuery(plainText, cursorPosition)

  if (atResult) {
    // 在 @ 查询中
    atSymbolPosition.value = atResult.atPosition

    // 搜索文件（空查询时返回项目根目录文件）
    try {
      const result = await fileSearchService.searchFiles(atResult.query, 10)
      atSymbolSearchResults.value = result.files
      atSymbolIsIndexing.value = result.isIndexing
      // 有结果或正在索引时显示弹窗
      showAtSymbolPopup.value = result.files.length > 0 || result.isIndexing
    } catch (error) {
      console.error('文件搜索失败:', error)
      atSymbolSearchResults.value = []
      atSymbolIsIndexing.value = false
      showAtSymbolPopup.value = false
    }
  } else {
    // 不在 @ 查询中
    showAtSymbolPopup.value = false
    atSymbolSearchResults.value = []
    atSymbolIsIndexing.value = false
  }
}

function handleAtSymbolFileSelect(file: IndexedFileInfo) {
  // 使用 RichTextInput 的方法删除 @ 查询并插入文件引用节点
  const cursorPosition = richTextInputRef.value?.getCursorPosition() ?? 0

  // 删除从 @ 位置到当前光标位置的文本，然后插入文件引用节点（使用绝对路径）
  richTextInputRef.value?.replaceRangeWithFileReference(
    atSymbolPosition.value,
    cursorPosition,
    file.absolutePath
  )

  // 关闭弹窗
  dismissAtSymbolPopup()
}

function dismissAtSymbolPopup() {
  showAtSymbolPopup.value = false
  atSymbolSearchResults.value = []
  atSymbolIsIndexing.value = false
}

// Slash Command Functions
// 已知的斜杠命令列表（保留以备将来使用）
const _knownSlashCommands = ['/compact', '/context', '/rename']

function checkSlashCommand() {
  // 使用纯文本检测斜杠命令（inputText 现在是 HTML 格式）
  const text = richTextInputRef.value?.getText() ?? ''  // 不使用 trim，保留空格以检测命令是否已完成

  // 只有当输入以 / 开头时才显示斜杠命令弹窗
  if (text.startsWith('/')) {
    // 检查是否有空格/tab 等不可见字符
    const hasWhitespace = /\s/.test(text)

    // 如果有空白字符，不显示弹窗（和上下文选择器一样）
    if (hasWhitespace) {
      showSlashCommandPopup.value = false
      slashCommandQuery.value = ''
      return
    }

    // 提取查询内容（/ 后面的部分）
    slashCommandQuery.value = text.slice(1)
    showSlashCommandPopup.value = true
  } else {
    showSlashCommandPopup.value = false
    slashCommandQuery.value = ''
  }
}

interface SlashCommand {
  name: string
  description: string
}

function handleSlashCommandSelect(cmd: SlashCommand) {
  // 替换输入框内容为选中的命令（末尾加空格）
  richTextInputRef.value?.setContent(cmd.name + ' ')
  inputText.value = cmd.name + ' '
  showSlashCommandPopup.value = false
  slashCommandQuery.value = ''

  // 聚焦输入框
  nextTick(() => {
    richTextInputRef.value?.focus()
  })
}

function dismissSlashCommandPopup() {
  showSlashCommandPopup.value = false
  slashCommandQuery.value = ''
}

async function handleKeydown(event: KeyboardEvent) {
  // 光标移动键 - 重新检测 @ 符号
  if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) {
    nextTick(() => checkAtSymbol())
  }

  // ESC 键处理
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation() // 防止全局监听器重复触发
    // 如果思考配置面板打开，先关闭它
    if (showThinkingConfig.value) {
      showThinkingConfig.value = false
      return
    }
    // 如果正在生成，打断生成
    if (props.isGenerating) {
      emit('stop')
      return
    }
    // 如果是 inline 模式，取消编辑
    if (props.inline) {
      emit('cancel')
      return
    }
  }

  // Shift + Tab - 轮换切换权限模式
  if (
    event.key === 'Tab' &&
    event.shiftKey &&
    !event.ctrlKey &&
    !event.metaKey
  ) {
    event.preventDefault()
    if (props.backendType === 'claude') {
      cyclePermissionMode()
    } else if (props.backendType === 'codex') {
      cycleCodexSandboxMode()
    }
    return
  }

  // Tab - 切换思考开关（仅 Claude 后端）
  if (
    event.key === 'Tab' &&
    !event.shiftKey &&
    !event.ctrlKey &&
    !event.metaKey &&
    props.backendType === 'claude'
  ) {
    event.preventDefault()
    await toggleThinkingEnabled('keyboard')
    return
  }
  // Tab - 切换推理强度（Codex 后端）
  if (
    event.key === 'Tab' &&
    !event.shiftKey &&
    !event.ctrlKey &&
    !event.metaKey &&
    props.backendType === 'codex'
  ) {
    event.preventDefault()
    cycleCodexReasoningEffort()
    return
  }
  // 如果 @ 符号弹窗显示，键盘事件由弹窗组件处理
  // 这里不需要额外处理，因为 AtSymbolFilePopup 组件会监听全局键盘事件

  // Ctrl+Enter - 强制发送（打断当前生成并发送）
  if (event.key === 'Enter' && event.ctrlKey && !event.shiftKey && !event.altKey) {
    event.preventDefault()
    handleForceSend()
    return
  }

  // Ctrl+B - 将当前任务切换到后台运行
  if (event.key === 'b' && event.ctrlKey && !event.shiftKey && !event.altKey) {
    event.preventDefault()
    handleRunInBackground()
    return
  }

  // Shift+Enter - 插入换行（默认行为）
  if (event.key === 'Enter' && event.shiftKey) {
    // 默认行为已经会插入换行，不需要额外处理
    return
  }

  // Ctrl+J - 插入换行（需要主动处理，浏览器默认行为不是换行）
  if (event.key === 'j' && event.ctrlKey && !event.shiftKey && !event.altKey) {
    event.preventDefault()
    richTextInputRef.value?.insertNewLine()
    return
  }

  // Ctrl+U - 清空光标位置到行首
  if (event.key === 'u' && event.ctrlKey && !event.shiftKey && !event.altKey) {
    event.preventDefault()
    richTextInputRef.value?.deleteToLineStart()
    return
  }

  // Enter 键由 RichTextInput 的 @submit 事件处理，这里不再重复处理
}

// toggleThinkingEnabled, cyclePermissionMode, getBaseModelLabel, getModeIcon,
// handleBaseModelChange, handleThinkingToggle, handleSkipPermissionsChange
// 这些函数现在由 useModelSelection composable 提供

/**
 * 处理后台运行快捷键
 */
async function handleRunInBackground() {
  if (!props.isGenerating) {
    return  // 没有正在执行的任务，忽略
  }

  try {
    await sessionStore.runInBackground()
    console.log('✅ [ChatInput] 后台运行请求已发送')
  } catch (err) {
    console.error('[ChatInput] 后台运行请求失败:', err)
  }
}

async function handleSend() {
  if (!canSend.value) return

  // 使用新方法提取有序内容块
  const contents = richTextInputRef.value?.extractContentBlocks() || []

  if (contents.length > 0) {
    // 检测是否是斜杠命令
    const text = richTextInputRef.value?.getText() || ''
    const isSlashCommand = isSlashCommandText(text)

    // 关闭斜杠命令弹窗
    dismissSlashCommandPopup()

    // 获取 IDE 上下文（当前打开的文件，结构化数据）
    // 斜杠命令不需要 IDE 上下文
    const ideContext = (!isSlashCommand && activeFileEnabled.value) ? currentActiveFile.value : null

    // 发送消息（父组件会处理队列逻辑和 XML 转换）
    emit('send', contents, { isSlashCommand, ideContext })

    // 清理输入框
    richTextInputRef.value?.clear()
    inputText.value = ''
    adjustHeight()
  }
}

async function handleForceSend() {
  // 使用新方法提取有序内容块
  const contents = richTextInputRef.value?.extractContentBlocks() || []

  if (contents.length === 0 || !props.isGenerating) return

  // 获取 IDE 上下文（当前打开的文件，结构化数据）
  const ideContext = activeFileEnabled.value ? currentActiveFile.value : null

  // 发送消息（父组件会处理 XML 转换）
  emit('force-send', contents, { ideContext })

  // 清理
  richTextInputRef.value?.clear()
  inputText.value = ''
  adjustHeight()
}

// handleSendButtonContextMenu, handleSendFromContextMenu, handleForceSendFromContextMenu, closeSendContextMenu
// 这些函数现在由 useContextMenu composable 提供

function removeContext(context: ContextReference) {
  emit('context-remove', context)
}

function handleContextClick(context: ContextReference) {
  // 图片类型：打开预览
  if (isImageContext(context)) {
    openContextImagePreview(context)
    return
  }
  // 非图片类型：切换启用/禁用状态
  emit('context-toggle', context)
}

function toggleContext(context: ContextReference) {
  emit('context-toggle', context)
}

// 关闭当前活跃文件标签
function dismissActiveFile() {
  activeFileDisabled.value = true
}

function handleAutoCleanupChange(value: boolean) {
  emit('auto-cleanup-change', value)
}

async function handleAddContextClick() {
  showContextSelectorPopup.value = true

  // 空查询返回项目根目录文件
  try {
    const result = await fileSearchService.searchFiles('', 10)
    contextSearchResults.value = result.files
    contextIsIndexing.value = result.isIndexing
  } catch (error) {
    console.error('获取文件失败:', error)
    contextSearchResults.value = []
    contextIsIndexing.value = false
  }
}

async function handleContextSearch(query: string) {
  const trimmedQuery = query.trim()

  // 统一使用 searchFiles（空查询返回项目根目录文件）
  try {
    const result = await fileSearchService.searchFiles(trimmedQuery, 10)
    contextSearchResults.value = result.files
    contextIsIndexing.value = result.isIndexing
  } catch (error) {
    console.error('文件搜索失败:', error)
    contextSearchResults.value = []
    contextIsIndexing.value = false
  }
}

function handleContextDismiss() {
  showContextSelectorPopup.value = false
  contextSearchResults.value = []
  contextIsIndexing.value = false
}

function handleContextSelect(result: IndexedFileInfo) {
  // 将文件转换为 ContextReference
  const contextRef: ContextReference = {
    type: 'file',
    uri: result.relativePath,
    displayType: 'TAG',
    path: result.relativePath,
    fullPath: result.absolutePath,  // 使用绝对路径作为完整路径（用于 tooltip 显示）
    name: result.name
  }

  emit('context-add', contextRef)
  showContextSelectorPopup.value = false
  contextSearchResults.value = []
}


/**
 * 获取上下文完整路径（用于 tooltip 显示）
 */
function getContextFullPath(context: ContextReference): string {
  if (isImageReference(context)) {
    return context.name || '图片'
  }
  if (isFileReference(context)) {
    return context.fullPath || context.path || context.uri || ''
  }
  if (isUrlReference(context)) {
    return context.url || ''
  }
  return context.uri || ''
}

/**
 * 获取上下文 tooltip（包含路径，图片可点击预览，文件可点击打开）
 */
function getContextTooltip(context: ContextReference): string {
  const path = getContextFullPath(context)
  if (isImageContext(context)) {
    return `${path}\n${t('chat.clickToPreview')}`
  }
  if (isFileReference(context)) {
    return `${path}\n${t('chat.clickToOpenFile')}`
  }
  return path
}

const activeFileTooltip = computed(() => {
  if (!currentActiveFile.value) return ''
  return currentActiveFile.value.path || currentActiveFile.value.relativePath || ''
})

/**
 * 获取上下文显示文本（使用类型守卫）- 只显示文件名
 */
function getContextDisplay(context: ContextReference): string {
  if (isImageReference(context)) {
    return '图片'  // 简化显示，不显示无意义的文件名
  }
  if (isFileReference(context)) {
    // 只显示文件名，不显示完整路径
    const path = context.path || context.uri || ''
    const parts = path.replace(/\\/g, '/').split('/')
    return parts[parts.length - 1] || path
  }
  if (isUrlReference(context)) {
    return context.title || context.url || ''
  }
  // 默认情况下也只取最后一部分
  const uri = context.uri || ''
  const parts = uri.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || uri
}

/**
 * 获取图片预览 URL（使用类型守卫）
 */
function getContextImagePreviewUrl(context: ContextReference): string {
  if (isImageReference(context)) {
    return getImagePreviewUrl(context as ImageReference)
  }
  return ''
}

/**
 * 打开上下文中的图片预览
 */
function openContextImagePreview(context: ContextReference) {
  if (isImageReference(context)) {
    openImagePreview(getContextImagePreviewUrl(context))
  }
}

// closeImagePreview 现在由 useImageHandling composable 提供

/**
 * 处理输入框中图片预览
 */
function handleInputImagePreview(src: string) {
  openImagePreview(src)
}

/**
 * 获取上下文图标（使用类型守卫）
 */
function getContextIcon(context: ContextReference): string {
  if (isImageReference(context)) return '🖼️'
  if (isFileReference(context)) return '📄'
  if (isUrlReference(context)) return '🌐'
  if (context.type === 'folder') return '📁'
  if ('path' in context) return '📄'
  return '📎'
}

function getTaskLabel(task: PendingTask): string {
  if (task.type === 'SWITCH_MODEL') {
    return `/model ${task.alias}`
  }
  return task.text.trim()
}

function getTaskStatusText(status: string): string {
  const map: Record<string, string> = {
    PENDING: t('chat.taskStatus.pending'),
    RUNNING: t('chat.taskStatus.running'),
    SUCCESS: t('chat.taskStatus.success'),
    FAILED: t('chat.taskStatus.failed')
  }
  return map[status] || status
}

function formatTokenUsage(usage: TokenUsage): string {
  // For Codex backend, token display might be different
  if (props.backendType === 'codex') {
    // Codex may not provide detailed token breakdown
    return `${usage.totalTokens} tokens`
  }
  // Claude backend
  return `${usage.totalTokens} tokens`
}

function getTokenTooltip(): string {
  if (!props.tokenUsage) return ''
  const u = props.tokenUsage

  // For Codex backend, tooltip might be simpler
  if (props.backendType === 'codex') {
    return `Total: ${u.totalTokens} tokens`
  }

  // Claude backend - detailed breakdown
  return t('chat.tokenTooltip', {
    input: u.inputTokens,
    output: u.outputTokens,
    cacheCreation: u.cacheCreationTokens,
    cacheRead: u.cacheReadTokens
  })
}

// handleDragOver, handleDragLeave, handleDrop, addFileToContext
// 这些函数现在由 useDragAndDrop composable 提供

// 包装 _handleDrop 用于模板
async function handleDrop(event: DragEvent) {
  await _handleDrop(event)
}

// 图片上传功能
function handleImageUploadClick() {
  imageInputRef.value?.click()
}

async function handleImageFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  await _handleImageFileSelect(input.files)
  // 清空 input，允许重复选择同一文件
  input.value = ''
}

/**
 * 类型守卫：检查是否为图片上下文
 */
function isImageReference(context: ContextReference): context is ImageReference {
  return context.type === 'image'
}

// 别名，用于模板中调用
const isImageContext = isImageReference

/**
 * 类型守卫：检查是否为文件上下文
 */
function isFileReference(context: ContextReference): boolean {
  return context.type === 'file'
}

/**
 * 类型守卫：检查是否为 URL 上下文
 */
function isUrlReference(context: ContextReference): boolean {
  return 'url' in context || context.type === 'web'
}

// addImageToContext 和 readImageAsBase64 现在由 useImageHandling composable 提供

/**
 * 暴露方法供父组件调用（用于编辑队列消息时恢复内容）
 */
defineExpose({
  /**
   * 设置输入框内容（从 ContentBlock[] 恢复）
   */
  setContent(contents: ContentBlock[]) {
    // 清空当前状态
    richTextInputRef.value?.clear()
    inputText.value = ''

    // 解析 contents 填充到编辑器
    for (const block of contents) {
      if (block.type === 'text' && 'text' in block) {
        // 文本块：设置到编辑器
        richTextInputRef.value?.setContent((block as any).text)
      } else if (block.type === 'image' && 'source' in block) {
        // 图片块：插入到编辑器
        const imageBlock = block as any
        if (imageBlock.source?.type === 'base64') {
          richTextInputRef.value?.insertImage(
            imageBlock.source.data,
            imageBlock.source.media_type
          )
        }
      }
    }

    // 调整高度
    adjustHeight()
  }
})

// 全局键盘事件处理（用于在任何焦点状态下响应 ESC 停止生成）
function handleGlobalKeydown(event: KeyboardEvent) {
  // ESC 键停止生成（全局监听，确保任何时候都能响应）
  if (event.key === 'Escape' && props.isGenerating) {
    event.preventDefault()
    event.stopPropagation()
    console.log('🛑 [GlobalKeydown] ESC pressed, stopping generation')
    emit('stop')
  }
}

// Lifecycle
onMounted(() => {
  nextTick(() => {
    setTimeout(() => {
      focusInput()
    }, 200)
  })

  // 添加全局键盘监听
  document.addEventListener('keydown', handleGlobalKeydown)

  // 添加全局点击监听（关闭思考配置面板）
  document.addEventListener('mousedown', handleGlobalThinkingConfigClick)

  // 订阅活跃文件变更
  activeFileUnsubscribe = jetbrainsRSocket.onActiveFileChange((file) => {
    currentActiveFile.value = file
    // 当新文件推送过来时，重置禁用状态
    activeFileDisabled.value = false
    console.log('📂 [ChatInput] 活跃文件更新:', file?.relativePath || '无')
  })

  // 初始化时主动获取当前活跃文件（解决前端启动时 IDE 已打开文件的场景）
  jetbrainsRSocket.getActiveFile().then((file) => {
    if (file) {
      currentActiveFile.value = file
      console.log('📂 [ChatInput] 初始活跃文件:', file.relativePath)
    }
  }).catch((error) => {
    console.warn('📂 [ChatInput] 获取初始活跃文件失败:', error)
  })
})

onUnmounted(() => {
  unbindSendContextMenuGlobalHandlers()
  // 移除全局键盘监听
  document.removeEventListener('keydown', handleGlobalKeydown)
  // 移除全局点击监听
  document.removeEventListener('mousedown', handleGlobalThinkingConfigClick)
  // 取消订阅活跃文件变更
  if (activeFileUnsubscribe) {
    activeFileUnsubscribe()
    activeFileUnsubscribe = null
  }
})
</script>

<style scoped>
.unified-chat-input-container {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--theme-panel-background, #f6f8fa);
  border: 1.5px solid var(--theme-border, #e1e4e8);
  border-radius: 12px;
  overflow: visible;  /* 允许拖拽手柄超出 */
}

/* 顶部拖拽手柄 */
.resize-handle {
  position: absolute;
  top: -4px;
  left: 0;
  right: 0;
  height: 12px;
  cursor: ns-resize;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
}

.resize-handle:hover .resize-handle-bar,
.resize-handle:active .resize-handle-bar {
  opacity: 1;
  background: var(--theme-accent, #0366d6);
}

.resize-handle-bar {
  width: 48px;
  height: 4px;
  background: var(--theme-border, #d0d7de);
  border-radius: 2px;
  opacity: 0.3;
  transition: all 0.2s;
}

.unified-chat-input-container.focused {
  border-color: var(--theme-accent, #0366d6);
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.1);
}

.unified-chat-input-container.generating {
  border-color: var(--theme-accent, #0366d6);
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.15);
  animation: generating-pulse 2s ease-in-out infinite;
}

/* Inline 模式样式 - 用于编辑消息 */
.unified-chat-input-container.inline-mode {
  border-radius: 8px;
  margin: 0;
}

@keyframes generating-pulse {
  0%, 100% {
    box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.15);
  }
  50% {
    box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.25);
  }
}

/* Drop Zone Overlay */
.drop-zone-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(3, 102, 214, 0.1);
  border: 2px dashed var(--theme-accent, #0366d6);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  pointer-events: none;
}

.drop-zone-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px;
  background: var(--theme-background, #ffffff);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.drop-icon {
  font-size: 48px;
}

.drop-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-accent, #0366d6);
}

/* Pending Task Bar */
.pending-task-bar {
  padding: 6px 12px;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-info-background, #f0f8ff);
}

.task-header {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-text-info, #0366d6);
  margin-bottom: 8px;
}

.task-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  margin-bottom: 4px;
  background: var(--theme-background, #ffffff);
  border-radius: 6px;
}

.task-label {
  flex: 1;
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.task-status.status-pending {
  background: var(--theme-warning, #ffc107);
  color: #000;
}

.task-status.status-running {
  background: var(--theme-accent, #0366d6);
  color: #fff;
}

/* Top Toolbar - 紧凑布局 */
.top-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px 10px;
}

.add-context-btn {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  height: 18px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  background: var(--theme-background, #ffffff);
  color: var(--theme-foreground, #24292e);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-context-btn:hover:not(:disabled) {
  background: var(--theme-hover-background, #f6f8fa);
  border-color: var(--theme-accent, #0366d6);
}

.add-context-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.add-image-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  height: 20px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  background: var(--theme-background, #ffffff);
  color: var(--theme-foreground, #24292e);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-image-btn:hover:not(:disabled) {
  background: var(--theme-hover-background, #f6f8fa);
  border-color: var(--theme-accent, #0366d6);
}

.add-image-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.context-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 3px;
  font-size: 11px;
  cursor: pointer;
  transition: opacity 0.2s, background 0.2s;
}

.context-tag:hover {
  background: var(--theme-hover-background, #f6f8fa);
}

.context-tag.image-tag {
  position: relative;
  padding: 2px;
}

/* 活跃文件标签 - 紧凑样式 */
.context-tag.active-file-tag {
  background: rgba(3, 102, 214, 0.08);
  border-color: var(--theme-accent, #0366d6);
}

.context-tag.active-file-tag .tag-icon {
  color: var(--theme-accent, #0366d6);
  font-size: 10px;
  flex-shrink: 0;
}

.context-tag.active-file-tag .tag-file-name {
  color: var(--theme-accent, #0366d6);
  font-weight: 500;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-tag.active-file-tag .tag-line-range {
  color: var(--theme-accent, #0366d6);
  font-weight: 600;
  flex-shrink: 0;
  white-space: nowrap;
}

/* 禁用状态的 context 标签 */
.context-tag.context-disabled {
  opacity: 0.5;
  border-style: dashed;
  background: var(--theme-hover-background, #f6f8fa);
}

.context-tag.context-disabled .tag-text {
  text-decoration: line-through;
}

.context-tag.context-disabled .tag-icon {
  opacity: 0.6;
}

/* 图片标签的删除按钮 - 右上角叠加 */
.context-tag.image-tag .tag-remove {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 14px;
  height: 14px;
  font-size: 10px;
  background: var(--theme-error, #d73a49);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  opacity: 0;
  transition: opacity 0.15s;
}

.context-tag.image-tag:hover .tag-remove {
  opacity: 1;
}

.tag-image-preview {
  width: 24px;
  height: 24px;
  object-fit: cover;
  border-radius: 3px;
  border: 1px solid var(--theme-border, #e1e4e8);
  cursor: pointer;
  transition: transform 0.15s;
}

.tag-image-preview:hover {
  transform: scale(1.05);
}

.tag-icon {
  font-size: 10px;
}

.tag-text {
  color: var(--theme-link, #0366d6);
  font-family: monospace;
  font-size: 11px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-remove {
  padding: 0;
  width: 14px;
  height: 14px;
  border: none;
  background: transparent;
  color: var(--theme-secondary-foreground, #586069);
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
}

.tag-remove:hover {
  color: var(--theme-error, #d73a49);
}

.context-more-hint {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  color: var(--theme-secondary-foreground, #6a737d);
  cursor: default;
}

/* Input Area - 更大的输入区域 */
.input-area {
  position: relative;
  padding: 10px 12px;
  cursor: text;
  min-height: 60px;
  max-height: var(--input-max-height);  /* 使用 CSS 变量，由 JS 设置 */
  overflow-y: auto;
  overflow-x: hidden;
}

/* 当容器有固定高度时，input-area 填充剩余空间 */
.unified-chat-input-container[style*="height"] .input-area {
  flex: 1;
}

/* 移除生成状态的额外 padding，保持输入框高度一致 */
/* .input-area.generating-state {
  padding-top: 32px;
} */

/* 生成中指示器 */
.generating-indicator {
  position: absolute;
  top: 8px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  z-index: 1;
}

.generating-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--theme-border, #e1e4e8);
  border-top-color: var(--theme-accent, #0366d6);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.generating-text {
  font-size: 12px;
  color: var(--theme-accent, #0366d6);
  font-weight: 500;
}

.message-textarea {
  width: 100%;
  min-height: 50px;
  height: 100%;  /* 填充父容器 */
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  line-height: 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: transparent;
  color: var(--theme-foreground, #24292e);
}

.message-textarea::placeholder {
  color: var(--theme-text-disabled, #6a737d);
}

.message-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Bottom Toolbar - 紧凑布局 */
.bottom-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 10px;
  border-top: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-panel-background, #f6f8fa);
  position: relative;
  z-index: 5;  /* 确保工具栏在输入区域之上 */
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auto-cleanup-toggle {
  white-space: nowrap;
}

/* ========== Cursor 风格选择器容器 ========== */
.cursor-style-selectors {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ========== Cursor 风格选择器 - 自动宽度 ========== */
.cursor-style-selectors :deep(.el-select.cursor-selector) {
  font-size: 11px;
  flex: 0 0 auto;
  margin: 0;
  /* 关键：覆盖 Element Plus 的 CSS 变量 */
  --el-select-width: auto !important;
  width: auto !important;
  display: inline-block;
}

/* 模式选择器 - 带灰色背景 */
.cursor-style-selectors :deep(.el-select.mode-selector .el-select__wrapper) {
  background: rgba(0, 0, 0, 0.08) !important;
  border-radius: 4px;
  padding: 2px 6px;
}

/* 模式选择器前缀图标 */
.mode-prefix-icon {
  font-size: 12px;
  color: var(--theme-secondary-foreground, #6a737d);
  margin-right: 2px;
  flex-shrink: 0;
}

/* 内部 wrapper - 自适应宽度 */
.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__wrapper) {
  display: inline-flex !important;
  width: auto !important;
  min-width: unset !important;
  padding: 2px 4px;
  border: none !important;
  border-radius: 4px;
  background: transparent !important;
  box-shadow: none !important;
  min-height: 20px;
  gap: 2px;
}

.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__wrapper:hover) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05)) !important;
}

.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__wrapper.is-focused) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05)) !important;
  box-shadow: none !important;
}

.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__placeholder) {
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 11px;
}

/* 选中项文本 - 不截断，自动宽度 */
.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__selection) {
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 11px;
  /* 关键：覆盖 Element Plus 的 flex: 1 */
  flex: 0 0 auto !important;
  min-width: 0;
  overflow: visible;
}

.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__selected-item) {
  white-space: nowrap;
  overflow: visible;
  flex-shrink: 0;
}

/* 隐藏 placeholder 的绝对定位，避免影响宽度 */
.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__placeholder) {
  position: static !important;
  width: auto !important;
  transform: none !important;
}

/* 确保内部 input wrapper 不影响宽度 */
.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__input-wrapper) {
  flex: 0 0 auto !important;
  width: 0 !important;
}

.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__input-wrapper.is-hidden) {
  display: none !important;
}

/* 下拉箭头 */
.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__suffix) {
  color: var(--theme-secondary-foreground, #9ca3af);
  margin-left: 0;
  flex-shrink: 0;
}

.cursor-style-selectors :deep(.el-select.cursor-selector .el-select__suffix .el-icon) {
  font-size: 12px;
}

.cursor-style-selectors :deep(.el-select.cursor-selector.is-disabled .el-select__wrapper) {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ========== 模式选择器下拉选项样式 ========== */
.mode-option-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.mode-option-label .mode-icon {
  font-size: 14px;
  width: 16px;
  text-align: center;
  color: var(--theme-secondary-foreground, #6a737d);
}

/* 模式下拉弹层样式 - 移动到全局样式块 */


.cursor-style-selectors :deep(.el-select.model-selector .el-select__suffix),
.cursor-style-selectors :deep(.el-select.mode-selector .el-select__suffix) {
  color: var(--theme-secondary-foreground, #6a737d);
}

.cursor-style-selectors :deep(.el-select.model-selector.is-disabled .el-select__wrapper),
.cursor-style-selectors :deep(.el-select.mode-selector.is-disabled .el-select__wrapper) {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--theme-panel-background, #f6f8fa);
}

/* ========== Codex Thinking Control ========== */
.codex-thinking-control {
  display: inline-flex;
  align-items: center;
}

.thinking-config-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  background: var(--theme-background, #ffffff);
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.thinking-config-btn:hover {
  background: var(--theme-hover-background, #f6f8fa);
  border-color: var(--theme-accent, #0366d6);
}

.thinking-config-btn.active {
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
  background: rgba(3, 102, 214, 0.08);
}

.thinking-icon {
  font-size: 12px;
  line-height: 1;
}

.thinking-label {
  font-weight: 500;
}

.expand-icon {
  font-size: 10px;
  opacity: 0.6;
}

/* Thinking Config Dropdown */
.thinking-config-dropdown {
  position: absolute;
  bottom: 100%;
  left: 10px;
  margin-bottom: 8px;
  min-width: 300px;
  max-width: 400px;
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 1000;
}


.thinking-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 999px;
  background: var(--theme-background, #ffffff);
  font-size: 12px;
  color: var(--theme-secondary-foreground, #6a737d);
  cursor: pointer;
  transition: all 0.2s ease;
}

.thinking-toggle .status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--theme-border, #d0d7de);
  transition: background 0.2s ease;
}

.thinking-toggle.active {
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
  background: rgba(3, 102, 214, 0.08);
}

.thinking-toggle.active .status-dot {
  background: var(--theme-accent, #0366d6);
}

.thinking-toggle:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.thinking-toggle .status-text {
  font-weight: 500;
}

.token-stats {
  font-size: 10px;
  color: var(--theme-secondary-foreground, #6a737d);
  padding: 2px 6px;
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
}

/* ========== 简洁图标按钮 - 紧凑 ========== */
.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: var(--theme-secondary-foreground, #6a737d);
  cursor: pointer;
  transition: all 0.15s ease;
}

.icon-btn:hover:not(:disabled) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.06));
  color: var(--theme-foreground, #24292e);
}

.icon-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* 附件按钮 */
.icon-btn.attach-btn {
  color: var(--theme-secondary-foreground, #6a737d);
}

.icon-btn.attach-btn:hover:not(:disabled) {
  color: var(--theme-accent, #0366d6);
}

/* 发送按钮 */
.icon-btn.send-icon-btn {
  color: var(--theme-secondary-foreground, #9ca3af);
}

.icon-btn.send-icon-btn.active {
  color: var(--theme-foreground, #24292e);
}

.icon-btn.send-icon-btn.active:hover {
  color: var(--theme-accent, #0366d6);
  background: rgba(3, 102, 214, 0.1);
}

/* 停止按钮 */
.icon-btn.stop-icon-btn {
  color: var(--theme-error, #d73a49);
}

.icon-btn.stop-icon-btn:hover {
  background: rgba(215, 58, 73, 0.1);
}


/* Send Button Context Menu (发送按钮右键菜单) */
.send-context-menu {
  position: fixed;
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 10000;
  min-width: 160px;
  padding: 4px;
  transform: translate(-50%, -100%);
  margin-top: -8px;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 14px;
}

.context-menu-item:hover {
  background: var(--theme-hover-background, #f6f8fa);
}

.menu-icon {
  font-size: 16px;
}

.menu-text {
  font-weight: 500;
  color: var(--theme-foreground, #24292e);
}

/* 错误提示区域 */
.error-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  background: var(--theme-info-background);
  border: 1px solid var(--theme-error);
  border-radius: 6px;
  margin: 8px 12px 0;
  cursor: pointer;
  transition: background 0.2s;
}

.error-banner:hover {
  background: var(--theme-hover-background);
}

.error-icon {
  flex-shrink: 0;
  font-size: 14px;
}

.error-text {
  flex: 1;
  font-size: 12px;
  line-height: 1.5;
  color: var(--theme-error, #dc2626);
  word-break: break-word;
  white-space: pre-wrap;
  max-height: 120px;
  overflow-y: auto;
}

.error-dismiss {
  flex-shrink: 0;
  padding: 0;
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  color: var(--theme-error, #dc2626);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  border-radius: 4px;
  transition: background 0.15s;
}

.error-dismiss:hover {
  background: rgba(220, 38, 38, 0.1);
}
</style>

<!-- 非 scoped 样式：用于 teleported 的下拉菜单 -->
<style>
/* 模式下拉弹层样式 */
.mode-dropdown .el-select-dropdown__item.is-selected .mode-icon {
  color: var(--theme-background, #ffffff);
}

/* 模型下拉弹层基础样式，使用主题变量 */
.chat-input-select-dropdown {
  background-color: var(--theme-background, #ffffff) !important;
  border: 1px solid var(--theme-border, #e1e4e8) !important;
}

.chat-input-select-dropdown .el-select-dropdown__item {
  color: var(--theme-foreground, #24292e) !important;
}

.chat-input-select-dropdown .el-select-dropdown__item.hover,
.chat-input-select-dropdown .el-select-dropdown__item:hover {
  background-color: var(--theme-hover-background, #f6f8fa) !important;
}

/* 选中项高亮：背景用 accent，文字用背景色（形成对比） */
.chat-input-select-dropdown .el-select-dropdown__item.is-selected {
  background-color: var(--theme-accent, #0366d6) !important;
  color: var(--theme-background, #ffffff) !important;
}

.chat-input-select-dropdown .el-select-dropdown__item.is-selected .model-option-label,
.chat-input-select-dropdown .el-select-dropdown__item.is-selected .mode-option-label {
  color: var(--theme-background, #ffffff) !important;
}

.chat-input-select-dropdown .model-option-label,
.chat-input-select-dropdown .mode-option-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--theme-foreground, #24292e);
}

.chat-input-select-dropdown .model-brain-icon {
  font-size: 14px;
}

/* 模式选项图标 */
.chat-input-select-dropdown .mode-icon {
  font-size: 14px;
  margin-right: 4px;
}
</style>

<style>
.claude-mode-dropdown,
.claude-model-dropdown {
  width: max-content !important;
  min-width: max-content !important;
  max-width: 80vw;
}

.claude-mode-dropdown .el-select-dropdown__item,
.claude-model-dropdown .el-select-dropdown__item {
  white-space: nowrap;
}
</style>
