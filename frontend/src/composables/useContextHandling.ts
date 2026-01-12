/**
 * useContextHandling - Context reference handling logic for ChatInput
 * 
 * Manages context references (files, images, URLs) including:
 * - Context type detection
 * - Context display formatting
 * - Context selector popup
 */

import { ref } from 'vue'
import type { ContextReference } from '@/types/display'
import type { ImageReference } from '@/types/enhancedMessage'
import { fileSearchService, type IndexedFileInfo } from '@/services/fileSearchService'

interface UseContextHandlingOptions {
  onContextAdd?: (context: ContextReference) => void
  onContextRemove?: (context: ContextReference) => void
}

export function useContextHandling(options: UseContextHandlingOptions = {}) {
  const { onContextAdd, onContextRemove } = options

  // State
  const showContextSelectorPopup = ref(false)
  const contextSearchResults = ref<IndexedFileInfo[]>([])
  const contextIsIndexing = ref(false)

  // Type guards
  function isImageReference(context: ContextReference): context is ImageReference {
    return context.type === 'image'
  }

  function isFileReference(context: ContextReference): boolean {
    return context.type === 'file'
  }

  function isUrlReference(context: ContextReference): boolean {
    return 'url' in context || context.type === 'web'
  }

  // Alias for template use
  const isImageContext = isImageReference

  // Display helpers
  function getContextFullPath(context: ContextReference): string {
    if (isImageReference(context)) {
      return context.name || '图片'
    }
    if (isFileReference(context)) {
      return (context as any).fullPath || (context as any).path || context.uri || ''
    }
    if (isUrlReference(context)) {
      return (context as any).url || ''
    }
    return context.uri || ''
  }

  function getContextDisplay(context: ContextReference): string {
    if (isImageReference(context)) {
      return '图片'
    }
    if (isFileReference(context)) {
      const path = (context as any).path || context.uri || ''
      const parts = path.replace(/\\/g, '/').split('/')
      return parts[parts.length - 1] || path
    }
    if (isUrlReference(context)) {
      return (context as any).title || (context as any).url || ''
    }
    const uri = context.uri || ''
    const parts = uri.replace(/\\/g, '/').split('/')
    return parts[parts.length - 1] || uri
  }

  function getContextIcon(context: ContextReference): string {
    if (isImageReference(context)) return '🖼️'
    if (isFileReference(context)) return '📄'
    if (isUrlReference(context)) return '🌐'
    if (context.type === 'folder') return '📁'
    if ('path' in context) return '📄'
    return '📎'
  }

  // Context selector handlers
  async function handleAddContextClick() {
    showContextSelectorPopup.value = true
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
    const contextRef: ContextReference = {
      id: `ctx_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
      type: 'file',
      uri: result.relativePath,
      displayType: 'TAG',
      path: result.relativePath,
      fullPath: result.absolutePath,
      name: result.name
    }

    onContextAdd?.(contextRef)
    showContextSelectorPopup.value = false
    contextSearchResults.value = []
  }

  function removeContext(context: ContextReference) {
    onContextRemove?.(context)
  }

  return {
    // State
    showContextSelectorPopup,
    contextSearchResults,
    contextIsIndexing,

    // Type guards
    isImageReference,
    isFileReference,
    isUrlReference,
    isImageContext,

    // Display helpers
    getContextFullPath,
    getContextDisplay,
    getContextIcon,

    // Handlers
    handleAddContextClick,
    handleContextSearch,
    handleContextDismiss,
    handleContextSelect,
    removeContext
  }
}
