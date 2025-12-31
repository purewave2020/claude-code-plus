package com.asakii.server.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer

interface TerminalMcpServerProvider {
    fun getServer(): McpServer?

    fun getServerForSession(aiSessionId: String): McpServer? = getServer()

    fun getDisallowedBuiltinTools(): List<String> = emptyList()

    fun setCurrentAiSession(aiSessionId: String?) {}

    fun disposeSession(aiSessionId: String?) {}
}

object DefaultTerminalMcpServerProvider : TerminalMcpServerProvider {
    override fun getServer(): McpServer? = null
    override fun getDisallowedBuiltinTools(): List<String> = emptyList()
}
