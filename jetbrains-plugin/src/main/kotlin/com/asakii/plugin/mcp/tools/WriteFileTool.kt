package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.currentToolUseId
import com.asakii.plugin.mcp.getString
import com.asakii.settings.AgentSettingsService
import com.intellij.history.LocalHistory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileDocumentManager
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
 * 支持写入项目外部的指定目录（需要在设置中配置）。
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

        // 安全检查：确保文件在允许的范围内（项目内或配置的外部目录内）
        val settings = AgentSettingsService.getInstance()
        if (!settings.isFilePathAllowed(absolutePath, projectBasePath)) {
            val externalDirs = settings.getJetbrainsFileExternalDirs()
            val hint = if (externalDirs.isEmpty()) {
                "Enable 'Allow external files' in settings to access files outside the project."
            } else {
                "Allowed external directories: ${externalDirs.joinToString(", ")}"
            }
            return ToolResult.error("File path must be within project directory or allowed external directories. $hint")
        }

        // 从协程上下文获取 toolUseId，使用 LocalHistory 打标签并缓存（仅对已存在的文件）
        val toolUseId = currentToolUseId()
        val file = File(absolutePath)
        if (toolUseId != null && file.exists()) {
            // 打标签并缓存 Label 对象（用于后续获取原始内容）
            val label = LocalHistory.getInstance().putSystemLabel(project, "claude_$toolUseId")
            FileChangeLabelCache.record(toolUseId, absolutePath, label)
            logger.info { "WriteFile: created LocalHistory label for toolUseId=$toolUseId" }
        }

        val isExternalFile = !absolutePath.startsWith(File(projectBasePath).canonicalPath)

        return try {
            var result: Any = ""
            ApplicationManager.getApplication().invokeAndWait {
                result = WriteAction.compute<Any, Exception> {
                    writeFileContent(absolutePath, content, filePath, isExternalFile)
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
     * 写入文件内容（使用 VirtualFile API）
     */
    private fun writeFileContent(absolutePath: String, content: String, originalPath: String, isExternalFile: Boolean): Any {
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

        // 使用 VirtualFile API 写入文件
        val virtualFile: VirtualFile?
        
        if (isNewFile) {
            // 新文件：先用 Java IO 创建，然后刷新 VFS
            file.writeText(content, Charsets.UTF_8)
            virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        } else {
            // 已存在的文件：尝试使用 VirtualFile API
            virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            if (virtualFile != null) {
                // 使用 VirtualFile API 写入（自动同步 VFS 和索引）
                virtualFile.setBinaryContent(content.toByteArray(virtualFile.charset))
            } else {
                // 回退到 Java IO
                file.writeText(content, Charsets.UTF_8)
            }
        }

        // 外部文件：立即刷新到磁盘
        if (isExternalFile && virtualFile != null) {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                FileDocumentManager.getInstance().saveDocument(document)
            }
        }

        return formatOutput(originalPath, absolutePath, content, isNewFile, isExternalFile, virtualFile)
    }

    /**
     * 格式化输出
     */
    private fun formatOutput(
        originalPath: String,
        absolutePath: String,
        content: String,
        isNewFile: Boolean,
        isExternalFile: Boolean,
        virtualFile: VirtualFile?
    ): String {
        val sb = StringBuilder()

        val action = if (isNewFile) "Created" else "Updated"
        val locationHint = if (isExternalFile) " (external)" else ""
        sb.appendLine("## $action File$locationHint: `$originalPath`")
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
