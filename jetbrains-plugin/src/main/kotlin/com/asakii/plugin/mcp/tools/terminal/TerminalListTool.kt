package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getInt
import kotlinx.serialization.json.JsonObject
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
    fun execute(arguments: JsonObject): String {
        val includePreview = arguments.getBoolean("include_output_preview") ?: false
        val previewLines = arguments.getInt("preview_lines") ?: 5

        logger.info { "Listing terminal sessions for current AI session (includePreview: $includePreview)" }

        // 只获取当前 AI 会话的终端
        val sessions = sessionManager.getCurrentSessionTerminals()

        val sessionInfoList = sessions.map { session ->
            TerminalResultFormatter.SessionInfo(
                id = session.id,
                name = session.name,
                shellType = session.shellType,
                isRunning = session.hasRunningCommands() ?: false,
                outputPreview = if (includePreview) session.getOutput(previewLines) else null
            )
        }

        return TerminalResultFormatter.formatListResult(
            success = true,
            count = sessions.size,
            sessions = sessionInfoList,
            error = null
        )
    }
}
