package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getInt
import com.asakii.plugin.mcp.getLong
import com.asakii.plugin.mcp.getString
import com.asakii.settings.AgentSettingsService
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalRead 工具 - 读取/搜索终端输出
 *
 * 读取终端会话的输出内容，支持正则表达式搜索。
 * 支持等待命令执行完成后再读取。
 */
class TerminalReadTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 读取终端输出
     *
     * @param arguments 参数：
     *   - session_id: String? - 会话 ID，不传则使用默认终端
     *   - max_lines: Int? - 最大行数（默认 1000）
     *   - search: String? - 搜索模式（正则表达式）
     *   - context_lines: Int? - 搜索结果上下文行数（默认 2）
     *   - wait: Boolean? - 是否等待命令执行完成（默认 false）
     *   - timeout: Int? - 等待超时时间（秒，默认 30，0 表示无限等待）
     */
    fun execute(arguments: JsonObject): String {
        // 如果未指定 session_id，使用默认终端
        val requestedSessionId = arguments.getString("session_id")
        val sessionId = requestedSessionId
            ?: sessionManager.getDefaultTerminalId()
            ?: return TerminalResultFormatter.formatReadResult(
                success = false,
                sessionId = null,
                isRunning = null,
                output = null,
                lineCount = null,
                searchMatches = null,
                waitTimedOut = null,
                waitMessage = null,
                error = "No session_id provided and no default terminal exists"
            )

        // 验证会话所有权
        sessionManager.validateSessionOwnership(sessionId)?.let {
            return TerminalResultFormatter.formatReadResult(
                success = false,
                sessionId = sessionId,
                isRunning = null,
                output = null,
                lineCount = null,
                searchMatches = null,
                waitTimedOut = null,
                waitMessage = null,
                error = "Session not found or not owned by current AI session: $sessionId"
            )
        }

        val maxLines = arguments.getInt("max_lines") ?: 1000
        val search = arguments.getString("search")
        val contextLines = arguments.getInt("context_lines") ?: 2
        // 默认不等待，可通过 wait=true 等待命令完成
        val waitForIdle = arguments.getBoolean("wait") ?: false
        // 使用配置的默认超时时间（秒）
        val settings = AgentSettingsService.getInstance()
        val defaultTimeoutSec = settings.terminalReadTimeoutMs / 1000
        val timeoutSec = arguments.getLong("timeout") ?: defaultTimeoutSec
        // 0 表示无限等待，使用一个很大的值；否则转换为毫秒
        val timeoutMs = if (timeoutSec <= 0) Long.MAX_VALUE else timeoutSec * 1000

        logger.info { "Reading output from session: $sessionId (maxLines: $maxLines, search: $search, waitForIdle: $waitForIdle)" }

        val result = sessionManager.readOutput(sessionId, maxLines, search, contextLines, waitForIdle, timeoutMs)

        return if (result.success) {
            val searchMatches = result.searchMatches?.map { match ->
                TerminalResultFormatter.SearchMatch(
                    lineNumber = match.lineNumber,
                    line = match.line,
                    context = match.context
                )
            }

            TerminalResultFormatter.formatReadResult(
                success = true,
                sessionId = result.sessionId,
                isRunning = result.isRunning,
                output = result.output,
                lineCount = result.lineCount,
                searchMatches = searchMatches,
                waitTimedOut = result.waitTimedOut,
                waitMessage = result.waitMessage,
                error = null
            )
        } else {
            TerminalResultFormatter.formatReadResult(
                success = false,
                sessionId = sessionId,
                isRunning = null,
                output = null,
                lineCount = null,
                searchMatches = null,
                waitTimedOut = null,
                waitMessage = null,
                error = result.error ?: "Unknown error"
            )
        }
    }
}
