<template>
  <div v-if="modifiedFiles.length > 0 && isIdeMode" class="file-rollback-bar">
    <div class="rollback-header">
      <span class="rollback-icon">↩</span>
      <span class="rollback-title">{{ t('tools.modifiedFiles') }}</span>
      <span class="file-count">({{ modifiedFiles.length }})</span>
    </div>
    <div class="file-list">
      <div
        v-for="file in modifiedFiles"
        :key="file"
        class="file-item"
      >
        <span class="file-name" :title="file">{{ getFileName(file) }}</span>
        <button
          class="rollback-file-btn"
          :class="{ loading: rollingBackFiles.has(file) }"
          :disabled="rollingBackFiles.has(file)"
          :title="t('tools.rollbackFile')"
          @click="handleRollbackFile(file)"
        >
          <span v-if="rollingBackFiles.has(file)" class="spinner" />
          <span v-else>↩</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { ideaBridge, rollbackService } from '@/services/ideaBridge'

const { t } = useI18n()

// 检测是否在 IDE 模式
const isIdeMode = computed(() => ideaBridge.isInIde())

// 修改过的文件列表
const modifiedFiles = ref<string[]>([])

// 正在回滚的文件
const rollingBackFiles = ref<Set<string>>(new Set())

// 获取文件名
function getFileName(filePath: string): string {
  const parts = filePath.split(/[/\\]/)
  return parts[parts.length - 1] || filePath
}

// 加载修改过的文件列表
async function loadModifiedFiles() {
  if (!isIdeMode.value) return
  try {
    modifiedFiles.value = await rollbackService.getModifiedFiles()
  } catch (error) {
    console.error('Failed to load modified files:', error)
  }
}

// 处理文件回滚
async function handleRollbackFile(filePath: string) {
  if (rollingBackFiles.value.has(filePath)) return

  // 确认对话框
  const fileName = getFileName(filePath)
  const confirmed = confirm(t('tools.rollbackFileConfirm', { file: fileName }))
  if (!confirmed) return

  rollingBackFiles.value.add(filePath)
  try {
    const result = await rollbackService.rollbackFile(filePath)
    if (result.success) {
      // 回滚成功，从列表中移除
      modifiedFiles.value = modifiedFiles.value.filter(f => f !== filePath)
    } else {
      console.error('Rollback failed:', result.error)
      alert(t('tools.rollbackFailed') + ': ' + result.error)
    }
  } catch (error) {
    console.error('Rollback error:', error)
    alert(t('tools.rollbackFailed'))
  } finally {
    rollingBackFiles.value.delete(filePath)
  }
}

// 定时刷新列表
let refreshInterval: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  loadModifiedFiles()
  // 每 5 秒刷新一次
  refreshInterval = setInterval(loadModifiedFiles, 5000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
})

// 暴露刷新方法
defineExpose({
  refresh: loadModifiedFiles
})
</script>

<style scoped>
.file-rollback-bar {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 12px;
  background: color-mix(in srgb, var(--theme-warning) 8%, var(--theme-panel-background));
  border: 1px solid color-mix(in srgb, var(--theme-warning) 25%, transparent);
  border-radius: 6px;
  margin-bottom: 8px;
}

.rollback-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--theme-warning);
}

.rollback-icon {
  font-size: 14px;
}

.rollback-title {
  font-weight: 600;
}

.file-count {
  color: var(--theme-secondary-foreground);
  font-weight: normal;
}

.file-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px 3px 8px;
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: 12px;
}

.file-name {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--theme-foreground);
}

.rollback-file-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--theme-secondary-foreground);
  cursor: pointer;
  font-size: 11px;
  transition: all 0.15s ease;
}

.rollback-file-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--theme-warning) 20%, transparent);
  color: var(--theme-warning);
}

.rollback-file-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.rollback-file-btn .spinner {
  width: 10px;
  height: 10px;
  border: 1.5px solid transparent;
  border-top-color: var(--theme-warning);
  border-right-color: var(--theme-warning);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
