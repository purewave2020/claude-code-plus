package com.asakii.claude.agent.sdk.callback

import java.util.concurrent.ConcurrentHashMap
import com.asakii.logging.*

/**
 * 工具回调注册表 - 管理所有自定义工具回调
 *
 * 线程安全，支持并发注册和查询。
 *
 * 使用示例：
 * ```kotlin
 * val registry = ToolCallbackRegistry()
 *
 * // 注册回调
 * registry.register(AskUserQuestionCallback())
 *
 * // 查询回调
 * val callback = registry.get("AskUserQuestion")
 * if (callback != null) {
 *     val result = callback.execute(toolId, input)
 * }
 * ```
 */
class ToolCallbackRegistry {
    private val logger = getLogger("ToolCallbackRegistry")
    private val callbacks = ConcurrentHashMap<String, ToolCallback>()

    /**
     * 注册工具回调
     *
     * @param callback 工具回调实现
     * @throws IllegalStateException 如果该工具名称已注册
     */
    fun register(callback: ToolCallback) {
        val existing = callbacks.putIfAbsent(callback.toolName, callback)
        if (existing != null) {
            logger.warn { "⚠️ [ToolCallbackRegistry] 工具 '${callback.toolName}' 已注册，覆盖旧回调" }
            callbacks[callback.toolName] = callback
        } else {
            logger.info { "✅ [ToolCallbackRegistry] 注册工具回调: ${callback.toolName}" }
        }
    }

    /**
     * 获取工具回调
     *
     * @param toolName 工具名称
     * @return 工具回调，如果未注册则返回 null
     */
    fun get(toolName: String): ToolCallback? {
        return callbacks[toolName]
    }

    /**
     * 检查是否已注册某个工具的回调
     *
     * @param toolName 工具名称
     * @return 是否已注册
     */
    fun hasCallback(toolName: String): Boolean {
        return callbacks.containsKey(toolName)
    }

    /**
     * 移除工具回调
     *
     * @param toolName 工具名称
     * @return 被移除的回调，如果不存在则返回 null
     */
    fun unregister(toolName: String): ToolCallback? {
        val removed = callbacks.remove(toolName)
        if (removed != null) {
            logger.info { "🗑️ [ToolCallbackRegistry] 移除工具回调: $toolName" }
        }
        return removed
    }

    /**
     * 获取所有已注册的工具名称
     */
    fun getRegisteredToolNames(): Set<String> {
        return callbacks.keys.toSet()
    }

    /**
     * 清空所有回调
     */
    fun clear() {
        callbacks.clear()
        logger.info { "🧹 [ToolCallbackRegistry] 已清空所有工具回调" }
    }
}
