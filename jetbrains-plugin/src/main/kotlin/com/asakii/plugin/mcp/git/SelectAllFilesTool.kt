package com.asakii.plugin.mcp.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import kotlinx.serialization.json.JsonObject

/**
 * 全选文件工具
 *
 * 在 Commit 面板中选中所有变更文件
 */
class SelectAllFilesTool(private val project: Project) {

    suspend fun execute(arguments: JsonObject): String {
        val accessor = CommitPanelAccessor.getInstance(project)

        // 获取所有变更
        val allChanges = accessor.getAllChanges()
        if (allChanges.isEmpty()) {
            return "No changes found in the project"
        }

        // 选中所有文件
        accessor.updateSelectedChanges(allChanges)

        return buildString {
            appendLine("# All Files Selected")
            appendLine()
            appendLine("**Total**: ${allChanges.size} files")
            appendLine()
            appendLine("## Selected Files")
            allChanges.forEach { change ->
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
