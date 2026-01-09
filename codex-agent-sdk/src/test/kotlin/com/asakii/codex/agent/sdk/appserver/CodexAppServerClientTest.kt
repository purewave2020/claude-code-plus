package com.asakii.codex.agent.sdk.appserver

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Codex App-Server Client 测试
 *
 * 验证 Codex SDK 是否能正常工作
 */
class CodexAppServerClientTest {

    /**
     * 测试 Codex App-Server 启动和初始化
     *
     * 前置条件：
     * 1. codex CLI 已安装（npm install -g @openai/codex）
     * 2. OPENAI_API_KEY 环境变量已设置
     */
    @Test
    fun testCodexAppServerStartAndInitialize() = runTest(timeout = 60.seconds) {
        println("=== Codex SDK Test ===")
        println("1. Creating CodexAppServerClient...")

        val client = try {
            CodexAppServerClient.create()
        } catch (e: Exception) {
            println("❌ Failed to create client: ${e.message}")
            e.printStackTrace()
            throw e
        }

        println("2. Client created, process alive: ${client.isAlive}")
        assertTrue(client.isAlive, "Process should be alive after creation")

        try {
            println("3. Initializing client...")
            val initResult = client.initialize(
                clientName = "codex-sdk-test",
                clientTitle = "Codex SDK Test",
                clientVersion = "1.0.0"
            )

            println("4. Initialize result:")
            println("   - User Agent: ${initResult.userAgent}")

            println("5. Listing available models...")
            val models = client.listModels()
            println("   - Models: ${models.data.map { it.id }}")

            println("6. Creating thread...")
            val thread = client.startThread()
            println("   - Thread ID: ${thread.id}")

            assertNotNull(thread.id, "Thread ID should not be null")

            println("7. Starting turn with message...")
            val turn = client.startTurn(
                threadId = thread.id,
                message = "Say 'Hello from Codex SDK test!' and nothing else."
            )
            println("   - Turn ID: ${turn.id}")

            println("8. Collecting events...")
            var responseText = StringBuilder()
            var turnCompleted = false

            withTimeoutOrNull(30_000) {
                client.events.takeWhile { !turnCompleted }.collect { event ->
                    when (event) {
                        is AppServerEvent.AgentMessageDelta -> {
                            print(event.delta)
                            responseText.append(event.delta)
                        }
                        is AppServerEvent.TurnCompleted -> {
                            println("\n   Turn completed!")
                            turnCompleted = true
                        }
                        is AppServerEvent.Error -> {
                            println("\n   Error: ${event.message}")
                            turnCompleted = true
                        }
                        else -> {}
                    }
                }
            }

            println("9. Response received: ${responseText.toString().take(100)}...")
            assertTrue(responseText.isNotEmpty(), "Should receive response from Codex")

            println("✅ Codex SDK test PASSED!")

        } finally {
            println("10. Closing client...")
            client.close()
            println("   Client closed.")
        }
    }

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
}
