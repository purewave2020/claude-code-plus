package com.asakii.server.mcp

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpHttpGatewayTest {
    @Test
    fun buildServerUrl_hasNoQueryParams() {
        val url = McpHttpGateway.buildServerUrl("jetbrains-file")
        val uri = URI(url)

        assertTrue(uri.query.isNullOrBlank())
        assertTrue(url.contains("/mcp/jetbrains-file"))
        assertEquals("/mcp/jetbrains-file", uri.path)
    }
}
