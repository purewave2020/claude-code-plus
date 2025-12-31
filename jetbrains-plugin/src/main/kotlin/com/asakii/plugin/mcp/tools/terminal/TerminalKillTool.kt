package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getStringList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalKill 工具 - 终止终端会话（支持批量）
 *
 * 只能终止当前 AI 会话的终端，无法终止其他会话的终端。
 */
class TerminalKillTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 终止终端会话
     *
     * @param arguments 参数：
     *   - session_ids: List<String> - 要终止的会话 ID 列表
     *   - all: Boolean - 是否终止当前 AI 会话的所有终端
     *   至少提供 session_ids 或 all 中的一个
     */
    fun execute(arguments: JsonObject): JsonObject {
        val sessionIds = arguments.getStringList("session_ids")
        val killAll = arguments.getBoolean("all") ?: false

        // 获取当前 AI 会话的终端 ID 列表
        val currentSessionTerminalIds = sessionManager.getCurrentSessionTerminals().map { it.id }.toSet()

        // 收集要删除的会话 ID
        val idsToKill = when {
            killAll -> currentSessionTerminalIds.toList()
            sessionIds != null -> {
                // 只允许删除当前 AI 会话的终端
                sessionIds.filter { it in currentSessionTerminalIds }
            }
            else -> return buildJsonObject {
                put("success", false)
                put("error", "Missing required parameter: session_ids or all")
            }
        }

        if (idsToKill.isEmpty()) {
            return buildJsonObject {
                put("success", true)
                put("message", "No sessions to terminate")
                put("killed", buildJsonArray { })
                put("failed", buildJsonArray { })
            }
        }

        logger.info { "Killing ${idsToKill.size} terminal session(s): $idsToKill" }

        // 执行删除
        val killed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (id in idsToKill) {
            if (sessionManager.killSession(id)) {
                killed.add(id)
            } else {
                failed.add(id)
            }
        }

        return buildJsonObject {
            put("success", failed.isEmpty())
            put("killed", buildJsonArray { killed.forEach { add(it) } })
            put("failed", buildJsonArray { failed.forEach { add(it) } })
            put("message", when {
                failed.isEmpty() -> "All ${killed.size} session(s) terminated successfully"
                killed.isEmpty() -> "Failed to terminate all ${failed.size} session(s)"
                else -> "Terminated ${killed.size} session(s), failed ${failed.size}"
            })
        }
    }
}
