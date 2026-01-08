<template>
  <div v-if="hasChanges && isIdeMode" class="file-rollback-bar">
    <!-- 头部：可点击展开/收起 -->
    <div class="rollback-header" @click="toggleExpanded">
      <span class="rollback-icon">↩</span>
      <span class="rollback-title">{{ t('tools.modifiedFiles') }}</span>
      <span class="file-count">({{ changedFileCount }})</span>
      <span class="expand-icon" :class="{ expanded }">▾</span>
    </div>

    <!-- 展开内容 -->
    <div v-if="expanded" class="file-list">
      <div
        v-for="file in fileChanges"
        :key="file.filePath"
        class="file-group"
      >
        <!-- 文件头部 -->
        <div class="file-header">
          <span class="file-icon">📄</span>
          <span 
            class="file-name" 
            :title="file.filePath"
            @click="openFile(file.filePath)"
          >
            {{ file.fileName }}
          </span>
          <button
            class="rollback-all-btn"
            :class="{ loading: isRollingBack(file.filePath) }"
            :disabled="isRollingBack(file.filePath)"
            :title="t('tools.rollbackAll')"
            @click="handleRollbackFile(file.filePath)"
          >
            <span v-if="isRollingBack(file.filePath)" class="spinner" />
            <span v-else>↩ {{ t('tools.rollbackAll') }}</span>
          </button>
        </div>

        <!-- 修改列表 -->
        <div class="modification-list">
          <div
            v-for="mod in getActiveModifications(file)"
            :key="mod.toolUseId"
            class="modification-item"
            :class="{ 'rolled-back': mod.rolledBack }"
          >
            <span class="mod-tool">{{ mod.toolName }}</span>
            <span class="mod-summary" :title="mod.summary">{{ mod.summary }}</span>
            <button
              v-if="!mod.rolledBack"
              class="rollback-mod-btn"
              :title="t('tools.rollbackModification')"
              @click="handleRollbackModification(file.filePath, mod.historyTs)"
            >
              ↩
            </button>
            <span v-else class="rolled-back-badge">{{ t('tools.rolledBack') }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 收起状态：简洁显示 -->
    <div v-else class="file-tags">
      <div
        v-for="file in fileChanges"
        :key="file.filePath"
        class="file-tag"
      >
        <span class="file-name" :title="file.filePath">{{ file.fileName }}</span>
        <button
          class="rollback-tag-btn"
          :class="{ loading: isRollingBack(file.filePath) }"
          :disabled="isRollingBack(file.filePath)"
          @click.stop="handleRollbackFile(file.filePath)"
        >
          <span v-if="isRollingBack(file.filePath)" class="spinner" />
          <span v-else>↩</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { ideaBridge } from '@/services/ideaBridge'
import { jetbrainsRSocket } from '@/services/jetbrainsRSocket'
import type { FileChangesInstance, FileChange } from '@/composables/useFileChanges'

const { t } = useI18n()

// 注入 fileChanges 实例（由父组件 provide）
const fileChangesInstance = inject<FileChangesInstance>('fileChanges')

// 检测是否在 IDE 模式
const isIdeMode = computed(() => ideaBridge.isInIde())

// 展开/收起状态
const expanded = ref(false)

// 从 composable 获取数据
const fileChanges = computed(() => fileChangesInstance?.fileChanges.value ?? [])
const hasChanges = computed(() => fileChangesInstance?.hasChanges.value ?? false)
const changedFileCount = computed(() => fileChangesInstance?.changedFileCount.value ?? 0)

// 检查文件是否正在回滚
function isRollingBack(filePath: string): boolean {
  return fileChangesInstance?.isRollingBack(filePath) ?? false
}

// 获取文件的活跃修改（未回滚的）
function getActiveModifications(file: FileChange) {
  return file.modifications.filter(m => !m.rolledBack)
}

// 切换展开/收起
function toggleExpanded() {
  expanded.value = !expanded.value
}

// 打开文件
async function openFile(filePath: string) {
  try {
    await jetbrainsRSocket.openFile({ filePath })
  } catch (error) {
    console.error('Failed to open file:', error)
  }
}

// 处理回滚整个文件
async function handleRollbackFile(filePath: string) {
  if (!fileChangesInstance) return
  
  const file = fileChanges.value.find(f => f.filePath === filePath)
  if (!file) return
  
  // 确认对话框
  const confirmed = confirm(t('tools.rollbackFileConfirm', { file: file.fileName }))
  if (!confirmed) return
  
  const result = await fileChangesInstance.rollbackFile(filePath)
  if (!result.success) {
    console.error('Rollback failed:', result.error)
    alert(t('tools.rollbackFailed') + ': ' + result.error)
  }
}

// 处理回滚单个修改
async function handleRollbackModification(filePath: string, historyTs: number) {
  if (!fileChangesInstance) return
  
  // 确认对话框
  const confirmed = confirm(t('tools.rollbackModificationConfirm'))
  if (!confirmed) return
  
  const result = await fileChangesInstance.rollbackModification(filePath, historyTs)
  if (!result.success) {
    console.error('Rollback failed:', result.error)
    alert(t('tools.rollbackFailed') + ': ' + result.error)
  }
}

// 暴露刷新方法
defineExpose({
  refresh: () => fileChangesInstance?.refresh()
})
</script>

<style scoped>
.file-rollback-bar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  background: color-mix(in srgb, var(--theme-warning) 8%, var(--theme-panel-background));
  border: 1px solid color-mix(in srgb, var(--theme-warning) 25%, transparent);
  border-radius: 8px;
  margin-bottom: 8px;
}

