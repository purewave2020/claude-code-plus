/**
 * useThinkingConfig - Thinking configuration panel logic for ChatInput
 * 
 * Handles thinking config panel visibility and configuration management
 */

import { ref, computed, type Ref } from 'vue'
import type { ThinkingConfig, CodexThinkingConfig } from '@/types/thinking'
import { isCodexThinking, getCodexEffortLevels } from '@/types/thinking'
import type { BackendType } from '@/types/backend'
import type { CodexReasoningEffort } from '@/types/codex'

interface UseThinkingConfigOptions {
  backendType: Ref<BackendType>
  thinkingConfig?: Ref<ThinkingConfig | undefined>
  codexReasoningEffort: Ref<CodexReasoningEffort>
  getTabReasoningEffort?: () => CodexReasoningEffort | undefined
  onConfigUpdate?: (config: ThinkingConfig) => void
}

export function useThinkingConfig(options: UseThinkingConfigOptions) {
  const {
    backendType,
    thinkingConfig,
    codexReasoningEffort,
    getTabReasoningEffort,
    onConfigUpdate
  } = options

  // State
  const showThinkingConfig = ref(false)

  // Current thinking config (from props or Tab's initial config or default)
  const currentThinkingConfig = computed<ThinkingConfig>(() => {
    if (thinkingConfig?.value) {
      return thinkingConfig.value
    }
    // Default config based on backend type
    if (backendType.value === 'codex') {
      const tabEffort = getTabReasoningEffort?.()
      return {
        type: 'codex',
        effort: tabEffort ?? 'xhigh',
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
    return isCodexThinking(currentThinkingConfig.value)
  })

  const codexEffortLabel = computed(() => {
    if (!isCodexThinking(currentThinkingConfig.value)) {
      return codexReasoningEffort.value
    }
    const effort = (currentThinkingConfig.value as CodexThinkingConfig).effort
    if (effort === null) return codexReasoningEffort.value

    const levels = getCodexEffortLevels()
    const level = levels.find(l => l.id === effort)
    return level?.label || effort
  })

  const codexThinkingTooltip = computed(() => {
    if (!isCodexThinking(currentThinkingConfig.value)) {
      return '配置推理强度'
    }
    const effort = (currentThinkingConfig.value as CodexThinkingConfig).effort
    if (effort === null) return '使用默认推理强度'

    const levels = getCodexEffortLevels()
    const level = levels.find(l => l.id === effort)
    return level?.description || '配置推理强度'
  })

  // Methods
  function toggleThinkingConfigPanel() {
    showThinkingConfig.value = !showThinkingConfig.value
  }

  function handleThinkingConfigUpdate(config: ThinkingConfig) {
    onConfigUpdate?.(config)
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

  return {
    // State
    showThinkingConfig,
    currentThinkingConfig,

    // Codex helpers
    codexThinkingEnabled,
    codexEffortLabel,
    codexThinkingTooltip,

    // Methods
    toggleThinkingConfigPanel,
    handleThinkingConfigUpdate,
    handleGlobalThinkingConfigClick
  }
}
