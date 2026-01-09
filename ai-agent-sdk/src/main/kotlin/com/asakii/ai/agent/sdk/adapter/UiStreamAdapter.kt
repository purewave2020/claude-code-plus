package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.model.*
import com.asakii.claude.agent.sdk.types.ToolType
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 将归一化事件转换为前端直接使用的 UI 事件。
 */
class UiStreamAdapter {
    private object CodexToolTypes {
        const val BASH = "CODEX_BASH"
        const val WRITE = "CODEX_WRITE"
        const val EDIT = "CODEX_EDIT"
        const val MCP = "CODEX_MCP"
        const val WEB_SEARCH = "CODEX_WEB_SEARCH"
        const val UNKNOWN = "CODEX_UNKNOWN"
    }

    // 维护 index → toolId 的映射，用于在 delta 事件中获取正确的 toolId
    private val indexToToolIdMap = mutableMapOf<Int, String>()
    // 维护 index → parentToolUseId 的映射，用于在 delta/complete 事件中获取正确的 parentToolUseId
    private val indexToParentToolUseIdMap = mutableMapOf<Int, String>()

    fun convert(event: NormalizedStreamEvent): List<UiStreamEvent> =
        when (event) {
            is MessageStartedEvent -> listOf(UiMessageStart(event.messageId, event.initialContent))
            is ContentDeltaEvent -> convertDelta(event)
            is ContentStartedEvent -> convertContentStart(event)
            is ContentCompletedEvent -> convertContentComplete(event)
            is TurnCompletedEvent -> listOf(UiMessageComplete(event.usage))
            is TurnFailedEvent -> listOf(UiError(event.error))
            is ResultSummaryEvent -> listOf(
                UiResultMessage(
                    subtype = event.subtype,
                    durationMs = event.durationMs,
                    durationApiMs = event.durationApiMs,
                    isError = event.isError,
                    numTurns = event.numTurns,
                    sessionId = event.sessionId,
                    totalCostUsd = event.totalCostUsd,
                    usage = event.usage,
                    result = event.result
                )
            )
            is AssistantMessageEvent -> listOf(
                UiAssistantMessage(id = event.id, content = event.content, parentToolUseId = event.parentToolUseId, uuid = event.uuid)
            )
            is UserMessageEvent -> listOf(
                UiUserMessage(event.content, event.isReplay, event.parentToolUseId, event.uuid)
            )
            is StatusSystemEvent -> listOf(
                UiStatusSystem(status = event.status, sessionId = event.sessionId)
            )
            is CompactBoundaryEvent -> listOf(
                UiCompactBoundary(
                    sessionId = event.sessionId,
                    trigger = event.trigger,
                    preTokens = event.preTokens
                )
            )
            is SystemInitEvent -> listOf(
                UiSystemInit(
                    sessionId = event.sessionId,
                    cwd = event.cwd,
                    model = event.model,
                    permissionMode = event.permissionMode,
                    apiKeySource = event.apiKeySource,
                    tools = event.tools,
                    mcpServers = event.mcpServers
                )
            )
            is TurnStartedEvent -> emptyList()
        }

    private fun convertDelta(event: ContentDeltaEvent): List<UiStreamEvent> =
        when (val delta = event.delta) {
            is TextDeltaPayload -> listOf(UiTextDelta(delta.text, index = event.index))
            is ThinkingDeltaPayload -> listOf(UiThinkingDelta(delta.thinking, index = event.index))
            is ToolDeltaPayload -> {
                // 使用映射查找真正的 toolId，如果找不到则 fallback 到 index
                val toolId = indexToToolIdMap[event.index] ?: event.index.toString()
                // 优先使用事件中的 parentToolUseId，其次使用映射中的
                val parentToolUseId = event.parentToolUseId ?: indexToParentToolUseIdMap[event.index]
                listOf(
                    UiToolProgress(
                        toolId = toolId,
                        status = ContentStatus.IN_PROGRESS,
                        outputPreview = delta.partialJson,
                        parentToolUseId = parentToolUseId
                    )
                )
            }
            is CommandDeltaPayload -> {
                emptyList()
            }
        }

    // 追踪内容块索引
    private var contentIndexCounter = 0

    private fun convertContentStart(event: ContentStartedEvent): List<UiStreamEvent> {
        val isToolLike = event.contentType.contains("tool") ||
            event.contentType.contains("command") ||
            event.contentType.contains("file_change") ||
            event.contentType.contains("web_search")

        return when {
            isToolLike -> {
                val toolName = event.toolName ?: event.contentType
                val resolvedToolType = resolveToolType(event, toolName)
                // 对于工具调用，从 content 中获取原生 id（如果有）
                val toolId = (event.content as? ToolUseContent)?.id ?: event.index.toString()
                // 记录 index → toolId 映射，供后续 delta 事件使用
                indexToToolIdMap[event.index] = toolId
                // 记录 index → parentToolUseId 映射
                event.parentToolUseId?.let { indexToParentToolUseIdMap[event.index] = it }
                listOf(
                    UiToolStart(
                        toolId = toolId,
                        toolName = toolName,
                        toolType = resolvedToolType,
                        inputPreview = event.content?.let { it.toString() },
                        input = (event.content as? ToolUseContent)?.input,
                        parentToolUseId = event.parentToolUseId
                    )
                )
            }
            event.contentType.contains("text") -> listOf(UiTextStart(event.index))
            event.contentType.contains("thinking") -> listOf(UiThinkingStart(event.index))
            else -> emptyList()
        }
    }

    // 重置索引计数器和映射（在 message_start 时调用）
    fun resetContentIndex() {
        contentIndexCounter = 0
        indexToToolIdMap.clear()
        indexToParentToolUseIdMap.clear()
    }

    private fun convertContentComplete(event: ContentCompletedEvent): List<UiStreamEvent> {
        // 优先使用事件中的 parentToolUseId，其次使用映射中的
        val parentToolUseId = event.parentToolUseId ?: indexToParentToolUseIdMap[event.index]
        return when (val content = event.content) {
            // TextContent 和 ThinkingContent：不再单独发送，因为 AssistantMessageEvent 已包含完整内容
            // 避免前端重复显示消息
            is TextContent -> emptyList()
            is ThinkingContent -> emptyList()
            is ToolUseContent -> listOf(
                UiToolComplete(
                    toolId = content.id,
                    result = content,
                    parentToolUseId = parentToolUseId
                )
            )
            is ToolResultContent -> listOf(
                UiToolComplete(
                    toolId = content.toolUseId,
                    result = content,
                    parentToolUseId = parentToolUseId
                )
            )
            else -> emptyList()
        }
    }

    private fun resolveToolType(event: ContentStartedEvent, toolName: String): String {
        if (event.provider != AiAgentProvider.CODEX) {
            return ToolType.fromToolName(toolName).type
        }

        return when (event.contentType) {
            "command_execution" -> CodexToolTypes.BASH
            "file_change" -> {
                val operation = (event.content as? ToolUseContent)
                    ?.input
                    ?.jsonObject
                    ?.get("operation")
                    ?.jsonPrimitive
                    ?.contentOrNull
                if (operation == "create") CodexToolTypes.WRITE else CodexToolTypes.EDIT
            }
            "mcp_tool_call" -> CodexToolTypes.MCP
            "web_search" -> CodexToolTypes.WEB_SEARCH
            else -> CodexToolTypes.UNKNOWN
        }
    }
}
