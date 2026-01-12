/**
 * Slash Commands Composable
 * Manages slash command detection and selection functionality
 */
import { ref, nextTick } from 'vue'

export interface SlashCommand {
  name: string
  description: string
}

export interface SlashCommandOptions {
  /** Function to get plain text content */
  getPlainText: () => string
  /** Function to set input content */
  setContent: (content: string) => void
  /** Function to focus the input */
  focusInput: () => void
}

export function useSlashCommands(options: SlashCommandOptions) {
  // State
  const showSlashCommandPopup = ref(false)
  const slashCommandQuery = ref('')

  // Known slash commands (preserved for future use)
  const knownSlashCommands: SlashCommand[] = [
    { name: '/compact', description: 'Compact the conversation' },
    { name: '/context', description: 'Add context to the conversation' },
    { name: '/rename', description: 'Rename the current session' }
  ]

  /**
   * Check if text is a slash command
   */
  function isSlashCommandText(text: string): boolean {
    // Slash command format: starts with / and followed by letters
    const trimmed = text.trim()
    if (!trimmed.startsWith('/')) return false

    // Extract command name (from / to first space)
    const commandMatch = trimmed.match(/^\/([a-zA-Z-]+)/)
    if (!commandMatch) return false

    return true
  }

  /**
   * Check for slash command input and update popup state
   */
  function checkSlashCommand() {
    // Use plain text to detect slash commands
    const text = options.getPlainText() // Don't trim, preserve spaces to detect if command is complete

    // Only show slash command popup when input starts with /
    if (text.startsWith('/')) {
      // Check if there's whitespace
      const hasWhitespace = /\s/.test(text)

      // If there's whitespace, don't show popup (same as context selector)
      if (hasWhitespace) {
        showSlashCommandPopup.value = false
        slashCommandQuery.value = ''
        return
      }

      // Extract query content (part after /)
      slashCommandQuery.value = text.slice(1)
      showSlashCommandPopup.value = true
    } else {
      showSlashCommandPopup.value = false
      slashCommandQuery.value = ''
    }
  }

  /**
   * Handle slash command selection from popup
   */
  function handleSlashCommandSelect(cmd: SlashCommand) {
    // Replace input content with selected command (add trailing space)
    options.setContent(cmd.name + ' ')
    showSlashCommandPopup.value = false
    slashCommandQuery.value = ''

    // Focus input
    nextTick(() => {
      options.focusInput()
    })
  }

  /**
   * Dismiss slash command popup
   */
  function dismissSlashCommandPopup() {
    showSlashCommandPopup.value = false
    slashCommandQuery.value = ''
  }

  return {
    // State
    showSlashCommandPopup,
    slashCommandQuery,
    knownSlashCommands,
    // Methods
    isSlashCommandText,
    checkSlashCommand,
    handleSlashCommandSelect,
    dismissSlashCommandPopup
  }
}
