package com.asakii.plugin.vcs

import com.asakii.plugin.mcp.git.CommitPanelAccessor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.IconLoader
import com.asakii.logging.*
import javax.swing.Icon

private val logger = getLogger("GenerateCommitMessageAction")

/**
 * Generate Commit Message Action
 *
 * 在 Commit 面板的 message 工具栏中添加按钮，点击后使用 Claude/Codex 生成 commit message
 */
class GenerateCommitMessageAction : AnAction(), DumbAware {

    companion object {
        private val CLAUDE_ICON: Icon = IconLoader.getIcon("/icons/claude-ai.svg", GenerateCommitMessageAction::class.java)
        private val CODEX_ICON: Icon = IconLoader.getIcon("/icons/codex-ai.svg", GenerateCommitMessageAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val settings = com.asakii.settings.AgentSettingsService.getInstance()
        if (!settings.gitGenerateEnabled) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // 根据后端类型动态切换图标和描述
        val isCodex = settings.gitGenerateBackend == "codex"
        e.presentation.icon = if (isCodex) CODEX_ICON else CLAUDE_ICON
        e.presentation.text = "Generate Commit Message"
        e.presentation.description = if (isCodex) {
            "Use Codex AI to generate commit message based on selected changes"
        } else {
            "Use Claude AI to generate commit message based on selected changes"
        }

        // 检查是否有选中的变更
        val accessor = CommitPanelAccessor.getInstance(project)
        val hasChanges = accessor.getSelectedChanges()?.isNotEmpty() == true ||
                accessor.getAllChanges().isNotEmpty()

        e.presentation.isEnabledAndVisible = hasChanges
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        logger.info { "GenerateCommitMessageAction: triggered" }

        // 使用后台任务运行
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Generating Commit Message...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Analyzing changes..."

                try {
                    val service = project.service<GenerateCommitMessageService>()
                    service.generateCommitMessage(indicator)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to generate commit message" }
                }
            }
        })
    }
}
