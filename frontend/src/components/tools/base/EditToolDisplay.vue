<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="true"
    :tool-call="toolCallData"
    @toggle="expanded = !expanded"
  >
    <template #header-actions>
      <button
        v-if="canAccept && !isAccepted"
        class="accept-btn"
        :title="t('tools.acceptModification')"
        @click.stop="handleAccept"
      >
        ✓
      </button>
    </template>
    <template #details>
      <div class="edit-tool-details">
        <DiffViewer :old-content="oldString" :new-content="newString" />

        <div v-if="replaceAll" class="replace-mode">
          <span class="badge">{{ t('tools.replaceAll') }}</span>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { GenericToolCall } from '@/types/display'
import { isJetBrainsFileEditTool, extractHistoryTs } from '@/composables/useFileChanges'
import { useSessionStore } from '@/stores/sessionStore'
import CompactToolCard from '../CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import DiffViewer from '../DiffViewer.vue'

const { t } = useI18n()

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// 默认折叠
const expanded = ref(false)

// 直接从 sessionStore 获取 fileChanges 实例（响应式）
const sessionStore = useSessionStore()
const fileChangesInstance = computed(() => sessionStore.currentFileChanges)

// 提取工具显示信息
const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

// 构造拦截器所需的工具调用数据
const toolCallData = computed(() => ({
  toolType: 'Edit',
  toolUseId: props.toolCall.result?.tool_use_id,
  input: props.toolCall.input as Record<string, unknown>,
  result: props.toolCall.result
}))

const input = computed(() => props.toolCall.input as Record<string, any>)
const oldString = computed(() => input.value.old_string || '')
const newString = computed(() => input.value.new_string || '')
const replaceAll = computed(() => input.value.replace_all || false)

// 检查是否可以接受（是 JetBrains 文件编辑工具且有 historyTs）
const canAccept = computed(() => {
  const toolName = props.toolCall.toolName || ''
  if (!isJetBrainsFileEditTool(toolName)) return false
  const historyTs = extractHistoryTs(props.toolCall.result)
  return historyTs !== null
})

// 检查是否已接受
const isAccepted = computed(() => {
  const toolUseId = props.toolCall.id || props.toolCall.result?.tool_use_id
  if (!toolUseId || !fileChangesInstance.value) return false
  return fileChangesInstance.value.isAccepted(toolUseId)
})

// 处理接受
function handleAccept() {
  const toolUseId = props.toolCall.id || props.toolCall.result?.tool_use_id
  if (!toolUseId || !fileChangesInstance.value) return
  fileChangesInstance.value.acceptByToolUseId(toolUseId)
}
</script>

<style scoped>
.accept-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--theme-secondary-foreground);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.15s ease;
}

.accept-btn:hover {
  background: color-mix(in srgb, var(--theme-success) 20%, transparent);
  color: var(--theme-success);
}

.edit-tool {
  border-color: var(--theme-error, #f9826c);
}

.edit-tool .tool-name {
  color: var(--theme-error, #d73a49);
}

.edit-preview {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 12px;
  margin: 12px 0;
}

.diff-section {
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
}

.diff-header {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
}

.diff-header.old {
  background: #ffeef0;
  color: var(--theme-error, #d73a49);
}

.diff-header.new {
  background: #e6ffed;
  color: var(--theme-success, #22863a);
}

.diff-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: var(--theme-editor-font-family);
  max-height: 200px;
  overflow: auto;
  background: var(--theme-background, white);
  color: var(--theme-code-foreground, #24292e);
}

.diff-content.old {
  background: #ffeef0;
}

.diff-content.new {
  background: #e6ffed;
}

.diff-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--theme-foreground, #586069);
  opacity: 0.7;
}

.replace-mode {
  margin-top: 8px;
}

.badge {
  display: inline-block;
  padding: 4px 8px;
  background: var(--theme-accent, #0366d6);
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 12px;
}
</style>
