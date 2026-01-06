package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.currentToolUseId
import com.asakii.plugin.mcp.getString
import com.asakii.server.services.FileContentCache
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * 写入文件工具
 *
 * 支持创建新文件或覆盖现有文件内容。
 * 支持相对路径（相对于项目根目录）和绝对路径。
 */
class WriteFileTool(private val project: Project) {

    suspend fun execute(arguments: JsonObject): Any {
        val filePath = arguments.getString("filePath")
            ?: return ToolResult.error("Missing required parameter: filePath")

        val content = arguments.getString("content")
            ?: return ToolResult.error("Missing required parameter: content")

        logger.info { "WriteFile: path=$filePath, content length=${content.length}" }

        val projectBasePath = project.basePath
            ?: return ToolResult.error("Cannot get project path")

        // 解析路径（支持相对路径和绝对路径）
        val absolutePath = resolvePath(filePath, projectBasePath)

        // 安全检查：确保文件在项目目录内
        if (!absolutePath.startsWith(File(projectBasePath).canonicalPath)) {
            return ToolResult.error("File path must be within project directory")
        }

        // 从协程上下文获取 toolUseId，保存原始文件内容用于 Diff 显示
        val toolUseId = currentToolUseId()
        if (toolUseId != null) {
            FileContentCache.saveOriginalContent(toolUseId, absolutePath)
            logger.info { "WriteFile: saved original content for toolUseId=$toolUseId" }
        }

        return try {
            var result: Any = ""
            ApplicationManager.getApplication().invokeAndWait {
                result = WriteAction.compute<Any, Exception> {
                    writeFileContent(absolutePath, content, filePath)
                }
            }
            result
        } catch (e: Exception) {
            logger.error(e) { "Error writing file: $filePath" }
            ToolResult.error("Error writing file: ${e.message}")
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
     * 写入文件内容
     */
    private fun writeFileContent(absolutePath: String, content: String, originalPath: String): Any {
        val file = File(absolutePath)
        val isNewFile = !file.exists()

        // 如果是新文件，创建父目录
        if (isNewFile) {
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    return ToolResult.error("Failed to create parent directories: ${parentDir.path}")
                }
            }
        }

        // 写入文件
        file.writeText(content, Charsets.UTF_8)

        // 刷新 VFS 以便 IDE 能看到变更
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

        return formatOutput(originalPath, absolutePath, content, isNewFile, virtualFile)
    }

    /**
     * 格式化输出
     */
    private fun formatOutput(
        originalPath: String,
        absolutePath: String,
        content: String,
        isNewFile: Boolean,
        virtualFile: VirtualFile?
    ): String {
        val sb = StringBuilder()

        val action = if (isNewFile) "Created" else "Updated"
        sb.appendLine("## $action File: `$originalPath`")
        sb.appendLine()
        sb.appendLine("**Path:** `$absolutePath`")
        sb.appendLine("**Size:** ${content.length} characters, ${content.lines().size} lines")

        if (virtualFile != null) {
            sb.appendLine("**Status:** ✅ File written and synced to IDE")
        } else {
            sb.appendLine("**Status:** ⚠️ File written but not synced to IDE (may need manual refresh)")
        }

        return sb.toString()
    }
}
