import type { Component } from 'vue'
import { CODEX_TOOL_TYPE } from '@/constants/toolTypes'

import CodexWriteToolDisplay from '@/components/tools/codex/CodexWriteToolDisplay.vue'
import CodexEditToolDisplay from '@/components/tools/codex/CodexEditToolDisplay.vue'
import CodexBashToolDisplay from '@/components/tools/codex/CodexBashToolDisplay.vue'
import CodexWebSearchToolDisplay from '@/components/tools/codex/CodexWebSearchToolDisplay.vue'

export const CODEX_TOOL_COMPONENTS: Record<string, Component> = {
  [CODEX_TOOL_TYPE.WRITE]: CodexWriteToolDisplay,
  [CODEX_TOOL_TYPE.EDIT]: CodexEditToolDisplay,
  [CODEX_TOOL_TYPE.BASH]: CodexBashToolDisplay,
  [CODEX_TOOL_TYPE.WEB_SEARCH]: CodexWebSearchToolDisplay,
}
