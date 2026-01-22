<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="skill-details">
        <!-- 参数区域 -->
        <div class="params-section">
          <div class="info-row">
            <span class="label">Skill:</span>
            <code class="value">{{ skill }}</code>
          </div>
          <div v-if="args" class="info-row">
            <span class="label">Args:</span>
            <code class="value">{{ args }}</code>
          </div>
        </div>

        <!-- Skill 内容区域（SKILL.md 内容） -->
        <div v-if="skillContent" class="skill-content-section">
          <div class="section-header" @click.stop="contentExpanded = !contentExpanded">
            <span class="expand-icon">{{ contentExpanded ? '▼' : '▶' }}</span>
            <span class="section-title">Skill Instructions</span>
          </div>
          <div v-if="contentExpanded" class="skill-content-container">
            <MarkdownRenderer :content="skillContent" class="skill-content" />
          </div>
        </div>

        <!-- 结果区域 -->
        <div v-if="hasResult" class="result-section">
          <div class="section-title-row">
            <span class="section-title">Result</span>
          </div>
          <pre class="result-content">{{ resultText }}</pre>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { GenericToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
const expanded = ref(false)
const contentExpanded = ref(true)  // 默认展开 Skill 内容

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))
const skill = computed(() => props.toolCall.input?.skill || '')
const args = computed(() => props.toolCall.input?.args || '')
const skillContent = computed(() => (props.toolCall as any).skillContent || '')

const resultText = computed(() => {
  const r = props.toolCall.result
  if (!r || r.is_error) return ''
  if (typeof r.content === 'string') return r.content
  return JSON.stringify(r.content, null, 2)
})

const hasResult = computed(() => {
  const r = props.toolCall.result
  return r && !r.is_error && resultText.value
})

const hasDetails = computed(() => !!skill.value || !!skillContent.value)
</script>

<style scoped>
.skill-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.params-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  align-items: baseline;
}

.label {
  color: var(--theme-secondary-foreground, #586069);
  min-width: 50px;
}

.value {
  color: var(--theme-foreground, #24292e);
}

code.value {
  background: var(--theme-code-background, #f0f4f8);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: var(--theme-editor-font-family);
}

/* Skill 内容区域 */
.skill-content-section {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding-top: 8px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
  padding: 4px 0;
}

.section-header:hover {
  opacity: 0.8;
}

.expand-icon {
  font-size: 10px;
  color: var(--theme-secondary-foreground, #586069);
  width: 12px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-secondary-foreground, #586069);
  text-transform: uppercase;
}

.section-title-row {
  margin-bottom: 6px;
}

.skill-content-container {
  margin-top: 8px;
  padding: 12px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  max-height: 400px;
  overflow-y: auto;
}

.skill-content {
  font-size: 12px;
}

.skill-content :deep(h1),
.skill-content :deep(h2),
.skill-content :deep(h3) {
  margin-top: 12px;
  margin-bottom: 8px;
}

.skill-content :deep(h1:first-child),
.skill-content :deep(h2:first-child),
.skill-content :deep(h3:first-child) {
  margin-top: 0;
}

.skill-content :deep(pre) {
  background: var(--theme-background, #fff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  padding: 8px;
  overflow-x: auto;
}

.skill-content :deep(code) {
  font-size: 11px;
}

/* 结果区域 */
.result-section {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding-top: 8px;
}

.result-content {
  margin: 0;
  padding: 8px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  font-family: var(--theme-editor-font-family);
  white-space: pre-wrap;
}
</style>
