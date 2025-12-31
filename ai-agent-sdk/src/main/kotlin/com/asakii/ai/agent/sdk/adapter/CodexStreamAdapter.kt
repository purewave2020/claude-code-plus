 package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.model.*
import com.asakii.codex.agent.sdk.ThreadEvent
import com.asakii.codex.agent.sdk.ThreadItem
import com.asakii.codex.agent.sdk.Usage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * 将 Codex ThreadEvent 流转换为统一的 NormalizedStreamEvent。
 */
class CodexStreamAdapter(
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val itemsBuffer = mutableMapOf<Int, ThreadItem>()
    private var indexCounter = 0
    private var currentSessionId: String? = null
    private var turnStartTimeMs: Long? = null

    fun convert(event: ThreadEvent): List<NormalizedStreamEvent> {
        val result = mutableListOf<NormalizedStreamEvent>()
        when (event.type) {
            "thread.started" -> {
                val id = event.threadId?.takeIf { it.isNotBlank() } ?: idGenerator()
                currentSessionId = id
                turnStartTimeMs = null
                result += MessageStartedEvent(
                    provider = AiAgentProvider.CODEX,
                    sessionId = id,
                    messageId = id
                )
            }

            "turn.started" -> {
                turnStartTimeMs = System.currentTimeMillis()
                result += TurnStartedEvent(AiAgentProvider.CODEX)
            }

            "item.started" -> {
                val item = event.item ?: return result
                if (turnStartTimeMs == null) {
                    turnStartTimeMs = System.currentTimeMillis()
                }
                val index = indexCounter++
                itemsBuffer[index] = item
                result += ContentStartedEvent(
                    provider = AiAgentProvider.CODEX,
                    index = index,
                    contentType = item.type
                )
            }

            "item.updated" -> {
                val item = event.item ?: return result
                // 找到对应的 index
                val index = itemsBuffer.entries.find { it.value.id == item.id }?.key ?: return result
                itemsBuffer[index] = item
                extractItemDelta(item)?.let { delta ->
                    result += ContentDeltaEvent(
                        provider = AiAgentProvider.CODEX,
                        index = index,
                        delta = delta
                    )
                }
            }

            "item.completed" -> {
                val item = event.item ?: return result
                val index = itemsBuffer.entries.find { it.value.id == item.id }?.key
                if (index != null) {
                    itemsBuffer.remove(index)
                    result += ContentCompletedEvent(
                        provider = AiAgentProvider.CODEX,
                        index = index,
                        content = convertThreadItem(item)
                    )
                }
            }

            "turn.completed" -> {
                val durationMs = resolveDurationMs()
                result += TurnCompletedEvent(
                    provider = AiAgentProvider.CODEX,
                    usage = event.usage?.toUnifiedUsage()
                )
                result += ResultSummaryEvent(
                    provider = AiAgentProvider.CODEX,
                    subtype = "completed",
                    durationMs = durationMs,
                    durationApiMs = durationMs,
                    isError = false,
                    numTurns = 1,
                    sessionId = resolveSessionId(event),
                    usage = event.usage?.toUsageJson(),
                    result = null
                )
            }

            "turn.failed" -> {
                val errorMsg = event.error?.message ?: "Codex turn failed"
                val durationMs = resolveDurationMs()
                result += ResultSummaryEvent(
                    provider = AiAgentProvider.CODEX,
                    subtype = "error_during_execution",
                    durationMs = durationMs,
                    durationApiMs = durationMs,
                    isError = true,
                    numTurns = 1,
                    sessionId = resolveSessionId(event),
                    usage = event.usage?.toUsageJson(),
                    result = errorMsg
                )
                result += TurnFailedEvent(
                    provider = AiAgentProvider.CODEX,
                    error = errorMsg
                )
            }
        }
        return result
    }

    private fun convertThreadItem(item: ThreadItem): UnifiedContentBlock =
        when (item.type) {
            "agent_message" -> TextContent(item.text.orEmpty())
            "reasoning" -> ThinkingContent(item.text.orEmpty())
            "command_execution" -> CommandExecutionContent(
                command = item.command ?: "",
                output = item.aggregatedOutput,
                exitCode = item.exitCode,
                status = item.status.toContentStatus()
            )
            "file_change" -> FileChangeContent(
                changes = item.changes.orEmpty(),
                status = item.status.toContentStatus()
            )
            "mcp_tool_call" -> McpToolCallContent(
                server = null,
                tool = null,
                arguments = item.arguments,
                result = item.result?.structuredContent ?: item.result?.content,
                status = item.status.toContentStatus()
            )
            "web_search" -> WebSearchContent(item.query.orEmpty())
            "todo_list" -> TodoListContent(item.items.orEmpty())
            "error" -> ErrorContent(item.error?.message ?: "Unknown Codex error")
            else -> TextContent(item.text ?: item.content?.toString().orEmpty())
        }

    private fun extractItemDelta(item: ThreadItem): ContentDeltaPayload? =
        when (item.type) {
            "agent_message" -> item.text?.let { TextDeltaPayload(it) }
            "reasoning" -> item.text?.let { ThinkingDeltaPayload(it) }
            "command_execution" -> item.aggregatedOutput?.let { CommandDeltaPayload(it) }
            else -> null
        }

    private fun Usage.toUnifiedUsage(): UnifiedUsage =
        UnifiedUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            provider = AiAgentProvider.CODEX
        )

    private fun Usage.toUsageJson(): JsonElement =
        buildJsonObject {
            put("input_tokens", inputTokens)
            put("output_tokens", outputTokens)
            put("cached_input_tokens", cachedInputTokens)
        }

    private fun String?.toContentStatus(): ContentStatus =
        when (this?.lowercase()) {
            "in_progress" -> ContentStatus.IN_PROGRESS
            "failed" -> ContentStatus.FAILED
            else -> ContentStatus.COMPLETED
        }

    private fun resolveSessionId(event: ThreadEvent): String {
        val incoming = event.threadId?.takeIf { it.isNotBlank() }
        if (incoming != null) {
            currentSessionId = incoming
            return incoming
        }
        return currentSessionId ?: idGenerator().also { currentSessionId = it }
    }

    private fun resolveDurationMs(): Long {
        val now = System.currentTimeMillis()
        val start = turnStartTimeMs ?: now
        turnStartTimeMs = null
        return (now - start).coerceAtLeast(0)
    }
}
