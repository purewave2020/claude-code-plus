<template>
  <div class="tool-call-display">
    <component :is="resolvedComponent" :tool-call="toolCall" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ToolCall } from '@/types/display'
import { CLAUDE_TOOL_COMPONENTS } from '@/components/chat/tool-displays/claudeRegistry'
import { CODEX_TOOL_COMPONENTS } from '@/components/chat/tool-displays/codexRegistry'
import GenericToolDisplay from '@/components/tools/GenericToolDisplay.vue'
import JetBrainsMcpToolDisplay from '@/components/tools/JetBrainsMcpToolDisplay.vue'
import TerminalMcpToolDisplay from '@/components/tools/TerminalMcpToolDisplay.vue'

interface Props {
  toolCall: ToolCall
}

const props = defineProps<Props>()

const isJetBrainsMcpTool = computed(() => {
  const name = props.toolCall?.toolName || ''
  // 匹配所有 jetbrains 相关的 MCP 工具：jetbrains-lsp、jetbrains-file、jetbrains_git 等
  // 支持三种命名格式：mcp__jetbrains-xxx、mcp__jetbrains__xxx、mcp__jetbrains_xxx
  return name.startsWith('mcp__jetbrains-') || name.startsWith('mcp__jetbrains_')
})

const isTerminalMcpTool = computed(() => {
  return props.toolCall?.toolName?.startsWith('mcp__terminal__') ?? false
})

const isCodexToolType = computed(() => {
  return props.toolCall?.toolType?.startsWith('CODEX_') ?? false
})

const resolvedComponent = computed(() => {
  if (isJetBrainsMcpTool.value) {
    return JetBrainsMcpToolDisplay
  }
  if (isTerminalMcpTool.value) {
    return TerminalMcpToolDisplay
  }

  const registry = isCodexToolType.value ? CODEX_TOOL_COMPONENTS : CLAUDE_TOOL_COMPONENTS
  return registry[props.toolCall.toolType] ?? GenericToolDisplay
})
</script>

<style scoped>
.tool-call-display {
  margin: 1px 0;
}
</style>
