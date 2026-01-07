package com.asakii.plugin.mcp.tools.terminal

/**
 * 终端工具结果格式化器
 *
 * 将 JSON 格式的结果转换为易读的 Markdown 格式
 */
object TerminalResultFormatter {

    /**
     * 格式化 Terminal 执行结果
     */
    fun formatTerminalResult(
        success: Boolean,
        sessionId: String?,
        sessionName: String?,
        message: String?,
        error: String?
    ): String = buildString {
        if (success) {
            appendLine("**Session:** `$sessionId` ($sessionName)")
            appendLine()
            appendLine("**Status:** Command sent")
            message?.let {
                appendLine()
                appendLine("> $it")
            }
        } else {
            appendLine("**Error:** $error")
            sessionId?.let { appendLine("**Session:** `$it`") }
        }
    }

    /**
     * 格式化 TerminalRead 结果
     */
    fun formatReadResult(
        success: Boolean,
        sessionId: String?,
        isRunning: Boolean?,
        output: String?,
        lineCount: Int?,
        searchMatches: List<SearchMatch>?,
        waitTimedOut: Boolean?,
        waitMessage: String?,
        error: String?
    ): String = buildString {
        if (success) {
            // 会话信息行
            val status = when (isRunning) {
                true -> "running"
                false -> "idle"
                null -> "unknown"
            }
            appendLine("**Session:** `$sessionId` | **Status:** $status")

            // 等待超时提示
            if (waitTimedOut == true) {
                appendLine()
                appendLine("> **Warning:** $waitMessage")
            }

            appendLine()

            // 搜索匹配结果
            if (searchMatches != null) {
                appendLine("**Matches:** ${searchMatches.size}")
                appendLine()
                searchMatches.forEach { match ->
                    appendLine("- **Line ${match.lineNumber}:** `${match.line.take(100)}${if (match.line.length > 100) "..." else ""}`")
                }
            } else {
                // 普通输出
                appendLine("**Lines:** $lineCount")
                appendLine()
                appendLine("```")
                appendLine(output?.trimEnd() ?: "")
                appendLine("```")
            }
        } else {
            appendLine("**Error:** $error")
            sessionId?.let { appendLine("**Session:** `$it`") }
        }
    }

    /**
     * 格式化 TerminalList 结果
     */
    fun formatListResult(
        success: Boolean,
        count: Int,
        sessions: List<SessionInfo>,
        error: String?
    ): String = buildString {
        if (success) {
            appendLine("**Terminal Sessions:** $count")
            appendLine()
            if (sessions.isEmpty()) {
                appendLine("_No active sessions_")
            } else {
                sessions.forEach { session ->
                    val status = if (session.isRunning) "running" else "idle"
                    appendLine("- `${session.id}` **${session.name}** [$status] (${session.shellType})")
                    session.outputPreview?.let { preview ->
                        if (preview.isNotBlank()) {
                            val shortPreview = preview.lines().lastOrNull { it.isNotBlank() }?.take(60) ?: ""
                            if (shortPreview.isNotBlank()) {
                                appendLine("  > `$shortPreview`")
                            }
                        }
                    }
                }
            }
        } else {
            appendLine("**Error:** $error")
        }
    }

    /**
     * 格式化 TerminalKill 结果
     */
    fun formatKillResult(
        success: Boolean,
        killed: List<String>,
        failed: List<String>,
        message: String?,
        error: String?
    ): String = buildString {
        if (success) {
            appendLine("**Result:** $message")
            if (killed.isNotEmpty()) {
                appendLine()
                appendLine("**Killed:** ${killed.joinToString(", ") { "`$it`" }}")
            }
            if (failed.isNotEmpty()) {
                appendLine()
                appendLine("**Failed:** ${failed.joinToString(", ") { "`$it`" }}")
            }
        } else {
            appendLine("**Error:** $error")
        }
    }

    /**
     * 格式化 TerminalTypes 结果
     */
    fun formatTypesResult(
        success: Boolean,
        platform: String?,
        types: List<ShellTypeInfo>,
        defaultType: String?,
        error: String?
    ): String = buildString {
        if (success) {
            appendLine("**Platform:** $platform | **Default:** $defaultType")
            appendLine()
            appendLine("**Available Shells:**")
            types.forEach { type ->
                val defaultMark = if (type.isDefault) " (default)" else ""
                appendLine("- `${type.name}` - ${type.displayName}$defaultMark")
            }
        } else {
            appendLine("**Error:** $error")
        }
    }

    /**
     * 格式化 TerminalRename 结果
     */
    fun formatRenameResult(
        success: Boolean,
        sessionId: String?,
        newName: String?,
        message: String?,
        error: String?
    ): String = buildString {
        if (success) {
            appendLine("**Session:** `$sessionId` renamed to **$newName**")
            message?.let { appendLine("> $it") }
        } else {
            appendLine("**Error:** $error")
            sessionId?.let { appendLine("**Session:** `$it`") }
        }
    }

    /**
     * 格式化 TerminalInterrupt 结果
     */
    fun formatInterruptResult(
        success: Boolean,
        sessionId: String?,
        signal: String?,
        wasRunning: Boolean?,
        isStillRunning: Boolean?,
        message: String?,
        error: String?
    ): String = buildString {
        if (success) {
            appendLine("**Session:** `$sessionId` | **Signal:** $signal")
            appendLine()
            val runningStatus = when {
                wasRunning == true && isStillRunning == true -> "Command may still be stopping"
                wasRunning == true && isStillRunning == false -> "Command stopped"
                wasRunning == false -> "No command was running"
                else -> "Status unknown"
            }
            appendLine("**Status:** $runningStatus")
            message?.let { appendLine("> $it") }
        } else {
            appendLine("**Error:** $error")
            sessionId?.let { appendLine("**Session:** `$it`") }
        }
    }

    // 数据类
    data class SearchMatch(
        val lineNumber: Int,
        val line: String,
        val context: String
    )

    data class SessionInfo(
        val id: String,
        val name: String,
        val shellType: String,
        val isRunning: Boolean,
        val outputPreview: String? = null
    )

    data class ShellTypeInfo(
        val name: String,
        val displayName: String,
        val isDefault: Boolean
    )
}
