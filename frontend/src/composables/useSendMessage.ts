/**
 * Send Message Composable
 * Manages message sending logic including content extraction, slash command detection, and IDE context handling
 */
import { computed, type Ref, type ComputedRef } from 'vue'
import type { ContentBlock } from '@/types/message'
import type { ActiveFileInfo } from '@/composables/useActiveFile'

/**
 * SendOptions interface for emit payloads
 */
export interface SendOptions {
  /** Whether this is a slash command (slash commands don't send contexts) */
  isSlashCommand?: boolean
  /** IDE context (currently open file info, structured data) */
  ideContext?: ActiveFileInfo | null
}

/**
 * RichTextInput instance interface (minimal required methods)
 */
export interface RichTextInputInstance {
  getText: () => string
  extractContentBlocks: () => ContentBlock[]
  clear: () => void
}

/**
 * Options for useSendMessage composable
 */
export interface SendMessageOptions {
  /** Reference to RichTextInput component instance */
  richTextInputRef: Ref<RichTextInputInstance | undefined>
  /** Reactive input text value */
  inputText: Ref<string>

  // Props values (reactive)
  /** Whether sending is enabled */
  enabled: ComputedRef<boolean> | Ref<boolean>
  /** Whether the model is currently generating */
  isGenerating: ComputedRef<boolean> | Ref<boolean>
  /** Whether editing is disabled (for edit mode) */
  editDisabled: ComputedRef<boolean> | Ref<boolean>

  // Functions from slash commands composable
  /** Check if text is a slash command */
  isSlashCommandText: (text: string) => boolean
  /** Dismiss slash command popup */
  dismissSlashCommandPopup: () => void

  // Active file state
  /** Whether active file context is enabled */
  activeFileEnabled: ComputedRef<boolean> | Ref<boolean>
  /** Current active file info */
  currentActiveFile: ComputedRef<ActiveFileInfo | null> | Ref<ActiveFileInfo | null>

  // Event callbacks
  /** Callback when sending a message */
  onSend: (contents: ContentBlock[], options?: SendOptions) => void
  /** Callback when force-sending a message (interrupt and send) */
  onForceSend: (contents: ContentBlock[], options?: SendOptions) => void
  /** Callback after a message is sent (for adding to history) */
  onAfterSend?: (text: string) => void
}

export function useSendMessage(options: SendMessageOptions) {
  const {
    richTextInputRef,
    inputText,
    enabled,
    isGenerating,
    editDisabled,
    isSlashCommandText,
    dismissSlashCommandPopup,
    activeFileEnabled,
    currentActiveFile,
    onSend,
    onForceSend,
    onAfterSend
  } = options

  /**
   * Check if input has content
   */
  const hasInput = computed(() => inputText.value.trim().length > 0)

  /**
   * Check if message can be sent
   * - Not disabled by edit mode
   * - Has content (text or images)
   * - Enabled by parent
   * Note: Does NOT block during generation (messages queue automatically)
   */
  const canSend = computed(() => {
    // If edit mode is disabled, can't send
    if (editDisabled.value) return false

    const hasContent = richTextInputRef.value?.getText()?.trim() ||
                       (richTextInputRef.value?.extractContentBlocks()?.length ?? 0) > 0

    return hasContent && enabled.value
  })

  /**
   * Extract content blocks from input
   */
  function extractContentBlocks(): ContentBlock[] {
    return richTextInputRef.value?.extractContentBlocks() || []
  }

  /**
   * Get plain text from input
   */
  function getPlainText(): string {
    return richTextInputRef.value?.getText() || ''
  }

  /**
   * Clear input after sending
   */
  function clearInput() {
    richTextInputRef.value?.clear()
    inputText.value = ''
  }

  /**
   * Build IDE context for sending
   * @param isSlashCommand - If true, returns null (slash commands don't need IDE context)
   */
  function buildIdeContext(isSlashCommand: boolean): ActiveFileInfo | null {
    if (isSlashCommand) return null
    if (!activeFileEnabled.value) return null
    return currentActiveFile.value
  }

  /**
   * Handle send action (normal send)
   * Called when user presses Enter or clicks send button
   */
  async function handleSend() {
    if (!canSend.value) return

    const contents = extractContentBlocks()
    if (contents.length === 0) return

    // Detect if this is a slash command
    const text = getPlainText()
    const isSlashCommand = isSlashCommandText(text)

    // Close slash command popup
    dismissSlashCommandPopup()

    // Build IDE context
    const ideContext = buildIdeContext(isSlashCommand)

    // Send message (parent handles queue logic and XML conversion)
    onSend(contents, { isSlashCommand, ideContext })

    // Add to history after successful send
    if (onAfterSend) {
      onAfterSend(text)
    }

    // Clear input
    clearInput()
  }

  /**
   * Handle force send action (interrupt current generation and send)
   * Called when user presses Ctrl+Enter
   */
  async function handleForceSend() {
    const contents = extractContentBlocks()

    // Force send requires content AND ongoing generation
    if (contents.length === 0 || !isGenerating.value) return

    // Build IDE context (force send doesn't check for slash commands)
    const ideContext = buildIdeContext(false)

    // Force send message (parent handles interrupt and XML conversion)
    onForceSend(contents, { ideContext })

    // Add to history after successful send
    if (onAfterSend) {
      const text = getPlainText()
      onAfterSend(text)
    }

    // Clear input
    clearInput()
  }

  /**
   * Handle RichTextInput submit event
   * Called when RichTextInput component emits submit (Enter key pressed inside)
   * Note: Even during generation, allows sending (parent queues automatically)
   */
  async function handleRichTextSubmit() {
    if (!enabled.value) return

    const contents = extractContentBlocks()
    if (contents.length === 0) return

    // Detect if this is a slash command
    const text = getPlainText()
    const isSlashCommand = isSlashCommandText(text)

    // Close slash command popup
    dismissSlashCommandPopup()

    // Build IDE context
    const ideContext = buildIdeContext(isSlashCommand)

    // Send message (parent handles queue logic)
    onSend(contents, { isSlashCommand, ideContext })

    // Add to history after successful send
    if (onAfterSend) {
      onAfterSend(text)
    }

    // Clear input
    clearInput()
  }

  return {
    // Computed
    hasInput,
    canSend,
    // Methods
    handleSend,
    handleForceSend,
    handleRichTextSubmit,
    // Helper methods (exposed for potential external use)
    extractContentBlocks,
    getPlainText,
    clearInput,
    buildIdeContext
  }
}
