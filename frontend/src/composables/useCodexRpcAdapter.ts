/**
 * Codex RPC Adapter Composable
 * 
 * 将 Codex BackendEvent 转换为 Claude RPC 流式事件格式
 * 使得 Codex 后端可以复用 Claude 的消息处理流程
 */

import type { SessionToolsInstance } from './useSessionTools'
import type { SessionMessagesInstance } from './useSessionMessages'
import { loggers } from '@/utils/logger'

const log = loggers.session

export interface CodexRpcState {
  messageStarted: boolean           // 是否已发送 message_start
  contentBlockIndex: number         // 当前 content_block 索引
  textBlockIndex: number            // 文本块索引，-1 表示未创建
  thinkingBlockIndex: number        // 思考块索引，-1 表示未创建
  toolBlockIndexMap: Map<string, number>  // toolId -> blockIndex
  currentMessageId: string          // 当前消息 ID
  
  // 内容累积（用于在 turn_completed 时构建完整消息）
  accumulatedText: string           // 累积的文本内容
  accumulatedThinking: string       // 累积的思考内容
  toolUseBlocks: Map<string, {      // 工具调用块
    id: string
    toolName: string
    toolType: string
    input: Record<string, unknown>
    result?: unknown
  }>
}

export interface CodexRpcAdapterOptions {
  tabId: string
  tools: SessionToolsInstance
  messagesHandler: SessionMessagesInstance
}

