package com.asakii.server.history

import com.asakii.claude.agent.sdk.types.ToolType
import com.asakii.codex.agent.sdk.appserver.CommandExecutionStatus
import com.asakii.codex.agent.sdk.appserver.McpToolCallResult
import com.asakii.codex.agent.sdk.appserver.McpToolCallStatus
import com.asakii.codex.agent.sdk.appserver.PatchApplyStatus
import com.asakii.codex.agent.sdk.appserver.PatchChangeKind
import com.asakii.codex.agent.sdk.appserver.ThreadInfo
import com.asakii.codex.agent.sdk.appserver.ThreadItem
import com.asakii.codex.agent.sdk.appserver.UserInput
import com.asakii.rpc.api.RpcAssistantMessage
import com.asakii.rpc.api.RpcContentBlock
import com.asakii.rpc.api.RpcContentStatus
import com.asakii.rpc.api.RpcErrorBlock
import com.asakii.rpc.api.RpcHistoryMetadata
import com.asakii.rpc.api.RpcHistoryResult
import com.asakii.rpc.api.RpcImageBlock
import com.asakii.rpc.api.RpcImageSource
import com.asakii.rpc.api.RpcMessage
import com.asakii.rpc.api.RpcMessageContent
import com.asakii.rpc.api.RpcProvider
import com.asakii.rpc.api.RpcTextBlock
import com.asakii.rpc.api.RpcThinkingBlock
import com.asakii.rpc.api.RpcToolResultBlock
import com.asakii.rpc.api.RpcToolUseBlock
import com.asakii.rpc.api.RpcUserMessage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

object CodexHistoryMapper {

    fun buildMetadata(thread: ThreadInfo, fallbackProjectPath: String): RpcHistoryMetadata {
        val totalLines = countMessages(thread)
        val projectPath = thread.cwd.takeIf { it.isNotBlank() } ?: fallbackProjectPath
        return RpcHistoryMetadata(
            totalLines = totalLines,
            sessionId = thread.id,
            projectPath = projectPath,
            customTitle = null
        )
    }

    fun buildHistoryResult(thread: ThreadInfo, offset: Int, limit: Int): RpcHistoryResult {
        val total = countMessages(thread)
        val (start, end) = computeRange(total, offset, limit)
        val messages = buildMessages(thread, start, end)
        return RpcHistoryResult(
            messages = messages,
            offset = offset,
            count = messages.size,
            availableCount = total
        )
    }

    fun computeRange(total: Int, offset: Int, limit: Int): Pair<Int, Int> {
        if (total <= 0) return 0 to 0
        if (offset < 0 && limit > 0) {
            val start = (total - limit).coerceAtLeast(0)
            return start to total
        }
        val start = offset.coerceAtLeast(0).coerceAtMost(total)
        val end = if (limit > 0) (start + limit).coerceAtMost(total) else total
        return start to end
    }

    fun countMessages(thread: ThreadInfo): Int {
        var count = 0
        var assistantHasContent = false

        for (turn in thread.turns) {
            for (item in turn.items) {
                if (item is ThreadItem.UserMessage) {
                    if (assistantHasContent) {
                        count += 1
                        assistantHasContent = false
                    }
                    count += 1
                } else {
                    assistantHasContent = true
                }
            }
            if (assistantHasContent) {
                count += 1
                assistantHasContent = false
            }
        }

        return count
    }

    fun buildMessages(thread: ThreadInfo, start: Int, end: Int): List<RpcMessage> {
        if (start >= end) return emptyList()

        val result = mutableListOf<RpcMessage>()
        var produced = 0

        var assistantHasContent = false
        var assistantCollecting = false
        val assistantBlocks = mutableListOf<RpcContentBlock>()

        fun flushAssistant() {
            if (!assistantHasContent) return
            if (assistantCollecting) {
                result += RpcAssistantMessage(
                    message = RpcMessageContent(content = assistantBlocks.toList()),
                    provider = RpcProvider.CODEX
                )
            }
            produced += 1
            assistantHasContent = false
            assistantCollecting = false
            assistantBlocks.clear()
        }

        outer@ for (turn in thread.turns) {
            for (item in turn.items) {
                when (item) {
                    is ThreadItem.UserMessage -> {
                        flushAssistant()

                        val shouldCollect = produced in start until end
                        if (shouldCollect) {
                            result += RpcUserMessage(
                                message = RpcMessageContent(content = toUserContentBlocks(item.content)),
                                provider = RpcProvider.CODEX,
                                uuid = item.id
                            )
                        }
                        produced += 1
                    }

                    else -> {
                        if (!assistantHasContent) {
                            assistantCollecting = produced in start until end
                        }
                        assistantHasContent = true
                        if (assistantCollecting) {
                            assistantBlocks.addAll(toAssistantBlocks(item))
                        }
                    }
                }

                if (produced >= end) {
                    break@outer
                }
            }

            flushAssistant()
            if (produced >= end) break@outer
        }

        flushAssistant()
        return result
    }

