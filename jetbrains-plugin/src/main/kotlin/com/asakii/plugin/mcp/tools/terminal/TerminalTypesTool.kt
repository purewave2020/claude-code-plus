package com.asakii.plugin.mcp.tools.terminal

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalTypes 工具 - 获取可用的 Shell 类型
 */
class TerminalTypesTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 获取可用的 Shell 类型
     *
     * @param arguments 参数（无需参数）
     */
    fun execute(arguments: JsonObject): JsonObject {
        logger.info { "Getting available shell types" }

        val types = sessionManager.getAvailableShellTypes()
        val platform = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "windows"
        } else {
            "unix"
        }

        val defaultType = types.find { it.isDefault }?.name ?: types.firstOrNull()?.name ?: "bash"

        return buildJsonObject {
            put("success", true)
            put("platform", platform)
            put("types", buildJsonArray {
                types.forEach { type ->
                    add(buildJsonObject {
                        put("name", type.name)
                        put("display_name", type.displayName)
                        type.command?.let { put("command", it) }
                        put("is_default", type.isDefault)
                    })
                }
            })
            put("default_type", defaultType)
        }
    }
}
