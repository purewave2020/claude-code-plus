package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getLong
import com.asakii.plugin.mcp.getString
import com.asakii.settings.AgentSettingsService
import kotlinx.serialization.json.JsonObject
import com.asakii.logging.*

private val logger = getLogger("TerminalTool")

/**
 * Terminal 工具 - 执行命令
 *
 * 在 IDEA 内置终端中执行命令。
 * 默认立即返回，可通过 wait=true 等待命令完成并返回输出。
 */
class TerminalTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 执行命令
     *
     * @param arguments 参数：
     *   - command: String - 要执行的命令（必需）
     *   - session_id: String? - 会话 ID，为空时使用当前 AI 会话的默认终端
     *   - session_name: String? - 新会话名称
     *   - shell_type: String? - Shell 类型（如 git-bash, powershell），不传则使用配置的默认终端
     *   - wait: Boolean? - 是否等待命令完成并返回输出（默认 false）
     *   - timeout: Long? - 等待超时时间（秒，默认 30，0 表示无限等待）
     */
    fun execute(arguments: JsonObject): String {
        val command = arguments.getString("command")
            ?: return TerminalResultFormatter.formatTerminalResult(
                success = false,
                sessionId = null,
                sessionName = null,
                message = null,
                error = "Missing required parameter: command"
            )

        val sessionId = arguments.getString("session_id")
        val sessionName = arguments.getString("session_name")
        val shellName = arguments.getString("shell_type")
        val wait = arguments.getBoolean("wait") ?: false
        val settings = AgentSettingsService.getInstance()
        val defaultTimeoutSec = settings.terminalReadTimeoutMs / 1000
        val timeoutSec = arguments.getLong("timeout") ?: defaultTimeoutSec
        // 0 表示无限等待，转换为 null；否则转换为毫秒
        val timeoutMs = if (timeoutSec <= 0) null else timeoutSec * 1000

        logger.info { "Executing command: $command (session: $sessionId, shellName: $shellName, wait: $wait)" }

        // 获取或创建会话
        val session = if (sessionId != null) {
            // 指定了 session_id，验证所有权并获取会话
            sessionManager.validateSessionOwnership(sessionId)?.let { errorJson ->
                return TerminalResultFormatter.formatTerminalResult(
                    success = false,
                    sessionId = sessionId,
                    sessionName = null,
                    message = null,
                    error = "Session not found or not owned by current AI session: $sessionId"
                )
            }
            sessionManager.getSession(sessionId)!!
        } else {
            // 未指定 session_id，使用当前 AI 会话的默认终端
            if (sessionName != null) {
                // 如果指定了 session_name，创建新会话
                sessionManager.createSession(sessionName, shellName)
            } else {
                // 使用默认终端
                sessionManager.getOrCreateDefaultTerminal(shellName)
            } ?: return TerminalResultFormatter.formatTerminalResult(
                success = false,
                sessionId = null,
                sessionName = null,
                message = null,
                error = "Failed to create terminal session"
            )
        }

        // 执行命令
        val execResult = sessionManager.executeCommandAsync(session.id, command)

        if (!execResult.success) {
            return TerminalResultFormatter.formatTerminalResult(
                success = false,
                sessionId = execResult.sessionId,
                sessionName = execResult.sessionName ?: session.name,
                message = null,
                error = execResult.error
            )
        }

        // 如果不等待，直接返回
        if (!wait) {
            return TerminalResultFormatter.formatTerminalResult(
                success = true,
                sessionId = execResult.sessionId,
                sessionName = execResult.sessionName ?: session.name,
                message = "Command sent. Use TerminalRead to check output.",
                error = null
            )
        }

        // 等待命令完成并返回输出
        val readResult = sessionManager.readOutput(
            sessionId = session.id,
            maxLines = 1000,
            search = null,
            contextLines = 2,
            waitForIdle = true,
            timeoutMs = timeoutMs ?: 30_000L
        )

        return if (readResult.success) {
            TerminalResultFormatter.formatReadResult(
                success = true,
                sessionId = readResult.sessionId,
                isRunning = readResult.isRunning,
                output = readResult.output,
                lineCount = readResult.lineCount,
                searchMatches = null,
                waitTimedOut = readResult.waitTimedOut,
                waitMessage = readResult.waitMessage,
                error = null
            )
        } else {
            TerminalResultFormatter.formatReadResult(
                success = false,
                sessionId = session.id,
                isRunning = null,
                output = null,
                lineCount = null,
                searchMatches = null,
                waitTimedOut = null,
                waitMessage = null,
                error = readResult.error ?: "Failed to read output"
            )
        }
    }
}
