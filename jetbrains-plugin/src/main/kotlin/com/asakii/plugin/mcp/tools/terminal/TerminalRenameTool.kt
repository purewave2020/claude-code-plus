package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.mcp.getString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

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
    fun execute(arguments: JsonObject): JsonObject {
        val sessionId = arguments.getString("session_id")
            ?: return buildJsonObject {
                put("success", false)
                put("error", "Missing required parameter: session_id")
            }

        val newName = arguments.getString("new_name")
            ?: return buildJsonObject {
                put("success", false)
                put("error", "Missing required parameter: new_name")
            }

        // 验证会话所有权
        sessionManager.validateSessionOwnership(sessionId)?.let { return it }

        logger.info { "Renaming terminal session $sessionId to: $newName" }

        val success = sessionManager.renameSession(sessionId, newName)

        return if (success) {
            buildJsonObject {
                put("success", true)
                put("session_id", sessionId)
                put("new_name", newName)
                put("message", "Session renamed successfully")
            }
        } else {
            buildJsonObject {
                put("success", false)
                put("session_id", sessionId)
                put("error", "Failed to rename session or session not found")
            }
        }
    }
}
