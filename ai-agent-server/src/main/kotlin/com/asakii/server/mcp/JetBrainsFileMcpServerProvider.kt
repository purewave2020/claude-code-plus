package com.asakii.server.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer

/**
 * JetBrains File MCP 服务器提供者接口
 *
 * 提供独立的文件操作 MCP 服务器（ReadFile, WriteFile, EditFile）。
 * 由于需要访问 IDEA Platform API，实现类必须在 jetbrains-plugin 模块中创建。
 */
interface JetBrainsFileMcpServerProvider {
    /**
     * 获取 JetBrains File MCP 服务器实例
     * @return McpServer 实例，如果不可用则返回 null
     */
    fun getServer(): McpServer?

    /**
     * 获取需要禁用的内置工具列表
     * 当 JetBrains File MCP 启用时，禁用内置的 Read、Write、Edit 工具
     * @return 需要禁用的工具名称列表
     */
    fun getDisallowedBuiltinTools(): List<String> = emptyList()
}

/**
 * 默认实现（不支持 JetBrains 集成时使用）
 */
object DefaultJetBrainsFileMcpServerProvider : JetBrainsFileMcpServerProvider {
    override fun getServer(): McpServer? = null
    override fun getDisallowedBuiltinTools(): List<String> = emptyList()
}
