package com.asakii.server.mcp

import com.asakii.codex.agent.sdk.appserver.CodexAppServerClient
import com.asakii.codex.agent.sdk.appserver.CodexAppServerException
import com.asakii.server.util.JsonTools
import io.modelcontextprotocol.server.McpServer as OfficialMcpServer
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector

class CodexHttpMcpQueryIntegrationTest {
    private data class RequestSnapshot(
        val query: String?,
        val sessionHeader: String?,
        val providerHeader: String?,
        val customHeader: String?
    )

    @Test
    fun codexUsesHttpHeadersForHttpMcp() = runBlocking {
        val captured = CompletableDeferred<RequestSnapshot>()
        val endpointPath = "/mcp/test"

        val transport = HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(JsonTools.mcpJsonMapper)
            .mcpEndpoint(endpointPath)
            .build()

        createMcpServer(transport)

        val jetty = Server()
        val connector = ServerConnector(jetty).apply {
            host = "127.0.0.1"
            port = 0
        }
        jetty.addConnector(connector)

        val context = ServletContextHandler(ServletContextHandler.NO_SESSIONS).apply {
            contextPath = "/"
            addServlet(
                ServletHolder(object : HttpServlet() {
                    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
                        if (!captured.isCompleted && req.requestURI.startsWith(endpointPath)) {
                            captured.complete(
                                RequestSnapshot(
                                    query = req.queryString,
                                    sessionHeader = req.getHeader("X-Session-Id"),
                                    providerHeader = req.getHeader("X-Provider"),
                                    customHeader = req.getHeader("X-Test")
                                )
                            )
                        }
                        transport.service(req, resp)
                    }
                }),
                "/mcp/*"
            )
        }
        jetty.handler = context
        jetty.start()

        val port = connector.localPort
        val url = "http://127.0.0.1:$port$endpointPath"

        val client = try {
            CodexAppServerClient.create(
                configOverrides = mapOf(
                    "mcp_servers.test.url" to "\"$url\"",
                    "mcp_servers.test.http_headers.X-Session-Id" to "\"sid-123\"",
                    "mcp_servers.test.http_headers.X-Provider" to "\"CODEX\"",
                    "mcp_servers.test.startup_timeout_sec" to "10"
                )
            )
        } catch (e: CodexAppServerException) {
            println("Codex not found: ${e.message}")
            println("Please install: npm install -g @openai/codex")
            jetty.stop()
            return@runBlocking
        }

        try {
            client.initialize(clientName = "codex-mcp-query-integration-test")
            client.listMcpServerStatus()

            val snapshot = withTimeout(10.seconds) { captured.await() }
            println(
                "Captured MCP request: query=${snapshot.query}, " +
                    "X-Session-Id=${snapshot.sessionHeader}, " +
                    "X-Provider=${snapshot.providerHeader}"
            )
            assertTrue(snapshot.query.isNullOrBlank())
            assertTrue(snapshot.sessionHeader == "sid-123")
            assertTrue(snapshot.providerHeader == "CODEX")
        } finally {
            client.close()
            jetty.stop()
        }
    }

    @Test
    fun codexSendsHttpHeadersToMcpServer() = runBlocking {
        val captured = CompletableDeferred<RequestSnapshot>()
        val endpointPath = "/mcp/test"

        val transport = HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(JsonTools.mcpJsonMapper)
            .mcpEndpoint(endpointPath)
            .build()

        createMcpServer(transport)

        val jetty = Server()
        val connector = ServerConnector(jetty).apply {
            host = "127.0.0.1"
            port = 0
        }
        jetty.addConnector(connector)

        val context = ServletContextHandler(ServletContextHandler.NO_SESSIONS).apply {
            contextPath = "/"
            addServlet(
                ServletHolder(object : HttpServlet() {
                    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
                        if (!captured.isCompleted && req.requestURI.startsWith(endpointPath)) {
                            captured.complete(
                                RequestSnapshot(
                                    query = req.queryString,
                                    sessionHeader = req.getHeader("X-Session-Id"),
                                    providerHeader = req.getHeader("X-Provider"),
                                    customHeader = req.getHeader("X-Test")
                                )
                            )
                        }
                        transport.service(req, resp)
                    }
                }),
                "/mcp/*"
            )
        }
        jetty.handler = context
        jetty.start()

        val port = connector.localPort
        val url = "http://127.0.0.1:$port$endpointPath"

        val client = try {
            CodexAppServerClient.create(
                configOverrides = mapOf(
                    "mcp_servers.test.url" to "\"$url\"",
                    "mcp_servers.test.http_headers.X-Test" to "\"hello\"",
                    "mcp_servers.test.startup_timeout_sec" to "10"
                )
            )
        } catch (e: CodexAppServerException) {
            println("Codex not found: ${e.message}")
            println("Please install: npm install -g @openai/codex")
            jetty.stop()
            return@runBlocking
        }

        try {
            client.initialize(clientName = "codex-mcp-header-integration-test")
            client.listMcpServerStatus()

            val snapshot = withTimeout(10.seconds) { captured.await() }
            println("Captured MCP header: X-Test=${snapshot.customHeader}")
            assertTrue(snapshot.customHeader == "hello")
        } finally {
            client.close()
            jetty.stop()
        }
    }

    private fun createMcpServer(
        transport: HttpServletStreamableServerTransportProvider
    ): McpSyncServer {
        val server = OfficialMcpServer.sync(transport)
            .serverInfo("test-mcp", "0.1.0")
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .jsonMapper(JsonTools.mcpJsonMapper)
            .jsonSchemaValidator(JsonTools.mcpJsonSchemaValidator)
            .build()

        val toolSchema = """{"type":"object","properties":{}}"""
        val tool = McpSchema.Tool.builder()
            .name("ping")
            .description("ping")
            .inputSchema(JsonTools.mcpJsonMapper, toolSchema)
            .build()

        val spec = McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler { _, _ ->
                McpSchema.CallToolResult.builder()
                    .content(listOf(McpSchema.TextContent("pong")))
                    .isError(false)
                    .build()
            }
            .build()
        server.addTool(spec)
        return server
    }
}
