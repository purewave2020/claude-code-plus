<template>
  <div class="codex-toolbar">
    <!-- Sandbox mode -->
    <el-select
      v-model="localSandboxMode"
      class="cursor-selector sandbox-selector"
      :disabled="disabled"
      placement="top-start"
      :teleported="true"
      popper-class="chat-input-select-dropdown mode-dropdown codex-sandbox-dropdown"
      :popper-options="{
        modifiers: [
          { name: 'preventOverflow', options: { boundary: 'viewport' } },
          { name: 'flip', options: { fallbackPlacements: ['top-start', 'top'] } }
        ]
      }"
      @change="handleSandboxChange"
    >
      <template #prefix>
        <span class="mode-prefix-icon">{{ getSandboxModeIcon(localSandboxMode) }}</span>
      </template>
      <el-option
        v-for="option in sandboxOptions"
        :key="option.value"
        :value="option.value"
        :label="option.label"
      >
        <span class="mode-option-label">
          <span class="mode-icon">{{ option.icon }}</span>
          <span>{{ option.label }}</span>
        </span>
      </el-option>
    </el-select>

    <!-- Model -->
    <el-select
      v-model="localModel"
      class="cursor-selector model-selector"
      :disabled="disabled"
      placement="top-start"
      :teleported="true"
      popper-class="chat-input-select-dropdown codex-model-dropdown"
      :popper-options="{
        modifiers: [
          { name: 'preventOverflow', options: { boundary: 'viewport' } },
          { name: 'flip', options: { fallbackPlacements: ['top-start', 'top'] } }
        ]
      }"
      @change="handleModelChange"
    >
      <el-option
        v-for="model in codexModels"
        :key="model.modelId"
        :value="model.modelId"
        :label="formatCodexModelLabel(model)"
      >
        <span class="model-option-label">
          <span class="model-option-check" :class="{ active: model.modelId === localModel }">✓</span>
          <span>{{ formatCodexModelLabel(model) }}</span>
        </span>
      </el-option>
    </el-select>

    <!-- Reasoning effort -->
    <el-select
      v-model="localReasoningEffort"
      class="cursor-selector reasoning-selector"
      :disabled="disabled"
      placement="top-start"
      :teleported="true"
      popper-class="chat-input-select-dropdown codex-reasoning-dropdown"
      :popper-options="{
        modifiers: [
          { name: 'preventOverflow', options: { boundary: 'viewport' } },
          { name: 'flip', options: { fallbackPlacements: ['top-start', 'top'] } }
        ]
      }"
      @change="handleReasoningChange"
    >
      <template #prefix>
        <span class="mode-prefix-icon">🧠</span>
      </template>
      <el-option
        v-for="option in reasoningOptions"
        :key="option.value"
        :value="option.value"
        :label="option.shortLabel"
      >
        <span class="mode-option-label">
          <span class="mode-icon">🧠</span>
          <span>{{ option.label }}</span>
        </span>
      </el-option>
    </el-select>

  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  SANDBOX_MODE_OPTIONS,
  REASONING_EFFORT_OPTIONS,
  getSandboxModeIcon,
  type CodexSandboxMode,
  type CodexReasoningEffort,
} from '@/types/codex'
import { useSettingsStore } from '@/stores/settingsStore'

interface Props {
  model: string
  sandboxMode: CodexSandboxMode
  reasoningEffort: CodexReasoningEffort
  disabled?: boolean
}

