package com.asakii.server.mcp

/**
 * Encapsulates all MCP Server Providers.
 *
 * This data class groups all MCP providers together to simplify constructor
 * parameter passing through the call chain:
 * HttpApiServer -> RSocketHandler -> AiAgentRpcServiceImpl
 *
 * Adding a new MCP Provider:
 * 1. Add a new field to this data class
 * 2. Update HttpServerProjectService to provide the implementation
 * No need to modify HttpApiServer, RSocketHandler, or AiAgentRpcServiceImpl constructors.
 */
data class McpProviders(
    val jetBrains: JetBrainsMcpServerProvider = DefaultJetBrainsMcpServerProvider,
    val jetBrainsFile: JetBrainsFileMcpServerProvider = DefaultJetBrainsFileMcpServerProvider,
    val terminal: TerminalMcpServerProvider = DefaultTerminalMcpServerProvider,
    val git: GitMcpServerProvider = DefaultGitMcpServerProvider
) {
    companion object {
        /**
         * Default instance using all default (no-op) providers.
         * Used when running in browser mode or standalone server.
         */
        val DEFAULT = McpProviders()
    }
}
