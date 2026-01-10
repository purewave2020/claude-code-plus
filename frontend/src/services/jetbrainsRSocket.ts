/**
 * JetBrains IDE 集成 RSocket 服务
 *
 * 使用 RSocket + Protobuf 与后端通信
 * 支持双向调用：
 * - 前端 → 后端：openFile, showDiff, getTheme 等
 * - 后端 → 前端：onThemeChanged, onSessionCommand 等
 */

import { RSocketClient } from './rsocket/RSocketClient'
import { resolveServerHttpUrl } from '@/utils/serverUrl'
import { create, toBinary, fromBinary } from '@bufbuild/protobuf'
import {
  JetBrainsOpenFileRequestSchema,
  JetBrainsShowDiffRequestSchema,
  JetBrainsShowMultiEditDiffRequestSchema,
  JetBrainsShowEditPreviewRequestSchema,
  JetBrainsShowEditFullDiffRequestSchema,
  JetBrainsShowMarkdownRequestSchema,
  JetBrainsEditOperationSchema,
  JetBrainsOperationResponseSchema,
  JetBrainsGetThemeResponseSchema,
  JetBrainsGetLocaleResponseSchema,
  JetBrainsGetProjectPathResponseSchema,
  JetBrainsSetLocaleRequestSchema,
  JetBrainsSessionStateSchema,
  JetBrainsSessionSummarySchema,
  JetBrainsGetOriginalContentResponseSchema,
  JetBrainsGetFileHistoryContentRequestSchema,
  JetBrainsGetFileHistoryContentResponseSchema,
  JetBrainsRollbackFileRequestSchema,
  JetBrainsRollbackFileResponseSchema
} from '@/proto/jetbrains_api_pb'
import {
  GetIdeSettingsResponseSchema,
  ActiveFileChangedNotifySchema
} from '@/proto/ai_agent_rpc_pb'
import type {
  OpenFileRequest,
  ShowDiffRequest,
  ShowMultiEditDiffRequest,
  ShowEditPreviewRequest,
  ShowEditFullDiffRequest,
  ShowMarkdownRequest
} from './jetbrainsApi'

// ========== Protobuf 编解码（使用官方库）==========

/**
 * 编码 JetBrainsOpenFileRequest
 */
