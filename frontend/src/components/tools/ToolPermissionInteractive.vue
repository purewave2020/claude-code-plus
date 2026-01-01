<template>
  <div
    v-if="pendingPermission"
    ref="containerRef"
    class="permission-request"
    tabindex="0"
    @keydown.esc="handleDeny"
  >
    <div class="permission-card">
      <!-- 工具信息头部 -->
      <div class="permission-header">
        <span class="tool-icon">{{ getToolIcon(pendingPermission.toolName) }}</span>
        <span class="tool-name">{{ getToolDisplayName(pendingPermission.toolName) || pendingPermission.toolName || 'Unknown Tool' }}</span>
        <span class="permission-label">{{ t('permission.needsAuth') }}</span>
      </div>

      <!-- 工具参数预览 -->
      <div class="permission-content">
        <ToolUseDisplay
          v-if="permissionToolCall"
          :tool-call="permissionToolCall"
          :backend-type="permissionBackendType"
        />
        <div v-else class="no-params-hint">{{ t('permission.noParams') }}</div>
      </div>

      <!-- 操作选项 -->
      <div class="permission-options">
        <!-- 允许（仅本次） -->
        <button class="btn-option btn-allow" @click="isExitPlanMode ? handleApproveWithMode('default') : handleApprove()">
          {{ t('permission.allow') }}
        </button>

        <!-- ExitPlanMode 专用选项 -->
        <template v-if="isExitPlanMode">
          <button class="btn-option btn-allow-rule" @click="handleApproveWithMode('acceptEdits')">
            Allow, with Accept Edits
          </button>
          <button class="btn-option btn-allow-rule" @click="handleApproveWithMode('bypassPermissions')">
            Allow, with Bypass
          </button>
        </template>

        <!-- 动态渲染 permissionSuggestions -->
        <button
          v-for="(suggestion, index) in pendingPermission.permissionSuggestions"
          :key="index"
          class="btn-option btn-allow-rule"
          @click="handleAllowWithUpdate(suggestion)"
        >
          {{ t('permission.allow') }}，{{ formatSuggestion(suggestion) }}
        </button>

        <!-- 不允许（带输入框） -->
        <div class="deny-inline">
          <input
            v-model="denyReason"
            class="deny-input"
            :placeholder="t('permission.denyReasonPlaceholder')"
            @keydown.enter="handleDeny"
          />
          <button class="btn-option btn-deny" @click="handleDeny">
            {{ t('permission.deny') }}
          </button>
        </div>
      </div>

      <!-- 快捷键提示 -->
      <div class="shortcut-hint">{{ t('permission.escToDeny') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useI18n } from '@/composables/useI18n'
import ToolUseDisplay from '@/components/tools/ToolUseDisplay.vue'
import { resolveToolType } from '@/constants/toolTypes'
import { ToolCallStatus, type ToolCall } from '@/types/display'
import type { PendingPermissionRequest, PermissionUpdate } from '@/types/permission'

const { t } = useI18n()
const sessionStore = useSessionStore()

const containerRef = ref<HTMLElement | null>(null)
const denyReason = ref('')

const pendingPermission = computed(() => {
  const permissions = sessionStore.getCurrentPendingPermissions()
  return permissions.length > 0 ? permissions[0] : null
})

const permissionBackendType = computed(() => {
  const request = pendingPermission.value
  if (!request) return 'claude'

  const toolName = request.toolName
  const inputType = String((request.input as any)?.type || '').toLowerCase()
  if (
    toolName === 'CommandExecution' ||
    toolName === 'FileChange' ||
    toolName === 'McpToolCall' ||
    inputType === 'commandexecution' ||
    inputType === 'filechange' ||
    inputType === 'mcptoolcall' ||
    inputType === 'reasoning'
  ) {
    return 'codex'
  }

  return 'claude'
})

const permissionToolCall = computed<ToolCall | null>(() => {
  const request = pendingPermission.value
  if (!request) return null

  const toolName = request.toolName || 'Unknown'
  const normalizedInput = normalizePermissionInput(request)

  return {
    id: request.id,
    displayType: 'toolCall',
    toolName,
    toolType: resolveToolType(toolName),
    status: ToolCallStatus.RUNNING,
    startTime: request.createdAt,
    timestamp: request.createdAt,
    input: normalizedInput,
  } as ToolCall
})

const isExitPlanMode = computed(() => {
  return pendingPermission.value?.toolName === 'ExitPlanMode'
})

watch(pendingPermission, (newVal) => {
  if (newVal) {
    denyReason.value = ''
    nextTick(() => {
      containerRef.value?.focus()
    })
  }
})

function handleApprove() {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, { approved: true })
  }
}

