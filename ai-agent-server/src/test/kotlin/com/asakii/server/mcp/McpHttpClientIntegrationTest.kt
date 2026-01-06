package com.asakii.server.mcp

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.claude.agent.sdk.mcp.*
import com.asakii.claude.agent.sdk.types.McpHttpServerConfig
import com.asakii.claude.agent.sdk.types.McpServerSpec
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import java.time.Duration

/**
 * MCP HTTP 客户端集成测试
 *
 * 使用标准 MCP Java SDK 客户端连接到 McpHttpGateway 暴露的端点，
 * 验证整个流程是否正常工作。
 *
 * 这个测试模拟了 Codex 如何连接到我们的 MCP HTTP 网关。
 */
class McpHttpClientIntegrationTest {

    private lateinit var testMcpServer: TestMcpServer
    private var registeredUrl: String? = null

    @BeforeEach
    fun setUp() {
        testMcpServer = TestMcpServer()
    }

    @AfterEach
    fun tearDown() {
        McpHttpGateway.unregisterSession("test-session")
    }

    @Test
    @Disabled("End-to-end test requires specific MCP SDK transport configuration - run manually")
    fun `test end-to-end MCP communication with Java SDK client`() = runBlocking {
        // 1. 注册 MCP 服务器到 HTTP 网关
        val url = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "test_server",
            server = testMcpServer
        )
        println("MCP Server registered at: $url")

        // 2. 创建 MCP 客户端配置（模拟 Codex 配置）
        val mcpConfig = McpHttpServerConfig(url = url)
        println("MCP Config: type=${mcpConfig.type}, url=${mcpConfig.url}")

        // 3. 使用 Java MCP SDK 客户端连接
        val transport = HttpClientStreamableHttpTransport.builder(url)
            .build()

