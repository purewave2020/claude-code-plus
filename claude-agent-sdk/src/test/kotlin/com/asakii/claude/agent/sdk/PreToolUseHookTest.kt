package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

/**
 * 测试 PreToolUse Hook 功能
 */
class PreToolUseHookTest {

    @Test
    fun `test hook receives toolUseId`() = runBlocking {
        val workDir = Path.of(System.getProperty("user.dir"))
        val hookCalls = mutableListOf<Pair<String?, JsonObject>>()

        val preToolUseHook: HookCallback = { input, toolUseId, _ ->
            println("🎣 Hook called: toolUseId=$toolUseId, input=$input")
            hookCalls.add(toolUseId to input)
            HookJSONOutput(decision = null)
        }

        val hooks = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(matcher = "*", hooks = listOf(preToolUseHook))
            )
        )

        val options = ClaudeAgentOptions(
            cwd = workDir,
            permissionMode = PermissionMode.BYPASS_PERMISSIONS,
            maxTurns = 2,
            hooks = hooks
        )

        println("📋 options.hooks: ${options.hooks}")
        println("📋 hooks size: ${options.hooks?.size}")

        val client = ClaudeCodeSdkClient(options)

        try {
            client.connect()
            client.query("读取 README.md 前3行")

            client.receiveResponse().collect { message ->
                if (message is ResultMessage) {
                    println("✅ 完成")
                }
            }

            println("Hook 调用次数: ${hookCalls.size}")
            hookCalls.forEach { (id, input) ->
                println("  toolUseId: $id, input: ${input.keys}")
            }

            // 验证 hook 被调用且有 toolUseId
            assertTrue(hookCalls.isNotEmpty(), "Hook 应该被调用")
            assertTrue(hookCalls.any { it.first != null }, "至少一个调用应该有 toolUseId")

        } finally {
            client.disconnect()
        }
    }
}