async function handleApproveWithMode(mode: 'default' | 'acceptEdits' | 'bypassPermissions') {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, { approved: true })

    const tab = sessionStore.currentTab
    if (tab) {
      await tab.setPermissionMode(mode)
      if (mode === 'bypassPermissions') {
        tab.skipPermissions.value = true
      }
    }
  }
}

function handleAllowWithUpdate(update: PermissionUpdate) {
  if (pendingPermission.value) {
    if (update.type === 'setMode' && update.mode) {
      sessionStore.setLocalPermissionMode(update.mode)
      if (update.mode === 'bypassPermissions') {
        const tab = sessionStore.currentTab
        if (tab) {
          tab.skipPermissions.value = true
        }
      }
    }

    sessionStore.respondPermission(pendingPermission.value.id, {
      approved: true,
      permissionUpdates: [update]
    })
  }
}

function handleDeny() {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, {
      approved: false,
      denyReason: denyReason.value || undefined
    })
  }
}

function normalizePermissionInput(request: PendingPermissionRequest): Record<string, any> {
  const rawInput = (request.input || {}) as Record<string, any>
  const item = rawInput.item && typeof rawInput.item === 'object'
    ? rawInput.item as Record<string, any>
    : null
  const merged: Record<string, any> = item ? { ...rawInput, ...item } : { ...rawInput }

  if (request.toolName === 'CommandExecution') {
    const parsed = merged.parsedCmd || merged.parsed_cmd
    if (!merged.command && parsed) {
      merged.command = typeof parsed === 'string'
        ? parsed
        : (parsed.raw || parsed.command || parsed.text || '')
    }
  }

  if (request.toolName === 'FileChange') {
    const changes = Array.isArray(merged.changes) ? merged.changes : null
    if (changes && changes.length > 0) {
      const first = changes[0] as Record<string, any>
      if (!merged.path && first.path) {
        merged.path = first.path
      }
      if (!merged.operation) {
        const kind = first.kind
        const kindType = typeof kind === 'string' ? kind : (kind?.type || kind?.kind)
        if (kindType === 'add') merged.operation = 'create'
        else if (kindType === 'delete') merged.operation = 'delete'
        else if (kindType) merged.operation = 'edit'
      }
    }
  }

  return merged
}

function formatSuggestion(suggestion: PermissionUpdate): string {
  const dest = t(`permission.destination.${suggestion.destination || 'session'}`)

  switch (suggestion.type) {
    case 'addRules':
      if (suggestion.rules?.length) {
        const rule = suggestion.rules[0]
        if (rule.ruleContent) {
          return t('permission.suggestion.rememberWithRuleTo', {
            tool: rule.toolName,
            rule: rule.ruleContent,
            dest
          })
        }
        return t('permission.suggestion.rememberTo', { tool: rule.toolName, dest })
      }
      break

    case 'replaceRules':
      return t('permission.suggestion.replaceTo', { dest })

    case 'removeRules':
      if (suggestion.rules?.length) {
        return t('permission.suggestion.removeFrom', { tool: suggestion.rules[0].toolName, dest })
      }
      return t('permission.suggestion.removeRulesFrom', { dest })

    case 'setMode': {
      const mode = t(`permission.mode.${suggestion.mode || 'default'}`)
      return t('permission.suggestion.switchTo', { mode })
    }

    case 'addDirectories':
      if (suggestion.directories?.length) {
        return t('permission.suggestion.addDirTo', { dir: suggestion.directories[0], dest })
      }
      break

    case 'removeDirectories':
      if (suggestion.directories?.length) {
        return t('permission.suggestion.removeDirFrom', { dir: suggestion.directories[0], dest })
      }
      break
  }

  return t('permission.suggestion.applyTo', { dest })
}

