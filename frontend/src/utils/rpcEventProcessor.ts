/**
 * RPC 流式事件处理器
 *
 * 统一处理 RpcStreamEvent 类型，直接操作 Message.content 数组
 * 不再进行 StreamEvent 转换，简化架构
 */

import type {
  RpcAppStreamEvent,
  RpcMessageStart,
  RpcTextDelta,
  RpcThinkingDelta,
  RpcToolStart,
  RpcToolProgress,
  RpcToolComplete,
  RpcMessageComplete,
  RpcErrorEvent,
  RpcAssistantMessage,
  RpcUserMessage
} from '@/types/rpc'
import type { Message, ContentBlock, ToolUseContent, ThinkingContent, TextContent } from '@/types/message'
import { isToolUseBlock, isTextBlock } from '@/utils/contentBlockUtils'
import { loggers } from '@/utils/logger'

const log = loggers.stream

/**
 * RPC 事件处理上下文
 */
export interface RpcEventContext {
  messages: Message[]  // 会话消息列表
  toolInputJsonAccumulator: Map<string, string>  // JSON 累积器 (toolId -> partial_json)
  // 注册工具调用回调（用于 pendingToolCalls 跟踪）
  registerToolCall?: (block: ToolUseContent) => void
}

/**
 * RPC 事件处理结果
 */
export interface RpcEventProcessResult {
  shouldUpdateMessages: boolean  // 是否需要更新 messages
  shouldSetGenerating: boolean | null  // 是否需要设置生成状态 (null = 不改变)
  messageUpdated: boolean  // 消息是否被更新
  newMessage?: Message  // 新创建的消息（用于添加到 displayItems）
}

function createNoOpResult(): RpcEventProcessResult {
  return {
    shouldUpdateMessages: false,
    shouldSetGenerating: null,
    messageUpdated: false
  }
}

/**
 * 生成唯一的助手消息 ID
 */
function generateAssistantMessageId(): string {
  return `assistant-${Date.now()}-${crypto.randomUUID().substring(0, 8)}`
}

/**
 * 确保消息 ID 唯一
 */
function ensureUniqueMessageId(
  desiredId: string | undefined,
  messages: Message[],
  excludeId?: string
): string {
  if (!desiredId) {
    return generateAssistantMessageId()
  }
  const hasConflict = messages.some(message => {
    if (excludeId && message.id === excludeId) {
      return false
    }
    return message.id === desiredId
  })
  if (!hasConflict) {
    return desiredId
  }
  return `${desiredId}-${crypto.randomUUID().substring(0, 8)}`
}

/**
 * 判断消息内容是否实际为空
 */
function isMessageContentEmpty(content: ContentBlock[]): boolean {
  if (content.length === 0) return true
  return content.every(block => {
    if (block.type === 'text') {
      const textBlock = block as TextContent
      return !textBlock.text || textBlock.text.trim() === ''
    }
    return false
  })
}

/**
 * 查找或创建最后一个 assistant 消息
 */
function findOrCreateLastAssistantMessage(messages: Message[]): Message {
  const lastMessage = messages
    .slice()
    .reverse()
    .find(m => m.role === 'assistant')

  if (lastMessage) {
    return lastMessage
  }

  // 创建新的 assistant 消息
  const newMessage: Message = {
    id: generateAssistantMessageId(),
    role: 'assistant',
    content: [],
    timestamp: Date.now()
  }
  messages.push(newMessage)
  return newMessage
}


/**
 * 判断消息是否是前端创建的占位符消息
 * 占位符消息的 ID 以 'assistant-' 开头（由 generateAssistantMessageId 生成）
 * 真实消息的 ID 以 'msg_' 开头（由后端生成）
 */
function isPlaceholderMessage(message: Message): boolean {
  return message.role === 'assistant' && message.id.startsWith('assistant-')
}