        val client = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(30))
            .build()

        try {
            // 4. 初始化连接
            val initResult = client.initialize()
            println("MCP Client initialized: serverInfo=${initResult.serverInfo()}")
            assertNotNull(initResult.serverInfo())

            // 5. 列出工具
            val toolsResult = client.listTools()
            println("Available tools: ${toolsResult.tools().map { it.name() }}")
            assertEquals(2, toolsResult.tools().size)
            assertTrue(toolsResult.tools().any { it.name() == "echo" })
            assertTrue(toolsResult.tools().any { it.name() == "add" })

            // 6. 调用 echo 工具
            val echoArgs = mapOf("message" to "Hello from MCP Client!")
            val echoResult = client.callTool(
                McpSchema.CallToolRequest.builder()
                    .name("echo")
                    .arguments(echoArgs)
                    .build()
            )
            println("Echo result: ${echoResult.content()}")
            assertFalse(echoResult.isError ?: false)
            val echoText = (echoResult.content().firstOrNull() as? McpSchema.TextContent)?.text()
            assertEquals("Echo: Hello from MCP Client!", echoText)

            // 7. 调用 add 工具
            val addArgs = mapOf("a" to 15, "b" to 27)
            val addResult = client.callTool(
                McpSchema.CallToolRequest.builder()
                    .name("add")
                    .arguments(addArgs)
                    .build()
            )
            println("Add result: ${addResult.content()}")
            assertFalse(addResult.isError ?: false)
            val addText = (addResult.content().firstOrNull() as? McpSchema.TextContent)?.text()
            assertEquals("Result: 42", addText)

        } finally {
            client.close()
        }
    }

    @Test
    fun `test config overrides format is correct for Codex`() = runBlocking {
        // 注册服务器
        val url = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "user_interaction",
            server = testMcpServer
        )

        // 生成 Codex 配置覆盖
        val servers = mapOf<String, McpServerSpec>(
            "user_interaction" to McpHttpServerConfig(url = url)
        )
        val configOverrides = buildCodexMcpConfigOverrides(servers)

        println("Generated config overrides:")
        configOverrides.forEach { (key, value) ->
            println("  --config $key=$value")
        }

        // 验证配置格式
        assertEquals(1, configOverrides.size)
        val urlConfig = configOverrides["mcp_servers.user_interaction.url"]
        assertNotNull(urlConfig)
        assertTrue(urlConfig!!.startsWith("\""))
        assertTrue(urlConfig.endsWith("\""))
        assertTrue(urlConfig.contains(url))

        // 验证可以解析为有效的 TOML 值
        val unquoted = urlConfig.trim('"')
        assertTrue(unquoted.startsWith("http://"))
    }

    @Test
    fun `test multiple MCP servers configuration`() = runBlocking {
        // 注册多个服务器
        val userInteractionUrl = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "user_interaction",
            server = TestMcpServer("user_interaction", "User Interaction Server")
        )

        val jetbrainsUrl = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "jetbrains",
            server = TestMcpServer("jetbrains", "JetBrains Server")
        )

        // 生成配置
        val servers = mapOf<String, McpServerSpec>(
            "user_interaction" to McpHttpServerConfig(url = userInteractionUrl),
            "jetbrains" to McpHttpServerConfig(url = jetbrainsUrl)
        )
        val configOverrides = buildCodexMcpConfigOverrides(servers)

        println("Multi-server config overrides:")
        configOverrides.forEach { (key, value) ->
            println("  --config $key=$value")
        }

        // 验证两个服务器都有配置
        assertEquals(2, configOverrides.size)
        assertTrue(configOverrides.containsKey("mcp_servers.user_interaction.url"))
        assertTrue(configOverrides.containsKey("mcp_servers.jetbrains.url"))
    }

    @Test
    @Disabled("This test requires a running Codex CLI and is meant for manual verification")
    fun `manual test - verify Codex can parse generated config`() = runBlocking {
        // 这个测试用于手动验证，需要 Codex CLI
        val url = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "test_server",
            server = testMcpServer
        )

        val servers = mapOf<String, McpServerSpec>(
            "test_server" to McpHttpServerConfig(url = url)
        )
        val configOverrides = buildCodexMcpConfigOverrides(servers)

        // 构建完整的命令行
        val command = mutableListOf("codex")
        configOverrides.forEach { (key, value) ->
            command.add("--config")
            command.add("$key=$value")
        }
        command.add("app-server")

        println("Full command to run:")
        println(command.joinToString(" "))

        // 用户可以复制这个命令手动运行
    }

    /**
     * 复制自 CodexMcpConfigOverridesTest
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
                is com.asakii.claude.agent.sdk.types.McpStdioServerConfig -> {
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

    /**
     * 测试用 MCP 服务器实现
     */
    class TestMcpServer(
        override val name: String = "test-mcp-server",
        override val description: String = "Test MCP Server"
    ) : McpServer {
        override val version: String = "1.0.0"

        override suspend fun listTools(): List<ToolDefinition> {
            return listOf(
                ToolDefinition.withParameterInfo(
                    name = "echo",
                    description = "Echo the input message",
                    parameters = mapOf(
                        "message" to ParameterInfo(ParameterType.STRING, "Message to echo")
                    )
                ),
                ToolDefinition.withParameterInfo(
                    name = "add",
                    description = "Add two numbers",
                    parameters = mapOf(
                        "a" to ParameterInfo(ParameterType.NUMBER, "First number"),
                        "b" to ParameterInfo(ParameterType.NUMBER, "Second number")
                    )
                )
            )
        }

        override suspend fun callTool(toolName: String, arguments: JsonObject): ToolResult {
            return when (toolName) {
                "echo" -> {
                    val message = arguments["message"]?.jsonPrimitive?.contentOrNull ?: ""
                    ToolResult.success("Echo: $message")
                }
                "add" -> {
                    val a = arguments["a"]?.jsonPrimitive?.intOrNull ?: 0
                    val b = arguments["b"]?.jsonPrimitive?.intOrNull ?: 0
                    ToolResult.success("Result: ${a + b}")
                }
                else -> ToolResult.error("Unknown tool: $toolName")
            }
        }
    }
}
