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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private val logger = getLogger("McpHttpGateway")

object McpHttpGateway {
    /**
     * 局部 MCP 端点路由键（每个 tab 独立）
     * @param provider AI 提供商
     * @param connectId 前端永久连接标识（tabId）
     * @param serverName MCP 服务器名称
     */
    private data class SessionEndpointKey(
        val provider: AiAgentProvider,
        val connectId: String,
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
    
    /** 全局 MCP 端点：所有 tab 共享，key = serverName */
    private val globalEndpoints = ConcurrentHashMap<String, Endpoint>()
    
    /** 局部 MCP 端点：每个 tab 独立，key = (provider, connectId, serverName) */
    private val sessionEndpoints = ConcurrentHashMap<SessionEndpointKey, Endpoint>()
    private val lifecycleLock = Any()
    private var jettyServer: Server? = null
    private var actualPort: Int = 0

    /** MCP 路由参数名称（会话级路由信息通过 query 参数传递） */
    const val QUERY_CONNECT_ID = "connectId"
    const val QUERY_PROVIDER = "provider"

    private class GatewayServlet(
        private val resolver: (String, String?, String?) -> HttpServletStreamableServerTransportProvider?
    ) : HttpServlet() {
        override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
            val path = req.requestURI ?: ""
            // MCP 会话级路由信息：只通过 query 参数传递（避免依赖客户端支持自定义 header）。
            val connectId = req.getParameter(QUERY_CONNECT_ID)?.takeIf { it.isNotBlank() }
            val providerName = req.getParameter(QUERY_PROVIDER)?.takeIf { it.isNotBlank() }
            logger.debug { "[MCP] Incoming request: ${req.method} $path (connectId=$connectId, provider=$providerName)" }

            val transport = resolver(path, connectId, providerName)
            if (transport == null) {
                logger.warn {
                    "[MCP] No transport found for path: $path, connectId=$connectId, provider=$providerName, query=${req.queryString}"
                }
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

    /**
     * Global MCP 服务使用的 connectId 常量
     */
    const val GLOBAL_CONNECT_ID = "__global__"
    
    /**
     * 注册 MCP 服务器
     * @param provider AI 提供商
     * @param connectId 前端永久连接标识（全局服务传 GLOBAL_CONNECT_ID，局部服务传 tabId）
     * @param serverName MCP 服务器名称
     * @param server MCP 服务器实例
     * @param permissionRequester 权限请求器（仅 Codex 需要）
     * @param allowedTools 允许的工具列表
     */
    suspend fun registerServer(
        provider: AiAgentProvider,
        connectId: String,
        serverName: String,
        server: SdkMcpServer,
        permissionRequester: PermissionRequester? = null,
        allowedTools: Set<String> = emptySet()
    ): String {
        ensureStarted()
        val isGlobal = (connectId == GLOBAL_CONNECT_ID)
        
        // 全局服务：所有 tab 共享，只用 serverName 作为 key
        if (isGlobal) {
            val existing = globalEndpoints[serverName]
            if (existing != null) {
                logger.info { "[MCP] Reusing global endpoint: $serverName" }
                return buildUrl(existing.path)
            }
        } else {
            // 局部服务：每个 tab 独立，替换旧端点
            val key = SessionEndpointKey(provider, connectId, serverName)
            sessionEndpoints[key]?.let { oldEndpoint ->
                logger.info { "[MCP] Replacing session endpoint: $key" }
                runCatching { oldEndpoint.server.closeGracefully() }
                    .onFailure { logger.warn(it) { "[MCP] Failed to close old endpoint" } }
                sessionEndpoints.remove(key)
            }
        }

        val endpointPath = buildEndpointPath(serverName)
        val transport = HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .mcpEndpoint(endpointPath)
            .keepAliveInterval(Duration.ofMinutes(1))
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
        
        if (isGlobal) {
            globalEndpoints[serverName] = endpoint
            logger.info { "[MCP] Registered global endpoint: $serverName" }
        } else {
            val key = SessionEndpointKey(provider, connectId, serverName)
            sessionEndpoints[key] = endpoint
            logger.info { "[MCP] Registered session endpoint: $key" }
        }
        
        val url = buildUrl(endpointPath)
        // 会话级 MCP：返回带路由 query 的 URL，避免依赖客户端必须支持自定义 header。
        return if (isGlobal) url else appendRoutingQuery(url, connectId, provider.name)
    }

    /**
     * 注销指定 connectId 的所有局部 MCP 端点
     * 注意：全局端点不会被注销（所有 tab 共享）
     */
    fun unregisterSession(connectId: String) {
        // 只清理局部端点，全局端点保留
        val keys = sessionEndpoints.keys.filter { it.connectId == connectId }
        keys.forEach { unregisterSessionEndpoint(it) }
    }

    /**
     * 注销指定 provider 和 connectId 的所有局部 MCP 端点
     */
    fun unregisterProviderSession(provider: AiAgentProvider, connectId: String) {
        val keys = sessionEndpoints.keys.filter { it.connectId == connectId && it.provider == provider }
        keys.forEach { unregisterSessionEndpoint(it) }
    }

    private fun unregisterSessionEndpoint(key: SessionEndpointKey) {
        val endpoint = sessionEndpoints.remove(key) ?: return
        logger.info { "[MCP] Unregistering session endpoint: $key" }
        runCatching { endpoint.server.closeGracefully() }
            .onFailure { logger.warn(it) { "[MCP] Failed to close endpoint ${endpoint.path}" } }
    }

    /**
     * 根据请求解析 transport。
     * URL 格式: /mcp/{serverName}
     * 
     * 路由优先级：
     * 1. 先查局部端点（需要 connectId 和 provider）
     * 2. 再查全局端点（只需要 serverName）
     */
    private fun resolveTransportByRequest(
        path: String,
        connectId: String?,
        providerName: String?
    ): HttpServletStreamableServerTransportProvider? {
        val normalized = path.trimEnd('/')
        val serverName = normalized.removePrefix("/mcp/").takeIf { it.isNotBlank() } ?: return null

        // 1. 尝试查找局部端点
        if (!connectId.isNullOrBlank() && !providerName.isNullOrBlank()) {
            val provider = try {
                AiAgentProvider.valueOf(providerName.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn { "[MCP] Invalid provider: $providerName" }
                null
            }
            
            if (provider != null) {
                val key = SessionEndpointKey(provider, connectId, serverName)
                sessionEndpoints[key]?.let { 
                    logger.debug { "[MCP] Resolved session endpoint: $key" }
                    return it.transport 
                }
            }
        }

        // 2. 查找全局端点
        globalEndpoints[serverName]?.let {
            logger.debug { "[MCP] Resolved global endpoint: $serverName" }
            return it.transport
        }

        logger.warn { "[MCP] No endpoint found for serverName=$serverName, connectId=$connectId, provider=$providerName" }
        return null
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
                        val jsonArgs = toJsonObject(request.arguments() ?: emptyMap())
                        // 从 _meta.progressToken 获取 toolUseId，没有则生成 UUID
                        val progressToken = request.progressToken()
                        val toolUseId = progressToken?.toString()
                            ?: java.util.UUID.randomUUID().toString()
                        logger.info { "[MCP] 🎯 Tool call: ${toolDef.name}, progressToken=$progressToken, toolUseId=$toolUseId" }
                        checkPermission(permissionContext, fullToolName, jsonArgs)
                            ?: server.callToolWithContext(toolDef.name, jsonArgs, toolUseId)
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
     * 构建端点路径：固定为 /mcp/{serverName}。
     * 会话级路由信息通过 query 参数传递（connectId / provider）。
     */
    private fun buildEndpointPath(serverName: String): String {
        return "/mcp/$serverName"
    }

    private fun buildUrl(path: String): String = "http://127.0.0.1:$actualPort$path"

    private fun appendRoutingQuery(url: String, connectId: String, providerName: String): String {
        val encodedConnectId = URLEncoder.encode(connectId, StandardCharsets.UTF_8)
        val encodedProvider = URLEncoder.encode(providerName, StandardCharsets.UTF_8)
        return "$url?$QUERY_CONNECT_ID=$encodedConnectId&$QUERY_PROVIDER=$encodedProvider"
    }

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
