<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="generic-details">
        <!-- 参数区域 -->
        <div class="params-section">
<div class="section-title">Params</div>
          <pre class="params-content">{{ paramsFormatted }}</pre>
        </div>
        <!-- 结果区域 -->
        <div v-if="hasResult" class="result-section">
          <div class="section-title">Result</div>
          <div class="result-content">
            <div v-for="(block, index) in resultBlocks" :key="index" class="result-block">
              <pre v-if="blockType(block) === 'text'" class="result-text">{{ blockText(block) }}</pre>
              <img
                v-else-if="blockType(block) === 'image'"
                class="result-image"
                :src="mediaUrl(block)"
                :alt="blockAlt(block)"
              />
              <audio
                v-else-if="blockType(block) === 'audio'"
                class="result-audio"
                controls
                :src="mediaUrl(block)"
              />
              <div v-else-if="blockType(block) === 'resource_link'" class="resource-link">
                <a :href="block.uri" target="_blank" rel="noreferrer noopener">
                  {{ block.title || block.name || block.uri }}
                </a>
                <div class="resource-meta">{{ resourceMeta(block) }}</div>
                <div v-if="block.description" class="resource-desc">{{ block.description }}</div>
              </div>
              <div v-else-if="blockType(block) === 'resource'" class="embedded-resource">
                <div class="resource-meta">{{ embeddedMeta(block) }}</div>
                <pre v-if="embeddedText(block)" class="result-text">{{ embeddedText(block) }}</pre>
                <a
                  v-else-if="embeddedBlob(block)"
                  class="resource-link-inline"
                  :href="embeddedBlobUrl(block)"
                  download
                >
                  Download embedded resource
                </a>
                <pre v-else class="result-text">{{ formatBlock(block) }}</pre>
              </div>
              <pre v-else class="result-text">{{ formatBlock(block) }}</pre>
            </div>
          </div>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { GenericToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall?.result as any))

const params = computed(() => props.toolCall?.input || {})
const paramsFormatted = computed(() => {
  try {
    return JSON.stringify(params.value, null, 2)
  } catch {
    return String(params.value)
  }
})

type JsonMcpBlock = {
  type: 'json'
  value: {
    content: any[]
  }
}

function isJsonMcpBlock(block: any): block is JsonMcpBlock {
  return (
    block &&
    typeof block === 'object' &&
    block.type === 'json' &&
    block.value &&
    typeof block.value === 'object' &&
    Array.isArray(block.value.content)
  )
}

function unwrapMcpBlocks(blocks: any[]): any[] {
  const unwrapped: any[] = []
  for (const block of blocks) {
    if (block?.type === 'json') {
      if (isJsonMcpBlock(block)) {
        unwrapped.push(...block.value.content)
      }
      continue
    }
    unwrapped.push(block)
  }
  return unwrapped
}

const resultBlocks = computed<any[]>(() => {
  const r = props.toolCall?.result
  if (!r) return []
  const content = (r as any).content
  if (typeof content === 'string') {
    return [{ type: 'text', text: content }]
  }
  if (Array.isArray(content)) {
    return unwrapMcpBlocks(content)
  }
  if (content?.type === 'json') {
    if (isJsonMcpBlock(content)) {
      return content.value.content
    }
    return []
  }
  // 处理单个 MCP 结果对象（如 { type: 'text', text: '...' }）
  if (content && typeof content === 'object' && content.type) {
    return [content]
  }
  return []
})

const hasResult = computed(() => (resultBlocks.value?.length ?? 0) > 0)

const hasDetails = computed(() => Object.keys(params.value || {}).length > 0 || hasResult.value)

function blockType(block: any): string {
  return typeof block?.type === 'string' ? block.type : 'unknown'
}

function blockText(block: any): string {
  if (typeof block?.text === 'string') return block.text
  if (typeof block?.value === 'string') return block.value
  return formatBlock(block)
}

function formatBlock(block: any): string {
  try {
    return JSON.stringify(block, null, 2)
  } catch {
    return String(block)
  }
}

function mediaUrl(block: any): string {
  const data = block?.data || block?.blob
  if (!data) return ''
  const mimeType = block?.mimeType || block?.mime_type || 'application/octet-stream'
  return `data:${mimeType};base64,${data}`
}

function blockAlt(block: any): string {
  return block?.mimeType || block?.mime_type || block?.type || 'media'
}

function resourceMeta(block: any): string {
  const parts = [block?.mimeType || block?.mime_type, block?.size ? `${block.size} bytes` : '']
  return parts.filter(Boolean).join(' · ')
}

function embeddedText(block: any): string | null {
  const resource = block?.resource
  if (resource && typeof resource.text === 'string') return resource.text
  return null
}

function embeddedBlob(block: any): string | null {
  const resource = block?.resource
  if (resource && typeof resource.blob === 'string') return resource.blob
  return null
}

function embeddedBlobUrl(block: any): string {
  const resource = block?.resource || {}
  const data = resource.blob
  if (!data) return ''
  const mimeType = resource.mimeType || resource.mime_type || 'application/octet-stream'
  return `data:${mimeType};base64,${data}`
}

function embeddedMeta(block: any): string {
  const resource = block?.resource || {}
  const uri = resource.uri ? ` ${resource.uri}` : ''
  const mimeType = resource.mimeType || resource.mime_type || ''
  return [mimeType, uri].filter(Boolean).join(' · ')
}
</script>

<style scoped>
.generic-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.params-section {
  display: flex;
  flex-direction: column;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-secondary-foreground, #586069);
  margin-bottom: 6px;
  text-transform: uppercase;
}

.params-content {
  margin: 0;
  padding: 8px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  font-family: var(--theme-editor-font-family);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}

.result-section {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding-top: 8px;
}

.result-content {
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  max-height: 300px;
  overflow-y: auto;
}

.result-block + .result-block {
  border-top: 1px solid var(--theme-border, #e1e4e8);
}

.result-text {
  margin: 0;
  padding: 8px;
  font-size: 12px;
  font-family: var(--theme-editor-font-family);
  white-space: pre-wrap;
  word-break: break-all;
}

.result-image {
  display: block;
  max-width: 100%;
  max-height: 220px;
  padding: 8px;
}

.result-audio {
  width: 100%;
  padding: 8px;
}

.resource-link,
.embedded-resource {
  padding: 8px;
  font-size: 12px;
  word-break: break-word;
}

.resource-meta {
  color: var(--theme-secondary-foreground, #586069);
  margin-top: 4px;
}

.resource-desc {
  margin-top: 4px;
}

.resource-link-inline {
  display: inline-block;
  padding: 8px;
  font-size: 12px;
}
</style>
