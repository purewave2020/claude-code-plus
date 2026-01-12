package com.asakii.server.mcp

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.client.PermissionRequest
import com.asakii.ai.agent.sdk.client.PermissionRequester
import com.asakii.server.util.JsonTools
import com.asakii.claude.agent.sdk.mcp.ContentItem
import com.asakii.claude.agent.sdk.mcp.McpServer as SdkMcpServer
import com.asakii.claude.agent.sdk.mcp.ToolResult
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.asakii.logging.*
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private val logger = getLogger("McpHttpGateway")

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

    /**
     * MCP 工具权限检查上下文
     * 仅 Codex 模式使用
     */
    private data class PermissionContext(
        val permissionRequester: PermissionRequester,
        val allowedTools: Set<String>  // 格式: mcp__{serverName}__{toolName}
    )

    private val json = JsonTools.kotlinJson
    private val jsonMapper = JsonTools.mcpJsonMapper
    private val endpointsByKey = ConcurrentHashMap<EndpointKey, Endpoint>()
    private val lifecycleLock = Any()
    private var jettyServer: Server? = null
    private var actualPort: Int = 0

    const val HEADER_SESSION_ID = "X-Session-Id"
    const val HEADER_PROVIDER = "X-Provider"

    private class GatewayServlet(
        private val resolver: (String, String?, String?) -> HttpServletStreamableServerTransportProvider?
    ) : HttpServlet() {
        override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
            val path = req.requestURI ?: ""
            val sessionId = req.getHeader(HEADER_SESSION_ID)?.takeIf { it.isNotBlank() }
            val providerName = req.getHeader(HEADER_PROVIDER)?.takeIf { it.isNotBlank() }
            logger.debug { "[MCP] Incoming request: ${req.method} $path (sessionId=$sessionId, provider=$providerName)" }
            val transport = resolver(path, sessionId, providerName)
            if (transport == null) {
                logger.warn { "[MCP] No transport found for path: $path, sessionId=$sessionId, provider=$providerName" }
                resp.sendError(HttpServletResponse.SC_NOT_FOUND)
                return
            }
            logger.debug { "[MCP] Routing to transport for path: $path" }
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
            context.addServlet(ServletHolder(GatewayServlet(::resolveTransportByRequest)), "/mcp/*")
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
        server: SdkMcpServer,
        permissionRequester: PermissionRequester? = null,
        allowedTools: Set<String> = emptySet()
    ): String {
        ensureStarted()
        val key = EndpointKey(provider, sessionId, serverName)
        endpointsByKey[key]?.let { return buildUrl(it.path) }

        val endpointPath = buildEndpointPath(serverName)
        val transport = HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .mcpEndpoint(endpointPath)
            .build()

        val serverBuilder = OfficialMcpServer.sync(transport)
            .serverInfo(server.name, server.version)
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .jsonMapper(JsonTools.mcpJsonMapper)
            .jsonSchemaValidator(JsonTools.mcpJsonSchemaValidator)

        server.getSystemPromptAppendix()
            ?.takeIf { it.isNotBlank() }
            ?.let { serverBuilder.instructions(it) }

        server.timeout?.takeIf { it > 0 }?.let {
            serverBuilder.requestTimeout(Duration.ofMillis(it))
        }

        val syncServer = serverBuilder.build()
        
        // 构建权限上下文（仅 Codex 需要）
        val permissionContext = if (provider == AiAgentProvider.CODEX && permissionRequester != null) {
            PermissionContext(permissionRequester, allowedTools)
        } else null
        
        registerTools(syncServer, server, permissionContext)

        val endpoint = Endpoint(endpointPath, syncServer, transport)
        endpointsByKey[key] = endpoint
        logger.info { "[MCP] Registered endpoint $endpointPath (provider=$provider, session=$sessionId, key=$key)" }
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
        logger.info { "[MCP] Unregistering endpoint (key=$key)" }
        runCatching { endpoint.server.closeGracefully() }
            .onFailure { logger.warn(it) { "[MCP] Failed to close endpoint ${endpoint.path}" } }
    }

    /**
     * 根据请求中的 X-Session-Id 和 X-Provider 解析 transport。
     * URL 格式: /mcp/{serverName}
     */
    private fun resolveTransportByRequest(
        path: String,
        sessionId: String?,
        providerName: String?
    ): HttpServletStreamableServerTransportProvider? {
        val normalized = path.trimEnd('/')
        // 从 /mcp/{serverName} 提取 serverName
        val serverName = normalized.removePrefix("/mcp/").takeIf { it.isNotBlank() } ?: return null
        
        // 如果没有会话信息，无法路由
        if (sessionId.isNullOrBlank() || providerName.isNullOrBlank()) {
            logger.warn { "[MCP] Missing required session info: sessionId=$sessionId, provider=$providerName" }
            return null
        }
        
        val provider = try {
            AiAgentProvider.valueOf(providerName.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn { "[MCP] Invalid provider: $providerName" }
            return null
        }
        
        val key = EndpointKey(provider, sessionId, serverName)
        return endpointsByKey[key]?.transport
    }

    private suspend fun registerTools(
        syncServer: McpSyncServer,
        server: SdkMcpServer,
        permissionContext: PermissionContext?
    ) {
        val tools = server.listTools()
        logger.info { "[MCP] Registering ${tools.size} tool(s) for server '${server.name}': ${tools.joinToString { it.name }}" }
        
        for (toolDef in tools) {
            val schemaJson = json.encodeToString(JsonObject.serializer(), toolDef.inputSchema)
            val tool = McpSchema.Tool.builder()
                .name(toolDef.name)
                .description(toolDef.description)
                .inputSchema(jsonMapper, schemaJson)
                .build()
            val fullToolName = "mcp__${server.name}__${toolDef.name}"

            val spec = McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler { _, request ->
                    runBlocking {
                        val jsonArgs = toJsonObject(request.arguments())
                        checkPermission(permissionContext, fullToolName, jsonArgs)
                            ?: server.callToolJson(toolDef.name, jsonArgs)
                    }.let(::toCallToolResult)
                }
                .build()
            syncServer.addTool(spec)
        }
    }

    /**
     * Codex 模式下检查 MCP 工具权限
     * @return 权限拒绝时返回错误结果，允许时返回 null
     */
    private suspend fun checkPermission(
        ctx: PermissionContext?,
        toolName: String,
        args: JsonObject
    ): ToolResult? {
        if (ctx == null || toolName in ctx.allowedTools) return null
        
        logger.debug { "[MCP] Requesting permission for $toolName" }
        val decision = ctx.permissionRequester.requestPermission(
            PermissionRequest(toolName = toolName, inputJson = args, toolUseId = null)
        )
        
        if (decision.approved) {
            logger.debug { "[MCP] Permission granted for $toolName" }
            return null
        }
        
        val reason = decision.denyReason ?: "User denied permission"
        logger.info { "[MCP] Permission denied for $toolName: $reason" }
        return ToolResult.error("Permission denied: $reason")
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
        return McpSchema.CallToolResult.builder()
            .content(contents)
            .isError(result.isError)
            .build()
    }

    /**
     * 构建端点路径。现在只用 serverName，会话信息通过 header 传递。
     */
    private fun buildEndpointPath(serverName: String): String {
        return "/mcp/$serverName"
    }

    private fun buildUrl(path: String): String = "http://127.0.0.1:$actualPort$path"

    /**
     * 获取 MCP 网关端口（启动后可用）
     */
    fun getPort(): Int = actualPort

    /**
     * 构建指定 serverName 的 MCP URL。
     */
    fun buildServerUrl(serverName: String): String {
        ensureStarted()
        val baseUrl = buildUrl(buildEndpointPath(serverName))
        return baseUrl
    }
}