/**
 * 处理 message_start 事件
 *
 * 关键逻辑：
 * - 如果最后一个 assistant 消息是占位符（ID 以 assistant- 开头），复用它并更新 ID
 * - 如果最后一个 assistant 消息是真实消息（ID 以 msg_ 开头）且有内容，创建新消息
 * - 这样处理可以正确处理 thinking_delta 先于 message_start 到达的情况
 */
function processMessageStart(
  event: RpcMessageStart,
  context: RpcEventContext
): RpcEventProcessResult {
  const eventMessageId = event.messageId
  console.log(`📨 processMessageStart: messageId="${eventMessageId}", messagesCount=${context.messages.length}`)
  log.debug(`processMessageStart: id=${eventMessageId}`)

  // 查找最后一个 assistant 消息
  const lastMessage = context.messages
    .slice()
    .reverse()
    .find(m => m.role === 'assistant')

  // 情况1：有占位符消息（无论是否有内容），复用它并更新 ID
  // 这处理了 thinking_delta/text_delta 先于 message_start 到达的情况
  if (lastMessage && isPlaceholderMessage(lastMessage)) {
    const oldId = lastMessage.id
    const resolvedId = ensureUniqueMessageId(eventMessageId, context.messages, lastMessage.id)

    const messageIndex = context.messages.findIndex(m => m.id === oldId)
    if (messageIndex !== -1) {
      // 保留已有的 content（可能包含通过 delta 事件添加的 thinking/text）
      // 如果 event 也带有 content，需要合并
      const mergedContent = lastMessage.content || []
      if (event.content && event.content.length > 0) {
        // 将 event.content 中不存在的块添加到现有内容
        const existingTypes = new Set(mergedContent.map(b => b.type))
        for (const block of event.content as ContentBlock[]) {
          // 对于 thinking 和 text 块，如果已有则不重复添加
          if ((block.type === 'thinking' || block.type === 'text') && existingTypes.has(block.type)) {
            continue
          }
          mergedContent.push(block)
        }
      }

      context.messages[messageIndex] = {
        ...lastMessage,
        id: resolvedId,
        content: mergedContent
      }

      log.debug(`processMessageStart: 复用占位符消息 ${oldId} -> ${resolvedId}, 保留 ${mergedContent.length} 个内容块`)
    }

    return {
      shouldUpdateMessages: true,
      shouldSetGenerating: true,
      messageUpdated: true
    }
  }

  // 情况2：有空的真实消息（罕见情况），继续使用它
  if (lastMessage && isMessageContentEmpty(lastMessage.content)) {
    const resolvedId = ensureUniqueMessageId(eventMessageId, context.messages, lastMessage.id)
    if (lastMessage.id !== resolvedId) {
      const messageIndex = context.messages.findIndex(m => m.id === lastMessage.id)
      if (messageIndex !== -1) {
        context.messages[messageIndex] = { ...lastMessage, id: resolvedId }
      }
    }

    // 如果 event 包含初始 content，更新它
    if (event.content && event.content.length > 0) {
      const messageIndex = context.messages.findIndex(m => m.id === lastMessage.id)
      if (messageIndex !== -1) {
        context.messages[messageIndex] = {
          ...context.messages[messageIndex],
          content: event.content as ContentBlock[]
        }
      }
    }

    return {
      shouldUpdateMessages: true,
      shouldSetGenerating: true,
      messageUpdated: true
    }
  }

  // 情况3：没有消息或最后一条消息是有内容的真实消息，创建新消息
  const newMessage: Message = {
    id: ensureUniqueMessageId(eventMessageId, context.messages),
    role: 'assistant',
    content: (event.content || []) as ContentBlock[],
    timestamp: Date.now()
  }
  context.messages.push(newMessage)

  return {
    shouldUpdateMessages: true,
    shouldSetGenerating: true,
    messageUpdated: true,
    newMessage: newMessage
  }
}

/**
 * 处理 text_delta 事件
 */
