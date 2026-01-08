package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.currentToolUseId
import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getString
import com.intellij.history.LocalHistory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * 编辑文件工具
 *
 * 通过字符串替换来编辑文件内容。
 * 支持相对路径（相对于项目根目录）和绝对路径。
 */
class EditFileTool(private val project: Project) {

    suspend fun execute(arguments: JsonObject): Any {
        val filePath = arguments.getString("filePath")
            ?: return ToolResult.error("Missing required parameter: filePath")

        val oldString = arguments.getString("oldString")
            ?: return ToolResult.error("Missing required parameter: oldString")

        val newString = arguments.getString("newString")
            ?: return ToolResult.error("Missing required parameter: newString")

        val replaceAll = arguments.getBoolean("replaceAll") ?: false

        logger.info { "EditFile: path=$filePath, oldString length=${oldString.length}, newString length=${newString.length}, replaceAll=$replaceAll" }

        val projectBasePath = project.basePath
            ?: return ToolResult.error("Cannot get project path")

        // 解析路径（支持相对路径和绝对路径）
        val absolutePath = resolvePath(filePath, projectBasePath)

        // 安全检查：确保文件在项目目录内
        if (!absolutePath.startsWith(File(projectBasePath).canonicalPath)) {
            return ToolResult.error("File path must be within project directory")
        }

        val file = File(absolutePath)
        if (!file.exists()) {
            return ToolResult.error("File not found: $filePath")
        }

        // 从协程上下文获取 toolUseId，使用 LocalHistory 打标签并缓存
        val toolUseId = currentToolUseId()
        if (toolUseId != null) {
            // 打标签并缓存 Label 对象（用于后续获取原始内容）
            val label = LocalHistory.getInstance().putSystemLabel(project, "claude_$toolUseId")
            FileChangeLabelCache.record(toolUseId, absolutePath, label)
            logger.info { "EditFile: created LocalHistory label for toolUseId=$toolUseId" }
        }

        return try {
            var result: Any = ""
            ApplicationManager.getApplication().invokeAndWait {
                result = WriteAction.compute<Any, Exception> {
                    editFileContent(file, oldString, newString, replaceAll, filePath)
                }
            }
            result
        } catch (e: Exception) {
            logger.error(e) { "Error editing file: $filePath" }
            ToolResult.error("Error editing file: ${e.message}")
        }
    }

    /**
     * 解析文件路径
     * 支持相对路径和绝对路径
     */
    private fun resolvePath(path: String, projectBasePath: String): String {
        return if (isRelativePath(path)) {
            File(projectBasePath, path).canonicalPath
        } else {
            File(path).canonicalPath
        }
    }

    /**
     * 判断路径是否为相对路径
     */
    private fun isRelativePath(path: String): Boolean {
        val normalizedPath = path.trim()
        // 以 / 开头 -> Unix 绝对路径
        if (normalizedPath.startsWith("/")) return false
        // 以 盘符: 开头 -> Windows 绝对路径 (C:, D:, etc.)
        if (normalizedPath.length >= 2 && normalizedPath[1] == ':') return false
        // 包含 :// -> 协议 URL
        if (normalizedPath.contains("://")) return false
        return true
    }

    /**
     * 标准化换行符（CRLF -> LF）
     * 用于解决 Windows 文件 CRLF 与参数 LF 不匹配的问题
     */
    private fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n")
    }

    /**
     * 编辑文件内容
     */
    private fun editFileContent(
        file: File,
        oldString: String,
        newString: String,
        replaceAll: Boolean,
        originalPath: String
    ): Any {
        val originalContent = file.readText(Charsets.UTF_8)

        // 标准化换行符用于匹配（解决 CRLF vs LF 问题）
        val normalizedContent = normalizeLineEndings(originalContent)
        val normalizedOldString = normalizeLineEndings(oldString)
        val normalizedNewString = normalizeLineEndings(newString)

        // 检查 oldString 是否存在（使用标准化后的内容匹配）
        if (!normalizedContent.contains(normalizedOldString)) {
            return ToolResult.error("""
                |oldString not found in file: $originalPath
                |
                |The specified text to replace was not found in the file.
                |Make sure the oldString matches exactly (including whitespace and line endings).
            """.trimMargin())
        }

        // 检查 oldString 是否唯一（如果不是 replaceAll 模式，使用标准化内容检查）
        if (!replaceAll) {
            val occurrences = countOccurrences(normalizedContent, normalizedOldString)
            if (occurrences > 1) {
                return ToolResult.error("""
                    |oldString is not unique in file: $originalPath
                    |
                    |Found $occurrences occurrences of the specified text.
                    |Either:
                    |1. Provide a larger string with more surrounding context to make it unique
                    |2. Use replaceAll=true to replace all occurrences
                """.trimMargin())
            }
        }

        // 执行替换（在标准化内容上进行）
        val newNormalizedContent = if (replaceAll) {
            normalizedContent.replace(normalizedOldString, normalizedNewString)
        } else {
            normalizedContent.replaceFirst(normalizedOldString, normalizedNewString)
        }

        // 检查是否有变化
        if (normalizedContent == newNormalizedContent) {
            return ToolResult.error("No changes made: oldString equals newString")
        }

        // 写入文件（保持原文件的换行符格式）
        val hasCRLF = originalContent.contains("\r\n")
        val finalContent = if (hasCRLF) {
            newNormalizedContent.replace("\n", "\r\n")
        } else {
            newNormalizedContent
        }
        file.writeText(finalContent, Charsets.UTF_8)

        // 刷新 VFS
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

        // 统计替换次数（使用标准化内容）
        val replacementCount = if (replaceAll) {
            countOccurrences(normalizedContent, normalizedOldString)
        } else {
            1
        }

        return formatOutput(originalPath, oldString, newString, replacementCount, replaceAll)
    }

    /**
     * 计算字符串出现次数
     */
    private fun countOccurrences(text: String, search: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(search, index)
            if (index < 0) break
            count++
            index += search.length
        }
        return count
    }

    /**
     * 格式化输出
     */
    private fun formatOutput(
        originalPath: String,
        oldString: String,
        newString: String,
        replacementCount: Int,
        replaceAll: Boolean
    ): String {
        val sb = StringBuilder()

        sb.appendLine("## Edited File: `$originalPath`")
        sb.appendLine()
        sb.appendLine("**Replacements:** $replacementCount occurrence(s)")
        sb.appendLine("**Mode:** ${if (replaceAll) "Replace All" else "Replace First"}")
        sb.appendLine()

        // 显示替换摘要
        val oldPreview = if (oldString.length > 100) {
            oldString.take(100) + "..."
        } else {
            oldString
        }
        val newPreview = if (newString.length > 100) {
            newString.take(100) + "..."
        } else {
            newString
        }

        sb.appendLine("**Old:** `${oldPreview.replace("\n", "\\n")}`")
        sb.appendLine("**New:** `${newPreview.replace("\n", "\\n")}`")
        sb.appendLine()
        sb.appendLine("✅ File updated successfully")

        return sb.toString()
    }
}
