/**
 * Codex Configuration Composable
 * Manages Codex backend configuration including model, sandbox mode, and reasoning effort
 */
import { computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useSettingsStore } from '@/stores/settingsStore'
import type { CodexSandboxMode, CodexReasoningEffort } from '@/types/codex'
import { REASONING_EFFORT_OPTIONS, SANDBOX_MODE_OPTIONS } from '@/types/codex'

export function useCodexConfig() {
  const sessionStore = useSessionStore()
  const settingsStore = useSettingsStore()

  // Codex model (from Tab or settings)
  const codexModel = computed(() => {
    return sessionStore.currentTab?.modelId?.value ?? settingsStore.settings.codexModel
  })

  // Codex sandbox mode (from Tab's initial config or settings)
  const codexSandboxMode = computed(() => {
    // Prefer Tab's initial connect config (snapshot at Tab creation), avoid global settings affecting existing sessions
    const tabSandboxMode = sessionStore.currentTab?.initialConnectOptions?.value?.sandboxMode
    const stored = tabSandboxMode ?? (settingsStore.settings as any).codexSandboxMode
    return stored === 'full-access' ? 'danger-full-access' : stored
  })

  // Codex reasoning effort (from Tab's initial config or settings)
  const codexReasoningEffort = computed<CodexReasoningEffort>(() => {
    // Prefer Tab's initial connect config (snapshot at Tab creation), avoid global settings affecting existing sessions
    const tabReasoningEffort = sessionStore.currentTab?.initialConnectOptions?.value?.reasoningEffort
    return tabReasoningEffort ?? (settingsStore.settings as any).codexReasoningEffort
  })

  // Handle model change
  function handleCodexModelChange(model: string) {
    console.log(`🔄 [Codex] Model change: ${model}`)
    const tab = sessionStore.currentTab
    if (tab) {
      tab.updateSettings({ model })
    }
    settingsStore.saveSettings({ codexModel: model })
  }

  // Handle sandbox mode change
  function handleCodexSandboxModeChange(mode: CodexSandboxMode) {
    console.log(`🔄 [Codex] Sandbox mode change: ${mode}`)
    settingsStore.saveSettings({ codexSandboxMode: mode } as any)
    const tab = sessionStore.currentTab
    if (tab?.backendType.value === 'codex' && tab.isConnected.value && !tab.isGenerating.value) {
      tab.reconnect({
        continueConversation: true,
        resumeSessionId: tab.sessionId.value || undefined
      })
    }
  }

  // Handle reasoning effort change
  function handleCodexReasoningEffortChange(effort: CodexReasoningEffort) {
    console.log(`🔄 [Codex] Reasoning effort change: ${effort}`)
    settingsStore.saveSettings({ codexReasoningEffort: effort } as any)
  }

  // Cycle through reasoning effort options
  function cycleCodexReasoningEffort() {
    const options = REASONING_EFFORT_OPTIONS.map(option => option.value)
    const current = codexReasoningEffort.value
    const currentIndex = options.indexOf(current)
    const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % options.length
    handleCodexReasoningEffortChange(options[nextIndex])
  }

  // Cycle through sandbox mode options
  function cycleCodexSandboxMode() {
    const options = SANDBOX_MODE_OPTIONS.map(option => option.value)
    const rawCurrent = codexSandboxMode.value as unknown as string
    const normalizedCurrent = rawCurrent === 'full-access' ? 'danger-full-access' : rawCurrent
    const currentIndex = options.indexOf(normalizedCurrent as CodexSandboxMode)
    const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % options.length
    handleCodexSandboxModeChange(options[nextIndex])
  }

  return {
    // State
    codexModel,
    codexSandboxMode,
    codexReasoningEffort,
    // Methods
    handleCodexModelChange,
    handleCodexSandboxModeChange,
    handleCodexReasoningEffortChange,
    cycleCodexReasoningEffort,
    cycleCodexSandboxMode
  }
}
