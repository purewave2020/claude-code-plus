<template>
  <div
    class="input-area"
  >
    <!-- 上下文引用显示 -->
    <div
      v-if="contextReferences.length > 0"
      class="context-references"
    >
      <div class="references-header">
        <span class="header-icon">📎</span>
        <span class="header-text">Context ({{ contextReferences.length }})</span>
      </div>
      <div class="references-list">
        <div
          v-for="(ref, index) in contextReferences"
          :key="index"
          class="reference-chip"
          :class="`reference-${ref.type}`"
        >
          <span class="chip-icon">{{ getRefIcon(ref.type) }}</span>
          <span class="chip-label">{{ formatRefLabel(ref) }}</span>
          <button
            class="chip-remove"
            @click="removeReference(index)"
          >
            ×
          </button>
        </div>
      </div>
    </div>

    <!-- 拖放区域提示 -->
    <div
      v-if="isDragging"
      class="drop-zone-overlay"
      @drop="handleDrop"
      @dragover.prevent
      @dragleave="handleDragLeave"
    >
      <div class="drop-zone-content">
        <span class="drop-icon">📁</span>
        <span class="drop-text">Drop files to add context</span>
      </div>
    </div>

    <!-- 输入区域 -->
    <div
      class="input-wrapper"
      @drop.prevent="handleDrop"
      @dragover.prevent="handleDragOver"
      @dragleave="handleDragLeave"
    >
      <textarea
        ref="textareaRef"
        v-model="localMessage"
        :placeholder="placeholder"
        :disabled="disabled"
        class="input-textarea"
        @keydown="handleKeyDown"
        @input="handleInput"
      />

      <!-- @ 提及建议 -->
      <div
        v-if="showMentionSuggestions"
        class="mention-suggestions"
        :style="suggestionPosition"
      >
        <div
          v-for="(suggestion, index) in filteredSuggestions"
          :key="index"
          class="suggestion-item"
          :class="{ selected: index === selectedSuggestionIndex }"
          @click="selectSuggestion(suggestion)"
        >
          <span class="suggestion-icon">{{ getSuggestionIcon(suggestion.type) }}</span>
          <div class="suggestion-content">
            <div class="suggestion-name">
              {{ suggestion.name }}
            </div>
            <div class="suggestion-path">
              {{ suggestion.path }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="input-actions">
      <button
        class="btn btn-secondary"
        :disabled="disabled"
        title="Add file reference"
        @click="triggerFileSelect"
      >
        <span class="btn-icon">📎</span>
        <span>Add File</span>
      </button>

      <div class="actions-spacer" />

      <span
        v-if="charCount > 0"
        class="char-count"
      >{{ charCount }} chars</span>

      <button
        class="btn btn-primary"
        :disabled="!canSend"
        @click="handleSend"
      >
        <span>{{ sendButtonText }}</span>
      </button>
    </div>

    <!-- 隐藏的文件选择器 -->
    <input
      ref="fileInputRef"
      type="file"
      multiple
      style="display: none"
      @change="handleFileSelect"
    >
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { fileSearchService } from '@/services/fileSearchService'

export interface ContextReference {
  type: 'file' | 'folder' | 'url' | 'code'
  name: string
  path: string
  content?: string
  lineStart?: number
  lineEnd?: number
}

interface MentionSuggestion {
  type: 'file' | 'folder' | 'symbol'
  name: string
  path: string
}

interface Props {
  modelValue: string
  disabled?: boolean
  placeholder?: string
  sendButtonText?: string
  references?: ContextReference[]
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'send', message: string, references: ContextReference[]): void
  (e: 'update:references', references: ContextReference[]): void
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  placeholder: 'Type message... (Ctrl+Enter to send)',
  sendButtonText: 'Send',
  references: () => []
})

const emit = defineEmits<Emits>()

const textareaRef = ref<HTMLTextAreaElement>()
const fileInputRef = ref<HTMLInputElement>()
const localMessage = ref(props.modelValue)
const contextReferences = ref<ContextReference[]>([...props.references])
const isDragging = ref(false)
const showMentionSuggestions = ref(false)
const mentionQuery = ref('')
const selectedSuggestionIndex = ref(0)
const suggestionPosition = ref({ top: '0px', left: '0px' })

// 计算属性
const charCount = computed(() => localMessage.value.length)

const canSend = computed(() => {
  return !props.disabled && localMessage.value.trim().length > 0
})

