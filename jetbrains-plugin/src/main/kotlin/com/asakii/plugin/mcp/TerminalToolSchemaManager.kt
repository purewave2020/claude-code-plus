package com.asakii.plugin.mcp

import com.asakii.logging.getLogger
import com.asakii.logging.info
import com.asakii.logging.warn
import com.asakii.logging.error
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.McpDefaults
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val logger = getLogger("TerminalToolSchemaManager")

/**
 * Manager for Terminal MCP tool schemas.
 * Handles loading, caching, and dynamic configuration of tool schemas.
 */
object TerminalToolSchemaManager {

    private val BASE_SCHEMAS: Map<String, JsonObject> = loadBaseSchemas()

    private fun loadBaseSchemas(): Map<String, JsonObject> {
        logger.info { "Loading Terminal tool schemas from McpDefaults" }

        return try {
            val json = Json { ignoreUnknownKeys = true }
            val toolsMap = json.decodeFromString<Map<String, JsonObject>>(McpDefaults.TERMINAL_TOOLS_SCHEMA)
            logger.info { "Loaded ${toolsMap.size} terminal tool schemas: ${toolsMap.keys}" }
            toolsMap
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse Terminal schemas: ${e.message}" }
            emptyMap()
        }
    }

    /**
     * Get all tool schemas with dynamic shell configuration applied.
     */
    fun getToolSchemas(): Map<String, JsonObject> {
        val settings = AgentSettingsService.getInstance()
        val baseSchemas = BASE_SCHEMAS.toMutableMap()

        val terminalSchema = baseSchemas["Terminal"] ?: return baseSchemas
        val properties = terminalSchema["properties"] as? JsonObject ?: return baseSchemas
        val shellTypeProperty = properties["shell_type"] as? JsonObject ?: return baseSchemas

        val availableShells = settings.getEffectiveAvailableShells()
        val defaultShell = settings.getEffectiveDefaultShell()

        logger.info { "Dynamic shell config - available: $availableShells, default: $defaultShell" }

        val shellTypeEntries = shellTypeProperty.toMutableMap()
        shellTypeEntries["enum"] = JsonArray(availableShells.map { JsonPrimitive(it) })
        shellTypeEntries["default"] = JsonPrimitive(defaultShell)

        val isWindows = settings.isWindows()
        val platform = if (isWindows) "Windows" else "Unix"
        shellTypeEntries["description"] = JsonPrimitive(
            "Shell type. Platform: $platform. Available: ${availableShells.joinToString(", ")}. Default: $defaultShell"
        )

        val updatedShellType = JsonObject(shellTypeEntries)
        val propertiesEntries = properties.toMutableMap()
        propertiesEntries["shell_type"] = updatedShellType
        val updatedProperties = JsonObject(propertiesEntries)
        val terminalEntries = terminalSchema.toMutableMap()
        terminalEntries["properties"] = updatedProperties
        baseSchemas["Terminal"] = JsonObject(terminalEntries)

        return baseSchemas
    }

    /**
     * Get schema for a specific tool by name.
     */
    fun getToolSchema(toolName: String): JsonObject {
        return getToolSchemas()[toolName] ?: run {
            logger.warn { "Terminal tool schema not found: $toolName" }
            buildJsonObject { }
        }
    }

    /**
     * Property accessor for all tool schemas (for compatibility).
     */
    val TOOL_SCHEMAS: Map<String, JsonObject>
        get() = getToolSchemas()
}
