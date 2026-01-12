package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.compat.CommandWaitResult
import com.asakii.plugin.compat.TerminalWidgetWrapper
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*

private val logger = Logger.getInstance("com.asakii.plugin.mcp.tools.terminal.TerminalModels")

/**
 * Terminal 会话信息
 */
data class TerminalSession(
    val id: String,
    val name: String,
    val shellType: String,
    val widgetWrapper: TerminalWidgetWrapper,
    val createdAt: Long = System.currentTimeMillis(),
    var lastCommandAt: Long = System.currentTimeMillis(),
    var isBackground: Boolean = false
) {
    /**
     * 检查是否有正在运行的命令
     *
     * @return true 表示有命令正在运行，false 表示没有，null 表示 API 不可用
     */
    fun hasRunningCommands(): Boolean? {
        return try {
            widgetWrapper.hasRunningCommands()
        } catch (e: Exception) {
            logger.warn { "Failed to check running commands for session $id: ${e.message}" }
            null
        }
    }

    /**
     * 等待命令执行完成
     *
     * 使用兼容层的实现（依赖 Shell Integration）：
     * - 2024.x ~ 2025.2: hasRunningCommands() API
     * - 2025.3+: isCommandRunning() API
     *
     * 如果 API 不可用，返回 ApiUnavailable
     *
     * @param timeoutMs 超时时间
     * @param initialDelayMs 初始等待时间
     * @param pollIntervalMs 轮询间隔
     * @return 等待结果
     */
    fun waitForCommandCompletion(
        timeoutMs: Long = 300_000,
        initialDelayMs: Long = 300,
        pollIntervalMs: Long = 100
    ): CommandWaitResult {
        return widgetWrapper.waitForCommandCompletion(
            timeoutMs = timeoutMs,
            initialDelayMs = initialDelayMs,
            pollIntervalMs = pollIntervalMs
        )
    }

    /**
     * 获取终端输出内容（使用 wrapper 的统一 API）
     */
    fun getOutput(maxLines: Int = 1000): String {
        return try {
            widgetWrapper.getOutput(maxLines)
        } catch (e: Exception) {
            logger.error("Failed to get output for session $id", e)
            ""
        }
    }

    /**
     * 搜索输出内容
     */
    fun searchOutput(pattern: String, contextLines: Int = 2): List<SearchMatch> {
        val output = getOutput()
        val lines = output.split("\n")
        val matches = mutableListOf<SearchMatch>()
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            Regex(Regex.escape(pattern))
        }

        lines.forEachIndexed { index, line ->
            if (regex.containsMatchIn(line)) {
                val startLine = maxOf(0, index - contextLines)
                val endLine = minOf(lines.size - 1, index + contextLines)
                val context = lines.subList(startLine, endLine + 1).joinToString("\n")
                matches.add(SearchMatch(
                    lineNumber = index + 1,
                    line = line,
                    context = context
                ))
            }
        }
        return matches
    }
}

/**
 * 搜索匹配结果
 */
data class SearchMatch(
    val lineNumber: Int,
    val line: String,
    val context: String
)

/**
 * 终端任务更新监听器
 */
typealias TerminalTaskUpdateListener = suspend (
    toolUseId: String,
    sessionId: String,
    action: String,  // "started", "completed", "backgrounded"
    command: String,
    isBackground: Boolean,
    startTime: Long,
    elapsedMs: Long?
) -> Unit

/**
 * 命令执行结果
 */
data class ExecuteResult(
    val success: Boolean,
    val sessionId: String,
    val sessionName: String? = null,
    val background: Boolean = false,
    val output: String? = null,
    val truncated: Boolean = false,
    val totalLines: Int? = null,
    val totalChars: Int? = null,
    val error: String? = null
)

/**
 * 命令中断结果
 */
data class InterruptResult(
    val success: Boolean,
    val sessionId: String,
    val signal: String? = null,  // 发送的信号类型
    val wasRunning: Boolean? = null,  // null 表示无法确定
    val isStillRunning: Boolean? = null,  // null 表示无法确定
    val message: String? = null,
    val error: String? = null
)

/**
 * 输出读取结果
 */
data class ReadResult(
    val success: Boolean,
    val sessionId: String,
    val output: String? = null,
    val isRunning: Boolean? = null,  // null 表示无法确定
    val lineCount: Int = 0,
    val searchMatches: List<SearchMatch>? = null,
    val error: String? = null,
    val waitTimedOut: Boolean = false,  // 等待是否超时
    val waitMessage: String? = null     // 等待相关的消息
)

/**
 * Shell 类型信息
 */
data class ShellTypeInfo(
    val name: String,
    val displayName: String,
    val command: String?,
    val isDefault: Boolean
)

/**
 * 终端后台任务信息
 * 用于追踪正在执行的 MCP 工具调用
 */
data class TerminalBackgroundTask(
    val sessionId: String,           // 终端会话 ID
    val toolUseId: String,           // MCP 工具调用 ID
    val command: String,             // 执行的命令
    val startTime: Long,             // 开始时间戳（毫秒）
    var isBackground: Boolean = false,  // 是否已移到后台
    var backgroundTime: Long? = null    // 移到后台的时间戳
) {
    /**
     * 获取已执行时长（毫秒）
     */
    fun getElapsedMs(): Long = System.currentTimeMillis() - startTime
}
