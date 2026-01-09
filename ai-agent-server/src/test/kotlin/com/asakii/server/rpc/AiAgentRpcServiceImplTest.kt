package com.asakii.server.rpc

import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.types.McpHttpServerConfig
import com.asakii.server.tools.IdeToolsDefault
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiAgentRpcServiceImplTest {
    @Test
    fun buildAppServerConfigOverrides_includesOnlyFeatures() {
        val ideTools = IdeToolsDefault(".")
        val service = AiAgentRpcServiceImpl(ideTools)

        val configOverrides = mapOf(
            "features.shell_tool" to JsonPrimitive(false),
            "mcp_servers.context7.url" to JsonPrimitive("https://mcp.context7.com/mcp"),
            "mcp_servers.context7.http_headers.CONTEXT7_API_KEY" to JsonPrimitive("key-123")
        )
        val method = AiAgentRpcServiceImpl::class.java.getDeclaredMethod(
            "buildAppServerConfigOverrides",
            Map::class.java,
            java.lang.Boolean::class.java
        )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(service, configOverrides, true) as Map<String, String>

        assertEquals("false", result["features.shell_tool"])
        assertEquals("true", result["features.web_search_request"])
        assertTrue(result.keys.none { it.startsWith("mcp_servers.") })
    }

    @Test
    fun buildCodexMcpThreadConfigOverrides_includesHttpHeaders() {
        val ideTools = IdeToolsDefault(".")
        val service = AiAgentRpcServiceImpl(ideTools)

        val url = "https://mcp.context7.com/mcp"
        val mcpServers = mapOf(
            "context7" to McpHttpServerConfig(
                url = url,
                headers = mapOf("CONTEXT7_API_KEY" to "key-123")
            )
        )
        val internalServers = emptyMap<String, McpServer>()

        val method = AiAgentRpcServiceImpl::class.java.getDeclaredMethod(
            "buildCodexMcpThreadConfigOverrides",
            Map::class.java,
            Map::class.java
        )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(service, mcpServers, internalServers) as Map<String, JsonElement>

        val urlElement = result["mcp_servers.context7.url"] as? JsonPrimitive
        val headerElement = result["mcp_servers.context7.http_headers.CONTEXT7_API_KEY"] as? JsonPrimitive

        assertEquals(url, urlElement?.content)
        assertEquals("key-123", headerElement?.content)
        assertTrue(result.keys.none { it == "mcp_servers.context7.http_headers" })
    }
}
