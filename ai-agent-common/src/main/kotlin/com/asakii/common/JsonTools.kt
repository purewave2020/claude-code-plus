package com.asakii.common

import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapperSupplier
import kotlinx.serialization.json.Json

object JsonTools {
    val kotlinJson: Json = Json { ignoreUnknownKeys = true }
    val mcpJsonMapper: McpJsonMapper by lazy { JacksonMcpJsonMapperSupplier().get() }
}
