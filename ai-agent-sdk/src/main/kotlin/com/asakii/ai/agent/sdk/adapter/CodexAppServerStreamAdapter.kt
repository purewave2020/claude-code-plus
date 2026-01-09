package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.model.*
import com.asakii.codex.agent.sdk.appserver.AppServerEvent
import com.asakii.codex.agent.sdk.appserver.CommandExecutionStatus
import com.asakii.codex.agent.sdk.appserver.McpToolCallResult
import com.asakii.codex.agent.sdk.appserver.McpToolCallStatus
import com.asakii.codex.agent.sdk.appserver.PatchApplyStatus
import com.asakii.codex.agent.sdk.appserver.PatchChangeKind
import com.asakii.codex.agent.sdk.appserver.ThreadItem
import com.asakii.codex.agent.sdk.appserver.ThreadTokenUsage
import com.asakii.codex.agent.sdk.appserver.TurnStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class CodexAppServerStreamAdapter(
    private val sessionIdProvider: () -> String?,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val itemIdToIndex = mutableMapOf<String, Int>()
    private var indexCounter = 0
    private var turnStartTimeMs: Long? = null
    private var currentSessionId: String? = null
    private var lastUsage: ThreadTokenUsage? = null

    // 累积文本和思考内容，用于在 TurnCompleted 时发送 AssistantMessageEvent
    private val accumulatedText = StringBuilder()
    private val accumulatedThinking = StringBuilder()
    // 累积工具调用内容
    private val accumulatedToolUses = mutableListOf<ToolUseContent>()
    private var currentMessageId: String? = null

    fun convert(event: AppServerEvent): List<NormalizedStreamEvent> {
        val result = mutableListOf<NormalizedStreamEvent>()
        when (event) {
            is AppServerEvent.ThreadStarted -> {
                // No-op for UI; keep session id if needed.
                currentSessionId = sessionIdProvider() ?: currentSessionId
            }

            is AppServerEvent.TurnStarted -> {
                turnStartTimeMs = System.currentTimeMillis()
                indexCounter = 0
                itemIdToIndex.clear()
                lastUsage = null
                // 重置累积器
                accumulatedText.clear()
                accumulatedThinking.clear()
                accumulatedToolUses.clear()
                currentMessageId = event.turn.id

                val sessionId = resolveSessionId()
                result += MessageStartedEvent(
                    provider = AiAgentProvider.CODEX,
                    sessionId = sessionId,
                    messageId = event.turn.id
                )
                result += TurnStartedEvent(AiAgentProvider.CODEX)
            }

            is AppServerEvent.ItemStarted -> {
                val index = nextIndexForItem(event.item.id)
                val toolContent = buildToolUseContent(event.item)
                // 累积工具调用内容，用于在 TurnCompleted 时发送 AssistantMessageEvent
                if (toolContent != null) {
                    accumulatedToolUses.add(toolContent)
                }
                result += ContentStartedEvent(
                    provider = AiAgentProvider.CODEX,
                    index = index,
                    contentType = resolveContentType(event.item),
                    toolName = toolContent?.name ?: resolveToolName(event.item),
                    content = toolContent
                )
            }

            is AppServerEvent.ItemCompleted -> {
                val index = indexForItem(event.item.id)
                if (index != null) {
                    itemIdToIndex.remove(event.item.id)
                    val resultContent = buildToolResultContent(event.item) ?: convertThreadItem(event.item)
                    result += ContentCompletedEvent(
                        provider = AiAgentProvider.CODEX,
                        index = index,
                        content = resultContent
                    )
                }
            }

            is AppServerEvent.AgentMessageDelta -> {
                val (index, started) = ensureIndexWithStart(event.itemId)
                if (started) {
                    result += ContentStartedEvent(
                        provider = AiAgentProvider.CODEX,
                        index = index,
                        contentType = "text"
                    )
                }
                // 累积文本内容
                accumulatedText.append(event.delta)
                result += ContentDeltaEvent(
                    provider = AiAgentProvider.CODEX,
                    index = index,
                    delta = TextDeltaPayload(event.delta)
                )
            }

            is AppServerEvent.ReasoningDelta -> {
                val (index, started) = ensureIndexWithStart(event.itemId)
                if (started) {
                    result += ContentStartedEvent(
                        provider = AiAgentProvider.CODEX,
                        index = index,
                        contentType = "thinking"
                    )
                }
                // 累积思考内容
                accumulatedThinking.append(event.delta)
                result += ContentDeltaEvent(
                    provider = AiAgentProvider.CODEX,
                    index = index,
                    delta = ThinkingDeltaPayload(event.delta)
                )
            }

            is AppServerEvent.CommandOutputDelta -> {
                // 忽略命令输出增量，完整输出会在 ItemCompleted 时通过 aggregatedOutput 返回
                // 打印日志用于调试
                println("[Codex] CommandOutputDelta ignored: itemId=${event.itemId}, delta length=${event.delta.length}")
            }

            is AppServerEvent.TokenUsageUpdated -> {
                lastUsage = event.usage
            }

            is AppServerEvent.TurnCompleted -> {
                val durationMs = resolveDurationMs()
                val usage = lastUsage?.toUnifiedUsage()

                when (event.turn.status) {
                    TurnStatus.Failed -> {
                        val errorMsg = event.turn.error?.message ?: "Codex turn failed"
                        result += ResultSummaryEvent(
                            provider = AiAgentProvider.CODEX,
                            subtype = "error_during_execution",
                            durationMs = durationMs,
                            durationApiMs = durationMs,
                            isError = true,
                            numTurns = 1,
                            sessionId = resolveSessionId(),
                            usage = lastUsage?.toUsageJson(),
                            result = errorMsg
                        )
                        result += TurnFailedEvent(AiAgentProvider.CODEX, errorMsg)
                    }
                    TurnStatus.Interrupted -> {
                        result += ResultSummaryEvent(
                            provider = AiAgentProvider.CODEX,
                            subtype = "interrupted",
                            durationMs = durationMs,
                            durationApiMs = durationMs,
                            isError = false,
                            numTurns = 1,
                            sessionId = resolveSessionId(),
                            usage = lastUsage?.toUsageJson(),
                            result = "Turn interrupted"
                        )
                        result += TurnFailedEvent(AiAgentProvider.CODEX, "Turn interrupted")
                    }
                    TurnStatus.Completed,
                    TurnStatus.InProgress -> {
                        // 发送 AssistantMessageEvent，包含累积的文本和思考内容
                        val contentBlocks = buildContentBlocks()
                        if (contentBlocks.isNotEmpty()) {
                            result += AssistantMessageEvent(
                                provider = AiAgentProvider.CODEX,
                                id = currentMessageId,
                                content = contentBlocks,
                                tokenUsage = usage
                            )
                        }
                        result += TurnCompletedEvent(
                            provider = AiAgentProvider.CODEX,
                            usage = usage
                        )
                        result += ResultSummaryEvent(
                            provider = AiAgentProvider.CODEX,
                            subtype = "completed",
                            durationMs = durationMs,
                            durationApiMs = durationMs,
                            isError = false,
                            numTurns = 1,
                            sessionId = resolveSessionId(),
                            usage = lastUsage?.toUsageJson(),
                            result = null
                        )
                    }
                }

            }

            is AppServerEvent.Error -> {
                val durationMs = resolveDurationMs()
                val errorMsg = event.message.ifBlank { "Codex error" }
                result += ResultSummaryEvent(
                    provider = AiAgentProvider.CODEX,
                    subtype = "error_during_execution",
                    durationMs = durationMs,
                    durationApiMs = durationMs,
                    isError = true,
                    numTurns = 1,
                    sessionId = resolveSessionId(),
                    usage = lastUsage?.toUsageJson(),
                    result = errorMsg
                )
                result += TurnFailedEvent(AiAgentProvider.CODEX, errorMsg)
            }

            is AppServerEvent.CommandApprovalRequired,
            is AppServerEvent.FileChangeApprovalRequired -> {
                // Approval is handled by the client; no UI stream event needed.
            }
    }
        return result
    }

    private fun buildToolUseContent(item: ThreadItem): ToolUseContent? =
        when (item) {
            is ThreadItem.CommandExecution -> ToolUseContent(
                id = item.id,
                name = "Bash",
                input = buildCommandExecutionInput(item),
                status = ContentStatus.IN_PROGRESS
            )
            is ThreadItem.FileChange -> ToolUseContent(
                id = item.id,
                name = resolveFileChangeToolName(item),
                input = buildFileChangeInput(item),
                status = ContentStatus.IN_PROGRESS
            )
            is ThreadItem.McpToolCall -> ToolUseContent(
                id = item.id,
                name = buildMcpToolName(item.server, item.tool),
                input = buildMcpToolInput(item),
                status = ContentStatus.IN_PROGRESS
            )
            is ThreadItem.WebSearch -> ToolUseContent(
                id = item.id,
                name = "WebSearch",
                input = buildWebSearchInput(item),
                status = ContentStatus.IN_PROGRESS
            )
            else -> null
        }

    private fun buildToolResultContent(item: ThreadItem): ToolResultContent? =
        when (item) {
            is ThreadItem.CommandExecution -> {
                val isError = item.status == CommandExecutionStatus.Failed ||
                    item.status == CommandExecutionStatus.Declined ||
                    (item.exitCode != null && item.exitCode != 0)
                val output = item.aggregatedOutput ?: ""
                val content = if (output.isNotBlank()) JsonPrimitive(output) else null
                ToolResultContent(
                    toolUseId = item.id,
                    content = content,
                    isError = isError
                )
            }
            is ThreadItem.FileChange -> {
                val isError = item.status == PatchApplyStatus.Failed ||
                    item.status == PatchApplyStatus.Declined
                val content = buildJsonObject {
                    put("status", item.status.name)
                    put(
                        "changes",
                        buildJsonArray {
                            item.changes.forEach { change ->
                                add(buildJsonObject {
                                    put("path", change.path)
                                    put("kind", change.kind.toKindString())
                                    put("diff", change.diff)
                                })
                            }
                        }
                    )
                }
                ToolResultContent(
                    toolUseId = item.id,
                    content = content,
                    isError = isError
                )
            }
            is ThreadItem.McpToolCall -> {
                val isError = item.status == McpToolCallStatus.Failed || item.error != null
                val content = if (isError) {
                    JsonPrimitive(item.error?.message ?: "MCP tool failed")
                } else {
                    item.result.toResultJson()
                }
                ToolResultContent(
                    toolUseId = item.id,
                    content = content,
                    isError = isError
                )
            }
            is ThreadItem.WebSearch -> ToolResultContent(
                toolUseId = item.id,
                content = JsonPrimitive(item.query),
                isError = false
            )
            else -> null
        }

    /**
     * 将 ThreadItem 转换为 UnifiedContentBlock。
     * 注意：工具类型（CommandExecution, FileChange, McpToolCall, WebSearch）
     * 应优先使用 buildToolResultContent() 转换为 ToolResultContent。
     * 此函数作为回退，处理非工具类型的 ThreadItem。
     */
    private fun convertThreadItem(item: ThreadItem): UnifiedContentBlock =
        when (item) {
            is ThreadItem.AgentMessage -> TextContent(item.text)
            is ThreadItem.Reasoning -> ThinkingContent(buildReasoningText(item))
            // 工具类型统一由 buildToolResultContent() 处理，返回 ToolResultContent
            // 此处作为回退（理论上不会执行到），也返回 ToolResultContent
            is ThreadItem.CommandExecution -> buildToolResultContent(item)!!
            is ThreadItem.FileChange -> buildToolResultContent(item)!!
            is ThreadItem.McpToolCall -> buildToolResultContent(item)!!
            is ThreadItem.WebSearch -> buildToolResultContent(item)!!
            is ThreadItem.EnteredReviewMode -> TextContent(item.review)
            is ThreadItem.ExitedReviewMode -> TextContent(item.review)
            is ThreadItem.ImageView -> TextContent("[Image: ${item.path}]")
            is ThreadItem.UserMessage -> TextContent(userMessageText(item))
        }

    private fun buildReasoningText(item: ThreadItem.Reasoning): String {
        return when {
            item.summary.isNotEmpty() -> item.summary.joinToString("\\n")
            item.content.isNotEmpty() -> item.content.joinToString("\\n")
            else -> ""
        }
    }

    private fun userMessageText(item: ThreadItem.UserMessage): String {
        if (item.content.isEmpty()) return ""
        return item.content.joinToString("\\n") { input ->
            when (input) {
                is com.asakii.codex.agent.sdk.appserver.UserInput.Text -> input.text
                is com.asakii.codex.agent.sdk.appserver.UserInput.Image -> "[Image: ${input.url}]"
                is com.asakii.codex.agent.sdk.appserver.UserInput.LocalImage -> "[Image: ${input.path}]"
            }
        }
    }

    private fun buildCommandExecutionInput(item: ThreadItem.CommandExecution): JsonElement =
        buildJsonObject {
            put("type", "CommandExecution")
            put("command", item.command)
            put("cwd", item.cwd)
            put("status", item.status.name)
            item.processId?.let { put("processId", it) }
            item.exitCode?.let { put("exitCode", it) }
            item.durationMs?.let { put("durationMs", it) }
        }

    private fun buildFileChangeInput(item: ThreadItem.FileChange): JsonElement {
        val primary = item.changes.firstOrNull()
        val operation = primary?.kind?.toOperation() ?: "edit"
        val diff = primary?.diff ?: ""
        val (oldContent, newContent) = extractDiffOldNew(diff)

        return buildJsonObject {
            put("type", "FileChange")
            if (primary != null) {
                put("path", primary.path)
                put("operation", operation)
                put("diff", primary.diff)
            }
            if (operation == "create" && newContent.isNotBlank()) {
                put("content", newContent)
            }
            if (oldContent.isNotBlank()) {
                put("oldContent", oldContent)
                put("old_string", oldContent)
            }
            if (newContent.isNotBlank()) {
                put("newContent", newContent)
                put("new_string", newContent)
            }
            put("status", item.status.name)
            put(
                "changes",
                buildJsonArray {
                    item.changes.forEach { change ->
                        add(buildJsonObject {
                            put("path", change.path)
                            put("kind", change.kind.toKindString())
                            put("diff", change.diff)
                        })
                    }
                }
            )
        }
    }

    private fun buildMcpToolInput(item: ThreadItem.McpToolCall): JsonElement =
        item.arguments

    private fun buildWebSearchInput(item: ThreadItem.WebSearch): JsonElement =
        buildJsonObject {
            put("type", "WebSearch")
            put("query", item.query)
        }

    private fun extractDiffOldNew(diff: String): Pair<String, String> {
        if (diff.isBlank()) return "" to ""
        val oldLines = mutableListOf<String>()
        val newLines = mutableListOf<String>()
        diff.lineSequence().forEach { line ->
            when {
                line.startsWith("---") || line.startsWith("+++") || line.startsWith("@@") -> Unit
                line.startsWith("-") -> oldLines.add(line.removePrefix("-"))
                line.startsWith("+") -> newLines.add(line.removePrefix("+"))
                else -> Unit
            }
        }
        return oldLines.joinToString("\n") to newLines.joinToString("\n")
    }

    private fun resolveContentType(item: ThreadItem): String =
        when (item) {
            is ThreadItem.AgentMessage -> "text"
            is ThreadItem.Reasoning -> "thinking"
            is ThreadItem.CommandExecution -> "command_execution"
            is ThreadItem.FileChange -> "file_change"
            is ThreadItem.McpToolCall -> "tool_use"
            is ThreadItem.WebSearch -> "web_search"
            is ThreadItem.UserMessage -> "text"
            is ThreadItem.EnteredReviewMode -> "text"
            is ThreadItem.ExitedReviewMode -> "text"
            is ThreadItem.ImageView -> "text"
        }

    private fun resolveToolName(item: ThreadItem): String? =
        when (item) {
            is ThreadItem.CommandExecution -> "Bash"
            is ThreadItem.FileChange -> "Edit"
            is ThreadItem.McpToolCall -> buildMcpToolName(item.server, item.tool)
            is ThreadItem.WebSearch -> "WebSearch"
            else -> null
        }

    private fun resolveFileChangeToolName(item: ThreadItem.FileChange): String {
        val primary = item.changes.firstOrNull()
        return when (primary?.kind) {
            is PatchChangeKind.Add -> "Write"
            else -> "Edit"
        }
    }

    private fun PatchChangeKind.toOperation(): String = when (this) {
        is PatchChangeKind.Add -> "create"
        is PatchChangeKind.Delete -> "delete"
        is PatchChangeKind.Update -> "edit"
    }

    private fun buildMcpToolName(server: String, tool: String): String {
        if (server.isBlank() || tool.isBlank()) {
            return "mcp__unknown"
        }
        return "mcp__${server}__${tool}"
    }

    private fun PatchChangeKind.toKindString(): String = when (this) {
        is PatchChangeKind.Add -> "add"
        is PatchChangeKind.Delete -> "delete"
        is PatchChangeKind.Update -> "update"
    }

    private fun McpToolCallResult?.toResultJson(): JsonElement? {
        if (this == null) return null
        if (structuredContent != null) return structuredContent
        // 直接返回 content 列表，而不是序列化整个对象
        // 这样前端收到的格式与 Claude Code 一致
        if (content.isNotEmpty()) {
            return if (content.size == 1) content.first() else buildJsonArray { content.forEach { add(it) } }
        }
        return null
    }

    private fun ThreadTokenUsage.toUnifiedUsage(): UnifiedUsage =
        UnifiedUsage(
            inputTokens = last.inputTokens.toUsageInt(),
            outputTokens = last.outputTokens.toUsageInt(),
            cachedInputTokens = last.cachedInputTokens.toUsageInt(),
            provider = AiAgentProvider.CODEX
        )

    private fun ThreadTokenUsage.toUsageJson(): JsonElement =
        buildJsonObject {
            put("input_tokens", last.inputTokens)
            put("output_tokens", last.outputTokens)
            put("cached_input_tokens", last.cachedInputTokens)
            put("total_tokens", last.totalTokens)
            put("reasoning_output_tokens", last.reasoningOutputTokens)
            modelContextWindow?.let { put("model_context_window", it) }
        }

    private fun Long.toUsageInt(): Int {
        return when {
            this > Int.MAX_VALUE -> Int.MAX_VALUE
            this < Int.MIN_VALUE -> Int.MIN_VALUE
            else -> this.toInt()
        }
    }

    private fun resolveSessionId(): String {
        val provided = sessionIdProvider()?.takeIf { it.isNotBlank() }
        if (provided != null) {
            currentSessionId = provided
            return provided
        }
        return currentSessionId ?: idGenerator().also { currentSessionId = it }
    }

    private fun resolveDurationMs(): Long {
        val now = System.currentTimeMillis()
        val start = turnStartTimeMs ?: now
        turnStartTimeMs = null
        return (now - start).coerceAtLeast(0)
    }

    private fun indexForItem(itemId: String): Int? = itemIdToIndex[itemId]

    private fun nextIndexForItem(itemId: String): Int {
        return itemIdToIndex.getOrPut(itemId) { indexCounter++ }
    }

    private fun ensureIndexWithStart(itemId: String): Pair<Int, Boolean> {
        val existing = itemIdToIndex[itemId]
        if (existing != null) return existing to false
        val index = nextIndexForItem(itemId)
        return index to true
    }

    /**
     * 构建内容块列表，包含累积的思考内容、文本内容和工具调用
     */
    private fun buildContentBlocks(): List<UnifiedContentBlock> {
        val blocks = mutableListOf<UnifiedContentBlock>()
        // 思考内容放在前面
        if (accumulatedThinking.isNotEmpty()) {
            blocks.add(ThinkingContent(accumulatedThinking.toString()))
        }
        // 文本内容
        if (accumulatedText.isNotEmpty()) {
            blocks.add(TextContent(accumulatedText.toString()))
        }
        // 工具调用内容（用于前端匹配 tool_use 和 tool_result）
        blocks.addAll(accumulatedToolUses)
        return blocks
    }
}