export function useCodexRpcAdapter(options: CodexRpcAdapterOptions) {
  const { tabId, tools, messagesHandler } = options

  // Codex RPC 转换状态
  const state: CodexRpcState = {
    messageStarted: false,
    contentBlockIndex: 0,
    textBlockIndex: -1,
    thinkingBlockIndex: -1,
    toolBlockIndexMap: new Map(),
    currentMessageId: '',
    accumulatedText: '',
    accumulatedThinking: '',
    toolUseBlocks: new Map()
  }

  /**
   * 重置 Codex RPC 转换状态（在 turn 结束时调用）
   */
  function resetState(): void {
    state.messageStarted = false
    state.contentBlockIndex = 0
    state.textBlockIndex = -1
    state.thinkingBlockIndex = -1
    state.toolBlockIndexMap.clear()
    state.currentMessageId = ''
    state.accumulatedText = ''
    state.accumulatedThinking = ''
    state.toolUseBlocks.clear()
  }

  /**
   * 发送 Claude RPC 流式事件
   */
  function emitRpcEvent(eventPayload: Record<string, unknown>): void {
    messagesHandler.handleStreamEvent({
      type: 'stream_event',
      uuid: state.currentMessageId,
      event: eventPayload
    } as any)
  }

  /**
   * 确保已发送 message_start（首次内容时触发）
   */
  function ensureMessageStarted(): void {
    if (!state.messageStarted) {
      state.currentMessageId = `codex-msg-${Date.now()}`
      emitRpcEvent({
        type: 'message_start',
        message: {
          id: state.currentMessageId,
          role: 'assistant',
          content: []
        }
      })
      state.messageStarted = true
      log.debug(`[Tab ${tabId}] Codex: 发送 message_start, id=${state.currentMessageId}`)
    }
  }

  /**
   * 处理文本类 delta 事件（text_delta / thinking_delta 共用）
   */
  function handleTextDelta(
    deltaText: string,
    blockType: 'text' | 'thinking'
  ): void {
    ensureMessageStarted()
    
    const isText = blockType === 'text'
    const blockIndexKey = isText ? 'textBlockIndex' : 'thinkingBlockIndex'
    const accumulatorKey = isText ? 'accumulatedText' : 'accumulatedThinking'
    
    // 首次收到时，发送 content_block_start
    if (state[blockIndexKey] === -1) {
      state[blockIndexKey] = state.contentBlockIndex++
      const contentBlock = isText 
        ? { type: 'text', text: '' }
        : { type: 'thinking', thinking: '' }
      emitRpcEvent({
        type: 'content_block_start',
        index: state[blockIndexKey],
        content_block: contentBlock
      })
      log.debug(`[Tab ${tabId}] Codex: 发送 content_block_start (${blockType}), index=${state[blockIndexKey]}`)
    }
    
    // 发送 content_block_delta
    const delta = isText 
      ? { type: 'text_delta', text: deltaText }
      : { type: 'thinking_delta', thinking: deltaText }
    emitRpcEvent({
      type: 'content_block_delta',
      index: state[blockIndexKey],
      delta
    })
    
    // 累积内容
    state[accumulatorKey] += deltaText
  }

  /**
   * 处理工具开始事件
   */
  function handleToolStarted(event: {
    itemId?: string
    toolName: string
    toolType: string
    parameters?: Record<string, unknown>
  }): void {
    log.info(`[Tab ${tabId}] 工具开始: ${event.toolName}, itemId=${event.itemId}`)
    ensureMessageStarted()
    
    if (event.itemId) {
      const toolBlockIndex = state.contentBlockIndex++
      state.toolBlockIndexMap.set(event.itemId, toolBlockIndex)
      
      emitRpcEvent({
        type: 'content_block_start',
        index: toolBlockIndex,
        content_block: {
          type: 'tool_use',
          id: event.itemId,
          toolName: event.toolName,
          name: event.toolName,
          toolType: event.toolType,
          input: event.parameters || {}
        }
      })
      log.debug(`[Tab ${tabId}] Codex: 发送 content_block_start (tool_use), index=${toolBlockIndex}, id=${event.itemId}`)
      
      // 记录工具调用（用于构建完整消息）
      state.toolUseBlocks.set(event.itemId, {
        id: event.itemId,
        toolName: event.toolName,
        toolType: event.toolType,
        input: event.parameters || {}
      })
    }
  }

  /**
   * 处理工具完成事件
   */
  function handleToolCompleted(event: {
    itemId?: string
    success: boolean
    result?: unknown
  }): void {
    log.info(`[Tab ${tabId}] 工具完成: ${event.itemId}, success=${event.success}`)
    
    if (event.itemId) {
      const toolBlockIndex = state.toolBlockIndexMap.get(event.itemId)
      if (toolBlockIndex !== undefined) {
        emitRpcEvent({ type: 'content_block_stop', index: toolBlockIndex })
        log.debug(`[Tab ${tabId}] Codex: 发送 content_block_stop, index=${toolBlockIndex}`)
      }
      
      // 更新工具结果（用于 UI 显示）
      if (event.result) {
        const updateSuccess = tools.updateToolResult(event.itemId, event.result as any)
        log.info(`[Tab ${tabId}] updateToolResult 结果: ${updateSuccess}`)
      }
      
      // 更新累积的工具块结果
      const toolBlock = state.toolUseBlocks.get(event.itemId)
      if (toolBlock && event.result) {
        toolBlock.result = event.result
      }
    }
  }

  /**
   * 构建完整的 assistant 消息内容块
   */
  function buildCompleteContentBlocks(): any[] {
    const contentBlocks: any[] = []
    
    // 1. 添加思考块（如果有）
    if (state.accumulatedThinking) {
      contentBlocks.push({
        type: 'thinking',
        thinking: state.accumulatedThinking,
        signature: 'codex-thinking'  // Codex 不提供 signature，使用占位符
      })
    }
    
    // 2. 添加文本块（如果有）
    if (state.accumulatedText) {
      contentBlocks.push({
        type: 'text',
        text: state.accumulatedText
      })
    }
    
    // 3. 添加工具调用块
    for (const [_id, tool] of state.toolUseBlocks) {
      contentBlocks.push({
        type: 'tool_use',
        id: tool.id,
        name: tool.toolName,
        toolName: tool.toolName,
        toolType: tool.toolType,
        input: tool.input
      })
    }
    
    return contentBlocks
  }

  /**
   * 处理轮次完成事件
   */
  function handleTurnCompleted(event: {
    turnId?: string
    status: string
  }, processNextQueuedMessage: () => void): void {
    log.info(`[Tab ${tabId}] 轮次完成: ${event.turnId}, status=${event.status}`)
    
    if (state.messageStarted) {
      // 发送 message_stop
      emitRpcEvent({ type: 'message_stop' })
      log.debug(`[Tab ${tabId}] Codex: 发送 message_stop`)
      
      // 构建并发送完整 assistant 消息（与 Claude 行为一致）
      const contentBlocks = buildCompleteContentBlocks()
      if (contentBlocks.length > 0) {
        messagesHandler.handleNormalMessage({
          type: 'assistant',
          id: state.currentMessageId,
          role: 'assistant',
          timestamp: Date.now(),
          content: contentBlocks,
          isStreaming: false
        } as any)
        log.debug(`[Tab ${tabId}] Codex: 发送完整 assistant 消息, contentBlocks=${contentBlocks.length}`)
      }
    }
    
    resetState()
    messagesHandler.stopGenerating()
    processNextQueuedMessage()
  }

  return {
    // State (只读访问)
    get state() { return state },
    get messageStarted() { return state.messageStarted },
    get currentMessageId() { return state.currentMessageId },
    
    // Methods
    resetState,
    emitRpcEvent,
    ensureMessageStarted,
    handleTextDelta,
    handleToolStarted,
    handleToolCompleted,
    buildCompleteContentBlocks,
    handleTurnCompleted
  }
}

export type CodexRpcAdapterInstance = ReturnType<typeof useCodexRpcAdapter>
