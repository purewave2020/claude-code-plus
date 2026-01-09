package com.asakii.plugin.adapters

import com.asakii.plugin.handlers.ToolClickManager
import com.asakii.plugin.handlers.ToolClickConfig
import com.asakii.plugin.types.LegacyToolCall
import com.asakii.plugin.tools.IdeToolsImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*
import java.util.*

/**
 * IntelliJ IDEA 的 IDE 集成实现
 */
class IdeaIdeIntegration(
    private val project: Project
) : IdeIntegration {
    
    companion object {
        private val logger = Logger.getInstance(IdeaIdeIntegration::class.java)
    }
    
    override fun handleToolClick(toolCall: LegacyToolCall): Boolean {
        logger.info { "🔧 [IdeaIdeIntegration] 处理工具点击: ${toolCall.name}" }
        logger.info { "- 工具ID: ${toolCall.id}" }
        logger.info { "- 工具状态: ${toolCall.status}" }
        logger.info { "- 有结果: ${toolCall.result != null}" }

        return try {
            val result = ToolClickManager.handleToolClick(toolCall, project, ToolClickConfig())
            logger.info { "✅ [IdeaIdeIntegration] ToolClickManager处理结果: $result" }
            result
        } catch (e: Exception) {
            logger.error("❌ [IdeaIdeIntegration] 处理工具点击失败", e)
            false
        }
    }
    
    override fun openFile(filePath: String, line: Int?, column: Int?): Boolean {
        return try {
            val ideTools = IdeToolsImpl(project)
            val result = ideTools.openFile(filePath, line ?: 0, column ?: 0)
            result.fold(
                onSuccess = { true },
                onFailure = { error ->
                    logger.error("打开文件失败: $filePath", error)
                    false
                }
            )
        } catch (e: Exception) {
            logger.error("打开文件失败", e)
            false
        }
    }
    
    override fun showDiff(filePath: String, oldContent: String, newContent: String): Boolean {
        return try {
            val ideTools = IdeToolsImpl(project)
            val diffRequest = com.asakii.rpc.api.DiffRequest(
                filePath = filePath,
                oldContent = oldContent,
                newContent = newContent
            )
            val result = ideTools.showDiff(diffRequest)
            result.fold(
                onSuccess = { true },
                onFailure = { error ->
                    logger.error("显示差异失败: $filePath", error)
                    false
                }
            )
        } catch (e: Exception) {
            logger.error("显示差异失败", e)
            false
        }
    }

    
    override fun showNotification(message: String, type: NotificationType) {
        try {
            val intellijType = when (type) {
                NotificationType.INFO -> com.intellij.notification.NotificationType.INFORMATION
                NotificationType.WARNING -> com.intellij.notification.NotificationType.WARNING
                NotificationType.ERROR -> com.intellij.notification.NotificationType.ERROR
            }

            val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("Claude Code Plus")

            if (notificationGroup != null) {
                val notification = notificationGroup.createNotification(
                    "Claude Code Plus",
                    message,
                    intellijType
                )
                com.intellij.notification.Notifications.Bus.notify(notification, project)
            } else {
                // Fallback: 直接创建通知（无分组）
                val notification = com.intellij.notification.Notification(
                    "Claude Code Plus",
                    "Claude Code Plus",
                    message,
                    intellijType
                )
                com.intellij.notification.Notifications.Bus.notify(notification, project)
            }
        } catch (e: Exception) {
            logger.warn("显示通知失败", e)
        }
    }
    
    override fun isSupported(): Boolean = true
    
    /**
     * 获取IntelliJ IDEA的界面语言设置
     * @return IDE的Locale设置
     */
    override fun getIdeLocale(): Locale {
        return try {
            // 使用系统默认语言设置
            val ideLocale = Locale.getDefault()
            logger.info { "🌐 获取IDE界面语言设置: $ideLocale (language=${ideLocale.language}, country=${ideLocale.country})" }
            ideLocale
        } catch (e: Exception) {
            logger.warn("获取IDE界面语言设置失败，使用英语作为默认", e)
            Locale.ENGLISH
        }
    }
}
