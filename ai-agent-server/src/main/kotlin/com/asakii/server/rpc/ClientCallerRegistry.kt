package com.asakii.server.rpc

import com.asakii.logging.getLogger
import com.asakii.logging.info
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * ClientCaller 全局注册表
 *
 * 维护 connectId → ClientCaller 的映射，
 * 用于 MCP 工具调用时通过 connectId 找到对应的 RSocket 连接。
 *
 * 生命周期管理：
 * - 注册时绑定到协程作用域
 * - 作用域取消时自动从注册表中移除
 * - 确保零内存泄漏
 */
object ClientCallerRegistry {
    private val registry = ConcurrentHashMap<String, ClientCaller>()
    private val logger = getLogger<ClientCallerRegistry>()

    /**
     * 注册 ClientCaller，并绑定到协程作用域的生命周期
     *
     * 当 scope 被取消（如 RSocket 连接关闭）时，自动从注册表中移除。
     *
     * @param connectId 前端连接标识（tab ID）
     * @param caller ClientCaller 实例
     * @param scope 要绑定的协程作用域（通常是 RSocket handler 的作用域）
     */
    fun register(connectId: String, caller: ClientCaller, scope: CoroutineScope) {
        registry[connectId] = caller
        logger.info { "✅ [Registry] 注册 ClientCaller: connectId=$connectId" }

        // 绑定到协程作用域的生命周期，自动清理
        scope.coroutineContext[Job]?.invokeOnCompletion { cause ->
            registry.remove(connectId)
            val reason = cause?.message ?: "正常关闭"
            logger.info { "🗑️ [Registry] 自动移除 ClientCaller: connectId=$connectId, reason=$reason" }
        }
    }

    /**
     * 获取 ClientCaller
     */
    fun get(connectId: String): ClientCaller? = registry[connectId]

    /**
     * 检查 connectId 是否已注册
     */
    fun contains(connectId: String): Boolean = registry.containsKey(connectId)

    /**
     * 获取当前注册数量（用于调试/监控）
     */
    fun size(): Int = registry.size
}
