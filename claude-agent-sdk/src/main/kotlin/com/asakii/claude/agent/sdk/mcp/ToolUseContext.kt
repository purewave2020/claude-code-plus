package com.asakii.claude.agent.sdk.mcp

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * 协程上下文元素，用于在 MCP 工具调用链中传递 toolUseId
 *
 * 类似于 ThreadLocal，在整个协程调用链中都可以访问 toolUseId。
 * 主要用于 MCP 工具执行时获取当前工具调用的 ID，以便进行文件历史记录等操作。
 *
 * 使用方式：
 * ```kotlin
 * // 在调用工具时注入上下文
 * withContext(ToolUseContext(toolUseId)) {
 *     tool.execute(arguments)
 * }
 *
 * // 在工具内部获取 toolUseId
 * val toolUseId = currentToolUseId()
 * ```
 */
data class ToolUseContext(val toolUseId: String?) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ToolUseContext>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * 从当前协程上下文获取 toolUseId
 *
 * @return 当前工具调用的 ID，如果不在工具调用上下文中则返回 null
 */
suspend fun currentToolUseId(): String? {
    return coroutineContext[ToolUseContext]?.toolUseId
}
