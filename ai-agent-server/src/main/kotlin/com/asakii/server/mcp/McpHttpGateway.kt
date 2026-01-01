package com.asakii.server.mcp

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.claude.agent.sdk.mcp.ContentItem
import com.asakii.claude.agent.sdk.mcp.McpServer as SdkMcpServer
import com.asakii.claude.agent.sdk.mcp.ToolResult
import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.server.McpServer as OfficialMcpServer
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

object McpHttpGateway {
    private data class EndpointKey(
        val provider: AiAgentProvider,
        val sessionId: String,
        val serverName: String
    )

    private data class Endpoint(
        val path: String,
        val server: McpSyncServer,
        val transport: HttpServletStreamableServerTransportProvider
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMapper: McpJsonMapper by lazy { createJsonMapper() }
    private val endpointsByKey = ConcurrentHashMap<EndpointKey, Endpoint>()
    private val endpointsByPath = ConcurrentHashMap<String, Endpoint>()
    private val lifecycleLock = Any()
    private var jettyServer: Server? = null
    private var actualPort: Int = 0

    private class GatewayServlet(
        private val resolver: (String) -> HttpServletStreamableServerTransportProvider?
    ) : HttpServlet() {
        override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
            val path = req.requestURI ?: ""
            val transport = resolver(path)
            if (transport == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }
            transport.service(req, resp)
        }
    }

    fun ensureStarted(): Int {
        synchronized(lifecycleLock) {
            if (jettyServer != null) return actualPort

            val server = Server()
            val connector = ServerConnector(server)
            connector.host = "127.0.0.1"
            connector.port = 0
            server.addConnector(connector)

            val context = ServletContextHandler(ServletContextHandler.NO_SESSIONS)
            context.contextPath = "/"
            context.addServlet(ServletHolder(GatewayServlet(::resolveTransport)), "/mcp/*")
            server.handler = context

            server.start()
            actualPort = connector.localPort
            jettyServer = server
            logger.info { "[MCP] HTTP gateway started on http://127.0.0.1:$actualPort/mcp" }
            return actualPort
        }
    }

    suspend fun registerServer(
        provider: AiAgentProvider,
        sessionId: String,
        serverName: String,
        server: SdkMcpServer
    ): String {
        ensureStarted()
        val key = EndpointKey(provider, sessionId, serverName)
        endpointsByKey[key]?.let { return buildUrl(it.path) }

        val endpointPath = buildEndpointPath(provider, sessionId, serverName)
        val transport = HttpServletStreamableServerTransportProvider.builder()
            .mcpEndpoint(endpointPath)
            .build()

        val serverBuilder = OfficialMcpServer.sync(transport)
            .serverInfo(server.name, server.version)
            .capabilities(ServerCapabilities.builder().tools(true).build())

        server.getSystemPromptAppendix()
            ?.takeIf { it.isNotBlank() }
            ?.let { serverBuilder.instructions(it) }

        server.timeout?.takeIf { it > 0 }?.let {
            serverBuilder.requestTimeout(Duration.ofMillis(it))
        }

        val syncServer = serverBuilder.build()
        registerTools(syncServer, server)

        val endpoint = Endpoint(endpointPath, syncServer, transport)
        endpointsByKey[key] = endpoint
        endpointsByPath[endpointPath] = endpoint
        logger.info { "[MCP] Registered endpoint $endpointPath (provider=$provider, session=$sessionId)" }
        return buildUrl(endpointPath)
    }

    fun unregisterSession(sessionId: String) {
        val keys = endpointsByKey.keys.filter { it.sessionId == sessionId }
        keys.forEach { unregister(it) }
    }

    fun unregisterProviderSession(provider: AiAgentProvider, sessionId: String) {
        val keys = endpointsByKey.keys.filter { it.sessionId == sessionId && it.provider == provider }
        keys.forEach { unregister(it) }
    }

    private fun unregister(key: EndpointKey) {
        val endpoint = endpointsByKey.remove(key) ?: return
        endpointsByPath.remove(endpoint.path)
        runCatching { endpoint.server.closeGracefully() }
            .onFailure { logger.warn(it) { "[MCP] Failed to close endpoint ${endpoint.path}" } }
    }

    private fun resolveTransport(path: String): HttpServletStreamableServerTransportProvider? {
        val normalized = path.trimEnd('/')
        return endpointsByPath[normalized]?.transport
    }

    private fun createJsonMapper(): McpJsonMapper {
        val loader = McpHttpGateway::class.java.classLoader
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        return try {
            thread.contextClassLoader = loader
            McpJsonMapper.getDefault()
        } finally {
            thread.contextClassLoader = previous
        }
    }

    private suspend fun registerTools(syncServer: McpSyncServer, server: SdkMcpServer) {
        val tools = server.listTools()
        tools.forEach { toolDef ->
            val schemaJson = json.encodeToString(JsonObject.serializer(), toolDef.inputSchema)
            val tool = McpSchema.Tool.builder()
                .name(toolDef.name)
                .description(toolDef.description)
                .inputSchema(jsonMapper, schemaJson)
                .build()

            val spec = McpServerFeatures.SyncToolSpecification(tool) { _, arguments ->
                runBlocking {
                    val jsonArgs = toJsonObject(arguments)
                    val result = server.callToolJson(toolDef.name, jsonArgs)
                    toCallToolResult(result)
                }
            }
            syncServer.addTool(spec)
        }
    }

    private fun toJsonObject(arguments: Map<String, Any>): JsonObject {
        if (arguments.isEmpty()) return JsonObject(emptyMap())
        val jsonText = jsonMapper.writeValueAsString(arguments)
        val parsed = json.parseToJsonElement(jsonText)
        return parsed as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun toCallToolResult(result: ToolResult): McpSchema.CallToolResult {
        val contents = result.content.map { item ->
            when (item) {
                is ContentItem.Text -> McpSchema.TextContent(item.text)
                is ContentItem.Json -> {
                    val text = json.encodeToString(JsonElement.serializer(), item.data)
                    McpSchema.TextContent(text)
                }
                is ContentItem.Binary -> {
                    val encoded = Base64.getEncoder().encodeToString(item.data)
                    if (item.mimeType.startsWith("image/")) {
                        McpSchema.ImageContent(null, encoded, item.mimeType)
                    } else {
                        McpSchema.TextContent(encoded)
                    }
                }
            }
        }
        return McpSchema.CallToolResult(contents, result.isError)
    }

    private fun buildEndpointPath(
        provider: AiAgentProvider,
        sessionId: String,
        serverName: String
    ): String {
        val providerKey = provider.name.lowercase()
        return "/mcp/$providerKey/$sessionId/$serverName"
    }

    private fun buildUrl(path: String): String = "http://127.0.0.1:$actualPort$path"
}