const filteredSuggestions = ref<MentionSuggestion[]>([])

// 监听外部变化
watch(() => props.modelValue, (newValue) => {
  if (newValue !== localMessage.value) {
    localMessage.value = newValue
  }
})

watch(() => props.references, (newRefs) => {
  contextReferences.value = [...newRefs]
})

watch(localMessage, (newValue) => {
  emit('update:modelValue', newValue)
})

// 引用管理
function addReference(ref: ContextReference) {
  contextReferences.value.push(ref)
  emit('update:references', contextReferences.value)
}

function removeReference(index: number) {
  contextReferences.value.splice(index, 1)
  emit('update:references', contextReferences.value)
}

function getRefIcon(type: string): string {
  const icons: Record<string, string> = {
    file: '📄',
    folder: '📁',
    url: '🔗',
    code: '💻'
  }
  return icons[type] || '📎'
}

function formatRefLabel(ref: ContextReference): string {
  if (ref.lineStart !== undefined) {
    const lineRange = ref.lineEnd && ref.lineEnd !== ref.lineStart
      ? `${ref.lineStart}-${ref.lineEnd}`
      : `${ref.lineStart}`
    return `${ref.name}:${lineRange}`
  }
  return ref.name
}

// 拖放处理
function handleDragOver(event: DragEvent) {
  event.preventDefault()
  isDragging.value = true
}

function handleDragLeave(event: DragEvent) {
  event.preventDefault()
  // 只有当离开整个拖放区域时才设置为 false
  if (event.target === event.currentTarget) {
    isDragging.value = false
  }
}

async function handleDrop(event: DragEvent) {
  event.preventDefault()
  isDragging.value = false

  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return

  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    await addFileReference(file)
  }
}

// 文件选择
function triggerFileSelect() {
  fileInputRef.value?.click()
}

async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return

  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    await addFileReference(file)
  }

  // 清空文件选择器
  input.value = ''
}

async function addFileReference(file: File) {
  try {
    // 读取文件内容
    const content = await readFileContent(file)

    const ref: ContextReference = {
      type: 'file',
      name: file.name,
      path: file.name, // 实际项目中应该获取完整路径
      content: content
    }

    addReference(ref)
  } catch (error) {
    console.error('Failed to read file:', error)
  }
}

function readFileContent(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target?.result as string)
    reader.onerror = reject
    reader.readAsText(file)
  })
}

// @ 提及功能
function handleInput(event: Event) {
  const target = event.target as HTMLTextAreaElement
  const cursorPosition = target.selectionStart
  const textBeforeCursor = localMessage.value.substring(0, cursorPosition)

  // 检查是否正在输入 @
  const mentionMatch = textBeforeCursor.match(/@([^\s]*)$/)

  if (mentionMatch) {
    mentionQuery.value = mentionMatch[1]
    showMentionSuggestions.value = true
    selectedSuggestionIndex.value = 0

    // 计算建议框位置
    updateSuggestionPosition()

    // 获取建议列表
    fetchSuggestions(mentionQuery.value)
  } else {
    showMentionSuggestions.value = false
  }
}

async function fetchSuggestions(query: string) {
  try {
    const result = await fileSearchService.searchFiles(query, 10)
    if (result.files.length > 0) {
      filteredSuggestions.value = result.files.map((file) => ({
        type: 'file' as const,
        name: file.name,
        path: file.relativePath
      }))
    } else {
      filteredSuggestions.value = []
    }
  } catch (error) {
    console.error('Failed to fetch suggestions:', error)
    filteredSuggestions.value = []
  }
}

function updateSuggestionPosition() {
  if (!textareaRef.value) return

  // 简化版本：在光标下方显示
  suggestionPosition.value = {
    top: '100%',
    left: '0px'
  }
}

function getSuggestionIcon(type: string): string {
  const icons: Record<string, string> = {
    file: '📄',
    folder: '📁',
    symbol: '🔣'
  }
  return icons[type] || '📎'
}

