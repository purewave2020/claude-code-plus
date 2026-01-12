/**
 * useTokenDisplay - Token usage display logic for ChatInput
 * 
 * Handles token usage formatting and tooltip generation
 */

import { type Ref } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { BackendType } from '@/types/backend'

export interface TokenUsage {
  inputTokens: number
  outputTokens: number
  cacheCreationTokens: number
  cacheReadTokens: number
  totalTokens: number
}

interface UseTokenDisplayOptions {
  tokenUsage: Ref<TokenUsage | undefined>
  backendType: Ref<BackendType>
}

export function useTokenDisplay(options: UseTokenDisplayOptions) {
  const { tokenUsage, backendType } = options
  const { t } = useI18n()

  // Format token usage for display
  function formatTokenUsage(usage: TokenUsage): string {
    // For Codex backend, token display might be different
    if (backendType.value === 'codex') {
      return `${usage.totalTokens} tokens`
    }
    // Claude backend
    return `${usage.totalTokens} tokens`
  }

  // Get detailed token tooltip
  function getTokenTooltip(): string {
    if (!tokenUsage.value) return ''
    const u = tokenUsage.value

    // For Codex backend, tooltip might be simpler
    if (backendType.value === 'codex') {
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

  return {
    formatTokenUsage,
    getTokenTooltip
  }
}