function processTextDelta(
  event: RpcTextDelta,
  context: RpcEventContext
): RpcEventProcessResult {
  if (!event.text) {
    return createNoOpResult()
  }

  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)
  
  // 查找最后一个文本块
  let lastTextBlock: TextContent | undefined
  let lastTextBlockIndex = -1

  for (let i = lastAssistantMessage.content.length - 1; i >= 0; i--) {
    const block = lastAssistantMessage.content[i]
    if (isTextBlock(block)) {
      lastTextBlock = block
      lastTextBlockIndex = i
      break
    }
  }

  if (lastTextBlock) {
    // 追加到现有文本块（更新数组以触发响应式）
    const updatedBlock: TextContent = {
      type: 'text',
      text: lastTextBlock.text + event.text
    }
    const newContent = [...lastAssistantMessage.content]
    newContent[lastTextBlockIndex] = updatedBlock
    lastAssistantMessage.content = newContent
  } else {
    // 创建新的文本块
    const newBlock: TextContent = {
      type: 'text',
      text: event.text
    }
    lastAssistantMessage.content = [...lastAssistantMessage.content, newBlock]
  }

  return {
    shouldUpdateMessages: true,
    shouldSetGenerating: true,
    messageUpdated: true
  }
}

/**
 * 处理 thinking_delta 事件
 */
function processThinkingDelta(
  event: RpcThinkingDelta,
  context: RpcEventContext
): RpcEventProcessResult {
  if (!event.thinking) {
    return createNoOpResult()
  }

  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)
  
  // 查找最后一个 thinking 块
  let lastThinkingBlock: ThinkingContent | undefined
  let lastThinkingBlockIndex = -1

  for (let i = lastAssistantMessage.content.length - 1; i >= 0; i--) {
    const block = lastAssistantMessage.content[i]
    if (block.type === 'thinking') {
      lastThinkingBlock = block as ThinkingContent
      lastThinkingBlockIndex = i
      break
    }
  }

  if (lastThinkingBlock) {
    // 追加到现有 thinking 块（更新数组以触发响应式）
    const updatedBlock: ThinkingContent = {
      type: 'thinking',
      thinking: lastThinkingBlock.thinking + event.thinking,
      signature: lastThinkingBlock.signature
    }
    const newContent = [...lastAssistantMessage.content]
    newContent[lastThinkingBlockIndex] = updatedBlock
    lastAssistantMessage.content = newContent
  } else {
    // 创建新的 thinking 块
    const newBlock: ThinkingContent = {
      type: 'thinking',
      thinking: event.thinking
    }
    lastAssistantMessage.content = [...lastAssistantMessage.content, newBlock]
  }

  return {
    shouldUpdateMessages: true,
    shouldSetGenerating: true,
    messageUpdated: true
  }
}

/**
 * 处理 tool_start 事件
 */
function processToolStart(
  event: RpcToolStart,
  context: RpcEventContext
): RpcEventProcessResult {
  // 🔧 调试日志：打印收到的 event
  console.log('📨 [processToolStart] event:', {
    toolId: event.toolId,
    toolName: event.toolName,
    toolType: event.toolType,
    provider: event.provider
  })

  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)

  // 检查是否已存在该工具调用
  const existingBlock = lastAssistantMessage.content.find(
    (block) => isToolUseBlock(block) && block.id === event.toolId
  )

  if (existingBlock) {
    return {
      shouldUpdateMessages: false,
      shouldSetGenerating: true,
      messageUpdated: false
    }
  }

  // 创建新的工具调用块
  const toolUseBlock: ToolUseContent = {
    type: 'tool_use',
    id: event.toolId,
    toolName: event.toolName,
    toolType: event.toolType,  // 保存后端传来的 toolType
    input: {}  // 初始为空，等待 tool_progress 填充
  }

  // 🔧 调试日志：打印创建的 toolUseBlock
  console.log('📦 [processToolStart] created toolUseBlock:', toolUseBlock)

  // 添加到消息内容
  lastAssistantMessage.content = [...lastAssistantMessage.content, toolUseBlock]

  // 初始化 JSON 累积器（使用统一的 key 格式）
  const accumulatorKey = `tool_input_${event.toolId}`
  context.toolInputJsonAccumulator.set(accumulatorKey, '')

  // 注册工具调用到 pendingToolCalls（用于后续 tool_result 匹配）
  if (context.registerToolCall) {
    context.registerToolCall(toolUseBlock)
  }

  return {
    shouldUpdateMessages: true,
    shouldSetGenerating: true,
    messageUpdated: true
  }
}

