/**
 * useBackendSwitcher - Backend switching logic for ChatInput
 * 
 * Handles backend type selection (Claude, Codex, etc.) and dropdown visibility
 */

import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { BackendType } from '@/types/backend'
import { getAvailableBackends, getBackendDisplayName } from '@/services/backendCapabilities'

interface UseBackendSwitcherOptions {
  initialBackend?: BackendType
  onBackendChange?: (backend: BackendType) => void
}

export function useBackendSwitcher(options: UseBackendSwitcherOptions = {}) {
  const { initialBackend = 'claude', onBackendChange } = options

  // State
  const showBackendSwitcher = ref(false)
  const currentBackend = ref<BackendType>(initialBackend)

  // Computed
  const availableBackends = computed(() => getAvailableBackends())

  // Methods
  function toggleBackendSwitcher() {
    showBackendSwitcher.value = !showBackendSwitcher.value
  }

  function closeBackendSwitcher() {
    showBackendSwitcher.value = false
  }

  function handleBackendChange(newBackend: BackendType) {
    showBackendSwitcher.value = false
    if (newBackend !== currentBackend.value) {
      currentBackend.value = newBackend
      onBackendChange?.(newBackend)
    }
  }

  function getDisplayName(backend: BackendType): string {
    return getBackendDisplayName(backend)
  }

  // Global click handler to close dropdown
  function handleGlobalClick() {
    closeBackendSwitcher()
  }

  // Lifecycle
  onMounted(() => {
    document.addEventListener('click', handleGlobalClick)
  })

  onUnmounted(() => {
    document.removeEventListener('click', handleGlobalClick)
  })

  return {
    // State
    showBackendSwitcher,
    currentBackend,
    availableBackends,

    // Methods
    toggleBackendSwitcher,
    closeBackendSwitcher,
    handleBackendChange,
    getDisplayName
  }
}

// Re-export for convenience
export { getBackendDisplayName }
