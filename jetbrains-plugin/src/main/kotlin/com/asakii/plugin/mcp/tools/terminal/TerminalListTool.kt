package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getInt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalList 工具 - 列出当前 AI 会话的终端
 */
class TerminalListTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 列出当前 AI 会话的终端（默认终端 + 溢出终端）
     *
     * @param arguments 参数：
     *   - include_output_preview: Boolean? - 是否包含输出预览（默认 false）
     *   - preview_lines: Int? - 预览行数（默认 5）
     */
    fun execute(arguments: JsonObject): JsonObject {
        val includePreview = arguments.getBoolean("include_output_preview") ?: false
        val previewLines = arguments.getInt("preview_lines") ?: 5

        logger.info { "Listing terminal sessions for current AI session (includePreview: $includePreview)" }

        // 只获取当前 AI 会话的终端
        val sessions = sessionManager.getCurrentSessionTerminals()

        val sessionList = buildJsonArray {
            sessions.forEach { session ->
                add(buildJsonObject {
                    put("id", session.id)
                    put("name", session.name)
                    put("shell_type", session.shellType)
                    put("is_running", session.hasRunningCommands())
                    put("created_at", session.createdAt)
                    put("last_command_at", session.lastCommandAt)
                    put("is_background", session.isBackground)

                    if (includePreview) {
                        val output = session.getOutput(previewLines)
                        put("output_preview", output)
                    }
                })
            }
        }

        return buildJsonObject {
            put("success", true)
            put("count", sessions.size)
            put("sessions", sessionList)
        }
    }
}
