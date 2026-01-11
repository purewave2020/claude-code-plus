package com.asakii.plugin.mcp.git

import com.asakii.plugin.mcp.getStringList
import com.asakii.plugin.util.toAbsolutePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import kotlinx.serialization.json.JsonObject

/**
 * 取消选择文件工具
 *
 * 在 Commit 面板中取消选中指定的文件
 */
class DeselectFilesTool(private val project: Project) {

    suspend fun execute(arguments: JsonObject): String {
        val paths = arguments.getStringList("paths")

        if (paths.isNullOrEmpty()) {
            return "Error: 'paths' parameter is required and cannot be empty"
        }

        val accessor = CommitPanelAccessor.getInstance(project)

        // 获取当前选中的变更
        val currentSelected = accessor.getSelectedChanges()
        if (currentSelected.isNullOrEmpty()) {
            return "No files are currently selected"
        }

        // 将输入路径转换为绝对路径
        val absolutePaths = paths.map { it.toAbsolutePath(project) }.toSet()

        // 过滤掉要取消选中的文件
        val newSelection = currentSelected.filter { change ->
            val changePath = getChangePath(change)
            !absolutePaths.any { path -> changePath.endsWith(path) || path.endsWith(changePath) || changePath == path }
        }

        val removedCount = currentSelected.size - newSelection.size

        // 更新选中状态
        accessor.updateSelectedChanges(newSelection)

        return buildString {
            appendLine("# File Selection Updated")
            appendLine()
            appendLine("**Deselected**: $removedCount files")
            appendLine("**Remaining Selected**: ${newSelection.size} files")
            appendLine()
            if (newSelection.isNotEmpty()) {
                appendLine("## Still Selected")
                newSelection.forEach { change ->
                    appendLine("- `${getChangePath(change)}` (${change.type.name})")
                }
            } else {
                appendLine("*No files selected*")
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
