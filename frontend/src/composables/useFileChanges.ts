/**
 * 文件改动追踪 Composable
 *
 * 从 displayItems 中提取 Edit/Write 工具的文件修改记录，
 * 按文件分组并支持回滚功能
 */

import { ref, computed, watch, type Ref, type ComputedRef } from 'vue'
import type { DisplayItem, ToolCall } from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { CLAUDE_TOOL_TYPE } from '@/constants/toolTypes'
import { jetbrainsRSocket } from '@/services/jetbrainsRSocket'

/**
 * 单次文件修改记录
 */
export interface FileModification {
  /** 工具调用 ID */
  toolUseId: string
  /** 本地历史时间戳（用于回滚） */
  historyTs: number
  /** 工具名称 */
  toolName: 'Edit' | 'Write' | 'MultiEdit'
  /** 修改摘要 */
  summary: string
  /** 文件路径 */
  filePath: string
  /** 是否已回滚 */
  rolledBack: boolean
  /** 时间戳（显示用） */
  timestamp: number
}

/**
 * 文件改动记录（包含该文件的所有修改）
 */
export interface FileChange {
  /** 文件路径 */
  filePath: string
  /** 文件名 */
  fileName: string
  /** 该文件的所有修改记录（按时间顺序） */
  modifications: FileModification[]
}

/**
 * 回滚结果
 */
export interface RollbackResult {
  success: boolean
  error?: string
}

/**
 * 从工具结果中提取 historyTs
 */
function extractHistoryTs(result: any): number | null {
  if (!result?.content) return null
  
  const content = typeof result.content === 'string' 
    ? result.content 
    : Array.isArray(result.content) 
      ? result.content.map((c: any) => typeof c === 'string' ? c : c.text || '').join('')
      : ''
  
  // 匹配 [jb:historyTs=xxx] 格式
  const match = content.match(/\[jb:historyTs=(\d+)\]/)
  if (match) {
    return parseInt(match[1], 10)
  }
  return null
}

/**
 * 从工具调用中提取文件路径
 */
function extractFilePath(toolCall: ToolCall): string | null {
  const input = toolCall.input as Record<string, any>
  return input?.file_path || input?.path || null
}

/**
 * 生成修改摘要
 */
function generateSummary(toolCall: ToolCall): string {
  const input = toolCall.input as Record<string, any>
  const toolName = toolCall.toolName
  
  if (toolName === 'Edit') {
    const oldStr = input?.old_string || ''
    const newStr = input?.new_string || ''
    const oldPreview = oldStr.length > 20 ? oldStr.slice(0, 20) + '...' : oldStr
    const newPreview = newStr.length > 20 ? newStr.slice(0, 20) + '...' : newStr
    return `"${oldPreview}" → "${newPreview}"`
  }
  
  if (toolName === 'MultiEdit') {
    const editsCount = input?.edits?.length || 0
    return `${editsCount} 处修改`
  }
  
  if (toolName === 'Write') {
    const content = input?.content || ''
    const lines = content.split('\n').length
    return `写入 ${lines} 行`
  }
  
  return toolName
}

/**
 * 文件改动追踪 Composable
 */