/**
 * 处理 tool_progress 事件
 */
function processToolProgress(
  event: RpcToolProgress,
  context: RpcEventContext
): RpcEventProcessResult {
  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)

  // 查找工具调用块
  const toolBlock = lastAssistantMessage.content.find(
    (block) => isToolUseBlock(block) && block.id === event.toolId
  ) as ToolUseContent | undefined

  if (!toolBlock) {
    log.warn(`processToolProgress: 找不到工具调用块: ${event.toolId}`)
    return {
      shouldUpdateMessages: false,
      shouldSetGenerating: true,
      messageUpdated: false
    }
  }

  // 累积 outputPreview（实际是工具输入的 partial_json）
  const accumulatorKey = `tool_input_${event.toolId}`
  const accumulatedJson = (context.toolInputJsonAccumulator.get(accumulatorKey) || '') + (event.outputPreview || '')
  context.toolInputJsonAccumulator.set(accumulatorKey, accumulatedJson)

  // 尝试解析累积的 JSON
  try {
    const parsed = JSON.parse(accumulatedJson)
    
    // 更新工具调用块的 input（需要更新数组以触发响应式）
    const toolIndex = lastAssistantMessage.content.findIndex(
      (block) => isToolUseBlock(block) && block.id === event.toolId
    )
    if (toolIndex !== -1) {
      const updatedBlock: ToolUseContent = {
        ...toolBlock,
        input: parsed
      }
      const newContent = [...lastAssistantMessage.content]
      newContent[toolIndex] = updatedBlock
      lastAssistantMessage.content = newContent
    }
    
    return {
      shouldUpdateMessages: true,
      shouldSetGenerating: true,
      messageUpdated: true
    }
  } catch {
    // JSON 可能还不完整，暂时不更新
    // 但保留累积的字符串，等待更多增量
    return {
      shouldUpdateMessages: false,
      shouldSetGenerating: null,
      messageUpdated: false
    }
  }
}

/**
 * 处理 tool_complete 事件
 */
function processToolComplete(
  event: RpcToolComplete,
  context: RpcEventContext
): RpcEventProcessResult {
  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)

  // 查找工具调用块
  const toolBlock = lastAssistantMessage.content.find(
    (block) => isToolUseBlock(block) && block.id === event.toolId
  ) as ToolUseContent | undefined

  if (!toolBlock) {
    log.warn(`processToolComplete: 找不到工具调用块: ${event.toolId}`)
    return {
      shouldUpdateMessages: false,
      shouldSetGenerating: true,
      messageUpdated: false
    }
  }

  // 更新工具输入（使用 result 中的完整数据）
  if (event.result && event.result.type === 'tool_use') {
    const resultBlock = event.result as any
    if (resultBlock.input) {
      const toolIndex = lastAssistantMessage.content.findIndex(
        (block) => isToolUseBlock(block) && block.id === event.toolId
      )
      if (toolIndex !== -1) {
        const updatedBlock: ToolUseContent = {
          ...toolBlock,
          input: resultBlock.input
        }
        const newContent = [...lastAssistantMessage.content]
        newContent[toolIndex] = updatedBlock
        lastAssistantMessage.content = newContent
      }
    }
  }

  // tool_result 处理说明：
  // 后端会发送包含 tool_result 的 user 消息，该消息会被添加到消息列表中
  // resolveToolStatus 会从消息列表中查找 tool_result 来计算工具状态
  // 因此这里不需要额外处理 tool_result
  if (event.result && event.result.type === 'tool_result') {
    log.debug(`processToolComplete: 收到 tool_result, toolId=${event.toolId}, 状态将通过 resolveToolStatus 计算`)
  }

  // 清理 JSON 累积器
  const accumulatorKey = `tool_input_${event.toolId}`
  context.toolInputJsonAccumulator.delete(accumulatorKey)

  return {
    shouldUpdateMessages: true,
    shouldSetGenerating: true,
    messageUpdated: true
  }
}

