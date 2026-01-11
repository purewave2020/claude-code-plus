package com.asakii.plugin.mcp.git

import com.asakii.logging.*
import com.asakii.plugin.mcp.getString
import com.asakii.plugin.mcp.getStringList
import com.asakii.plugin.util.toAbsolutePath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val logger = getLogger("SelectFilesTool")

/**
 * 选择文件工具
 *
 * 在 Commit 面板中选中指定的文件
 */
class SelectFilesTool(private val project: Project) {

    suspend fun execute(arguments: JsonObject): String {
        val paths = arguments.getStringList("paths")
        val mode = arguments.getString("mode") ?: "add"

        if (paths.isNullOrEmpty()) {
            return "Error: 'paths' parameter is required and cannot be empty"
        }

        val accessor = CommitPanelAccessor.getInstance(project)

        // 获取所有变更
        val allChanges = accessor.getAllChanges()
        if (allChanges.isEmpty()) {
            return "No changes found in the project"
        }

        // 将输入路径转换为绝对路径
        val absolutePaths = paths.map { it.toAbsolutePath(project) }.toSet()

        // 找到匹配的变更
        val matchedChanges = allChanges.filter { change ->
            val changePath = getChangePath(change)
            absolutePaths.any { path -> changePath.endsWith(path) || path.endsWith(changePath) || changePath == path }
        }

        if (matchedChanges.isEmpty()) {
            return buildString {
                appendLine("No matching files found.")
                appendLine()
                appendLine("Requested paths:")
                paths.forEach { appendLine("- $it") }
                appendLine()
                appendLine("Available changes:")
                allChanges.take(10).forEach { appendLine("- ${getChangePath(it)}") }
                if (allChanges.size > 10) {
                    appendLine("... and ${allChanges.size - 10} more")
                }
            }
        }

        // 更新选中状态
        val currentSelected = accessor.getSelectedChanges() ?: emptyList()
        val newSelection = when (mode) {
            "replace" -> matchedChanges
            "add" -> (currentSelected + matchedChanges).distinctBy { getChangePath(it) }
            else -> matchedChanges
        }

        // 更新 accessor 中的选中状态
        accessor.updateSelectedChanges(newSelection)

        return buildString {
            appendLine("# File Selection Updated")
            appendLine()
            appendLine("**Mode**: $mode")
            appendLine("**Matched**: ${matchedChanges.size} files")
            appendLine("**Total Selected**: ${newSelection.size} files")
            appendLine()
            appendLine("## Selected Files")
            newSelection.forEach { change ->
                appendLine("- `${getChangePath(change)}` (${change.type.name})")
            }
        }
    }

    private fun getChangePath(change: Change): String {
        return change.virtualFile?.path
            ?: change.afterRevision?.file?.path
            ?: change.beforeRevision?.file?.path
            ?: "unknown"
    }
}