export function useFileChanges(displayItems: Ref<DisplayItem[]> | ComputedRef<DisplayItem[]>) {
  // 文件改动 Map：filePath -> FileChange
  const changesMap = ref<Map<string, FileChange>>(new Map())
  
  // 正在回滚的文件集合
  const rollingBackFiles = ref<Set<string>>(new Set())
  
  /**
   * 计算属性：有改动的文件列表
   */
  const fileChanges = computed<FileChange[]>(() => {
    return Array.from(changesMap.value.values())
      .filter(fc => fc.modifications.some(m => !m.rolledBack))
      .sort((a, b) => {
        // 按最新修改时间排序
        const aLatest = Math.max(...a.modifications.filter(m => !m.rolledBack).map(m => m.timestamp))
        const bLatest = Math.max(...b.modifications.filter(m => !m.rolledBack).map(m => m.timestamp))
        return bLatest - aLatest
      })
  })
  
  /**
   * 计算属性：是否有未回滚的改动
   */
  const hasChanges = computed(() => fileChanges.value.length > 0)
  
  /**
   * 计算属性：总修改文件数
   */
  const changedFileCount = computed(() => fileChanges.value.length)
  
  /**
   * 扫描 displayItems 并提取文件改动
   */
  function scanDisplayItems(items: DisplayItem[]) {
    const newMap = new Map<string, FileChange>()
    
    for (const item of items) {
      if (item.displayType !== 'toolCall') continue
      
      const toolCall = item as ToolCall
      const toolType = toolCall.toolType
      
      // 只处理 Edit/Write/MultiEdit 工具
      if (toolType !== CLAUDE_TOOL_TYPE.EDIT && 
          toolType !== CLAUDE_TOOL_TYPE.WRITE && 
          toolType !== CLAUDE_TOOL_TYPE.MULTI_EDIT) {
        continue
      }
      
      // 只处理成功的工具调用
      if (toolCall.status !== ToolCallStatus.SUCCESS) continue
      
      // 提取文件路径
      const filePath = extractFilePath(toolCall)
      if (!filePath) continue
      
      // 提取 historyTs
      const historyTs = extractHistoryTs(toolCall.result)
      if (!historyTs) continue
      
      // 获取或创建文件改动记录
      let fileChange = newMap.get(filePath)
      if (!fileChange) {
        const fileName = filePath.split(/[/\\]/).pop() || filePath
        fileChange = {
          filePath,
          fileName,
          modifications: []
        }
        newMap.set(filePath, fileChange)
      }
      
      // 检查是否已存在该修改（避免重复）
      const existing = fileChange.modifications.find(m => m.toolUseId === toolCall.id)
      if (existing) continue
      
      // 保留旧的回滚状态
      const oldFileChange = changesMap.value.get(filePath)
      const oldMod = oldFileChange?.modifications.find(m => m.toolUseId === toolCall.id)
      const rolledBack = oldMod?.rolledBack ?? false
      
      // 添加修改记录
      const toolName = toolCall.toolName as 'Edit' | 'Write' | 'MultiEdit'
      fileChange.modifications.push({
        toolUseId: toolCall.id,
        historyTs,
        toolName,
        summary: generateSummary(toolCall),
        filePath,
        rolledBack,
        timestamp: toolCall.timestamp
      })
    }
    
    // 按时间排序每个文件的修改
    for (const fileChange of newMap.values()) {
      fileChange.modifications.sort((a, b) => a.timestamp - b.timestamp)
    }
    
    changesMap.value = newMap
  }
  
  /**
   * 回滚单个修改
   * 会同时标记该修改及其之后的所有修改为已回滚
   */
  async function rollbackModification(filePath: string, historyTs: number): Promise<RollbackResult> {
    const fileChange = changesMap.value.get(filePath)
    if (!fileChange) {
      return { success: false, error: '文件改动记录不存在' }
    }
    
    // 设置回滚中状态
    rollingBackFiles.value.add(filePath)
    
    try {
      // 调用 RSocket API 执行回滚
      const result = await jetbrainsRSocket.rollbackFile(filePath, historyTs)
      
      if (result.success) {
        // 标记该修改及之后的所有修改为已回滚
        for (const mod of fileChange.modifications) {
          if (mod.historyTs >= historyTs) {
            mod.rolledBack = true
          }
        }
        
        // 触发响应式更新
        changesMap.value = new Map(changesMap.value)
      }
      
      return result
    } finally {
      rollingBackFiles.value.delete(filePath)
    }
  }
  
  /**
   * 回滚整个文件（回滚到最早的修改之前）
   */
  async function rollbackFile(filePath: string): Promise<RollbackResult> {
    const fileChange = changesMap.value.get(filePath)
    if (!fileChange || fileChange.modifications.length === 0) {
      return { success: false, error: '文件改动记录不存在' }
    }
    
    // 找到最早的未回滚修改
    const earliestMod = fileChange.modifications.find(m => !m.rolledBack)
    if (!earliestMod) {
      return { success: false, error: '没有可回滚的修改' }
    }
    
    return rollbackModification(filePath, earliestMod.historyTs)
  }
  
  /**
   * 检查文件是否正在回滚
   */
  function isRollingBack(filePath: string): boolean {
    return rollingBackFiles.value.has(filePath)
  }
  
  /**
   * 根据 toolUseId 标记修改为已回滚
   * 用于工具卡片上的回滚按钮联动
   */
  function markRolledBackByToolUseId(toolUseId: string) {
    for (const fileChange of changesMap.value.values()) {
      const modIndex = fileChange.modifications.findIndex(m => m.toolUseId === toolUseId)
      if (modIndex >= 0) {
        // 标记该修改及之后的所有修改为已回滚
        for (let i = modIndex; i < fileChange.modifications.length; i++) {
          fileChange.modifications[i].rolledBack = true
        }
        // 触发响应式更新
        changesMap.value = new Map(changesMap.value)
        break
      }
    }
  }
  
  /**
   * 根据 toolUseId 执行回滚
   * 用于工具卡片上的回滚按钮
   */
  async function rollbackByToolUseId(toolUseId: string): Promise<RollbackResult> {
    for (const fileChange of changesMap.value.values()) {
      const mod = fileChange.modifications.find(m => m.toolUseId === toolUseId)
      if (mod) {
        return rollbackModification(fileChange.filePath, mod.historyTs)
      }
    }
    return { success: false, error: '找不到对应的修改记录' }
  }
  
  /**
   * 获取指定 toolUseId 的修改信息
   */
  function getModificationByToolUseId(toolUseId: string): FileModification | null {
    for (const fileChange of changesMap.value.values()) {
      const mod = fileChange.modifications.find(m => m.toolUseId === toolUseId)
      if (mod) return mod
    }
    return null
  }
  
  /**
   * 清空所有改动记录
   */
  function clear() {
    changesMap.value = new Map()
    rollingBackFiles.value = new Set()
  }
  
  // 监听 displayItems 变化
  watch(
    displayItems,
    (newItems) => {
      scanDisplayItems(newItems)
    },
    { immediate: true, deep: true }
  )
  
  return {
    // 状态
    fileChanges,
    hasChanges,
    changedFileCount,
    
    // 方法
    rollbackFile,
    rollbackModification,
    rollbackByToolUseId,
    markRolledBackByToolUseId,
    getModificationByToolUseId,
    isRollingBack,
    clear,
    
    // 手动刷新
    refresh: () => scanDisplayItems(displayItems.value)
  }
}

/**
 * useFileChanges 返回类型
 */
export type FileChangesInstance = ReturnType<typeof useFileChanges>