/**
 * 处理 message_complete 事件
 */
function processMessageComplete(
  event: RpcMessageComplete,
  context: RpcEventContext
): RpcEventProcessResult {
  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)

  // 更新消息的 timestamp（使用当前时间作为完成时间）
  const messageIndex = context.messages.findIndex(m => m.id === lastAssistantMessage.id)
  if (messageIndex !== -1) {
    context.messages[messageIndex] = {
      ...context.messages[messageIndex],
      timestamp: Date.now(),
      tokenUsage: event.usage ? {
        inputTokens: event.usage.inputTokens || 0,
        outputTokens: event.usage.outputTokens || 0,
        cachedInputTokens: event.usage.cachedInputTokens
      } : undefined
    }
  }

  // 清理所有工具输入的 JSON 累积器
  context.toolInputJsonAccumulator.clear()

  // 注意：shouldSetGenerating 设为 null，不修改 isGenerating 状态
  // isGenerating 只在 handleResultMessage() 中设置为 false
  return {
    shouldUpdateMessages: true,
    shouldSetGenerating: null,
    messageUpdated: true
  }
}

/**
 * 处理 error 事件
 */
function processErrorEvent(
  event: RpcErrorEvent
): RpcEventProcessResult {
  log.error(`RPC Error: ${event.message}`)

  // 可以在这里添加错误处理逻辑，比如显示错误消息
  // 目前只记录日志，不更新消息状态

  return {
    shouldUpdateMessages: false,
    shouldSetGenerating: null,
    messageUpdated: false
  }
}

/**
 * 处理 assistant 事件（完整消息校验）
 */
function processAssistantMessage(
  event: RpcAssistantMessage,
  context: RpcEventContext
): RpcEventProcessResult {
  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)

  // 校验流式响应是否完整
  // 这里可以添加校验逻辑，比如比较 content 是否一致
  log.debug('processAssistantMessage: 收到完整消息校验', {
    messageId: lastAssistantMessage.id,
    contentBlocks: event.message?.content?.length ?? 0
  })

  return {
    shouldUpdateMessages: false,
    shouldSetGenerating: null,
    messageUpdated: false
  }
}

/**
 * 检测是否是压缩摘要消息
 * 压缩摘要消息的特征：isReplay = false 且内容以 "This session is being continued" 开头
 */
function isCompactSummaryMessage(event: RpcUserMessage, content: ContentBlock[]): boolean {
  // isReplay = false 表示这是压缩摘要（新生成的上下文）
  if (event.isReplay !== false) {
    return false
  }

  // 检查第一个文本块是否以压缩摘要标识开头
  const firstTextBlock = content.find(block => block.type === 'text') as { type: 'text', text: string } | undefined
  if (firstTextBlock?.text?.startsWith('This session is being continued')) {
    return true
  }

  return false
}

/**
 * 处理 user 事件
 *
 * user 消息通常包含 tool_result 块，用于标记工具调用完成
 * 这些消息需要添加到消息列表中，以便 resolveToolStatus 能够找到结果
 *
 * 特殊处理：
 * - 压缩摘要消息（isReplay = false）：标记为 isCompactSummary = true
 * - 压缩确认消息（isReplay = true）：标记为 isReplay = true
 */
