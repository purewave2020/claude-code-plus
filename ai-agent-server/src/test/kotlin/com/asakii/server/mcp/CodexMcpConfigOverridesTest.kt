package com.asakii.server.mcp

import com.asakii.claude.agent.sdk.types.McpHttpServerConfig
import com.asakii.claude.agent.sdk.types.McpServerSpec
import com.asakii.claude.agent.sdk.types.McpStdioServerConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 测试 Codex MCP 配置覆盖生成逻辑
 *
 * 验证 MCP 服务器配置能够正确转换为 Codex CLI 的 --config 参数格式
 */
class CodexMcpConfigOverridesTest {

    /**
     * 将 MCP 服务器配置转换为 Codex CLI 的配置覆盖格式
     * 复制自 AiAgentRpcServiceImpl.buildCodexMcpConfigOverrides
     */
    private fun buildCodexMcpConfigOverrides(mcpServers: Map<String, McpServerSpec>): Map<String, String> {
        if (mcpServers.isEmpty()) return emptyMap()

        val overrides = mutableMapOf<String, String>()
        mcpServers.forEach { (name, server) ->
            when (server) {
                is McpHttpServerConfig -> {
                    overrides["mcp_servers.$name.url"] = toTomlString(server.url)
                    if (server.headers.isNotEmpty()) {
                        overrides["mcp_servers.$name.http_headers"] = toTomlInlineTable(server.headers)
                    }
                }
                is McpStdioServerConfig -> {
                    overrides["mcp_servers.$name.command"] = toTomlString(server.command)
                    if (server.args.isNotEmpty()) {
                        overrides["mcp_servers.$name.args"] = toTomlArray(server.args)
                    }
                    if (server.env.isNotEmpty()) {
                        overrides["mcp_servers.$name.env"] = toTomlInlineTable(server.env)
                    }
                }
            }
        }
        return overrides
    }

    private fun toTomlArray(values: List<String>): String {
        return values.joinToString(prefix = "[", postfix = "]") { toTomlString(it) }
    }

    private fun toTomlInlineTable(entries: Map<String, String>): String {
        return entries.entries.joinToString(prefix = "{ ", postfix = " }") { (key, value) ->
            "${toTomlString(key)} = ${toTomlString(value)}"
        }
    }

    private fun toTomlString(value: String): String {
        return "\"${escapeTomlString(value)}\""
    }

