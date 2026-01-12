package com.asakii.claude.agent.sdk.protocol

import com.asakii.claude.agent.sdk.mcp.*
import com.asakii.logging.*
import kotlinx.serialization.json.*

/**
 * Handler for MCP (Model Context Protocol) messages.
 * Processes initialize, tools/list, tools/call, and notifications/initialized requests.
 */
class McpMessageHandler {
    private val logger = getLogger("McpMessageHandler")
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Handle MCP server method invocations.
     * Routes to appropriate handler based on the method name.
     *
     * @param server The MCP server instance
     * @param method The method being called (e.g., "initialize", "tools/list", "tools/call")
     * @param params The parameters for the method call
     * @param id The JSON-RPC request ID
     * @return JsonElement response in JSON-RPC format
     */
    suspend fun handleMethod(
        server: McpServer,
        method: String?,
        params: JsonObject,
        id: JsonElement?
    ): JsonElement {
        return when (method) {
            "initialize" -> handleInitialize(server, id)
            "tools/list" -> handleToolsList(server, id)
            "tools/call" -> handleToolsCall(server, params, id)
            "notifications/initialized" -> handleNotificationsInitialized()
            else -> handleMethodNotFound(method, id)
        }
    }

    private fun handleInitialize(server: McpServer, id: JsonElement?): JsonElement {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("result") {
                put("protocolVersion", "2024-11-05")
                putJsonObject("capabilities") {
                    putJsonObject("tools") {}
                }
                putJsonObject("serverInfo") {
                    put("name", server.name)
                    put("version", server.version)
                    put("description", server.description)
                }
            }
        }
    }

    private suspend fun handleToolsList(server: McpServer, id: JsonElement?): JsonElement {
        val tools = server.listTools()
        return buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("result") {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchema", tool.inputSchema)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleToolsCall(
        server: McpServer,
        params: JsonObject,
        id: JsonElement?
    ): JsonElement {
        val toolName = params["name"]?.jsonPrimitive?.content
            ?: return buildJsonObject {
                put("jsonrpc", "2.0")
                id?.let { put("id", it) }
                putJsonObject("error") {
                    put("code", -32602)
                    put("message", "Missing required parameter: name")
                }
            }

        val argumentsJson = params["arguments"]?.jsonObject ?: buildJsonObject {}

        logger.debug { "🛠️ Calling tool: $toolName, args: $argumentsJson" }

        val result = server.callToolJson(toolName, argumentsJson)

        return when (result) {
            is ToolResult.Success -> buildSuccessResponse(result, id)
            is ToolResult.Error -> buildErrorResponse(result, id)
        }
    }

    private fun buildSuccessResponse(result: ToolResult.Success, id: JsonElement?): JsonElement {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("result") {
                putJsonArray("content") {
                    result.content.forEach { contentItem ->
                        addJsonObject {
                            when (contentItem) {
                                is ContentItem.Text -> {
                                    put("type", "text")
                                    put("text", contentItem.text)
                                }
                                is ContentItem.Json -> {
                                    put("type", "text")
                                    put("text", contentItem.data.toString())
                                }
                                is ContentItem.Binary -> {
                                    put("type", "resource")
                                    put("mimeType", contentItem.mimeType)
                                    put("data", java.util.Base64.getEncoder().encodeToString(contentItem.data))
                                }
                            }
                        }
                    }
                }
                if (result.metadata.isNotEmpty()) {
                    put("meta", Json.encodeToJsonElement(result.metadata))
                }
            }
        }
    }

    private fun buildErrorResponse(result: ToolResult.Error, id: JsonElement?): JsonElement {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("error") {
                put("code", result.code)
                put("message", result.error)
            }
        }
    }

    private fun handleNotificationsInitialized(): JsonElement {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            putJsonObject("result") {}
        }
    }

    private fun handleMethodNotFound(method: String?, id: JsonElement?): JsonElement {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("error") {
                put("code", -32601)
                put("message", "Method '$method' not found")
            }
        }
    }

    /**
     * Build a JSON-RPC error response for server not found.
     */
    fun buildServerNotFoundError(serverName: String, messageId: JsonElement?): JsonElement {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            messageId?.let { put("id", it) }
            putJsonObject("error") {
                put("code", -32601)
                put("message", "Server '$serverName' not found")
            }
        }
    }

    /**
     * Build a JSON-RPC error response for internal errors.
     */
    fun buildInternalError(errorMessage: String, messageId: JsonElement?): JsonElement {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            messageId?.let { put("id", it) }
            putJsonObject("error") {
                put("code", -32603)
                put("message", errorMessage)
            }
        }
    }
}
