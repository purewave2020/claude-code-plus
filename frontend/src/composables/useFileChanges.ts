/**
 * 文件改动追踪 Composable
 *
 * 追踪当前会话中 JetBrains MCP 工具（WriteFile/EditFile）的文件修改，
 * 支持回滚功能。仅追踪实时收到的工具调用，历史加载的不追踪。
 */

import { ref, computed, watch, type Ref, type ComputedRef } from 'vue'
import type { DisplayItem, ToolCall } from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { jetbrainsRSocket } from '@/services/jetbrainsRSocket'
import { useSessionStore } from '@/stores/sessionStore'

// ============ 类型定义 ============

/**
 * JetBrains MCP 文件编辑工具名称
 */
const JETBRAINS_FILE_EDIT_TOOLS = [
  'mcp__jetbrains-file__WriteFile',
  'mcp__jetbrains-file__EditFile'
] as const

type JetBrainsFileEditToolName = typeof JETBRAINS_FILE_EDIT_TOOLS[number]

/**
 * 单次文件修改记录
 */
export interface FileModification {
  /** 工具调用 ID */
  toolUseId: string
  /** 本地历史时间戳（用于回滚） */
  historyTs: number
  /** 工具名称 */
  toolName: 'WriteFile' | 'EditFile'
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

// ============ 工具函数 ============

/**
 * 判断是否为 JetBrains 文件编辑工具
 */
export function isJetBrainsFileEditTool(toolName: string): toolName is JetBrainsFileEditToolName {
  return JETBRAINS_FILE_EDIT_TOOLS.includes(toolName as JetBrainsFileEditToolName)
}

/**
 * 从工具结果中提取 historyTs
 */
export function extractHistoryTs(result: any): number | null {
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
 * 从 JetBrains MCP 工具调用中提取文件路径（camelCase）
 */
function extractFilePath(toolCall: ToolCall): string | null {
  const input = toolCall.input as Record<string, any>
  return input?.filePath || null
}

/**
 * 生成修改摘要
 */
function generateSummary(toolCall: ToolCall): string {
  const input = toolCall.input as Record<string, any>
  const toolName = toolCall.toolName
  
  if (toolName === 'mcp__jetbrains-file__EditFile') {
    const oldStr = input?.oldString || ''
    const newStr = input?.newString || ''
    const oldPreview = oldStr.length > 20 ? oldStr.slice(0, 20) + '...' : oldStr
    const newPreview = newStr.length > 20 ? newStr.slice(0, 20) + '...' : newStr
    return `"${oldPreview}" → "${newPreview}"`
  }
  
  if (toolName === 'mcp__jetbrains-file__WriteFile') {
    const content = input?.content || ''
    const lines = content.split('\n').length
    return `${lines} lines`
  }
  
  return toolName
}

/**
 * 获取简短的工具名称
 */
function getShortToolName(toolName: string): 'WriteFile' | 'EditFile' {
  if (toolName === 'mcp__jetbrains-file__WriteFile') return 'WriteFile'
  return 'EditFile'
}

// ============ Composable ============

/**
 * 文件改动追踪 Composable
 */
export function useFileChanges(displayItems: Ref<DisplayItem[]> | ComputedRef<DisplayItem[]>) {
  const sessionStore = useSessionStore()
  
  // 当前会话的文件修改记录（直接存储，不从 displayItems 计算）
  const fileEdits = ref<FileModification[]>([])
  
  // 正在回滚的文件集合
  const rollingBackFiles = ref<Set<string>>(new Set())
  
  // toolUseId -> FileModification 映射（用于快速查找）
  const fileEditMap = computed(() => 
    new Map(fileEdits.value.map(e => [e.toolUseId, e]))
  )
  
  /**
   * 计算属性：按文件分组的改动列表
   */
  const fileChanges = computed<FileChange[]>(() => {
    const map = new Map<string, FileChange>()
    
    for (const edit of fileEdits.value) {
      if (edit.rolledBack) continue
      
      let fileChange = map.get(edit.filePath)
      if (!fileChange) {
        const fileName = edit.filePath.split(/[/\\]/).pop() || edit.filePath
        fileChange = {
          filePath: edit.filePath,
          fileName,
          modifications: []
        }
        map.set(edit.filePath, fileChange)
      }
      fileChange.modifications.push(edit)
    }
    
    // 按时间排序
    for (const fileChange of map.values()) {
      fileChange.modifications.sort((a, b) => a.timestamp - b.timestamp)
    }
    
    // 按最新修改时间排序文件列表
    return Array.from(map.values()).sort((a, b) => {
      const aLatest = Math.max(...a.modifications.map(m => m.timestamp))
      const bLatest = Math.max(...b.modifications.map(m => m.timestamp))
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
   * 计算属性：总编辑次数
   */
  const totalEditCount = computed(() => 
    fileEdits.value.filter(e => !e.rolledBack).length
  )
  
  /**
   * 添加文件修改记录（由外部在工具调用完成时调用）
   */
  function addFileEdit(toolCall: ToolCall): boolean {
    // 检查是否为 JetBrains 文件编辑工具
    if (!isJetBrainsFileEditTool(toolCall.toolName)) return false
    
    // 检查是否成功
    if (toolCall.status !== ToolCallStatus.SUCCESS) return false
    
    // 提取文件路径
    const filePath = extractFilePath(toolCall)
    if (!filePath) return false
    
    // 提取 historyTs
    const historyTs = extractHistoryTs(toolCall.result)
    if (!historyTs) return false
    
    // 检查是否已存在（避免重复）
    if (fileEditMap.value.has(toolCall.id)) return false
    
    // 添加记录
    fileEdits.value.push({
      toolUseId: toolCall.id,
      historyTs,
      toolName: getShortToolName(toolCall.toolName),
      summary: generateSummary(toolCall),
      filePath,
      rolledBack: false,
      timestamp: toolCall.timestamp
    })
    
    return true
  }
  
  /**
   * 检查某个工具调用是否可以回滚
   */
  function canRollback(toolUseId: string): boolean {
    const edit = fileEditMap.value.get(toolUseId)
    return edit != null && !edit.rolledBack
  }
  
  /**
   * 回滚单个修改
   * 会同时标记该修改及其之后的所有修改为已回滚
   */
  async function rollbackModification(filePath: string, historyTs: number): Promise<RollbackResult> {
    // 设置回滚中状态
    rollingBackFiles.value.add(filePath)
    
    try {
      // 调用 RSocket API 执行回滚
      const result = await jetbrainsRSocket.rollbackFile(filePath, historyTs)
      
      if (result.success) {
        // 标记该文件中 historyTs >= 指定时间的所有修改为已回滚
        for (const edit of fileEdits.value) {
          if (edit.filePath === filePath && edit.historyTs >= historyTs) {
            edit.rolledBack = true
          }
        }
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
    // 找到该文件最早的未回滚修改
    const fileModifications = fileEdits.value
      .filter(e => e.filePath === filePath && !e.rolledBack)
      .sort((a, b) => a.timestamp - b.timestamp)
    
    if (fileModifications.length === 0) {
      return { success: false, error: 'No modifications to rollback' }
    }
    
    const earliestMod = fileModifications[0]
    return rollbackModification(filePath, earliestMod.historyTs)
  }
  
  /**
   * 检查文件是否正在回滚
   */
  function isRollingBack(filePath: string): boolean {
    return rollingBackFiles.value.has(filePath)
  }
  
  /**
   * 根据 toolUseId 执行回滚
   * 用于工具卡片上的回滚按钮
   */
  async function rollbackByToolUseId(toolUseId: string): Promise<RollbackResult> {
    const edit = fileEditMap.value.get(toolUseId)
    if (!edit) {
      return { success: false, error: 'Modification not found' }
    }
    return rollbackModification(edit.filePath, edit.historyTs)
  }
  
  /**
   * 获取指定 toolUseId 的修改信息
   */
  function getModificationByToolUseId(toolUseId: string): FileModification | null {
    return fileEditMap.value.get(toolUseId) || null
  }
  
  /**
   * 清空所有改动记录
   */
  function clear() {
    fileEdits.value = []
    rollingBackFiles.value = new Set()
  }
  
  // 监听 displayItems 变化
  // 1. 自动检测并添加新完成的 JetBrains MCP 工具调用（仅在生成中）
  // 2. 清理已删除的工具调用
  watch(
    displayItems,
    (newItems) => {
      // 收集当前有效的 toolUseId
      const validIds = new Set<string>()
      
      // 只有在生成中（实时接收消息）时才添加新的工具调用
      // 历史加载时不添加，因为那些不属于"当前会话"
      const isGenerating = sessionStore.currentIsGenerating
      
      for (const item of newItems) {
        if (item.displayType !== 'toolCall') continue
        validIds.add(item.id)
        
        // 只在生成中才尝试添加新的工具调用
        if (isGenerating) {
          const toolCall = item as ToolCall
          if (toolCall.status === ToolCallStatus.SUCCESS) {
            addFileEdit(toolCall)
          }
        }
      }
      
      // 过滤掉不存在的记录（用户编辑历史消息时会删除后面的 items）
      const before = fileEdits.value.length
      fileEdits.value = fileEdits.value.filter(e => validIds.has(e.toolUseId))
      
      if (fileEdits.value.length < before) {
        console.log(`[useFileChanges] Cleaned ${before - fileEdits.value.length} stale file edits`)
      }
    },
    { deep: false }
  )
  
  return {
    // 状态
    fileChanges,
    hasChanges,
    changedFileCount,
    totalEditCount,
    
    // 方法
    addFileEdit,
    canRollback,
    rollbackFile,
    rollbackModification,
    rollbackByToolUseId,
    getModificationByToolUseId,
    isRollingBack,
    clear
  }
}

/**
 * useFileChanges 返回类型
 */
export type FileChangesI