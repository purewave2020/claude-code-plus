package com.asakii.plugin.services

import com.intellij.history.LocalHistory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/**
 * 文件历史服务
 *
 * 基于 IDEA LocalHistory 实现文件历史内容查询和回滚。
 * 通过时间戳查询指定时间点之前的文件内容，用于 Edit/Write 工具的 Diff 显示和回滚。
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
                logger.info { "File not found in VFS: $filePath" }
                return null
            }

            // 检查文件是否在 LocalHistory 控制下
            if (!LocalHistory.getInstance().isUnderControl(file)) {
                logger.info { "File not under LocalHistory control: $filePath" }
                return null
            }

            // 使用时间戳比较器获取历史内容
            // FileRevisionTimestampComparator 返回第一个满足条件的版本
            val byteContent = LocalHistory.getInstance().getByteContent(file) { revisionTimestamp ->
                // 返回时间戳小于指定时间的版本（即之前的版本）
                revisionTimestamp < beforeTimestamp
            }

            if (byteContent == null) {
                logger.info { "No history content found before timestamp $beforeTimestamp for: $filePath" }
                return null
            }

            val content = byteContent.toString(Charsets.UTF_8)
            logger.info { "Found history content (${content.length} chars) before $beforeTimestamp for: $filePath" }
            content
        } catch (e: Exception) {
            logger.warn("Failed to get history content for $filePath: ${e.message}", e)
            null
        }
    }

    /**
     * 回滚文件到指定时间戳之前的版本
     *
     * @param filePath 文件绝对路径
     * @param beforeTimestamp 时间戳（毫秒），回滚到此时间之前的版本
     * @return RollbackResult 回滚结果
     */
    fun rollbackToTimestamp(filePath: String, beforeTimestamp: Long): RollbackResult {
        logger.info { "Rollback request: file=$filePath, beforeTs=$beforeTimestamp" }

        return try {
            val virtualFile = LocalFileSystem.getInstance()
                .findFileByIoFile(File(filePath)) ?: run {
                logger.warn { "Rollback failed - file not found: $filePath" }
                return RollbackResult(
                    success = false,
                    error = "File not found: $filePath"
                )
            }

            // 检查文件是否在 LocalHistory 控制下
            if (!LocalHistory.getInstance().isUnderControl(virtualFile)) {
                logger.warn { "Rollback failed - file not under LocalHistory control: $filePath" }
                return RollbackResult(
                    success = false,
                    error = "File not under LocalHistory control: $filePath"
                )
            }

            // 获取历史内容
            val byteContent = LocalHistory.getInstance().getByteContent(virtualFile) { revisionTs ->
                revisionTs < beforeTimestamp
            }

            if (byteContent == null) {
                logger.warn { "Rollback failed - no history found before timestamp $beforeTimestamp for: $filePath" }
                return RollbackResult(
                    success = false,
                    error = "No history found before timestamp $beforeTimestamp"
                )
            }

            val historicalContent = byteContent.toString(Charsets.UTF_8)

            // 写回文件
            var writeResult: RollbackResult? = null
            ApplicationManager.getApplication().invokeAndWait {
                WriteAction.run<Exception> {
                    try {
                        virtualFile.setBinaryContent(historicalContent.toByteArray(virtualFile.charset))
                        writeResult = RollbackResult(success = true)
                        logger.info { "Rollback successful: $filePath (restored ${historicalContent.length} chars)" }
                    } catch (e: Exception) {
                        logger.error("Failed to write rollback content for $filePath", e)
                        writeResult = RollbackResult(
                            success = false,
                            error = "Failed to write file: ${e.message}"
                        )
                    }
                }
            }

            writeResult ?: RollbackResult(success = false, error = "Unknown error during rollback")

        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Rollback failed for $filePath", e)
            RollbackResult(
                success = false,
                error = "Rollback failed: ${e.message}"
            )
        }
    }
}

/**
 * 回滚操作结果
 */
data class RollbackResult(
    val success: Boolean,
    val error: String? = null
)
