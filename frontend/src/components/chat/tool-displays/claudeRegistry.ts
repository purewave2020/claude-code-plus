import type { Component } from 'vue'
import { CLAUDE_TOOL_TYPE } from '@/constants/toolTypes'

import ClaudeReadToolDisplay from '@/components/tools/claude/ClaudeReadToolDisplay.vue'
import ClaudeWriteToolDisplay from '@/components/tools/claude/ClaudeWriteToolDisplay.vue'
import ClaudeEditToolDisplay from '@/components/tools/claude/ClaudeEditToolDisplay.vue'
import MultiEditToolDisplay from '@/components/tools/MultiEditToolDisplay.vue'
import ClaudeBashToolDisplay from '@/components/tools/claude/ClaudeBashToolDisplay.vue'
import BashOutputToolDisplay from '@/components/tools/BashOutputToolDisplay.vue'
import KillShellToolDisplay from '@/components/tools/KillShellToolDisplay.vue'
import GrepToolDisplay from '@/components/tools/GrepToolDisplay.vue'
import GlobToolDisplay from '@/components/tools/GlobToolDisplay.vue'
import ClaudeWebSearchToolDisplay from '@/components/tools/claude/ClaudeWebSearchToolDisplay.vue'
import WebFetchToolDisplay from '@/components/tools/WebFetchToolDisplay.vue'
import TodoWriteDisplay from '@/components/tools/TodoWriteDisplay.vue'
import TaskToolDisplay from '@/components/tools/TaskToolDisplay.vue'
import AskUserQuestionDisplay from '@/components/tools/AskUserQuestionDisplay.vue'
import NotebookEditToolDisplay from '@/components/tools/NotebookEditToolDisplay.vue'
import ExitPlanModeToolDisplay from '@/components/tools/ExitPlanModeToolDisplay.vue'
import EnterPlanModeToolDisplay from '@/components/tools/EnterPlanModeToolDisplay.vue'
import SkillToolDisplay from '@/components/tools/SkillToolDisplay.vue'
import SlashCommandToolDisplay from '@/components/tools/SlashCommandToolDisplay.vue'
import ListMcpResourcesToolDisplay from '@/components/tools/ListMcpResourcesToolDisplay.vue'
import ReadMcpResourceToolDisplay from '@/components/tools/ReadMcpResourceToolDisplay.vue'

export const CLAUDE_TOOL_COMPONENTS: Record<string, Component> = {
  [CLAUDE_TOOL_TYPE.READ]: ClaudeReadToolDisplay,
  [CLAUDE_TOOL_TYPE.WRITE]: ClaudeWriteToolDisplay,
  [CLAUDE_TOOL_TYPE.EDIT]: ClaudeEditToolDisplay,
  [CLAUDE_TOOL_TYPE.MULTI_EDIT]: MultiEditToolDisplay,
  [CLAUDE_TOOL_TYPE.BASH]: ClaudeBashToolDisplay,
  [CLAUDE_TOOL_TYPE.BASH_OUTPUT]: BashOutputToolDisplay,
  [CLAUDE_TOOL_TYPE.KILL_SHELL]: KillShellToolDisplay,
  [CLAUDE_TOOL_TYPE.GREP]: GrepToolDisplay,
  [CLAUDE_TOOL_TYPE.GLOB]: GlobToolDisplay,
  [CLAUDE_TOOL_TYPE.WEB_SEARCH]: ClaudeWebSearchToolDisplay,
  [CLAUDE_TOOL_TYPE.WEB_FETCH]: WebFetchToolDisplay,
  [CLAUDE_TOOL_TYPE.TODO_WRITE]: TodoWriteDisplay,
  [CLAUDE_TOOL_TYPE.TASK]: TaskToolDisplay,
  [CLAUDE_TOOL_TYPE.ASK_USER_QUESTION]: AskUserQuestionDisplay,
  [CLAUDE_TOOL_TYPE.NOTEBOOK_EDIT]: NotebookEditToolDisplay,
  [CLAUDE_TOOL_TYPE.EXIT_PLAN_MODE]: ExitPlanModeToolDisplay,
  [CLAUDE_TOOL_TYPE.ENTER_PLAN_MODE]: EnterPlanModeToolDisplay,
  [CLAUDE_TOOL_TYPE.SKILL]: SkillToolDisplay,
  [CLAUDE_TOOL_TYPE.SLASH_COMMAND]: SlashCommandToolDisplay,
  [CLAUDE_TOOL_TYPE.LIST_MCP_RESOURCES]: ListMcpResourcesToolDisplay,
  [CLAUDE_TOOL_TYPE.READ_MCP_RESOURCE]: ReadMcpResourceToolDisplay,
}