    fun toUserContentBlocks(inputs: List<UserInput>): List<RpcContentBlock> {
        if (inputs.isEmpty()) return listOf(RpcTextBlock(""))

        val blocks = mutableListOf<RpcContentBlock>()
        for (input in inputs) {
            when (input) {
                is UserInput.Text -> blocks += RpcTextBlock(input.text)
                is UserInput.Image -> blocks += RpcImageBlock(
                    source = RpcImageSource(
                        type = "url",
                        mediaType = "image/*",
                        url = input.url
                    )
                )
                is UserInput.LocalImage -> blocks += readLocalImageBlock(input.path)
            }
        }
        return blocks.ifEmpty { listOf(RpcTextBlock("")) }
    }

    private fun readLocalImageBlock(path: String): RpcContentBlock {
        val imagePath = runCatching { Path.of(path) }.getOrNull()
            ?: return RpcErrorBlock("Invalid local image path: $path")

        return try {
            if (!Files.exists(imagePath)) {
                return RpcErrorBlock("Local image not found: $path")
            }
            val bytes = Files.readAllBytes(imagePath)
            val base64 = Base64.getEncoder().encodeToString(bytes)
            RpcImageBlock(
                source = RpcImageSource(
                    type = "base64",
                    mediaType = guessImageMediaType(imagePath),
                    data = base64
                )
            )
        } catch (e: Exception) {
            RpcErrorBlock("Failed to read local image: ${e.message ?: path}")
        }
    }

