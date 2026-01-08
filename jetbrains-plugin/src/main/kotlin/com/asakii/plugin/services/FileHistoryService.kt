package com.asakii.plugin.services

import com.intellij.history.LocalHistory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/**
 * 文件历史服务
 *
 * 基于 IDEA LocalHistory 实现文件历史内容查询。
 * 通过时间戳查询指定时间点之前的文件内容，用于 Edit/Write 工具的 Diff 显示。
 */
object FileHistoryService {
    private val logger = Logger.getInstance(FileHistoryService::class.java)

    /**
     * 获取指定时间之前的文件内容
     *
     * @param filePath 文件绝对路径
     * @param beforeTimestamp 时间戳（毫秒），获取此时间之前的版本
     * @return 历史内容，如果不存在或获取失败则返回 null
     */
    fun getContentBefore(filePath: String, beforeTimestamp: Long): String? {
        return try {
            val file = LocalFileSystem.getInstance()
                .findFileByIoFile(File(filePath)) ?: run {
                logger.info("File not found in VFS: $filePath")
                return null
            }

            // 检查文件是否在 LocalHistory 控制下
            if (!LocalHistory.getInstance().isUnderControl(file)) {
                logger.info("File not under LocalHistory control: $filePath")
                return null
            }

            // 使用时间戳比较器获取历史内容
            // FileRevisionTimestampComparator 返回第一个满足条件的版本
            val byteContent = LocalHistory.getInstance().getByteContent(file) { revisionTimestamp ->
                // 返回时间戳小于指定时间的版本（即之前的版本）
                revisionTimestamp < beforeTimestamp
            }

            if (byteContent == null) {
                logger.info("No history content found before timestamp $beforeTimestamp for: $filePath")
                return null
            }

            val content = byteContent.toString(Charsets.UTF_8)
            logger.info("Found history content (${content.length} chars) before $beforeTimestamp for: $filePath")
            content
        } catch (e: Exception) {
            logger.warn("Failed to get history content for $filePath: ${e.message}", e)
            null
        }
    }
}
