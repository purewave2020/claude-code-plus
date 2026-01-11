package com.asakii.plugin.mcp.git

import com.asakii.logging.*
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject

private val logger = getLogger("DeselectAllFilesTool")

/**
 * 取消全选工具
 *
 * 在 Commit 面板中取消选中所有文件
 */
class DeselectAllFilesTool(private val project: Project) {

    suspend fun execute(arguments: JsonObject): String {
        val accessor = CommitPanelAccessor.getInstance(project)

        // 获取当前选中数量
        val previousCount = accessor.getSelectedChanges()?.size ?: 0

        // 清空选中
        accessor.updateSelectedChanges(emptyList())

        return buildString {
            appendLine("# All Files Deselected")
            appendLine()
            appendLine("**Previously Selected**: $previousCount files")
            appendLine("**Currently Selected**: 0 files")
        }
    }
}
