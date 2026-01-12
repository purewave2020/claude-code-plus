package com.asakii.plugin.mcp

import com.asakii.settings.AgentSettingsService

/**
 * Shared configuration object for Terminal MCP feature flags.
 * Used by both TerminalMcpServerImpl and TerminalMcpServerProviderImpl.
 */
object TerminalFeatureConfig {

    /**
     * Get list of builtin tools that should be disabled when Terminal MCP is enabled.
     * Currently disables the builtin "Bash" tool when terminalDisableBuiltinBash is true.
     *
     * @return List of tool names to disable (e.g., ["Bash"])
     */
    fun getDisallowedBuiltinTools(): List<String> {
        val settings = AgentSettingsService.getInstance()
        return if (settings.enableTerminalMcp && settings.terminalDisableBuiltinBash) {
            listOf("Bash")
        } else {
            emptyList()
        }
    }

    /**
     * Get list of Codex features that should be disabled when Terminal MCP is enabled.
     * Currently disables "shell_tool" when terminalDisableBuiltinBash is true.
     *
     * @return List of feature names to disable (e.g., ["shell_tool"])
     */
    fun getCodexDisabledFeatures(): List<String> {
        val settings = AgentSettingsService.getInstance()
        return if (settings.enableTerminalMcp && settings.terminalDisableBuiltinBash) {
            listOf("shell_tool")
        } else {
            emptyList()
        }
    }
}