function encodeOpenFileRequest(request: OpenFileRequest): Uint8Array {
  const proto = create(JetBrainsOpenFileRequestSchema, {
    filePath: request.filePath,
    line: request.line,
    column: request.column,
    startOffset: request.startOffset,
    endOffset: request.endOffset
  })
  return toBinary(JetBrainsOpenFileRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowDiffRequest
 */
function encodeShowDiffRequest(request: ShowDiffRequest): Uint8Array {
  const proto = create(JetBrainsShowDiffRequestSchema, {
    filePath: request.filePath,
    oldContent: request.oldContent,
    newContent: request.newContent,
    title: request.title
  })
  return toBinary(JetBrainsShowDiffRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowMultiEditDiffRequest
 */
function encodeShowMultiEditDiffRequest(request: ShowMultiEditDiffRequest): Uint8Array {
  const proto = create(JetBrainsShowMultiEditDiffRequestSchema, {
    filePath: request.filePath,
    edits: request.edits.map(edit => create(JetBrainsEditOperationSchema, {
      oldString: edit.oldString,
      newString: edit.newString,
      replaceAll: edit.replaceAll
    })),
    currentContent: request.currentContent
  })
  return toBinary(JetBrainsShowMultiEditDiffRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowEditPreviewRequest
 */
function encodeShowEditPreviewRequest(request: ShowEditPreviewRequest): Uint8Array {
  const proto = create(JetBrainsShowEditPreviewRequestSchema, {
    filePath: request.filePath,
    edits: request.edits.map(edit => create(JetBrainsEditOperationSchema, {
      oldString: edit.oldString,
      newString: edit.newString,
      replaceAll: edit.replaceAll
    })),
    title: request.title
  })
  return toBinary(JetBrainsShowEditPreviewRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowMarkdownRequest
 */
function encodeShowMarkdownRequest(request: ShowMarkdownRequest): Uint8Array {
  const proto = create(JetBrainsShowMarkdownRequestSchema, {
    content: request.content,
    title: request.title
  })
  return toBinary(JetBrainsShowMarkdownRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowEditFullDiffRequest
 */
function encodeShowEditFullDiffRequest(request: ShowEditFullDiffRequest): Uint8Array {
  const proto = create(JetBrainsShowEditFullDiffRequestSchema, {
    filePath: request.filePath,
    oldString: request.oldString,
    newString: request.newString,
    replaceAll: request.replaceAll,
    title: request.title
  })
  return toBinary(JetBrainsShowEditFullDiffRequestSchema, proto)
}

/**
 * 编码 JetBrainsSetLocaleRequest
 */
function encodeSetLocaleRequest(locale: string): Uint8Array {
  const proto = create(JetBrainsSetLocaleRequestSchema, { locale })
  return toBinary(JetBrainsSetLocaleRequestSchema, proto)
}

/**
 * 解码 JetBrainsOperationResponse
 */
function decodeOperationResponse(data: Uint8Array): { success: boolean; error?: string } {
  const proto = fromBinary(JetBrainsOperationResponseSchema, data)
  return { success: proto.success, error: proto.error || undefined }
}

/**
 * 解码 JetBrainsGetThemeResponse -> IdeTheme
 */
function decodeThemeResponse(data: Uint8Array): IdeTheme | null {
  const proto = fromBinary(JetBrainsGetThemeResponseSchema, data)
  if (!proto.theme) return null

  const theme = proto.theme
  return {
    background: theme.background,
    foreground: theme.foreground,
    borderColor: theme.borderColor,
    panelBackground: theme.panelBackground,
    textFieldBackground: theme.textFieldBackground,
    selectionBackground: theme.selectionBackground,
    selectionForeground: theme.selectionForeground,
    linkColor: theme.linkColor,
    errorColor: theme.errorColor,
    warningColor: theme.warningColor,
    successColor: theme.successColor,
    separatorColor: theme.separatorColor,
    hoverBackground: theme.hoverBackground,
    accentColor: theme.accentColor,
    infoBackground: theme.infoBackground,
    codeBackground: theme.codeBackground,
    secondaryForeground: theme.secondaryForeground,
    fontFamily: theme.fontFamily,
    fontSize: theme.fontSize,
    editorFontFamily: theme.editorFontFamily,
    editorFontSize: theme.editorFontSize
  }
}

/**
 * 解码 JetBrainsGetLocaleResponse
 */
function decodeLocaleResponse(data: Uint8Array): string {
  const proto = fromBinary(JetBrainsGetLocaleResponseSchema, data)
  return proto.locale || 'en-US'
}

/**
 * 解码 JetBrainsGetProjectPathResponse
 */
function decodeProjectPathResponse(data: Uint8Array): string {
  const proto = fromBinary(JetBrainsGetProjectPathResponseSchema, data)
  return proto.projectPath || ''
}

/**
 * 解码 JetBrainsGetOriginalContentResponse
 */
function decodeGetOriginalContentResponse(data: Uint8Array): { success: boolean; found: boolean; content?: string; error?: string } {
  const proto = fromBinary(JetBrainsGetOriginalContentResponseSchema, data)
  return {
    success: proto.success,
    found: proto.found,
    content: proto.content || undefined,
    error: proto.error || undefined
  }
}

/**
 * 编码 JetBrainsGetFileHistoryContentRequest
 */
function encodeGetFileHistoryContentRequest(filePath: string, beforeTimestamp: number): Uint8Array {
  const proto = create(JetBrainsGetFileHistoryContentRequestSchema, {
    filePath,
    beforeTimestamp: BigInt(beforeTimestamp)
  })
  return toBinary(JetBrainsGetFileHistoryContentRequestSchema, proto)
}

/**
 * 解码 JetBrainsGetFileHistoryContentResponse
 */
function decodeGetFileHistoryContentResponse(data: Uint8Array): { success: boolean; found: boolean; content?: string; error?: string } {
  const proto = fromBinary(JetBrainsGetFileHistoryContentResponseSchema, data)
  return {
    success: proto.success,
    found: proto.found,
    content: proto.content || undefined,
    error: proto.error || undefined
  }
}

/**
 * 编码 JetBrainsRollbackFileRequest
 */
function encodeRollbackFileRequest(filePath: string, beforeTimestamp: number): Uint8Array {
  const proto = create(JetBrainsRollbackFileRequestSchema, {
    filePath,
    beforeTimestamp: BigInt(beforeTimestamp)
  })
  return toBinary(JetBrainsRollbackFileRequestSchema, proto)
}

/**
 * 解码 JetBrainsRollbackFileResponse
 */
function decodeRollbackFileResponse(data: Uint8Array): { success: boolean; error?: string } {
  const proto = fromBinary(JetBrainsRollbackFileResponseSchema, data)
  return {
    success: proto.success,
    error: proto.error || undefined
  }
}

// ========== 批量回滚相关（手动实现 Protobuf 编解码）==========

/**
 * 回滚状态枚举
 */
export enum RollbackStatus {
  STARTED = 0,
  SUCCESS = 1,
  FAILED = 2
}

/**
 * 批量回滚项
 */
export interface BatchRollbackItem {
  filePath: string
  beforeTimestamp: number
  toolUseId: string
}

/**
 * 批量回滚事件
 */
export interface BatchRollbackEvent {
  filePath: string
  toolUseId: string
  status: RollbackStatus
  error?: string
}

/**
 * 手动编码 BatchRollbackRequest (简单 Protobuf 编码)
 * message JetBrainsBatchRollbackRequest { repeated JetBrainsBatchRollbackItem items = 1; }
 * message JetBrainsBatchRollbackItem { string file_path=1; int64 before_timestamp=2; string tool_use_id=3; }
 */
function encodeBatchRollbackRequest(items: BatchRollbackItem[]): Uint8Array {
  const parts: Uint8Array[] = []
  
  for (const item of items) {
    // 编码单个 item
    const itemParts: Uint8Array[] = []
    
    // field 1: file_path (string)
    const filePathBytes = new TextEncoder().encode(item.filePath)
    itemParts.push(encodeField(1, 2, filePathBytes)) // wire type 2 = length-delimited
    
    // field 2: before_timestamp (int64 as varint)
    itemParts.push(encodeVarintField(2, item.beforeTimestamp))
    
    // field 3: tool_use_id (string)
    const toolUseIdBytes = new TextEncoder().encode(item.toolUseId)
    itemParts.push(encodeField(3, 2, toolUseIdBytes))
    
    // 合并 item 字节
    const itemBytes = concatUint8Arrays(itemParts)
    
    // 将 item 作为嵌套消息添加到 items (field 1)
    parts.push(encodeField(1, 2, itemBytes))
  }
  
  return concatUint8Arrays(parts)
}

/**
 * 编码字段（带 wire type）
 */
function encodeField(fieldNum: number, wireType: number, data: Uint8Array): Uint8Array {
  const tag = (fieldNum << 3) | wireType
  const tagBytes = encodeVarint(tag)
  const lenBytes = encodeVarint(data.length)
  return concatUint8Arrays([tagBytes, lenBytes, data])
}

/**
 * 编码 varint 字段
 */
function encodeVarintField(fieldNum: number, value: number): Uint8Array {
  const tag = (fieldNum << 3) | 0 // wire type 0 = varint
  const tagBytes = encodeVarint(tag)
  const valueBytes = encodeVarint(value)
  return concatUint8Arrays([tagBytes, valueBytes])
}

/**
 * 编码 varint
 */
function encodeVarint(value: number): Uint8Array {
  const bytes: number[] = []
  while (value > 127) {
    bytes.push((value & 0x7f) | 0x80)
    value = Math.floor(value / 128)
  }
  bytes.push(value & 0x7f)
  return new Uint8Array(bytes)
}

/**
 * 合并 Uint8Array 数组
 */
function concatUint8Arrays(arrays: Uint8Array[]): Uint8Array {
  const totalLength = arrays.reduce((sum, arr) => sum + arr.length, 0)
  const result = new Uint8Array(totalLength)
  let offset = 0
  for (const arr of arrays) {
    result.set(arr, offset)
    offset += arr.length
  }
  return result
}

/**
 * 手动解码 BatchRollbackEvent
 * message JetBrainsBatchRollbackEvent { string file_path=1; string tool_use_id=2; RollbackStatus status=3; optional string error=4; }
 */
function decodeBatchRollbackEvent(data: Uint8Array): BatchRollbackEvent {
  const result: BatchRollbackEvent = {
    filePath: '',
    toolUseId: '',
    status: RollbackStatus.STARTED
  }
  
  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNum = tag >> 3
    const wireType = tag & 0x7
    
    if (wireType === 2) {
      // length-delimited (string)
      let len = 0
      let shift = 0
      while (offset < data.length) {
        const b = data[offset++]
        len |= (b & 0x7f) << shift
        if (!(b & 0x80)) break
        shift += 7
      }
      const strBytes = data.slice(offset, offset + len)
      offset += len
      const str = new TextDecoder().decode(strBytes)
      
      if (fieldNum === 1) result.filePath = str
      else if (fieldNum === 2) result.toolUseId = str
      else if (fieldNum === 4) result.error = str
    } else if (wireType === 0) {
      // varint
      let value = 0
      let shift = 0
      while (offset < data.length) {
        const b = data[offset++]
        value |= (b & 0x7f) << shift
        if (!(b & 0x80)) break
        shift += 7
      }
      
      if (fieldNum === 3) result.status = value as RollbackStatus
    }
  }
  
  return result
}

/**
 * 解码 ActiveFileChangedNotify（用于 getActiveFile 响应）
 */
function decodeActiveFileResponse(data: Uint8Array): ActiveFileInfo | null {
  const proto = fromBinary(ActiveFileChangedNotifySchema, data)

  if (!proto.hasActiveFile) {
    return null
  }

  return {
    path: proto.path || '',
    relativePath: proto.relativePath || '',
    name: proto.name || '',
    line: proto.line || undefined,
    column: proto.column || undefined,
    hasSelection: proto.hasSelection,
    startLine: proto.startLine || undefined,
    startColumn: proto.startColumn || undefined,
    endLine: proto.endLine || undefined,
    endColumn: proto.endColumn || undefined,
    selectedContent: proto.selectedContent || undefined
  }
}

/**
 * 解码 GetIdeSettingsResponse
 */
function decodeSettingsResponse(data: Uint8Array): IdeSettings | null {
  // 默认思考级别列表
  const defaultThinkingLevels: ThinkingLevelConfig[] = [
    { id: 'off', name: 'Off', tokens: 0, isCustom: false },
    { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
    { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false }
  ]

  const proto = fromBinary(GetIdeSettingsResponseSchema, data)
  const s = proto.settings

    if (!s) {
      return {
        defaultModelId: '',
        defaultModelName: '',
        defaultBypassPermissions: false,
        claudeDefaultAutoCleanupContexts: true,
        codexDefaultAutoCleanupContexts: true,
        enableUserInteractionMcp: true,
        enableJetbrainsMcp: true,
        includePartialMessages: true,
      codexDefaultReasoningEffort: 'medium',
      codexDefaultReasoningSummary: 'auto',
      codexDefaultSandboxMode: 'workspace-write',
      defaultThinkingLevel: 'ULTRA',
      defaultThinkingTokens: 8096,
      defaultThinkingLevelId: 'ultra',
      thinkingLevels: defaultThinkingLevels,
      permissionMode: 'default'
    }
  }

  // 辅助函数：映射 OptionConfig
  const mapOptionConfig = (opt: any): OptionConfig => ({
    id: opt.id || '',
    label: opt.label || '',
    description: opt.description || '',
    isDefault: opt.isDefault ?? false
  })

  return {
    defaultModelId: s.defaultModelId || '',
    defaultModelName: s.defaultModelName || '',
    defaultBypassPermissions: s.defaultBypassPermissions,
    claudeDefaultAutoCleanupContexts: s.claudeDefaultAutoCleanupContexts,
    codexDefaultAutoCleanupContexts: s.codexDefaultAutoCleanupContexts,
    enableUserInteractionMcp: s.enableUserInteractionMcp,
    enableJetbrainsMcp: s.enableJetbrainsMcp,
    includePartialMessages: s.includePartialMessages,
    codexDefaultModelId: s.codexDefaultModelId || undefined,
    codexDefaultReasoningEffort: s.codexDefaultReasoningEffort || undefined,
    codexDefaultReasoningSummary: s.codexDefaultReasoningSummary || undefined,
    codexDefaultSandboxMode: s.codexDefaultSandboxMode || undefined,
    defaultThinkingLevel: s.defaultThinkingLevel || 'ULTRA',
    defaultThinkingTokens: s.defaultThinkingTokens || 8096,
    defaultThinkingLevelId: s.defaultThinkingLevelId || 'ultra',
    thinkingLevels: s.thinkingLevels.length > 0
      ? s.thinkingLevels.map(level => ({
          id: level.id,
          name: level.name,
          tokens: level.tokens,
          isCustom: level.isCustom
        }))
      : defaultThinkingLevels,
    permissionMode: s.permissionMode || 'default',
    // 配置选项列表
    codexReasoningEffortOptions: s.codexReasoningEffortOptions?.map(mapOptionConfig) || [],
    codexReasoningSummaryOptions: s.codexReasoningSummaryOptions?.map(mapOptionConfig) || [],
    codexSandboxModeOptions: s.codexSandboxModeOptions?.map(mapOptionConfig) || [],
    permissionModeOptions: s.permissionModeOptions?.map(mapOptionConfig) || []
  }
}

/**
 * 编码 JetBrainsSessionState
 */
function encodeSessionState(state: SessionState): Uint8Array {
  const proto = create(JetBrainsSessionStateSchema, {
    sessions: state.sessions.map(session => create(JetBrainsSessionSummarySchema, {
      id: session.id,
      title: session.title,
      sessionId: session.sessionId || undefined,
      isGenerating: session.isGenerating,
      isConnected: session.isConnected,
      isConnecting: session.isConnecting
    })),
    activeSessionId: state.activeSessionId || undefined
  })
  return toBinary(JetBrainsSessionStateSchema, proto)
}

// ========== 类型定义 ==========

export interface IdeTheme {
  background: string
  foreground: string
  borderColor: string
  panelBackground: string
  textFieldBackground: string
  selectionBackground: string
  selectionForeground: string
  linkColor: string
  errorColor: string
  warningColor: string
  successColor: string
  separatorColor: string
  hoverBackground: string
  accentColor: string
  infoBackground: string
  codeBackground: string
  secondaryForeground: string
  fontFamily: string
  fontSize: number
  editorFontFamily: string
  editorFontSize: number
}

export interface SessionCommand {
  type: 'switch' | 'create' | 'close' | 'rename' | 'toggleHistory' | 'setLocale' | 'delete' | 'reset'
  sessionId?: string
  newName?: string
  locale?: string
}

/**
 * 映射 protoCodec 的 SessionCommandParams.type 到 SessionCommand.type
 */
function mapSessionCommandType(type: string): SessionCommand['type'] {
  switch (type) {
    case 'switch': return 'switch'
    case 'create': return 'create'
    case 'close': return 'close'
    case 'rename': return 'rename'
    case 'toggleHistory': return 'toggleHistory'
    case 'setLocale': return 'setLocale'
    case 'delete': return 'delete'
    case 'reset': return 'reset'
    default:
      console.warn(`[JetBrainsRSocket] Unknown session command type: ${type}`)
      return type as SessionCommand['type'] // 保持原值，避免错误转换
  }
}

export interface SessionSummary {
  id: string
  title: string
  sessionId?: string | null
  isGenerating: boolean
  isConnected: boolean
  isConnecting: boolean
}

export interface SessionState {
  sessions: SessionSummary[]
  activeSessionId?: string | null
}

export type ThemeChangeHandler = (theme: IdeTheme) => void
export type SessionCommandHandler = (command: SessionCommand) => void
export type SettingsChangeHandler = (settings: IdeSettings) => void
export type ActiveFileChangeHandler = (activeFile: ActiveFileInfo | null) => void

// 当前活跃文件信息
export interface ActiveFileInfo {
  path: string           // 文件绝对路径
  relativePath: string   // 相对于项目根目录的路径
  name: string           // 文件名
  line?: number          // 当前光标所在行（1-based）
  column?: number        // 当前光标所在列（1-based）
  // 选区信息
  hasSelection: boolean
  startLine?: number     // 选区起始行（1-based）
  startColumn?: number   // 选区起始列（1-based）
  endLine?: number       // 选区结束行（1-based）
  endColumn?: number     // 选区结束列（1-based）
  selectedContent?: string // 选中的文本内容（可选）
  // 文件类型相关字段
  fileType?: string      // 文件类型: "text", "diff", "image", "binary"
  // Diff 视图专用字段
  diffOldContent?: string  // Diff 旧内容（左侧）
  diffNewContent?: string  // Diff 新内容（右侧）
  diffTitle?: string       // Diff 标题
}

// IDE 设置接口（所有属性可选，以支持扩展和部分更新）
export interface IdeSettings {
  defaultModelId?: string
  defaultModelName?: string
  defaultBypassPermissions?: boolean
  claudeDefaultAutoCleanupContexts?: boolean
  codexDefaultAutoCleanupContexts?: boolean
  enableUserInteractionMcp?: boolean
  enableJetbrainsMcp?: boolean
  includePartialMessages?: boolean
  codexDefaultModelId?: string
  codexDefaultReasoningEffort?: string
  codexDefaultReasoningSummary?: string
  codexDefaultSandboxMode?: string
  // 思考配置
  defaultThinkingLevelId?: string  // 默认思考级别 ID（如 "off", "think", "ultra", "custom_xxx"）
  defaultThinkingTokens?: number   // 默认思考 token 数量
  thinkingLevels?: ThinkingLevelConfig[]  // 所有可用的思考级别
  // 旧字段，保留向后兼容
  defaultThinkingLevel?: string  // 思考等级枚举名称（如 "HIGH", "MEDIUM", "OFF"）
  // 权限模式
  permissionMode?: string  // 权限模式（default, acceptEdits, plan, bypassPermissions）

  // 配置选项列表（由后端动态返回）
  codexReasoningEffortOptions?: OptionConfig[]  // Codex 推理努力级别选项
  codexReasoningSummaryOptions?: OptionConfig[] // Codex 推理总结模式选项
  codexSandboxModeOptions?: OptionConfig[]      // Codex 沙盒模式选项
  permissionModeOptions?: OptionConfig[]        // 权限模式选项
}

// 思考级别配置
export interface ThinkingLevelConfig {
  id: string        // 唯一标识：off, think, ultra, custom_xxx
  name: string      // 显示名称
  tokens: number    // token 数量
  isCustom: boolean // 是否为自定义级别
}

// 通用选项配置（用于下拉列表）
export interface OptionConfig {
  id: string           // 唯一标识
  label: string        // 显示名称
  description?: string // 描述（可选）
  isDefault?: boolean  // 是否为默认值
}

// ========== RSocket 服务 ==========

class JetBrainsRSocketService {
  private client: RSocketClient | null = null
  private themeChangeHandlers: ThemeChangeHandler[] = []
  private sessionCommandHandlers: SessionCommandHandler[] = []
  private settingsChangeHandlers: SettingsChangeHandler[] = []
  private activeFileChangeHandlers: ActiveFileChangeHandler[] = []
  private connected = false

  /**
   * 连接到 JetBrains RSocket 端点（带自动重试）
   *
   * 无限重试，直到连接成功。使用指数退避策略，最大延迟 10 秒。
   * 适用于 IDEA 插件场景，后端一定会启动，只是时间问题。
   *
   * @param initialDelayMs 初始重试延迟（毫秒），默认 500ms
   * @param maxDelayMs 最大重试延迟（毫秒），默认 10000ms
   */
  async connect(initialDelayMs = 500, maxDelayMs = 10000): Promise<boolean> {
    if (this.connected) return true

    let attempt = 0

    while (true) {
      attempt++
      try {
        console.log(`[JetBrainsRSocket] 连接尝试 #${attempt}...`)
        const success = await this.tryConnect()
        if (success) {
          if (attempt > 1) {
            console.log(`[JetBrainsRSocket] 第 ${attempt} 次尝试连接成功`)
          }
          return true
        }
      } catch (error) {
        const errorMsg = error instanceof Error ? error.message : String(error)
        console.warn(`[JetBrainsRSocket] 连接尝试 #${attempt} 失败:`, errorMsg)
      }

      // 指数退避，但不超过最大延迟
      const delayMs = Math.min(initialDelayMs * Math.pow(2, attempt - 1), maxDelayMs)
      console.log(`[JetBrainsRSocket] ${delayMs}ms 后重试...`)
      await new Promise(resolve => setTimeout(resolve, delayMs))
    }
  }

  /**
   * 尝试单次连接
   */
  private async tryConnect(): Promise<boolean> {
    try {
      const httpUrl = resolveServerHttpUrl()
      const wsUrl = httpUrl.replace(/^http/, 'ws') + '/jetbrains-rsocket'

      this.client = new RSocketClient({ url: wsUrl })

      // 注册 ServerCall handler（统一 Protobuf 格式）
      // 后端通过 client.call 路由发送，RSocketClient 解码后按 method 分发
      this.client.registerHandler('onThemeChanged', async (params: any) => {
        console.log('[JetBrainsRSocket] 收到主题变化推送 (Protobuf)')
        // params 已经是 ThemeChangedParams 类型
        const theme: IdeTheme = {
          background: params.background,
          foreground: params.foreground,
          borderColor: params.borderColor,
          panelBackground: params.panelBackground,
          textFieldBackground: params.textFieldBackground,
          selectionBackground: params.selectionBackground,
          selectionForeground: params.selectionForeground,
          linkColor: params.linkColor,
          errorColor: params.errorColor,
          warningColor: params.warningColor,
          successColor: params.successColor,
          separatorColor: params.separatorColor,
          hoverBackground: params.hoverBackground,
          accentColor: params.accentColor,
          infoBackground: params.infoBackground,
          codeBackground: params.codeBackground,
          secondaryForeground: params.secondaryForeground,
          fontFamily: params.fontFamily,
          fontSize: params.fontSize,
          editorFontFamily: params.editorFontFamily,
          editorFontSize: params.editorFontSize
        }
        this.themeChangeHandlers.forEach(h => h(theme))
        return {} // 返回空响应
      })

      this.client.registerHandler('onSessionCommand', async (params: any) => {
        console.log('[JetBrainsRSocket] 收到会话命令推送 (Protobuf)')
        // params 已经是 SessionCommandParams 类型
        const command: SessionCommand = {
          type: mapSessionCommandType(params.type),
          sessionId: params.sessionId,
          newName: params.newName,
          locale: params.locale
        }
        console.log('[JetBrainsRSocket] 会话命令:', command)
        this.sessionCommandHandlers.forEach(h => h(command))
        return {} // 返回空响应
      })

      this.client.registerHandler('onSettingsChanged', async (params: any) => {
        console.log('[JetBrainsRSocket] 收到设置变更推送 (Protobuf)')
        // params 已由 protoCodec 解码，包含 { settings: IdeSettings }
        const settingsData = params.settings || params

        const settings: IdeSettings = {
          defaultModelId: settingsData.defaultModelId || '',
          defaultModelName: settingsData.defaultModelName || '',
          defaultBypassPermissions: settingsData.defaultBypassPermissions ?? false,
          claudeDefaultAutoCleanupContexts: settingsData.claudeDefaultAutoCleanupContexts ?? true,
          codexDefaultAutoCleanupContexts: settingsData.codexDefaultAutoCleanupContexts ?? true,
          enableUserInteractionMcp: settingsData.enableUserInteractionMcp ?? true,
          enableJetbrainsMcp: settingsData.enableJetbrainsMcp ?? true,
          includePartialMessages: settingsData.includePartialMessages ?? true,
          codexDefaultModelId: settingsData.codexDefaultModelId || undefined,
          codexDefaultReasoningEffort: settingsData.codexDefaultReasoningEffort || undefined,
          codexDefaultReasoningSummary: settingsData.codexDefaultReasoningSummary || undefined,
          codexDefaultSandboxMode: settingsData.codexDefaultSandboxMode || undefined,
          defaultThinkingLevel: settingsData.defaultThinkingLevel || 'ULTRA',
          defaultThinkingTokens: settingsData.defaultThinkingTokens,
          defaultThinkingLevelId: settingsData.defaultThinkingLevelId || 'ultra',
          thinkingLevels: settingsData.thinkingLevels || [
            { id: 'off', name: 'Off', tokens: 0, isCustom: false },
            { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
            { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false }
          ],
          permissionMode: settingsData.permissionMode || 'default',
          // 配置选项列表
          codexReasoningEffortOptions: settingsData.codexReasoningEffortOptions || [],
          codexReasoningSummaryOptions: settingsData.codexReasoningSummaryOptions || [],
          codexSandboxModeOptions: settingsData.codexSandboxModeOptions || [],
          permissionModeOptions: settingsData.permissionModeOptions || []
        }
        console.log('[JetBrainsRSocket] 设置变更:', settings)
        this.settingsChangeHandlers.forEach(h => h(settings))
        return {} // 返回空响应
      })

      this.client.registerHandler('onActiveFileChanged', async (params: any) => {
        console.log('[JetBrainsRSocket] 收到活跃文件变更推送 (Protobuf)')
        // params 是 ActiveFileChangedNotify 类型
        let activeFile: ActiveFileInfo | null = null
        if (params.hasActiveFile) {
          activeFile = {
            path: params.path || '',
            relativePath: params.relativePath || '',
            name: params.name || '',
            line: params.line || undefined,
            column: params.column || undefined,
            hasSelection: params.hasSelection || false,
            startLine: params.startLine || undefined,
            startColumn: params.startColumn || undefined,
            endLine: params.endLine || undefined,
            endColumn: params.endColumn || undefined,
            selectedContent: params.selectedContent || undefined
          }
          console.log('[JetBrainsRSocket] 活跃文件:', activeFile.relativePath,
            activeFile.hasSelection ? `(selection: ${activeFile.startLine}:${activeFile.startColumn} - ${activeFile.endLine}:${activeFile.endColumn}, content: ${activeFile.selectedContent?.substring(0, 50)}...)` : '')
        } else {
          console.log('[JetBrainsRSocket] 无活跃文件')
        }
        this.activeFileChangeHandlers.forEach(h => h(activeFile))
        return {} // 返回空响应
      })

      await this.client.connect()
      this.connected = true
      console.log('[JetBrainsRSocket] Connected')
      return true
    } catch (error) {
      // 清理失败的 client
      if (this.client) {
        try {
          this.client.disconnect()
        } catch (_) { /* ignore */ }
        this.client = null
      }
      throw error
    }
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    if (this.client) {
      this.client.disconnect()
      this.client = null
      this.connected = false
      console.log('[JetBrainsRSocket] Disconnected')
    }
  }

  /**
   * 检查是否已连接
   */
  isConnected(): boolean {
    return this.connected
  }

  // ========== 前端 → 后端 调用 ==========

  /**
   * 打开文件
   */
  async openFile(request: OpenFileRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeOpenFileRequest(request)
      const response = await this.client.requestResponse('jetbrains.openFile', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Opened file:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to open file:', error)
      return false
    }
  }

  /**
   * 显示 Diff
   */
  async showDiff(request: ShowDiffRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowDiffRequest(request)
      const response = await this.client.requestResponse('jetbrains.showDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Showing diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to show diff:', error)
      return false
    }
  }

  /**
   * 显示多编辑 Diff
   */
  async showMultiEditDiff(request: ShowMultiEditDiffRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowMultiEditDiffRequest(request)
      const response = await this.client.requestResponse('jetbrains.showMultiEditDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Showing multi-edit diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to show multi-edit diff:', error)
      return false
    }
  }

  /**
   * 显示编辑预览 Diff（权限请求时使用）
   */
  async showEditPreviewDiff(request: ShowEditPreviewRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowEditPreviewRequest(request)
      const response = await this.client.requestResponse('jetbrains.showEditPreviewDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Showing edit preview diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to show edit preview diff:', error)
      return false
    }
  }

  /**
   * 显示 Markdown 内容（计划预览）
   */
  async showMarkdown(request: ShowMarkdownRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowMarkdownRequest(request)
      const response = await this.client.requestResponse('jetbrains.showMarkdown', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Showing markdown:', request.title || 'Plan Preview')
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to show markdown:', error)
      return false
    }
  }

  /**
   * 显示完整文件 Diff（修改前后对比）
   */
  async showEditFullDiff(request: ShowEditFullDiffRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowEditFullDiffRequest(request)
      const response = await this.client.requestResponse('jetbrains.showEditFullDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Showing edit full diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to show edit full diff:', error)
      return false
    }
  }

  /**
   * 获取主题
   */
  async getTheme(): Promise<IdeTheme | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('jetbrains.getTheme', new Uint8Array())
      return decodeThemeResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get theme:', error)
      return null
    }
  }

  /**
   * 获取 IDE 设置
   */
  async getSettings(): Promise<IdeSettings | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('jetbrains.getSettings', new Uint8Array())
      return decodeSettingsResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get settings:', error)
      return null
    }
  }

  /**
   * 获取语言设置
   */
  async getLocale(): Promise<string> {
    if (!this.client) return 'en-US'

    try {
      const response = await this.client.requestResponse('jetbrains.getLocale', new Uint8Array())
      return decodeLocaleResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get locale:', error)
      return 'en-US'
    }
  }

  /**
   * 设置语言
   */
  async setLocale(locale: string): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeSetLocaleRequest(locale)
      const response = await this.client.requestResponse('jetbrains.setLocale', data)
      const result = decodeOperationResponse(response)
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to set locale:', error)
      return false
    }
  }

  /**
   * 获取项目路径
   */
  async getProjectPath(): Promise<string | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('jetbrains.getProjectPath', new Uint8Array())
      return decodeProjectPathResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get project path:', error)
      return null
    }
  }

  /**
   * 获取当前活跃文件
   */
  async getActiveFile(): Promise<ActiveFileInfo | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('jetbrains.getActiveFile', new Uint8Array())
      return decodeActiveFileResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get active file:', error)
      return null
    }
  }

  /**
   * 获取文件修改前的原始内容
   * 基于 LocalHistory Label 机制
   */
  async getOriginalContent(toolUseId: string): Promise<string | null> {
    if (!this.client) return null

    try {
      // 直接发送 toolUseId 字符串作为请求数据
      const data = new TextEncoder().encode(toolUseId)
      const response = await this.client.requestResponse('jetbrains.getOriginalContent', data)
      const result = decodeGetOriginalContentResponse(response)
      if (result.success && result.found) {
        console.log('[JetBrainsRSocket] Got original content for:', toolUseId)
        return result.content || null
      }
      if (!result.found) {
        console.log('[JetBrainsRSocket] Original content not found for:', toolUseId)
      }
      if (result.error) {
        console.warn('[JetBrainsRSocket] Error getting original content:', result.error)
      }
      return null
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get original content:', error)
      return null
    }
  }

  /**
   * 获取文件历史内容（基于时间戳查询 LocalHistory）
   * 用于历史会话加载时的 Diff 显示
   *
   * @param filePath 文件绝对路径
   * @param beforeTimestamp 时间戳（毫秒），获取此时间之前的版本
   * @returns 历史文件内容，如果不存在返回 null
   */
  async getFileHistoryContent(filePath: string, beforeTimestamp: number): Promise<string | null> {
    if (!this.client) return null

    try {
      const data = encodeGetFileHistoryContentRequest(filePath, beforeTimestamp)
      const response = await this.client.requestResponse('jetbrains.getFileHistoryContent', data)
      const result = decodeGetFileHistoryContentResponse(response)
      if (result.success && result.found) {
        console.log('[JetBrainsRSocket] Got file history content for:', filePath, 'before:', beforeTimestamp)
        return result.content || null
      }
      if (!result.found) {
        console.log('[JetBrainsRSocket] File history content not found for:', filePath)
      }
      if (result.error) {
        console.warn('[JetBrainsRSocket] Error getting file history content:', result.error)
      }
      return null
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get file history content:', error)
      return null
    }
  }

  /**
   * 回滚文件到指定时间戳之前的版本
   * 使用 LocalHistory API 恢复文件内容
   *
   * @param filePath 文件绝对路径
   * @param beforeTimestamp 时间戳（毫秒），回滚到此时间之前的版本
   * @returns 回滚结果，包含是否成功和错误信息
   */
  async rollbackFile(filePath: string, beforeTimestamp: number): Promise<{ success: boolean; error?: string }> {
    if (!this.client) {
      return { success: false, error: 'RSocket client not connected' }
    }

    try {
      const data = encodeRollbackFileRequest(filePath, beforeTimestamp)
      const response = await this.client.requestResponse('jetbrains.rollbackFile', data)
      const result = decodeRollbackFileResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Rolled back file:', filePath, 'to before:', beforeTimestamp)
      } else {
        console.warn('[JetBrainsRSocket] Rollback failed:', result.error)
      }
      return result
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to rollback file:', error)
      return { success: false, error: String(error) }
    }
  }

  /**
   * 批量回滚文件（流式返回结果）
   * 用于"回滚所有"功能，实时返回每个文件的回滚状态
   *
   * @param items 回滚项列表
   * @param onEvent 事件回调（每个文件的状态变化）
   * @returns 取消函数
   */
  batchRollback(
    items: BatchRollbackItem[],
    onEvent: (event: BatchRollbackEvent) => void,
    onComplete?: () => void,
    onError?: (error: Error) => void
  ): () => void {
    if (!this.client) {
      onError?.(new Error('RSocket client not connected'))
      return () => {}
    }

    console.log('[JetBrainsRSocket] Starting batch rollback:', items.length, 'items')
    const data = encodeBatchRollbackRequest(items)

    return this.client.requestStream(
      'jetbrains.batchRollback',
      data,
      {
        onNext: (responseData: Uint8Array) => {
          try {
            const event = decodeBatchRollbackEvent(responseData)
            console.log('[JetBrainsRSocket] Rollback event:', event.toolUseId, RollbackStatus[event.status])
            onEvent(event)
          } catch (e) {
            console.error('[JetBrainsRSocket] Failed to decode rollback event:', e)
          }
        },
        onComplete: () => {
          console.log('[JetBrainsRSocket] Batch rollback completed')
          onComplete?.()
        },
        onError: (error: Error) => {
          console.error('[JetBrainsRSocket] Batch rollback error:', error)
          onError?.(error)
        }
      }
    )
  }

  /**
   * 上报会话状态到后端
   */
  async reportSessionState(state: SessionState): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeSessionState(state)
      const response = await this.client.requestResponse('jetbrains.reportSessionState', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Reported session state:', state.sessions.length, 'sessions')
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to report session state:', error)
      return false
    }
  }

  // ========== 后端 → 前端 事件监听 ==========

  /**
   * 添加主题变化监听器
   */
  onThemeChange(handler: ThemeChangeHandler): () => void {
    this.themeChangeHandlers.push(handler)
    return () => {
      const index = this.themeChangeHandlers.indexOf(handler)
      if (index >= 0) this.themeChangeHandlers.splice(index, 1)
    }
  }

  /**
   * 添加会话命令监听器
   */
  onSessionCommand(handler: SessionCommandHandler): () => void {
    this.sessionCommandHandlers.push(handler)
    return () => {
      const index = this.sessionCommandHandlers.indexOf(handler)
      if (index >= 0) this.sessionCommandHandlers.splice(index, 1)
    }
  }

  /**
   * 添加设置变更监听器
   */
  onSettingsChange(handler: SettingsChangeHandler): () => void {
    this.settingsChangeHandlers.push(handler)
    return () => {
      const index = this.settingsChangeHandlers.indexOf(handler)
      if (index >= 0) this.settingsChangeHandlers.splice(index, 1)
    }
  }

  /**
   * 添加活跃文件变更监听器
   */
  onActiveFileChange(handler: ActiveFileChangeHandler): () => void {
    this.activeFileChangeHandlers.push(handler)
    return () => {
      const index = this.activeFileChangeHandlers.indexOf(handler)
      if (index >= 0) this.activeFileChangeHandlers.splice(index, 1)
    }
  }
}

// ========== 单例导出 ==========

export const jetbrainsRSocket = new JetBrainsRSocketService()