    private fun guessImageMediaType(path: Path): String {
        val name = path.fileName?.toString()?.lowercase() ?: return "image/*"
        return when {
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".gif") -> "image/gif"
            else -> "image/*"
        }
    }

    private fun toAssistantBlocks(item: ThreadItem): List<RpcContentBlock> =
        when (item) {
            is ThreadItem.AgentMessage -> listOf(RpcTextBlock(item.text))
            is ThreadItem.Reasoning -> listOf(RpcThinkingBlock(thinking = buildReasoningText(item)))
            is ThreadItem.CommandExecution -> buildCommandExecutionBlocks(item)
            is ThreadItem.FileChange -> buildFileChangeBlocks(item)
            is ThreadItem.McpToolCall -> buildMcpToolCallBlocks(item)
            is ThreadItem.WebSearch -> buildWebSearchBlocks(item)
            is ThreadItem.ImageView -> listOf(RpcTextBlock("[Image: ${item.path}]"))
            is ThreadItem.EnteredReviewMode -> listOf(RpcTextBlock(item.review))
            is ThreadItem.ExitedReviewMode -> listOf(RpcTextBlock(item.review))
            is ThreadItem.UserMessage -> listOf(RpcTextBlock(item.content.joinToString("\n") { input -> input.toString() }))
        }

    private fun buildReasoningText(item: ThreadItem.Reasoning): String {
        return when {
            item.summary.isNotEmpty() -> item.summary.joinToString("\n")
            item.content.isNotEmpty() -> item.content.joinToString("\n")
            else -> ""
        }
    }

    private fun buildCommandExecutionBlocks(item: ThreadItem.CommandExecution): List<RpcContentBlock> {
        val status = item.status.toRpcContentStatus()
        val toolName = "Bash"
        val toolType = ToolType.fromToolName(toolName).type
        val input = buildJsonObject {
            put("type", "CommandExecution")
            put("command", item.command)
            put("cwd", item.cwd)
            put("status", item.status.name)
            item.processId?.let { put("processId", it) }
            item.exitCode?.let { put("exitCode", it) }
            item.durationMs?.let { put("durationMs", it) }
        }
        val isError = item.status == CommandExecutionStatus.Failed ||
            item.status == CommandExecutionStatus.Declined ||
            (item.exitCode != null && item.exitCode != 0)

        return listOf(
            RpcToolUseBlock(
                id = item.id,
                toolName = toolName,
                toolType = toolType,
                input = input,
                status = status
            ),
            RpcToolResultBlock(
                toolUseId = item.id,
                content = item.aggregatedOutput?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) },
                isError = isError
            )
        )
    }

    private fun buildFileChangeBlocks(item: ThreadItem.FileChange): List<RpcContentBlock> {
        val status = item.status.toRpcContentStatus()
        val primary = item.changes.firstOrNull()
        val toolName = when (primary?.kind) {
            is PatchChangeKind.Add -> "Write"
            else -> "Edit"
        }
        val toolType = ToolType.fromToolName(toolName).type

        val (oldContent, newContent) = extractDiffOldNew(primary?.diff ?: "")
        val operation = primary?.kind?.toOperation() ?: "edit"

        val input = buildJsonObject {
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

        val isError = item.status == PatchApplyStatus.Failed || item.status == PatchApplyStatus.Declined
        val resultJson = buildJsonObject {
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

        return listOf(
            RpcToolUseBlock(
                id = item.id,
                toolName = toolName,
                toolType = toolType,
                input = input,
                status = status
            ),
            RpcToolResultBlock(
                toolUseId = item.id,
                content = resultJson,
                isError = isError
            )
        )
    }

    private fun buildMcpToolCallBlocks(item: ThreadItem.McpToolCall): List<RpcContentBlock> {
        val status = item.status.toRpcContentStatus()
        val toolName = if (item.server.isBlank() || item.tool.isBlank()) {
            "mcp__unknown"
        } else {
            "mcp__${item.server}__${item.tool}"
        }
        val toolType = ToolType.fromToolName(toolName).type
        val input = buildJsonObject {
            put("type", "McpToolCall")
            put("server", item.server)
            put("tool", item.tool)
            put("toolName", toolName)
            put("arguments", item.arguments)
        }

        val isError = item.status == McpToolCallStatus.Failed || item.error != null
        val resultContent = if (isError) {
            item.result.toResultJson()
                ?: item.error?.message?.let { JsonPrimitive(it) }
                ?: JsonPrimitive("MCP tool failed")
        } else {
            item.result.toResultJson()
        }

        return listOf(
            RpcToolUseBlock(
                id = item.id,
                toolName = toolName,
                toolType = toolType,
                input = input,
                status = status
            ),
            RpcToolResultBlock(
                toolUseId = item.id,
                content = resultContent,
                isError = isError
            )
        )
    }

    private fun buildWebSearchBlocks(item: ThreadItem.WebSearch): List<RpcContentBlock> {
        val toolName = "WebSearch"
        val toolType = ToolType.fromToolName(toolName).type
        val input = buildJsonObject {
            put("type", "WebSearch")
            put("query", item.query)
        }
        return listOf(
            RpcToolUseBlock(
                id = item.id,
                toolName = toolName,
                toolType = toolType,
                input = input,
                status = RpcContentStatus.COMPLETED
            ),
            RpcToolResultBlock(
                toolUseId = item.id,
                content = JsonPrimitive(item.query),
                isError = false
            )
        )
    }

    private fun CommandExecutionStatus.toRpcContentStatus(): RpcContentStatus = when (this) {
        CommandExecutionStatus.InProgress -> RpcContentStatus.IN_PROGRESS
        CommandExecutionStatus.Completed -> RpcContentStatus.COMPLETED
        CommandExecutionStatus.Failed,
        CommandExecutionStatus.Declined -> RpcContentStatus.FAILED
    }

    private fun PatchApplyStatus.toRpcContentStatus(): RpcContentStatus = when (this) {
        PatchApplyStatus.InProgress -> RpcContentStatus.IN_PROGRESS
        PatchApplyStatus.Completed -> RpcContentStatus.COMPLETED
        PatchApplyStatus.Failed,
        PatchApplyStatus.Declined -> RpcContentStatus.FAILED
    }

    private fun McpToolCallStatus.toRpcContentStatus(): RpcContentStatus = when (this) {
        McpToolCallStatus.InProgress -> RpcContentStatus.IN_PROGRESS
        McpToolCallStatus.Completed -> RpcContentStatus.COMPLETED
        McpToolCallStatus.Failed -> RpcContentStatus.FAILED
    }

    private fun PatchChangeKind.toOperation(): String = when (this) {
        is PatchChangeKind.Add -> "create"
        is PatchChangeKind.Delete -> "delete"
        is PatchChangeKind.Update -> "edit"
    }

    private fun PatchChangeKind.toKindString(): String = when (this) {
        is PatchChangeKind.Add -> "add"
        is PatchChangeKind.Delete -> "delete"
        is PatchChangeKind.Update -> "update"
    }

    private fun McpToolCallResult?.toResultJson(): JsonElement? {
        if (this == null) return null
        if (structuredContent != null) return structuredContent
        // 直接返回 content，避免嵌套 { content: { content: [...] } }
        if (content.isNotEmpty()) {
            return if (content.size == 1) content.first() else buildJsonArray { content.forEach { add(it) } }
        }
        return null
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
}
