package com.asakii.server.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer

/**
 * Result of terminal run-to-background operation.
 */
data class TerminalBackgroundResult(
    val success: Boolean,
    val backgroundedIds: List<String> = emptyList(),
    val count: Int = 0,
    val error: String? = null
)

interface TerminalMcpServerProvider {
    fun getServer(): McpServer?

    fun getServerForSession(aiSessionId: String): McpServer? = getServer()

    fun getDisallowedBuiltinTools(): List<String> = emptyList()

    /** 获取启用此 MCP 时需要禁用的 Codex features（如 "shell_tool"） */
    fun getCodexDisabledFeatures(): List<String> = emptyList()

    fun setCurrentAiSession(aiSessionId: String?) {}

    fun disposeSession(aiSessionId: String?) {}

    /**
     * Move terminal tasks to background.
     *
     * @param toolUseId Optional specific tool use ID. If null, backgrounds all running tasks.
     * @return Result with list of backgrounded task IDs
     */
    fun runToBackground(toolUseId: String? = null): TerminalBackgroundResult =
        TerminalBackgroundResult(success = false, error = "Not implemented")
}

object DefaultTerminalMcpServerProvider : TerminalMcpServerProvider {
    override fun getServer(): McpServer? = null
    override fun getDisallowedBuiltinTools(): List<String> = emptyList()
    override fun getCodexDisabledFeatures(): List<String> = emptyList()
}
