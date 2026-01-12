package com.asakii.server

import kotlinx.serialization.Serializable

/**
 * 前端期望的文件信息格式
 * 用于 /api/files/search 和 /api/files/recent 端点
 */
@Serializable
data class IndexedFileInfo(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val fileType: String,
    val size: Long,
    val lastModified: Long
)

/**
 * 文件搜索 API 响应
 */
@Serializable
data class FileSearchResponse(
    val success: Boolean,
    val data: List<IndexedFileInfo>? = null,
    val error: String? = null,
    val errorCode: String? = null  // 错误码：INDEXING 表示正在索引
)

/**
 * JetBrains RSocket Handler 接口
 * 由插件模块实现，用于处理 JetBrains IDE 集成的 RSocket 调用
 */
interface JetBrainsRSocketHandlerProvider {
    /**
     * 创建 RSocket 请求处理器
     */
    fun createHandler(): io.rsocket.kotlin.RSocket

    /**
     * 设置客户端 requester（用于反向调用）
     */
    fun setClientRequester(clientId: String, requester: io.rsocket.kotlin.RSocket)

    /**
     * 移除客户端
     */
    fun removeClient(clientId: String)
}
