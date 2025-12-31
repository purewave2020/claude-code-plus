package com.asakii.plugin.mcp.git

import com.asakii.plugin.mcp.getString
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject

/**
 * 设置 Commit Message 工具
 *
 * 设置或追加 IDEA Commit 面板中的 commit message
 * 返回格式：Markdown
 */
class SetCommitMessageTool(private val project: Project) {

    suspend fun execute(arguments: JsonObject): String {
        val message = arguments.getString("message")
        if (message.isNullOrBlank()) {
            return buildString {
                appendLine("# Set Commit Message")
                appendLine()
                appendLine("**Error**: `message` parameter is required")
            }
        }

        val mode = arguments.getString("mode") ?: "replace"
        val append = mode == "append"

        val accessor = CommitPanelAccessor.getInstance(project)

        if (!accessor.isCommitPanelOpen()) {
            return buildString {
                appendLine("# Set Commit Message")
                appendLine()
                appendLine("**Error**: Commit panel is not open")
                appendLine()
                appendLine("Please open the Commit tool window first.")
            }
        }

        accessor.setCommitMessage(message, append)

        return buildString {
            appendLine("# Set Commit Message")
            appendLine()
            appendLine("**Status**: Success")
            appendLine("- **Mode**: ${if (append) "Append" else "Replace"}")
            appendLine("- **Message Length**: ${message.length} characters")
            appendLine()
            appendLine("## Message Set")
            appendLine("```")
            appendLine(message)
            appendLine("```")
        }
    }
}