function selectSuggestion(suggestion: MentionSuggestion) {
  const cursorPosition = textareaRef.value?.selectionStart ?? 0
  const textBeforeCursor = localMessage.value.substring(0, cursorPosition)
  const textAfterCursor = localMessage.value.substring(cursorPosition)

  // 找到 @ 的位置
  const mentionMatch = textBeforeCursor.match(/@([^\s]*)$/)
  if (!mentionMatch) return

  const mentionStart = cursorPosition - mentionMatch[0].length
  const newText = localMessage.value.substring(0, mentionStart) +
    `@${suggestion.name} ` +
    textAfterCursor

  localMessage.value = newText
  showMentionSuggestions.value = false

  // 添加到引用列表
  addReference({
    type: suggestion.type === 'folder' ? 'folder' : 'file',
    name: suggestion.name,
    path: suggestion.path
  })

  // 恢复焦点
  nextTick(() => {
    textareaRef.value?.focus()
  })
}

// 键盘处理
function handleKeyDown(event: KeyboardEvent) {
  // @ 提及建议导航
  if (showMentionSuggestions.value) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      selectedSuggestionIndex.value = Math.min(
        selectedSuggestionIndex.value + 1,
        filteredSuggestions.value.length - 1
      )
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      selectedSuggestionIndex.value = Math.max(selectedSuggestionIndex.value - 1, 0)
    } else if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      const selected = filteredSuggestions.value[selectedSuggestionIndex.value]
      if (selected) {
        selectSuggestion(selected)
      }
      return
    } else if (event.key === 'Escape') {
      event.preventDefault()
      showMentionSuggestions.value = false
      return
    }
    return
  }

  // 发送消息
  if (event.ctrlKey && event.key === 'Enter') {
    event.preventDefault()
    handleSend()
  }

  // Shift+Enter 换行（默认行为）
}

// 发送消息
function handleSend() {
  if (!canSend.value) return

  const message = localMessage.value.trim()
  const refs = [...contextReferences.value]

  emit('send', message, refs)

  // 清空输入
  localMessage.value = ''
  contextReferences.value = []
  emit('update:references', [])

  // 恢复焦点
  nextTick(() => {
    textareaRef.value?.focus()
  })
}
</script>

<style scoped>
.input-area {
  display: flex;
  flex-direction: column;
  padding: 6px 8px;
  background: #f6f8fa;
  border-top: 1px solid #e1e4e8;
  position: relative;
}

/* 上下文引用 */
.context-references {
  margin-bottom: 12px;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
}

.references-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #586069;
}

.references-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.reference-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 12px;
  font-size: 12px;
  color: #24292e;
}

.reference-file {
  border-color: #0366d6;
  background: #f1f8ff;
}

.reference-folder {
  border-color: #ffa657;
  background: #fff8dc;
}

.chip-icon {
  font-size: 14px;
}

.chip-label {
  font-family: var(--theme-editor-font-family);
}

.chip-remove {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #586069;
  cursor: pointer;
  font-size: 16px;
  padding: 0;
  margin-left: 2px;
}

.chip-remove:hover {
  color: #d73a49;
}

/* 拖放区域 */
.drop-zone-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(3, 102, 214, 0.1);
  border: 2px dashed #0366d6;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.drop-zone-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.drop-icon {
  font-size: 48px;
}

.drop-text {
  font-size: 14px;
  font-weight: 600;
  color: #0366d6;
}

/* 输入框 */
.input-wrapper {
  position: relative;
}

.input-textarea {
  width: 100%;
  min-height: 100px;
  max-height: 300px;
  padding: 12px;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  background: #ffffff;
  color: #24292e;
  font-family: var(--theme-font-family);
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
}

.input-textarea:focus {
  border-color: #0366d6;
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.1);
}

.input-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* @ 提及建议 */
.mention-suggestions {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  max-height: 200px;
  overflow-y: auto;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 100;
  margin-bottom: 4px;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.suggestion-item:hover,
.suggestion-item.selected {
  background: #f6f8fa;
}

.suggestion-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.suggestion-content {
  flex: 1;
  min-width: 0;
}

.suggestion-name {
  font-size: 13px;
  font-weight: 600;
  color: #24292e;
}

.suggestion-path {
  font-size: 11px;
  color: #586069;
  font-family: var(--theme-editor-font-family);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 操作按钮 */
.input-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.actions-spacer {
  flex: 1;
}

.char-count {
  font-size: 12px;
  color: #586069;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-icon {
  font-size: 16px;
}

.btn-secondary {
  background: #ffffff;
  color: #24292e;
  border: 1px solid #e1e4e8;
}

.btn-secondary:hover:not(:disabled) {
  background: #f6f8fa;
  border-color: #d1d5da;
}

.btn-primary {
  background: #0366d6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0256c0;
}
</style>