function getToolDisplayName(name: string): string {
  const names: Record<string, string> = {
    'Bash': 'Terminal',
    'Write': 'Write File',
    'Edit': 'Edit File',
    'Read': 'Read File',
    'MultiEdit': 'Multi Edit',
    'Glob': 'Find Files',
    'Grep': 'Search Content',
    'CommandExecution': 'Command Execution',
    'FileChange': 'File Change',
    'McpToolCall': 'MCP Tool'
  }
  return names[name] || name
}

function getToolIcon(name: string): string {
  const icons: Record<string, string> = {
    'Bash': '🖥',
    'Write': '📝',
    'Edit': '✏️',
    'Read': '📖',
    'MultiEdit': '📋',
    'Glob': '🔍',
    'Grep': '🔎',
    'CommandExecution': '🖥',
    'FileChange': '📝',
    'McpToolCall': '🧩'
  }
  return icons[name] || '🔧'
}
</script>


<style scoped>
.permission-request {
  outline: none;
  max-height: 60vh; /* 限制最大高度，避免遮挡过多 */
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.permission-request:focus .permission-card {
  box-shadow: 0 0 0 2px var(--theme-accent, #0366d6), 0 8px 32px rgba(0, 0, 0, 0.15);
}

.permission-card {
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 100%;
}

.permission-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--theme-panel-background, #f6f8fa);
  color: var(--theme-foreground, #24292e);
}

.tool-icon {
  font-size: 18px;
}

.tool-name {
  font-size: 14px;
  font-weight: 600;
}

.permission-label {
  font-size: 12px;
  background: var(--theme-accent-subtle, #e8f1fb);
  color: var(--theme-accent, #0366d6);
  padding: 2px 8px;
  border-radius: 999px;
  margin-left: auto;
  border: 1px solid var(--theme-accent, #0366d6);
}

.permission-content {
  padding: 16px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: var(--theme-background, #fff);
}

.no-params-hint {
  color: var(--theme-secondary-foreground, #586069);
  font-size: 13px;
  font-style: italic;
  padding: 8px 0;
}

.permission-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-background, #fff);
}

.btn-option {
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
  border: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-panel-background, #f6f8fa);
  color: var(--theme-foreground, #24292e);
}

.btn-option:hover {
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
  background: var(--theme-accent-subtle, #e8f1fb);
}

.btn-allow {
  background: var(--theme-accent-subtle, #e8f1fb);
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
}

.btn-allow:hover {
  background: var(--theme-accent, #0366d6);
  color: #fff;
}

.btn-allow-rule {
  background: var(--theme-panel-background, #f6f8fa);
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
}

.btn-allow-rule:hover {
  background: var(--theme-accent, #0366d6);
  color: #fff;
}

.deny-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.deny-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  font-size: 13px;
  background: var(--theme-background, #fff);
  color: var(--theme-foreground, #24292e);
}

.deny-input:focus {
  outline: none;
  border-color: var(--theme-error, #dc3545);
}

.btn-deny {
  background: var(--theme-background, #fff);
  border: 1px solid var(--theme-error, #dc3545);
  color: var(--theme-error, #dc3545);
  flex-shrink: 0;
}

.btn-deny:hover {
  background: var(--theme-error, #dc3545);
  color: white;
}

.shortcut-hint {
  font-size: 11px;
  color: var(--theme-muted, #6a737d);
  text-align: right;
  padding: 8px 16px;
  background: var(--theme-panel-background, #f6f8fa);
  border-top: 1px solid var(--theme-border, #e1e4e8);
}

</style>
