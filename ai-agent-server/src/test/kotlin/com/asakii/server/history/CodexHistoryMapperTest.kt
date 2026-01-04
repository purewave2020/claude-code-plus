package com.asakii.server.history

import com.asakii.codex.agent.sdk.appserver.CommandExecutionStatus
import com.asakii.codex.agent.sdk.appserver.FileUpdateChange
import com.asakii.codex.agent.sdk.appserver.McpToolCallResult
import com.asakii.codex.agent.sdk.appserver.McpToolCallStatus
import com.asakii.codex.agent.sdk.appserver.PatchApplyStatus
import com.asakii.codex.agent.sdk.appserver.PatchChangeKind
import com.asakii.codex.agent.sdk.appserver.ThreadInfo
import com.asakii.codex.agent.sdk.appserver.ThreadItem
import com.asakii.codex.agent.sdk.appserver.TurnInfo
import com.asakii.codex.agent.sdk.appserver.TurnStatus
import com.asakii.codex.agent.sdk.appserver.UserInput
import com.asakii.rpc.api.RpcAssistantMessage
import com.asakii.rpc.api.RpcTextBlock
import com.asakii.rpc.api.RpcToolUseBlock
import com.asakii.rpc.api.RpcUserMessage
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CodexHistoryMapperTest {

    @Test
    fun `countMessages counts user and assistant messages`() {
        val thread = buildSampleThread()
        assertEquals(4, CodexHistoryMapper.countMessages(thread))
    }

    @Test
    fun `buildHistoryResult returns messages in order`() {
        val thread = buildSampleThread()
        val result = CodexHistoryMapper.buildHistoryResult(thread, offset = 0, limit = 0)

        assertEquals(4, result.availableCount)
        assertEquals(4, result.count)
        assertEquals(4, result.messages.size)

        val m0 = assertIs<RpcUserMessage>(result.messages[0])
        val m0Text = assertIs<RpcTextBlock>(m0.message.content.first())
        assertEquals("1+1=?", m0Text.text)

        val m1 = assertIs<RpcAssistantMessage>(result.messages[1])
        val m1Text = assertIs<RpcTextBlock>(m1.message.content.first())
        assertEquals("2", m1Text.text)

        val m2 = assertIs<RpcUserMessage>(result.messages[2])
        val m2Text = assertIs<RpcTextBlock>(m2.message.content.first())
        assertEquals("run", m2Text.text)

        val m3 = assertIs<RpcAssistantMessage>(result.messages[3])
        val toolNames = m3.message.content.filterIsInstance<RpcToolUseBlock>().map { it.toolName }.toSet()
        assertTrue("Bash" in toolNames, "Should include Bash tool")
        assertTrue("Write" in toolNames, "Should include Write tool for PatchChangeKind.Add")
        assertTrue("mcp__jetbrains__openFile" in toolNames, "Should include MCP tool name")
    }

    @Test
    fun `buildHistoryResult supports offset and limit`() {
        val thread = buildSampleThread()
        val result = CodexHistoryMapper.buildHistoryResult(thread, offset = 2, limit = 2)

        assertEquals(4, result.availableCount)
        assertEquals(2, result.count)
        assertEquals(2, result.messages.size)

        val first = assertIs<RpcUserMessage>(result.messages[0])
        val firstText = assertIs<RpcTextBlock>(first.message.content.first())
        assertEquals("run", firstText.text)
    }

    @Test
    fun `buildHistoryResult supports tail loading via negative offset`() {
        val thread = buildSampleThread()
        val result = CodexHistoryMapper.buildHistoryResult(thread, offset = -1, limit = 2)

        assertEquals(4, result.availableCount)
        assertEquals(2, result.count)
        assertEquals(2, result.messages.size)

        val first = assertIs<RpcUserMessage>(result.messages[0])
        val firstText = assertIs<RpcTextBlock>(first.message.content.first())
        assertEquals("run", firstText.text)
    }

    private fun buildSampleThread(): ThreadInfo {
        val turn1 = TurnInfo(
            id = "turn-1",
            status = TurnStatus.Completed,
            items = listOf(
                ThreadItem.UserMessage(
                    id = "u1",
                    content = listOf(UserInput.Text("1+1=?"))
                ),
                ThreadItem.AgentMessage(
                    id = "a1",
                    text = "2"
                )
            )
        )

        val turn2 = TurnInfo(
            id = "turn-2",
            status = TurnStatus.Completed,
            items = listOf(
                ThreadItem.UserMessage(
                    id = "u2",
                    content = listOf(UserInput.Text("run"))
                ),
                ThreadItem.CommandExecution(
                    id = "cmd1",
                    command = "echo hi",
                    cwd = "C:/proj",
                    status = CommandExecutionStatus.Completed,
                    aggregatedOutput = "hi\n",
                    exitCode = 0,
                    durationMs = 10
                ),
                ThreadItem.FileChange(
                    id = "fc1",
                    changes = listOf(
                        FileUpdateChange(
                            path = "README.md",
                            kind = PatchChangeKind.Add,
                            diff = "@@ -0,0 +1 @@\n+hello\n"
                        )
                    ),
                    status = PatchApplyStatus.Completed
                ),
                ThreadItem.McpToolCall(
                    id = "mcp1",
                    server = "jetbrains",
                    tool = "openFile",
                    status = McpToolCallStatus.Completed,
                    arguments = buildJsonObject {
                        put("filePath", JsonPrimitive("a.ts"))
                    },
                    result = McpToolCallResult(
                        content = listOf(JsonPrimitive("ok")),
                        structuredContent = null
                    ),
                    error = null,
                    durationMs = 5
                ),
                ThreadItem.WebSearch(
                    id = "ws1",
                    query = "kotlin"
                ),
                ThreadItem.Reasoning(
                    id = "r1",
                    summary = listOf("think"),
                    content = emptyList()
                ),
                ThreadItem.AgentMessage(
                    id = "a2",
                    text = "done"
                )
            )
        )

        return ThreadInfo(
            id = "thread-1",
            preview = "preview",
            modelProvider = "codex",
            createdAt = 1L,
            path = "",
            cwd = "C:/proj",
            cliVersion = "0.0.0",
            turns = listOf(turn1, turn2)
        )
    }
}

