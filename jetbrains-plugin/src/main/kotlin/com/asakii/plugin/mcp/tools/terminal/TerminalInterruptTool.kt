package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.mcp.getString
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalInterrupt 工具 - 发送终端控制信号
 *
 * 只能操作当前 AI 会话的终端。
 *
 * 支持的信号：
 * - SIGINT (Ctrl+C): 中断信号，默认值
 * - SIGQUIT (Ctrl+\): 强制退出信号
 * - SIGTSTP (Ctrl+Z): 暂停进程信号
 */
class TerminalInterruptTool(private val sessionManager: TerminalSessionManager) {

    companion object {
        val VALID_SIGNALS = setOf("SIGINT", "SIGQUIT", "SIGTSTP")
    }

    /**
     * 发送终端控制信号
     *
     * @param arguments 参数：
     *   - session_id: String - 会话 ID（必需）
     *   - signal: String - 信号类型（可选，默认 SIGINT）
     */
    fun execute(arguments: JsonObject): String {
        val sessionId = arguments.getString("session_id")
            ?: return TerminalResultFormatter.formatInterruptResult(
                success = false,
                sessionId = null,
                signal = null,
                wasRunning = null,
                isStillRunning = null,
                message = null,
                error = "Missing required parameter: session_id"
            )

        // 验证会话所有权
        sessionManager.validateSessionOwnership(sessionId)?.let {
            return TerminalResultFormatter.formatInterruptResult(
                success = false,
                sessionId = sessionId,
                signal = null,
                wasRunning = null,
                isStillRunning = null,
                message = null,
                error = "Session not found or not owned by current AI session: $sessionId"
            )
        }

        // 解析 signal 参数，默认 SIGINT
        val signal = arguments.getString("signal")?.uppercase() ?: "SIGINT"

        // 验证 signal 值
        if (signal !in VALID_SIGNALS) {
            return TerminalResultFormatter.formatInterruptResult(
                success = false,
                sessionId = sessionId,
                signal = signal,
                wasRunning = null,
                isStillRunning = null,
                message = null,
                error = "Invalid signal: $signal. Valid values: ${VALID_SIGNALS.joinToString(", ")}"
            )
        }

        logger.info { "Sending $signal to session: $sessionId" }

        val result = sessionManager.interruptCommand(sessionId, signal)

        return if (result.success) {
            TerminalResultFormatter.formatInterruptResult(
                success = true,
                sessionId = result.sessionId,
                signal = result.signal,
                wasRunning = result.wasRunning,
                isStillRunning = result.isStillRunning,
                message = result.message,
                error = null
            )
        } else {
            TerminalResultFormatter.formatInterruptResult(
                success = false,
                sessionId = result.sessionId,
                signal = result.signal,
                wasRunning = null,
                isStillRunning = null,
                message = null,
                error = result.error ?: "Unknown error"
            )
        }
    }
}
