/**
 * Input History Composable
 * Manages a history of user inputs for quick recall via Up/Down arrow keys
 * Similar to terminal command history
 */
import { ref, computed } from 'vue'

export interface InputHistoryOptions {
  maxHistorySize?: number
}

/**
 * Composable for managing input history
 */
export function useInputHistory(options: InputHistoryOptions = {}) {
  const { maxHistorySize = 100 } = options

  // History storage - array of input strings
  const history = ref<string[]>([])
  // Current position in history (-1 means not navigating history)
  const historyIndex = ref(-1)
  // Temporarily store current input when navigating history
  const tempInput = ref('')

  /**
   * Add an entry to history
   * - Removes duplicates
   * - Adds to the end (most recent)
   * - Trims to max size
   */
  function addToHistory(input: string) {
    const trimmed = input.trim()
    if (!trimmed) return

    // Remove if already exists (to avoid duplicates)
    const existingIndex = history.value.indexOf(trimmed)
    if (existingIndex !== -1) {
      history.value.splice(existingIndex, 1)
    }

    // Add to end (most recent)
    history.value.push(trimmed)

    // Trim to max size (remove oldest)
    if (history.value.length > maxHistorySize) {
      history.value.shift()
    }

    // Reset navigation index
    historyIndex.value = -1
    tempInput.value = ''
  }

  /**
   * Navigate to previous (older) history entry
   * Returns the history entry or null if at the end of history
   */
  function navigateUp(currentInput: string): string | null {
    // If starting navigation, save current input
    if (historyIndex.value === -1) {
      tempInput.value = currentInput
    }

    // Check if we can navigate further up
    const newIndex = historyIndex.value + 1
    if (newIndex >= history.value.length) {
      return null // Already at oldest entry
    }

    historyIndex.value = newIndex
    // Return from the end (most recent = last element)
    return history.value[history.value.length - 1 - historyIndex.value]
  }

  /**
   * Navigate to next (newer) history entry
   * Returns the history entry, temp input, or null
   */
  function navigateDown(_currentInput: string): string | null {
    if (historyIndex.value === -1) {
      return null // Not navigating history
    }

    const newIndex = historyIndex.value - 1
    if (newIndex < 0) {
      // Return to current input
      historyIndex.value = -1
      const result = tempInput.value
      tempInput.value = ''
      return result
    }

    historyIndex.value = newIndex
    return history.value[history.value.length - 1 - historyIndex.value]
  }

  /**
   * Check if currently navigating history
   */
  const isNavigatingHistory = computed(() => historyIndex.value !== -1)

  /**
   * Clear history
   */
  function clearHistory() {
    history.value = []
    historyIndex.value = -1
    tempInput.value = ''
  }

  /**
   * Reset navigation (call when input is submitted or cancelled)
   */
  function resetNavigation() {
    historyIndex.value = -1
    tempInput.value = ''
  }

  return {
    history: computed(() => history.value),
    historyIndex: computed(() => historyIndex.value),
    isNavigatingHistory,
    addToHistory,
    navigateUp,
    navigateDown,
    clearHistory,
    resetNavigation
  }
}
