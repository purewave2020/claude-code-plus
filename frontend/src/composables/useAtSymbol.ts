/**
 * @ Symbol File Selection Composable
 * Manages @ symbol triggered file search and selection functionality
 */
import { ref, type Ref } from 'vue'
import { fileSearchService, type IndexedFileInfo } from '@/services/fileSearchService'
import { isInAtQuery } from '@/utils/atSymbolDetector'

export interface AtSymbolOptions {
  /** Function to get current cursor position */
  getCursorPosition: () => number
  /** Function to get plain text content */
  getPlainText: () => string
  /** Function to replace text range with file reference */
  replaceRangeWithFileReference: (start: number, end: number, filePath: string) => void
  /** Function to focus the input */
  focusInput?: () => void
}

export function useAtSymbol(options: AtSymbolOptions) {
  // State
  const showAtSymbolPopup = ref(false)
  const atSymbolPosition = ref(0)
  const atSymbolSearchResults = ref<IndexedFileInfo[]>([])
  const atSymbolIsIndexing = ref(false)

  /**
   * Check for @ symbol and trigger file search
   */
  async function checkAtSymbol() {
    const cursorPosition = options.getCursorPosition()
    const plainText = options.getPlainText()
    const atResult = isInAtQuery(plainText, cursorPosition)

    if (atResult) {
      // In @ query
      atSymbolPosition.value = atResult.atPosition

      // Search files (empty query returns project root files)
      try {
        const result = await fileSearchService.searchFiles(atResult.query, 10)
        atSymbolSearchResults.value = result.files
        atSymbolIsIndexing.value = result.isIndexing
        // Show popup if there are results or indexing
        showAtSymbolPopup.value = result.files.length > 0 || result.isIndexing
      } catch (error) {
        console.error('File search failed:', error)
        atSymbolSearchResults.value = []
        atSymbolIsIndexing.value = false
        showAtSymbolPopup.value = false
      }
    } else {
      // Not in @ query
      showAtSymbolPopup.value = false
      atSymbolSearchResults.value = []
      atSymbolIsIndexing.value = false
    }
  }

  /**
   * Handle file selection from @ symbol popup
   */
  function handleAtSymbolFileSelect(file: IndexedFileInfo) {
    const cursorPosition = options.getCursorPosition()

    // Delete from @ position to current cursor position, then insert file reference node (using absolute path)
    options.replaceRangeWithFileReference(
      atSymbolPosition.value,
      cursorPosition,
      file.absolutePath
    )

    // Close popup
    dismissAtSymbolPopup()
  }

  /**
   * Dismiss @ symbol popup
   */
  function dismissAtSymbolPopup() {
    showAtSymbolPopup.value = false
    atSymbolSearchResults.value = []
    atSymbolIsIndexing.value = false
  }

  return {
    // State
    showAtSymbolPopup,
    atSymbolPosition,
    atSymbolSearchResults,
    atSymbolIsIndexing,
    // Methods
    checkAtSymbol,
    handleAtSymbolFileSelect,
    dismissAtSymbolPopup
  }
}
