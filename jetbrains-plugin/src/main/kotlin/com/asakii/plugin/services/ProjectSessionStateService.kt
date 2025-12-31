package com.asakii.plugin.services

/**
 * 项目级会话状态服务
 *
 * 提供项目级别的会话状态管理功能
 */
object ProjectSessionStateService {

    /**
     * 清理当前会话
     */
    fun clearCurrentSession() {
        // 简化实现 - 清理会话状态
    }

    /**
     * 获取统计信息
     */
    fun getStats(): kotlinx.serialization.json.JsonObject {
        return kotlinx.serialization.json.JsonObject(
            mapOf(
                "activeSessionsCount" to kotlinx.serialization.json.JsonPrimitive(0),
                "totalMessages" to kotlinx.serialization.json.JsonPrimitive(0),
                "status" to kotlinx.serialization.json.JsonPrimitive("ready")
            )
        )
    }

    /**
     * 获取服务统计信息
     */
    fun getServiceStats(): kotlinx.serialization.json.JsonObject {
        return getStats()
    }
}
