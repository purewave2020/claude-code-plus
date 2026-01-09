package com.asakii.plugin.mcp.tools.terminal

import kotlinx.serialization.json.JsonObject
import com.asakii.logging.*

private val logger = getLogger("TerminalTypesTool")

/**
 * TerminalTypes 工具 - 获取可用的 Shell 类型
 */
class TerminalTypesTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 获取可用的 Shell 类型
     *
     * @param arguments 参数（无需参数）
     */
    fun execute(arguments: JsonObject): String {
        logger.info { "Getting available shell types" }

        val types = sessionManager.getAvailableShellTypes()
        val platform = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "Windows"
        } else {
            "Unix"
        }

        val defaultType = types.find { it.isDefault }?.name ?: types.firstOrNull()?.name ?: "bash"

        val typeInfoList = types.map { type ->
            TerminalResultFormatter.ShellTypeInfo(
                name = type.name,
                displayName = type.displayName,
                isDefault = type.isDefault
            )
        }

        return TerminalResultFormatter.formatTypesResult(
            success = true,
            platform = platform,
            types = typeInfoList,
            defaultType = defaultType,
            error = null
        )
    }
}
