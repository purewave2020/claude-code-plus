package com.asakii.plugin.mcp.git

import com.asakii.logging.*
import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getString
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import kotlinx.serialization.json.JsonObject

private val logger = getLogger("CommitChangesTool")

/**
 * 提交变更工具
 *
 * 提交选中的文件到版本控制系统
 */
class CommitChangesTool(private val project: Project) {

    suspend fun execute(arguments: JsonObject): String {
        val message = arguments.getString("message")
        val amend = arguments.getBoolean("amend") ?: false
        val push = arguments.getBoolean("push") ?: false

        val accessor = CommitPanelAccessor.getInstance(project)

        // 获取提交消息
        val commitMessage = message ?: accessor.getCommitMessage()
        if (commitMessage.isNullOrBlank()) {
            return "Error: Commit message is required. Either provide 'message' parameter or set it in the Commit panel."
        }

        // 获取要提交的变更
        val selectedChanges = accessor.getSelectedChanges()
        if (selectedChanges.isNullOrEmpty()) {
            // 如果没有选中的，使用所有变更
            val allChanges = accessor.getAllChanges()
            if (allChanges.isEmpty()) {
                return "Error: No changes to commit"
            }
            return commitChanges(allChanges, commitMessage, amend, push)
        }

        return commitChanges(selectedChanges, commitMessage, amend, push)
    }

    private fun commitChanges(
        changes: List<Change>,
        message: String,
        amend: Boolean,
        push: Boolean
    ): String {
        return try {
            // 获取 Git VCS
            val gitVcs = GitVcs.getInstance(project)
            val checkinEnvironment = gitVcs.checkinEnvironment
                ?: return "Error: Git checkin environment not available"

            // 构建 git commit 命令参数
            val repositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = repositoryManager.repositories
            if (repositories.isEmpty()) {
                return "Error: No Git repositories found"
            }

            // 使用 Git 命令直接提交
            val git = Git.getInstance()
            val results = mutableListOf<String>()
            var hasError = false

            for (repo in repositories) {
                // 获取该仓库中的变更
                val repoChanges = changes.filter { change ->
                    val path = getChangePath(change)
                    path.startsWith(repo.root.path)
                }

                if (repoChanges.isEmpty()) continue

                // 使用 git commit 命令
                val handler = GitLineHandler(project, repo.root, GitCommand.COMMIT)
                handler.addParameters("-m", message)
                if (amend) {
                    handler.addParameters("--amend")
                }

                // 添加文件路径
                repoChanges.forEach { change ->
                    change.virtualFile?.let { vf ->
                        handler.addRelativePaths(VcsUtil.getFilePath(vf))
                    }
                }

                val result = git.runCommand(handler)
                if (result.success()) {
                    results.add("✓ Committed to ${repo.root.name}")
                } else {
                    hasError = true
                    results.add("✗ Failed: ${result.errorOutputAsJoinedString}")
                }
            }

            if (hasError) {
                return buildString {
                    appendLine("# Commit Partially Failed")
                    appendLine()
                    results.forEach { appendLine(it) }
                }
            }

            // 提交成功
            val result = buildString {
                appendLine("# Commit Successful")
                appendLine()
                appendLine("**Message**: $message")
                appendLine("**Files**: ${changes.size}")
                if (amend) appendLine("**Amend**: Yes")
                appendLine()
                appendLine("## Results")
                results.forEach { appendLine(it) }
                appendLine()
                appendLine("## Committed Files")
                changes.forEach { change ->
                    appendLine("- `${getChangePath(change)}` (${change.type.name})")
                }
            }

            // 如果需要 push
            if (push) {
                val pushResult = executePush()
                return "$result\n\n$pushResult"
            }

            result
        } catch (e: Exception) {
            logger.error(e) { "Failed to commit changes" }
            "Error: Failed to commit - ${e.message}"
        }
    }

    private fun executePush(): String {
        return try {
            val repositoryManager = GitRepositoryManager.getInstance(project)
            val repositories = repositoryManager.repositories

            if (repositories.isEmpty()) {
                return "## Push Skipped\nNo Git repositories found"
            }

            val git = Git.getInstance()
            val results = mutableListOf<String>()

            for (repo in repositories) {
                val handler = GitLineHandler(project, repo.root, GitCommand.PUSH)
                val result = git.runCommand(handler)

                if (result.success()) {
                    results.add("✓ Pushed to ${repo.root.name}")
                } else {
                    results.add("✗ Failed to push ${repo.root.name}: ${result.errorOutputAsJoinedString}")
                }
            }

            buildString {
                appendLine("## Push Results")
                results.forEach { appendLine(it) }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to push" }
            "## Push Failed\n${e.message}"
        }
    }

    private fun getChangePath(change: Change): String {
        return change.virtualFile?.path
            ?: change.afterRevision?.file?.path
            ?: change.beforeRevision?.file?.path
            ?: "unknown"
    }
}
