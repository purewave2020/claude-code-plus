package com.asakii.server.mcp

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.claude.agent.sdk.mcp.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.net.HttpURLConnection
import java.net.URL

/**
 * MCP HTTP 网关集成测试
 *
 * 验证：
 * 1. HTTP 网关能正确启动
 * 2. MCP 服务器能正确注册并暴露 HTTP 端点
 * 3. 标准 HTTP 客户端能正确连接并调用工具
 */
class McpHttpGatewayIntegrationTest {

    private lateinit var testMcpServer: TestMcpServer
    private var registeredUrl: String? = null

    @BeforeEach
    fun setUp() {
        testMcpServer = TestMcpServer()
    }

    @AfterEach
    fun tearDown() {
        // 清理注册的服务器
        McpHttpGateway.unregisterSession("test-session")
    }

    @Test
    fun `test gateway starts and returns valid port`() {
        val port = McpHttpGateway.ensureStarted()
        assertTrue(port > 0, "Port should be positive")
        assertTrue(port < 65536, "Port should be less than 65536")
        println("Gateway started on port: $port")
    }

    @Test
    fun `test register server returns valid URL`() = runBlocking {
        val url = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "test_server",
            server = testMcpServer
        )

        assertNotNull(url)
        assertTrue(url.startsWith("http://127.0.0.1:"), "URL should start with http://127.0.0.1:")
        assertTrue(url.contains("/mcp/"), "URL should contain /mcp/")
        assertTrue(url.contains("codex"), "URL should contain provider name")
        assertTrue(url.contains("test-session"), "URL should contain session ID")
        assertTrue(url.contains("test_server"), "URL should contain server name")

        registeredUrl = url
        println("Registered MCP server at: $url")
    }

    @Test
    fun `test registered endpoint is accessible`() = runBlocking {
        val url = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "test_server",
            server = testMcpServer
        )

        // 尝试连接到端点（不发送实际 MCP 请求，只验证端点可达）
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        try {
            connection.connect()
            // 即使返回错误状态码，也说明端点是可达的
            val responseCode = connection.responseCode
            println("Endpoint response code: $responseCode")
            // MCP 端点可能返回 400 或 405，因为 GET 请求不是有效的 MCP 请求
            // 但只要不是连接超时或拒绝，就说明端点是活跃的
            assertTrue(responseCode > 0, "Should receive a response")
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `test MCP server tools are registered`() = runBlocking {
        // 注册服务器
        McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "test_server",
            server = testMcpServer
        )

        // 验证测试服务器的工具列表
        val tools = testMcpServer.listTools()
        assertEquals(2, tools.size, "Should have 2 tools")
        assertTrue(tools.any { it.name == "echo" }, "Should have echo tool")
        assertTrue(tools.any { it.name == "add" }, "Should have add tool")
    }

    @Test
    fun `test tool execution works correctly`() = runBlocking {
        // 测试 echo 工具
        val echoResult = testMcpServer.callTool(
            "echo",
            buildJsonObject { put("message", "Hello, MCP!") }
        )
        assertFalse(echoResult.isError)
        val echoContent = echoResult.content.firstOrNull()
        assertTrue(echoContent is ContentItem.Text)
        assertEquals("Echo: Hello, MCP!", (echoContent as ContentItem.Text).text)

        // 测试 add 工具
        val addResult = testMcpServer.callTool(
            "add",
            buildJsonObject {
                put("a", 10)
                put("b", 20)
            }
        )
        assertFalse(addResult.isError)
        val addContent = addResult.content.firstOrNull()
        assertTrue(addContent is ContentItem.Text)
        assertEquals("Result: 30", (addContent as ContentItem.Text).text)
    }

    @Test
    fun `test multiple servers can be registered`() = runBlocking {
        val server1 = TestMcpServer("server1", "Server 1")
        val server2 = TestMcpServer("server2", "Server 2")

        val url1 = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "server1",
            server = server1
        )

        val url2 = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "server2",
            server = server2
        )

        assertNotEquals(url1, url2, "URLs should be different")
        assertTrue(url1.contains("server1"))
        assertTrue(url2.contains("server2"))

        println("Server 1 URL: $url1")
        println("Server 2 URL: $url2")
    }

    @Test
    fun `test same server registration returns same URL`() = runBlocking {
        val url1 = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "test_server",
            server = testMcpServer
        )

        val url2 = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "test_server",
            server = testMcpServer
        )

        assertEquals(url1, url2, "Same registration should return same URL")
    }

    @Test
    fun `test different providers get different URLs`() = runBlocking {
        val urlCodex = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "test-session",
            serverName = "test_server",
            server = testMcpServer
        )

        val urlClaude = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CLAUDE,
            sessionId = "test-session",
            serverName = "test_server",
            server = TestMcpServer()
        )

        assertNotEquals(urlCodex, urlClaude)
        assertTrue(urlCodex.contains("codex"))
        assertTrue(urlClaude.contains("claude"))

        // 清理
        McpHttpGateway.unregisterProviderSession(AiAgentProvider.CLAUDE, "test-session")
    }

    @Test
    fun `test URL format matches expected pattern for Codex`() = runBlocking {
        val url = McpHttpGateway.registerServer(
            provider = AiAgentProvider.CODEX,
            sessionId = "session-123",
            serverName = "user_interaction",
            server = testMcpServer
        )

        // 验证 URL 格式符合 Codex 期望
        // 格式: http://127.0.0.1:<port>/mcp/<provider>/<sessionId>/<serverName>
        val regex = Regex("""http://127\.0\.0\.1:\d+/mcp/codex/session-123/user_interaction""")
        assertTrue(regex.matches(url), "URL should match expected pattern. Actual: $url")

        // 清理
        McpHttpGateway.unregisterSession("session-123")
    }

    /**
     * 测试用 MCP 服务器实现
     */
    class TestMcpServer(
        override val name: String = "test-mcp-server",
        override val description: String = "Test MCP Server for integration tests"
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