.rollback-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--theme-warning);
  cursor: pointer;
  user-select: none;
}

.rollback-header:hover {
  opacity: 0.85;
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

.expand-icon {
  margin-left: auto;
  transition: transform 0.2s ease;
  font-size: 10px;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

/* 收起状态：标签列表 */
.file-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.file-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px 4px 10px;
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: 12px;
}

.file-tag .file-name {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--theme-foreground);
}

.rollback-tag-btn {
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

.rollback-tag-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--theme-warning) 20%, transparent);
  color: var(--theme-warning);
}

/* 展开状态：文件组 */
.file-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-group {
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  overflow: hidden;
}

.file-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: color-mix(in srgb, var(--theme-foreground) 3%, transparent);
  border-bottom: 1px solid var(--theme-border);
}

.file-icon {
  font-size: 12px;
}

.file-header .file-name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-link);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-header .file-name:hover {
  text-decoration: underline;
}

.rollback-all-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid color-mix(in srgb, var(--theme-warning) 40%, transparent);
  border-radius: 4px;
  background: transparent;
  color: var(--theme-warning);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.rollback-all-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--theme-warning) 15%, transparent);
}

.rollback-all-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

/* 修改列表 */
.modification-list {
  display: flex;
  flex-direction: column;
}

.modification-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--theme-border) 50%, transparent);
}

.modification-item:last-child {
  border-bottom: none;
}

.modification-item.rolled-back {
  opacity: 0.5;
  text-decoration: line-through;
}

.mod-tool {
  padding: 2px 6px;
  background: color-mix(in srgb, var(--theme-accent) 15%, transparent);
  border-radius: 3px;
  color: var(--theme-accent);
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
}

.mod-summary {
  flex: 1;
  color: var(--theme-secondary-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rollback-mod-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--theme-secondary-foreground);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.15s ease;
}

.rollback-mod-btn:hover {
  background: color-mix(in srgb, var(--theme-warning) 20%, transparent);
  color: var(--theme-warning);
}

.rolled-back-badge {
  font-size: 10px;
  color: var(--theme-secondary-foreground);
  font-style: italic;
}

/* 加载状态 */
.spinner {
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
