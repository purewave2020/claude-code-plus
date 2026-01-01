package com.asakii.server.util

import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapperSupplier
import io.modelcontextprotocol.json.schema.JsonSchemaValidator
import io.modelcontextprotocol.json.schema.jackson.JacksonJsonSchemaValidatorSupplier
import kotlinx.serialization.json.Json

object JsonTools {
    val kotlinJson: Json = Json { ignoreUnknownKeys = true }
    val mcpJsonMapper: McpJsonMapper by lazy { JacksonMcpJsonMapperSupplier().get() }
    val mcpJsonSchemaValidator: JsonSchemaValidator by lazy { JacksonJsonSchemaValidatorSupplier().get() }
}