function processUserMessage(
  event: RpcUserMessage,
  context: RpcEventContext
): RpcEventProcessResult {
  const contentBlocks = (event.message?.content || []) as ContentBlock[]

  // 检测是否是压缩摘要消息
  const isCompactSummary = isCompactSummaryMessage(event, contentBlocks)

  // 创建新的 user 消息
  const newMessage: Message = {
    id: `user-${Date.now()}-${crypto.randomUUID().substring(0, 8)}`,
    role: 'user',
    content: contentBlocks,
    timestamp: Date.now(),
    isReplay: event.isReplay,
    isCompactSummary: isCompactSummary
  }

  // 检查是否包含 tool_result
  const toolResults = newMessage.content.filter(block => block.type === 'tool_result')
  if (toolResults.length > 0) {
    console.log(`👤 [processUserMessage] 添加包含 ${toolResults.length} 个 tool_result 的 user 消息`)
  }

  // 打印压缩相关消息的调试信息
  if (isCompactSummary) {
    console.log(`📦 [processUserMessage] 检测到压缩摘要消息`)
  } else if (event.isReplay === true) {
    console.log(`🔄 [processUserMessage] 检测到压缩确认消息 (isReplay=true)`)
  }

  context.messages.push(newMessage)

  return {
    shouldUpdateMessages: true,
    shouldSetGenerating: true,
    messageUpdated: true,
    newMessage: newMessage
  }
}

/**
 * 统一的 RPC 事件处理入口
 *
 * 根据事件类型分发到对应的处理函数
 */
export function processRpcStreamEvent(
  event: RpcAppStreamEvent,
  context: RpcEventContext
): RpcEventProcessResult {
  const eventType = event.type
  
  console.log(`🔄 [processRpcStreamEvent] 处理事件: type=${eventType}`)
  log.debug(`处理 RPC 事件: type=${eventType}`)

  switch (eventType) {
    case 'message_start':
      return processMessageStart(event, context)
    
    case 'text_delta':
      console.log(`📝 [processRpcStreamEvent] TextDelta: "${event.text.substring(0, 50)}${event.text.length > 50 ? '...' : ''}"`)
      return processTextDelta(event, context)
    
    case 'thinking_delta':
      console.log(`💭 [processRpcStreamEvent] ThinkingDelta: "${event.thinking.substring(0, 50)}${event.thinking.length > 50 ? '...' : ''}"`)
      return processThinkingDelta(event, context)
    
    case 'tool_start':
      console.log(`🔧 [processRpcStreamEvent] ToolStart: toolId=${event.toolId}, toolName=${event.toolName}`)
      return processToolStart(event, context)
    
    case 'tool_progress':
      console.log(`⏳ [processRpcStreamEvent] ToolProgress: toolId=${event.toolId}, status=${event.status}`)
      return processToolProgress(event, context)
    
    case 'tool_complete':
      console.log(`✅ [processRpcStreamEvent] ToolComplete: toolId=${event.toolId}`)
      return processToolComplete(event, context)
    
    case 'message_complete':
      console.log(`🏁 [processRpcStreamEvent] MessageComplete`)
      return processMessageComplete(event, context)
    
    case 'assistant':
      return processAssistantMessage(event, context)

    case 'user':
      console.log(`👤 [processRpcStreamEvent] UserMessage: ${event.content?.length || 0} blocks`)
      return processUserMessage(event, context)

    case 'error':
      console.error(`❌ [processRpcStreamEvent] Error: ${event.message}`)
      log.error(`RPC 错误事件: ${event.message}`)
      return processErrorEvent(event)

    default:
      console.warn(`⚠️ [processRpcStreamEvent] 未知的事件类型: ${eventType}`)
      log.warn(`未知的 RPC 事件类型: ${eventType}`)
      return createNoOpResult()
  }
}
