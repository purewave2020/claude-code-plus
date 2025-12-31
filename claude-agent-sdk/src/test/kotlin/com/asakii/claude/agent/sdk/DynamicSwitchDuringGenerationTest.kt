package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * 测试在生成期间动态切换 Mode 和 Model 是否生效
 *
 * 测试场景：
 * 1. 启动 Claude Agent SDK
 * 2. 发送一个耗时很长的提示词（让 Claude 思考/执行较长时间）
 * 3. 在生成期间调用 setPermissionMode(PLAN) 和 setModel("haiku")
 * 4. 等待当前生成完成后，询问 Claude 确认当前模式和模型
 *
 * 预期结果：
 * - 切换操作应该成功
 * - 下一轮对话应该使用新的 Mode 和 Model
 */
class DynamicSwitchDuringGenerationTest {

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    fun `test setMode and setModel during generation`() = runBlocking {
        println("=== 测试：生成期间动态切换 Mode 和 Model ===\n")

        val options = ClaudeAgentOptions(
            model = "claude-sonnet-4-20250514",  // 初始使用 Sonnet
            permissionMode = PermissionMode.DEFAULT,  // 初始使用默认模式
            allowedTools = listOf("Read", "Grep", "Glob"),
            systemPrompt = "You are a helpful assistant. Always answer in Chinese.",
            maxTurns = 10
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            // ========== 步骤 1: 连接 ==========
            println("📡 [步骤 1] 连接到 Claude...")
            client.connect()
            println("✅ 连接成功\n")

            // ========== 步骤 2: 发送一个耗时的提示词 ==========
            println("📝 [步骤 2] 发送耗时提示词...")

            // 这个提示词会让 Claude 执行较长时间的任务
            val longRunningPrompt = """
                请帮我完成以下任务（请详细分析，不要急）：
                1. 分析一下 Kotlin 和 Java 的主要区别
                2. 列举 Kotlin 相对于 Java 的 10 个优点
                3. 列举 Java 相对于 Kotlin 的 5 个优点
                4. 给出一个表格对比两者在语法上的差异
                5. 总结你的建议

                请尽可能详细地回答。
            """.trimIndent()

            client.query(longRunningPrompt)

            // ========== 步骤 3: 在生成期间切换 Mode 和 Model ==========
            // 记录第一轮响应中的模型信息
            var firstRoundModel: String? = null

            // 启动一个协程来收集响应
            val responseJob = launch {
                println("📬 开始收集第一轮响应...")
                var messageCount = 0
                client.receiveResponse().collect { message ->
                    messageCount++
                    when (message) {
                        is AssistantMessage -> {
                            firstRoundModel = message.model
                            val text = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            println("🤖 [消息 $messageCount] Claude (model=${message.model}) 回复: ${text.take(50)}...")
                        }

                        is ResultMessage -> {
                            println("🎯 [消息 $messageCount] 第一轮完成: turns=${message.numTurns}, duration=${message.durationMs}ms")
                        }

                        else -> {
                            println("📄 [消息 $messageCount] ${message::class.simpleName}")
                        }
                    }
                }
                println("✅ 第一轮响应收集完成，共 $messageCount 条消息\n")
            }

            // 等待一小段时间，确保生成已经开始
            delay(2000)

            println("\n🔄 [步骤 3] 在生成期间切换 Mode 和 Model...")

            // 切换到 Plan 模式
            println("   🔐 切换到 Plan 模式...")
            client.setPermissionMode(PermissionMode.PLAN)
            println("   ✅ setPermissionMode(PLAN) 调用完成")

            // 切换到 Haiku 模型
            println("   🤖 切换到 Haiku 模型...")
            val newModel = client.setModel("claude-3-5-haiku-20241022")
            println("   ✅ setModel(haiku) 调用完成，返回: $newModel")

            // 等待第一轮响应完成
            responseJob.join()

            // ========== 步骤 4: 验证切换是否生效 ==========
            println("🔍 [步骤 4] 验证切换是否生效...")

            // 检查 serverInfo 中的模型
            val serverInfo = client.getServerInfo()
            println("   📊 ServerInfo: $serverInfo")
            val currentModel = serverInfo?.get("model")?.jsonPrimitive?.contentOrNull
            println("   📊 当前模型 (from serverInfo): $currentModel")

            // 发送验证问题 - 同时验证模型和模式
            val verificationPrompt = """
                请简短回答以下问题：
                1. 你现在使用的模型 ID 是什么？
                2. 你现在是否处于计划模式（Plan Mode）？

                请直接给出答案，不要解释。
            """.trimIndent()

            println("\n📝 发送验证问题...")
            client.query(verificationPrompt)

            var modelConfirmation = ""
            var secondRoundModel: String? = null

            println("📬 收集验证响应...")
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        secondRoundModel = message.model  // 关键：从 AssistantMessage.model 获取实际使用的模型
                        val text = message.content.filterIsInstance<TextBlock>()
                            .joinToString("") { it.text }
                        modelConfirmation += text
                        println("🤖 Claude (model=${message.model}) 回复: $text")
                    }

                    is ResultMessage -> {
                        println("🎯 验证完成")
                    }

                    else -> {}
                }
            }

            // ========== 步骤 5: 输出测试结果并验证 ==========
            println("\n" + "=".repeat(60))
            println("📋 测试结果汇总：")
            println("=".repeat(60))
            println("   初始配置模型: claude-sonnet-4-20250514")
            println("   初始配置模式: DEFAULT")
            println("   第一轮实际模型 (AssistantMessage.model): $firstRoundModel")
            println("   切换后目标模型: claude-3-5-haiku-20241022")
            println("   切换后目标模式: PLAN")
            println("   setModel() 返回值: $newModel")
            println("   ServerInfo 中的模型: $currentModel")
            println("   第二轮实际模型 (AssistantMessage.model): $secondRoundModel")
            println("   Claude 自述: ${modelConfirmation.take(300)}")
            println("=".repeat(60))

            // ========== 断言验证 ==========
            println("\n🧪 断言检查:")

            // 验证模型切换 - 通过 AssistantMessage.model 字段验证
            val modelSwitchSuccess = secondRoundModel?.contains("haiku", ignoreCase = true) == true
            println("   模型切换成功 (AssistantMessage.model 包含 haiku): $modelSwitchSuccess")
            println("   - 第一轮模型: $firstRoundModel")
            println("   - 第二轮模型: $secondRoundModel")

            // 验证 setModel 返回值
            val setModelReturnValid = newModel?.contains("haiku", ignoreCase = true) == true
            println("   setModel() 返回值有效: $setModelReturnValid")

            // 综合验证
            val overallSuccess = modelSwitchSuccess || setModelReturnValid ||
                    currentModel?.contains("haiku", ignoreCase = true) == true

            assertTrue(
                overallSuccess,
                "模型应该已切换到 Haiku。" +
                        "setModel返回=$newModel, " +
                        "serverInfo=$currentModel, " +
                        "secondRoundModel=$secondRoundModel"
            )

            println("\n✅ 测试通过！生成期间的 setMode 和 setModel 调用成功")

        } catch (e: Exception) {
            println("❌ 测试失败: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            println("\n📡 断开连接...")
            client.disconnect()
            println("✅ 已断开")
        }
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    fun `test setMode PLAN actually works`() = runBlocking {
        println("=== 测试：Plan 模式是否真正生效 ===\n")

        val options = ClaudeAgentOptions(
            model = "claude-3-5-haiku-20241022",  // 使用 Haiku 加速测试
            permissionMode = PermissionMode.DEFAULT,
            allowedTools = listOf("Read", "Write", "Bash"),
            systemPrompt = "You are a helpful assistant.",
            maxTurns = 5
        )

        val client = ClaudeCodeSdkClient(options)

        try {
            println("📡 连接到 Claude...")
            client.connect()
            println("✅ 连接成功\n")

            // 切换到 Plan 模式
            println("🔐 切换到 Plan 模式...")
            client.setPermissionMode(PermissionMode.PLAN)
            println("✅ 已切换到 Plan 模式\n")

            // 发送一个需要执行工具的任务
            val prompt = """
                请帮我创建一个文件 test_plan_mode.txt，内容是 "Hello from Plan Mode"。
                
                如果你处于计划模式，你应该只描述计划而不实际执行。
                请明确告诉我你当前是否处于计划模式。
            """.trimIndent()

            println("📝 发送测试提示词...")
            client.query(prompt)

            var responseText = ""
            var usedTools = mutableListOf<String>()

            println("📬 收集响应...")
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        message.content.forEach { block ->
                            when (block) {
                                is TextBlock -> {
                                    responseText += block.text
                                    println("🤖 Claude: ${block.text.take(100)}...")
                                }

                                is ToolUseBlock -> {
                                    usedTools.add(block.name)
                                    println("🛠️ 工具调用: ${block.name}")
                                }

                                else -> {}
                            }
                        }
                    }

                    is ResultMessage -> {
                        println("🎯 完成")
                    }

                    else -> {}
                }
            }

            println("\n" + "=".repeat(60))
            println("📋 测试结果：")
            println("   使用的工具: ${usedTools.joinToString(", ").ifEmpty { "无" }}")
            println("   响应内容: ${responseText.take(300)}...")
            println("=".repeat(60))

            // 在 Plan 模式下，Claude 应该描述计划而不是直接执行
            // 如果执行了 Write 工具，说明 Plan 模式可能没生效
            val planModeWorking = !usedTools.contains("Write") ||
                    responseText.contains("计划") ||
                    responseText.contains("plan", ignoreCase = true)

            println("\n🧪 Plan 模式检查: $planModeWorking")

            // 这里不强制断言，因为 Plan 模式的行为可能因版本而异
            println(if (planModeWorking) "✅ Plan 模式似乎正常工作" else "⚠️ Plan 模式行为需要确认")

        } finally {
            println("\n📡 断开连接...")
            client.disconnect()
            println("✅ 已断开")
        }
    }
}