    private fun escapeTomlString(value: String): String {
        val builder = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    @Test
    fun `test empty servers returns empty map`() {
        val result = buildCodexMcpConfigOverrides(emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test HTTP server generates correct url config`() {
        val servers = mapOf<String, McpServerSpec>(
            "user_interaction" to McpHttpServerConfig(
                url = "http://127.0.0.1:12345/mcp/codex/session1/user_interaction"
            )
        )

        val result = buildCodexMcpConfigOverrides(servers)

        assertEquals(1, result.size)
        assertEquals(
            "\"http://127.0.0.1:12345/mcp/codex/session1/user_interaction\"",
            result["mcp_servers.user_interaction.url"]
        )
    }

    @Test
    fun `test HTTP server with headers generates correct config`() {
        val servers = mapOf<String, McpServerSpec>(
            "context7" to McpHttpServerConfig(
                url = "https://mcp.context7.com/mcp",
                headers = mapOf(
                    "Authorization" to "Bearer token123",
                    "X-Custom-Header" to "value"
                )
            )
        )

        val result = buildCodexMcpConfigOverrides(servers)

        assertEquals(2, result.size)
        assertEquals("\"https://mcp.context7.com/mcp\"", result["mcp_servers.context7.url"])

        val headersValue = result["mcp_servers.context7.http_headers"]
        assertNotNull(headersValue)
        // 验证 headers 是 TOML inline table 格式
        assertTrue(headersValue!!.startsWith("{ "))
        assertTrue(headersValue.endsWith(" }"))
        assertTrue(headersValue.contains("\"Authorization\""))
        assertTrue(headersValue.contains("\"Bearer token123\""))
    }

    @Test
    fun `test stdio server generates correct command config`() {
        val servers = mapOf<String, McpServerSpec>(
            "my_server" to McpStdioServerConfig(
                command = "node",
                args = listOf("server.js", "--port", "3000"),
                env = mapOf("API_KEY" to "secret123")
            )
        )

        val result = buildCodexMcpConfigOverrides(servers)

        assertEquals(3, result.size)
        assertEquals("\"node\"", result["mcp_servers.my_server.command"])
        assertEquals("[\"server.js\", \"--port\", \"3000\"]", result["mcp_servers.my_server.args"])
        assertTrue(result["mcp_servers.my_server.env"]!!.contains("\"API_KEY\""))
        assertTrue(result["mcp_servers.my_server.env"]!!.contains("\"secret123\""))
    }

    @Test
    fun `test multiple servers generates all configs`() {
        val servers = mapOf<String, McpServerSpec>(
            "user_interaction" to McpHttpServerConfig(
                url = "http://127.0.0.1:12345/mcp/codex/session1/user_interaction"
            ),
            "jetbrains" to McpHttpServerConfig(
                url = "http://127.0.0.1:12345/mcp/codex/session1/jetbrains"
            ),
            "custom_stdio" to McpStdioServerConfig(
                command = "python",
                args = listOf("-m", "mcp_server")
            )
        )

        val result = buildCodexMcpConfigOverrides(servers)

        // user_interaction: 1 key (url)
        // jetbrains: 1 key (url)
        // custom_stdio: 2 keys (command, args)
        assertEquals(4, result.size)
        assertTrue(result.containsKey("mcp_servers.user_interaction.url"))
        assertTrue(result.containsKey("mcp_servers.jetbrains.url"))
        assertTrue(result.containsKey("mcp_servers.custom_stdio.command"))
        assertTrue(result.containsKey("mcp_servers.custom_stdio.args"))
    }

    @Test
    fun `test url with special characters is properly escaped`() {
        val servers = mapOf<String, McpServerSpec>(
            "test" to McpHttpServerConfig(
                url = "http://127.0.0.1:12345/mcp?query=test&value=1"
            )
        )

        val result = buildCodexMcpConfigOverrides(servers)

        // URL 中的 & 和 ? 不需要转义
        assertEquals(
            "\"http://127.0.0.1:12345/mcp?query=test&value=1\"",
            result["mcp_servers.test.url"]
        )
    }

    @Test
    fun `test command with quotes is properly escaped`() {
        val servers = mapOf<String, McpServerSpec>(
            "test" to McpStdioServerConfig(
                command = "node",
                args = listOf("--eval", "console.log(\"hello\")")
            )
        )

        val result = buildCodexMcpConfigOverrides(servers)

        // 引号应该被转义
        val argsValue = result["mcp_servers.test.args"]
        assertNotNull(argsValue)
        assertTrue(argsValue!!.contains("\\\"hello\\\""))
    }

    @Test
    fun `test config format matches Codex CLI expectations`() {
        // 验证生成的配置格式符合 Codex CLI 的 --config key=value 期望
        val servers = mapOf<String, McpServerSpec>(
            "user_interaction" to McpHttpServerConfig(
                url = "http://127.0.0.1:12345/mcp/codex/session1/user_interaction"
            )
        )

        val result = buildCodexMcpConfigOverrides(servers)

        // 键格式: mcp_servers.<name>.url
        assertTrue(result.keys.all { it.startsWith("mcp_servers.") })

        // 值格式: TOML 字符串 (带引号)
        assertTrue(result.values.all { it.startsWith("\"") && it.endsWith("\"") })
    }

    @Test
    fun `test simulated command line argument construction`() {
        // 模拟 CodexAppServerProcess.spawn 中的命令行参数构建
        val servers = mapOf<String, McpServerSpec>(
            "user_interaction" to McpHttpServerConfig(
                url = "http://127.0.0.1:12345/mcp/codex/session1/user_interaction"
            )
        )

        val configOverrides = buildCodexMcpConfigOverrides(servers)

        // 模拟构建命令行
        val command = mutableListOf("codex")
        configOverrides.forEach { (key, value) ->
            command.add("--config")
            command.add("$key=$value")
        }
        command.add("app-server")

        // 验证命令行格式
        assertEquals("codex", command[0])
        assertEquals("--config", command[1])
        assertTrue(command[2].startsWith("mcp_servers."))
        assertTrue(command[2].contains("="))
        assertEquals("app-server", command.last())

        println("Generated command: ${command.joinToString(" ")}")
    }
}
