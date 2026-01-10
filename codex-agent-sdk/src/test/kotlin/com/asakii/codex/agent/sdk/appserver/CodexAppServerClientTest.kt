package com.asakii.codex.agent.sdk.appserver

import kotlinx.coroutines.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Codex App-Server Client 测试
 *
 * 验证 Codex SDK 是否能正常工作
 */
class CodexAppServerClientTest {

    /**
     * 简单的连接测试（不发送消息）
     */
    @Test
    fun testCodexAppServerConnect() = runTest(timeout = 30.seconds) {
        println("=== Codex Connection Test ===")

        val client = try {
            println("Creating client...")
            CodexAppServerClient.create()
        } catch (e: CodexAppServerException) {
            println("❌ Codex not found: ${e.message}")
            println("   Please install: npm install -g @openai/codex")
            return@runTest
        }

        assertTrue(client.isAlive, "Process should be alive")
        println("✓ Process started")

        try {
            println("Initializing...")
            val result = client.initialize()
            println("✓ Initialized: userAgent=${result.userAgent}")

            println("Listing MCP status...")
            val mcpStatus = client.listMcpServerStatus()
            println("✓ MCP servers: ${mcpStatus.data.size}")
            mcpStatus.data.forEach { server ->
                println("  - ${server.name}: authStatus=${server.authStatus}, tools=${server.tools.size}")
            }

        } finally {
            client.close()
            println("✓ Closed")
        }

        println("✅ Connection test PASSED!")
    }
    @Test
    fun testInvalidHttpHeadersOverrideTimeouts() = runTest(timeout = 20.seconds) {
        println("=== Codex Invalid http_headers Override Test ===")

        val client = try {
            CodexAppServerClient.create(
                configOverrides = mapOf(
                    "mcp_servers.test.url" to "\"http://127.0.0.1:12345\"",
                    "mcp_servers.test.http_headers" to "{\"X-Test\":\"1\"}"
                )
            )
        } catch (e: CodexAppServerException) {
            println("Codex not found: ${e.message}")
            println("Please install: npm install -g @openai/codex")
            return@runTest
        }

        try {
            val initResult = runCatching {
                withTimeout(5.seconds) {
                    client.initialize(clientName = "codex-invalid-headers-test")
                }
            }
            assertTrue(initResult.isFailure, "Expected initialize to fail with invalid http_headers override")
        } finally {
            client.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testQueryParamsOverrideInitializeSuccess() = runTest(timeout = 20.seconds) {
        println("=== Codex Query Param Override Test ===")

        val client = try {
            CodexAppServerClient.create(
                configOverrides = mapOf(
                    "mcp_servers.test.url" to "\"http://127.0.0.1:12345/mcp/test?sessionId=abc&provider=CODEX\"",
                    "mcp_servers.test.enabled" to "false"
                )
            )
        } catch (e: CodexAppServerException) {
            println("Codex not found: ${e.message}")
            println("Please install: npm install -g @openai/codex")
            return@runTest
        }

        try {
            val initResult = withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(15.seconds) {
                    client.initialize(clientName = "codex-query-params-test")
                }
            }
            assertTrue(!initResult.userAgent.isNullOrBlank(), "Expected non-empty userAgent")
        } finally {
            client.close()
        }
    }

}
