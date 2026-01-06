package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.currentToolUseId
import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getString
import com.asakii.server.services.FileContentCache
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

        // 从协程上下文获取 toolUseId，保存原始文件内容用于 Diff 显示
        val toolUseId = currentToolUseId()
        if (toolUseId != null) {
            FileContentCache.saveOriginalContent(toolUseId, absolutePath)
            logger.info { "EditFile: saved original content for toolUseId=$toolUseId" }
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

        // 检查 oldString 是否存在
        if (!originalContent.contains(oldString)) {
            return ToolResult.error("""
                |oldString not found in file: $originalPath
                |
                |The specified text to replace was not found in the file.
                |Make sure the oldString matches exactly (including whitespace and line endings).
            """.trimMargin())
        }

        // 检查 oldString 是否唯一（如果不是 replaceAll 模式）
        if (!replaceAll) {
            val occurrences = countOccurrences(originalContent, oldString)
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

        // 执行替换
        val newContent = if (replaceAll) {
            originalContent.replace(oldString, newString)
        } else {
            originalContent.replaceFirst(oldString, newString)
        }

        // 检查是否有变化
        if (originalContent == newContent) {
            return ToolResult.error("No changes made: oldString equals newString")
        }

        // 写入文件
        file.writeText(newContent, Charsets.UTF_8)

        // 刷新 VFS
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

        // 统计替换次数
        val replacementCount = if (replaceAll) {
            countOccurrences(originalContent, oldString)
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
