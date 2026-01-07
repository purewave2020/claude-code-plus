package com.asakii.plugin.mcp.tools

import com.intellij.history.Label
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * 文件变更标签缓存
 *
 * 使用 IDEA LocalHistory API 的 Label 对象来获取文件修改前的内容。
 * 缓存是会话级别的：
 * - 当前会话：支持
 * - 重载会话：支持（内存缓存保留）
 * - 历史会话加载：不支持（没有 Label 缓存）
 * - 会话销毁：缓存丢失
 */
object FileChangeLabelCache {

    /**
     * toolUseId → (filePath, Label)
     */
    private val cache = ConcurrentHashMap<String, Pair<String, Label>>()

    /**
     * 记录文件变更标签
     *
     * @param toolUseId 工具调用 ID
     * @param filePath 文件路径
     * @param label LocalHistory 标签对象
     */
    fun record(toolUseId: String, filePath: String, label: Label) {
        cache[toolUseId] = Pair(filePath, label)
        logger.info { "[FileChangeLabelCache] 记录标签: toolUseId=$toolUseId, filePath=$filePath" }
    }

    /**
     * 获取文件修改前的原始内容
     *
     * @param toolUseId 工具调用 ID
     * @return 原始内容，如果不存在或获取失败则返回 null
     */
    fun getOriginalContent(toolUseId: String): String? {
        val (filePath, label) = cache[toolUseId] ?: run {
            logger.debug { "[FileChangeLabelCache] 缓存未命中: toolUseId=$toolUseId" }
            return null
        }

        return try {
            val byteContent = label.getByteContent(filePath)
            if (byteContent == null) {
                logger.warn { "[FileChangeLabelCache] 无法获取内容（可能是新建文件）: toolUseId=$toolUseId, filePath=$filePath" }
                return null
            }
            val content = String(byteContent.bytes, Charsets.UTF_8)
            logger.debug { "[FileChangeLabelCache] 获取原始内容成功: toolUseId=$toolUseId, size=${content.length}" }
            content
        } catch (e: Exception) {
            logger.error(e) { "[FileChangeLabelCache] 获取原始内容失败: toolUseId=$toolUseId, filePath=$filePath" }
            null
        }
    }

    /**
     * 获取 Label 对象（用于回滚等操作）
     */
    fun getLabel(toolUseId: String): Label? = cache[toolUseId]?.second

    /**
     * 获取文件路径
     */
    fun getFilePath(toolUseId: String): String? = cache[toolUseId]?.first

    /**
     * 移除指定的缓存条目
     */
    fun remove(toolUseId: String) {
        cache.remove(toolUseId)
        logger.debug { "[FileChangeLabelCache] 移除缓存: toolUseId=$toolUseId" }
    }

    /**
     * 清空指定会话的所有缓存
     *
     * 通过 toolUseId 前缀匹配来识别同一会话的工具调用
     *
     * @param sessionId 会话 ID
     */
    fun clearSession(sessionId: String) {
        val keysToRemove = cache.keys.filter { it.startsWith(sessionId) }
        keysToRemove.forEach { cache.remove(it) }
        if (keysToRemove.isNotEmpty()) {
            logger.info { "[FileChangeLabelCache] 清空会话缓存: sessionId=$sessionId, 移除 ${keysToRemove.size} 个条目" }
        }
    }

    /**
     * 清空所有缓存
     */
    fun clear() {
        val size = cache.size
        cache.clear()
        logger.info { "[FileChangeLabelCache] 清空所有缓存: 移除 $size 个条目" }
    }

    /**
     * 获取缓存大小
     */
    fun size(): Int = cache.size

    /**
     * 检查是否有指定的缓存
     */
    fun contains(toolUseId: String): Boolean = cache.containsKey(toolUseId)
}
