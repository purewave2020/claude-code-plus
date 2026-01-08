package com.asakii.plugin.mcp.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * 文件修改的原始内容缓存
 * 用于在 Edit/Write 操作前保存文件原始内容，以支持 Diff 显示和回滚
 */
object FileChangeLabelCache {
    // toolUseId -> 原始文件内容
    private val originalContents = ConcurrentHashMap<String, String>()

    /**
     * 记录文件修改前的原始内容
     * @param toolUseId 工具调用 ID
     * @param content 原始文件内容
     */
    fun recordOriginalContent(toolUseId: String, content: String) {
        originalContents[toolUseId] = content
    }

    /**
     * 获取文件修改前的原始内容
     * @param toolUseId 工具调用 ID
     * @return 原始内容，如果不存在则返回 null
     */
    fun getOriginalContent(toolUseId: String): String? {
        return originalContents[toolUseId]
    }

    /**
     * 移除指定的原始内容记录
     * @param toolUseId 工具调用 ID
     */
    fun remove(toolUseId: String) {
        originalContents.remove(toolUseId)
    }

    /**
     * 清除所有缓存（会话结束时调用）
     */
    fun clearAll() {
        originalContents.clear()
    }

    /**
     * 获取当前缓存的条目数
     */
    fun size(): Int = originalContents.size
}