interface Emits {
  (e: 'update:model', value: string): void
  (e: 'update:sandboxMode', value: CodexSandboxMode): void
  (e: 'update:reasoningEffort', value: CodexReasoningEffort): void
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<Emits>()

// Local state
const localModel = ref(props.model)
const localSandboxMode = ref(props.sandboxMode)
const localReasoningEffort = ref(props.reasoningEffort)

// Options
const settingsStore = useSettingsStore()
const codexModels = computed(() => {
  const models = settingsStore.getModelsForBackend('codex') || []
  const currentModelId = props.model
  if (!currentModelId || models.some(model => model.modelId === currentModelId)) {
    return models
  }
  return [
    {
      modelId: currentModelId,
      displayName: currentModelId,
      description: 'Custom model',
      supportsThinking: true,
    },
    ...models
  ]
})
const sandboxOptions = SANDBOX_MODE_OPTIONS
const reasoningOptions = REASONING_EFFORT_OPTIONS

// Watch props
watch(() => props.model, (val) => { localModel.value = val })
watch(() => props.sandboxMode, (val) => { localSandboxMode.value = val })
watch(() => props.reasoningEffort, (val) => { localReasoningEffort.value = val })

// Handlers
function handleModelChange(value: string) {
  emit('update:model', value)
}

function formatCodexModelLabel(model: { modelId: string; displayName: string }) {
  const name = model.displayName || model.modelId
  if (!name) return ''
  const lower = name.toLowerCase()
  if (name === lower && lower.startsWith('gpt-')) {
    return lower
      .split('-')
      .map(part => {
        if (part === 'gpt') return 'GPT'
        if (/^\d/.test(part)) return part
        return part.charAt(0).toUpperCase() + part.slice(1)
      })
      .join('-')
  }
  return name
}


function handleSandboxChange(value: CodexSandboxMode) {
  emit('update:sandboxMode', value)
}

function handleReasoningChange(value: CodexReasoningEffort) {
  emit('update:reasoningEffort', value)
}
</script>

<style scoped>
.codex-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ========== Cursor 风格选择器 - 自动宽度 ========== */
.cursor-selector {
  font-size: 11px;
  flex: 0 0 auto;
  margin: 0;
}

/* 强制 el-select 根据内容自适应宽度 */
.cursor-selector.el-select {
  flex: 0 0 auto;
  margin: 0;
  /* 关键：覆盖 Element Plus 的 CSS 变量 */
  --el-select-width: auto !important;
  width: auto !important;
  display: inline-block;
}

/* 内部 wrapper 也需要自适应宽度 */
.cursor-selector :deep(.el-select__wrapper) {
  display: inline-flex !important;
  width: auto !important;
  min-width: unset !important;
  padding: 2px 4px;
  border: none !important;
  border-radius: 4px;
  background: transparent !important;
  box-shadow: none !important;
  min-height: 20px;
  gap: 2px;
}

.cursor-selector :deep(.el-select__wrapper):hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05)) !important;
}

.cursor-selector :deep(.el-select__wrapper.is-focused) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05)) !important;
  box-shadow: none !important;
}

.cursor-selector :deep(.el-select__placeholder) {
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 11px;
}

/* 选中项文本 - 不截断，自动宽度 */
.cursor-selector :deep(.el-select__selection) {
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 11px;
  /* 关键：覆盖 Element Plus 的 flex: 1 */
  flex: 0 0 auto !important;
  min-width: 0;
  overflow: visible;
}

.cursor-selector :deep(.el-select__selected-item) {
  white-space: nowrap;
  overflow: visible;
  flex-shrink: 0;
}

/* 隐藏 placeholder 的绝对定位，避免影响宽度 */
.cursor-selector :deep(.el-select__placeholder) {
  position: static !important;
  width: auto !important;
  transform: none !important;
}

/* 确保内部 input wrapper 不影响宽度 */
.cursor-selector :deep(.el-select__input-wrapper) {
  flex: 0 0 auto !important;
  width: 0 !important;
}

.cursor-selector :deep(.el-select__input-wrapper.is-hidden) {
  display: none !important;
}

/* 下拉箭头 */
.cursor-selector :deep(.el-select__suffix) {
  color: var(--theme-secondary-foreground, #9ca3af);
  margin-left: 0;
  flex-shrink: 0;
}

.cursor-selector :deep(.el-select__suffix .el-icon) {
  font-size: 12px;
}

.cursor-selector.is-disabled :deep(.el-select__wrapper) {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 前缀图标样式 */
.mode-prefix-icon {
  font-size: 12px;
  color: var(--theme-secondary-foreground, #6a737d);
  margin-right: 1px;
  flex-shrink: 0;
}

/* 推理选择器前缀图标颜色 - 使用主题强调色 */
.reasoning-selector .mode-prefix-icon {
  color: var(--theme-accent, #3b82f6);
}

/* ========== 模式选择器下拉选项样式 ========== */
.model-option-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.model-option-check {
  width: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  opacity: 0;
  color: var(--theme-secondary-foreground, #6a737d);
}

.model-option-check.active {
  opacity: 1;
  color: var(--theme-accent, #3b82f6);
}

.mode-option-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.mode-option-label .mode-icon {
  font-size: 14px;
  width: 16px;
  text-align: center;
  color: var(--theme-secondary-foreground, #6a737d);
}
</style>

<style>
.codex-sandbox-dropdown,
.codex-model-dropdown,
.codex-reasoning-dropdown {
  width: max-content !important;
  min-width: max-content !important;
  max-width: 80vw;
}

.codex-sandbox-dropdown .el-select-dropdown__item,
.codex-model-dropdown .el-select-dropdown__item,
.codex-reasoning-dropdown .el-select-dropdown__item {
  white-space: nowrap;
}
</style>
