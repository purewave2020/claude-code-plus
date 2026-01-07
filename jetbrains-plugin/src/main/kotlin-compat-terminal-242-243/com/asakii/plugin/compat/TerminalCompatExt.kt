package com.asakii.plugin.compat

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

private val logger = Logger.getInstance("com.asakii.plugin.compat.TerminalCompatExt")

/**
 * Terminal 兼容层扩展 - 242-250 版本
 *
 * 使用 ShellStartupOptions + startShellTerminalWidget 创建终端，
 * 支持设置环境变量（如 TERM=dumb）。
 */
fun createShellWidget(
    project: Project,
    workingDirectory: String,
    tabName: String,
    shellCommand: List<String>? = null,
    envVariables: Map<String, String> = emptyMap()
): TerminalWidgetWrapper? {
    logger.info("=== [TerminalCompat-242] createShellWidget ===")
    logger.info("  project: ${project.name}")
    logger.info("  workingDirectory: $workingDirectory")
    logger.info("  tabName: $tabName")
    logger.info("  shellCommand: $shellCommand")
    logger.info("  envVariables: $envVariables")

    return try {
        val manager = TerminalToolWindowManager.getInstance(project)
        val runner = manager.terminalRunner
        logger.info("  terminalRunner: ${runner.javaClass.name}")

        // 使用 ShellStartupOptions.Builder 构建启动选项
        val startupOptions = ShellStartupOptions.Builder()
            .workingDirectory(workingDirectory)
            .shellCommand(shellCommand)
            .envVariables(envVariables)
            .build()

        logger.info("  Created ShellStartupOptions with envVariables: ${startupOptions.envVariables}")

        // 获取或创建终端工具窗口的 ContentManager
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

        if (toolWindow == null) {
            logger.warn("  Terminal tool window not found!")
            return null
        }

        val contentManager = toolWindow.contentManager

        // 使用 startShellTerminalWidget 创建终端
        logger.info("  Calling runner.startShellTerminalWidget...")
        val terminalWidget = runner.startShellTerminalWidget(
            contentManager,  // parentDisposable
            startupOptions,
            false  // deferSessionStartUntilUiShown
        )

        logger.info("  Created terminal widget: ${terminalWidget.javaClass.name}")

        // 创建 Content 并添加到工具窗口
        val content = ContentFactory.getInstance().createContent(
            terminalWidget.component,
            tabName,
            false
        )
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)

        // 转换为 ShellTerminalWidget
        val shellWidget = ShellTerminalWidget.toShellJediTermWidgetOrThrow(terminalWidget)
        logger.info("  Created shell widget: ${shellWidget.javaClass.name}")
        TerminalWidgetWrapper(shellWidget)
    } catch (e: Exception) {
        logger.error("Failed to create terminal widget", e)
        null
    }
}
