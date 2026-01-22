package com.asakii.server.mcp

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * MCP 调用上下文
 *
 * 在协程中传递 MCP 调用相关的上下文信息，包括：
 * - connectId: 前端连接标识（用于回调前端）
 *
 * 使用方式：
 * ```kotlin
 * // 在 Gateway 层注入上下文
 * withContext(McpCallContext(connectId = "xxx")) {
 *     server.callToolWithContext(...)
 * }
 *
 * // 在 MCP 工具内部获取
 * val connectId = currentConnectId()
 * ```
 */
data class McpCallContext(val connectId: String?) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<McpCallContext>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * 从当前协程上下文获取 connectId
 *
 * @return 前端连接标识，如果不在 MCP 调用上下文中则返回 null
 */
suspend fun currentConnectId(): String? {
    return coroutineContext[McpCallContext]?.connectId
}
