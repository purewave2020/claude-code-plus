package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.mcp.getString
import kotlinx.serialization.json.JsonObject
import com.asakii.logging.*

private val logger = getLogger("TerminalRenameTool")

/**
 * TerminalRename 工具 - 重命名终端会话
 *
 * 只能重命名当前 AI 会话的终端。
 */
class TerminalRenameTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 重命名终端会话
     *
     * @param arguments 参数：
     *   - session_id: String - 会话 ID（必需）
     *   - new_name: String - 新名称（必需）
     */
    fun execute(arguments: JsonObject): String {
        val sessionId = arguments.getString("session_id")
            ?: return TerminalResultFormatter.formatRenameResult(
                success = false,
                sessionId = null,
                newName = null,
                message = null,
                error = "Missing required parameter: session_id"
            )

        val newName = arguments.getString("new_name")
            ?: return TerminalResultFormatter.formatRenameResult(
                success = false,
                sessionId = sessionId,
                newName = null,
                message = null,
                error = "Missing required parameter: new_name"
            )

        // 验证会话所有权
        sessionManager.validateSessionOwnership(sessionId)?.let {
            return TerminalResultFormatter.formatRenameResult(
                success = false,
                sessionId = sessionId,
                newName = null,
                message = null,
                error = "Session not found or not owned by current AI session: $sessionId"
            )
        }

        logger.info { "Renaming terminal session $sessionId to: $newName" }

        val success = sessionManager.renameSession(sessionId, newName)

        return if (success) {
            TerminalResultFormatter.formatRenameResult(
                success = true,
                sessionId = sessionId,
                newName = newName,
                message = "Session renamed successfully",
                error = null
            )
        } else {
            TerminalResultFormatter.formatRenameResult(
                success = false,
                sessionId = sessionId,
                newName = null,
                message = null,
                error = "Failed to rename session or session not found"
            )
        }
    }
}
