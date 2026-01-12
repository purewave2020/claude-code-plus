package com.asakii.plugin.mcp.tools.terminal

import com.asakii.settings.AgentSettingsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.asakii.plugin.compat.CommandWaitResult
import com.asakii.plugin.compat.TerminalCompat
import com.asakii.plugin.compat.TerminalWidgetWrapper
import com.asakii.plugin.compat.createShellWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val logger = Logger.getInstance("com.asakii.plugin.mcp.tools.terminal.TerminalSessionManager")

/**
 * Terminal 会话管理器
 *
 * 管理 IDEA 内置终端的会话，支持命令执行、输出读取、会话管理等功能。
 */
class TerminalSessionManager(private val project: Project) {

    // 用于异步任务的协程作用域，随项目生命周期管理
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val sessionCounter = AtomicInteger(0)

    // AI 会话 -> 默认终端 ID 的映射
    private val aiSessionDefaultTerminals = ConcurrentHashMap<String, String>()

    // AI 会话 -> 溢出终端列表（当默认终端忙时创建的额外终端）
    private val aiSessionOverflowTerminals = ConcurrentHashMap<String, MutableList<String>>()

    // 正在执行的后台任务追踪 (toolUseId -> TerminalBackgroundTask)
    private val runningTasks = ConcurrentHashMap<String, TerminalBackgroundTask>()

    // 任务更新监听器
    private var taskUpdateListener: TerminalTaskUpdateListener? = null

    /**
     * 设置任务更新监听器
     * 用于通知前端任务状态变化
     */
    fun setTaskUpdateListener(listener: TerminalTaskUpdateListener?) {
        this.taskUpdateListener = listener
        logger.info { "🔔 Task update listener ${if (listener != null) "registered" else "unregistered"}" }
    }

    /**
     * 通知任务更新（内部使用）
     */
    private fun notifyTaskUpdate(
        toolUseId: String,
        sessionId: String,
        action: String,
        command: String,
        isBackground: Boolean,
        startTime: Long,
        elapsedMs: Long? = null
    ) {
        val listener = taskUpdateListener ?: return
        // 使用协程在后台执行，避免阻塞调用线程
        scope.launch {
            try {
                listener(toolUseId, sessionId, action, command, isBackground, startTime, elapsedMs)
            } catch (e: Exception) {
                logger.warn { "⚠️ Failed to notify task update: ${e.message}" }
            }
        }
    }

    // 当前 AI 会话 ID
    @Volatile
    var currentAiSessionId: String? = null
        private set

    /**
     * 设置当前 AI 会话 ID
     */
    fun setCurrentAiSession(aiSessionId: String?) {
        currentAiSessionId = aiSessionId
        logger.info { "Set current AI session: $aiSessionId" }
    }

    /**
     * 终端关闭时的清理回调
     * 从 sessions 和映射表中移除对应记录
     */
    private fun onTerminalClosed(terminalId: String) {
        // 从 sessions 中移除
        sessions.remove(terminalId)

        // 从默认终端映射中移除
        aiSessionDefaultTerminals.entries.removeIf { it.value == terminalId }

        // 从溢出终端列表中移除
        aiSessionOverflowTerminals.values.forEach { list ->
            list.remove(terminalId)
        }

        logger.info { "Cleaned up mappings for closed terminal: $terminalId" }
    }

    /**
     * 获取当前 AI 会话的默认终端 ID
     */
    fun getDefaultTerminalId(): String? {
        val aiSessionId = currentAiSessionId ?: return null
        return aiSessionDefaultTerminals[aiSessionId]
    }

    /**
     * 检查终端是否属于当前 AI 会话
     */
    fun isSessionOwnedByCurrentAiSession(terminalId: String): Boolean {
        val currentTerminalIds = getCurrentSessionTerminals().map { it.id }.toSet()
        return terminalId in currentTerminalIds
    }

    /**
     * 验证会话所有权，如果验证失败返回错误响应，否则返回 null
     * 用于各工具统一的会话验证逻辑
     */
    fun validateSessionOwnership(sessionId: String): JsonObject? {
        return if (!isSessionOwnedByCurrentAiSession(sessionId)) {
            buildJsonObject {
                put("success", false)
                put("error", "Session not found or not owned by current AI session: $sessionId")
            }
        } else {
            null
        }
    }

    /**
     * 获取当前 AI 会话的默认终端，如果不存在或已被删除则创建新的
     * 如果默认终端正在执行命令，则创建溢出终端
     */
    fun getOrCreateDefaultTerminal(shellName: String? = null): TerminalSession? {
        val aiSessionId = currentAiSessionId ?: return createSession(null, shellName)

        // 检查是否已有默认终端
        val existingTerminalId = aiSessionDefaultTerminals[aiSessionId]
        if (existingTerminalId != null) {
            val existingSession = sessions[existingTerminalId]
            if (existingSession != null) {
                // 检查默认终端是否正在执行命令（null 视为空闲）
                if (existingSession.hasRunningCommands() != true) {
                    logger.info { "Using existing default terminal for AI session $aiSessionId: $existingTerminalId" }
                    return existingSession
                }
                // 默认终端正在执行命令，尝试使用溢出终端
                logger.info { "Default terminal $existingTerminalId is busy, looking for available overflow terminal" }
                val availableOverflow = findAvailableOverflowTerminal(aiSessionId)
                if (availableOverflow != null) {
                    logger.info { "Using available overflow terminal: ${availableOverflow.id}" }
                    return availableOverflow
                }
                // 没有可用的溢出终端，创建新的
                val overflowSession = createSession("Overflow Terminal", shellName)
                if (overflowSession != null) {
                    aiSessionOverflowTerminals.getOrPut(aiSessionId) { mutableListOf() }.add(overflowSession.id)
                    logger.info { "Created overflow terminal for AI session $aiSessionId: ${overflowSession.id}" }
                }
                return overflowSession
            }
            // 终端已被删除，移除映射
            aiSessionDefaultTerminals.remove(aiSessionId)
            logger.info { "Default terminal $existingTerminalId was deleted, creating new one" }
        }

        // 创建新的默认终端
        val newSession = createSession("Default Terminal", shellName)
        if (newSession != null) {
            aiSessionDefaultTerminals[aiSessionId] = newSession.id
            logger.info { "Created default terminal for AI session $aiSessionId: ${newSession.id}" }
        }
        return newSession
    }

    /**
     * 查找可用的溢出终端（不在执行命令的）
     */
    private fun findAvailableOverflowTerminal(aiSessionId: String): TerminalSession? {
        val overflowIds = aiSessionOverflowTerminals[aiSessionId] ?: return null
        // 清理已删除的终端
        overflowIds.removeAll { sessions[it] == null }
        // 查找空闲的溢出终端
        for (terminalId in overflowIds) {
            val session = sessions[terminalId]
            if (session != null && session.hasRunningCommands() != true) {
                return session
            }
        }
        return null
    }

    /**
     * 获取当前 AI 会话的所有终端（默认终端 + 溢出终端）
     * 如果没有当前 AI 会话，返回空列表
     */
    fun getCurrentSessionTerminals(): List<TerminalSession> {
        val aiSessionId = currentAiSessionId ?: return emptyList()
        val result = mutableListOf<TerminalSession>()

        // 添加默认终端
        aiSessionDefaultTerminals[aiSessionId]?.let { terminalId ->
            sessions[terminalId]?.let { result.add(it) }
        }

        // 添加溢出终端
        aiSessionOverflowTerminals[aiSessionId]?.forEach { terminalId ->
            sessions[terminalId]?.let { result.add(it) }
        }

        return result
    }

    /**
     * 创建新终端会话
     *
     * @param name 会话名称
     * @param shellName shell 名称（如 "git-bash", "powershell"），为 null 时使用配置的默认终端
     */
    fun createSession(
        name: String? = null,
        shellName: String? = null
    ): TerminalSession? {
        return try {
            val sessionId = "terminal-${sessionCounter.incrementAndGet()}"
            val sessionName = name ?: "Claude Terminal ${sessionCounter.get()}"

            // 确定实际使用的 shell 名称：传入的 > 配置的默认
            val actualShellName = shellName ?: getDefaultShellName()

            // 获取 shell 命令（用于 IDEA Terminal API）
            val shellCommand = ShellResolver.getShellCommand(actualShellName)
            logger.info { "=== [TerminalSessionManager] createSession ===" }
            logger.info { "  requested shellName: $shellName" }
            logger.info { "  actualShellName: $actualShellName" }
            logger.info { "  shellCommand: $shellCommand" }

            var wrapper: TerminalWidgetWrapper? = null

            // 获取终端环境变量配置（如 TERM=dumb 禁用交互式命令）
            val envVariables = AgentSettingsService.getInstance().getTerminalEnvVariables()
            logger.info { "  envVariables: $envVariables" }

            ApplicationManager.getApplication().invokeAndWait {
                try {
                    val basePath = project.basePath ?: System.getProperty("user.home")

                    // 使用兼容层创建终端（传递 shellCommand 和 envVariables）
                    wrapper = createShellWidget(project, basePath, sessionName, shellCommand, envVariables)

                    if (wrapper == null) {
                        logger.warn { "Failed to create TerminalWidgetWrapper" }
                    }
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to create terminal widget", e)
                }
            }

            wrapper?.let { w ->
                val session = TerminalSession(
                    id = sessionId,
                    name = sessionName,
                    shellType = actualShellName,
                    widgetWrapper = w
                )
                sessions[sessionId] = session

                // 注册终端关闭回调，自动清理映射
                w.addTerminationCallback {
                    logger.info { "Terminal $sessionId was closed, cleaning up mappings" }
                    onTerminalClosed(sessionId)
                }

                logger.info { "Created terminal session: $sessionId ($sessionName), shell=$actualShellName, widget type: ${w.widgetClassName}" }
                session
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to create terminal session", e)
            null
        }
    }

    /**
     * 获取或创建会话
     */
    fun getOrCreateSession(sessionId: String?): TerminalSession? {
        return if (sessionId != null && sessions.containsKey(sessionId)) {
            sessions[sessionId]
        } else {
            createSession()
        }
    }

    /**
     * 获取会话
     */
    fun getSession(sessionId: String): TerminalSession? {
        return sessions[sessionId]
    }

    /**
     * 获取所有会话
     */
    fun getAllSessions(): List<TerminalSession> {
        return sessions.values.toList()
    }

    /**
     * 异步执行命令（立即返回，不等待完成）
     *
     * @param sessionId 会话 ID
     * @param command 要执行的命令
     * @return 执行结果（仅表示命令是否成功发送）
     */
    fun executeCommandAsync(sessionId: String, command: String): ExecuteResult {
        val session = getSession(sessionId) ?: return ExecuteResult(
            success = false,
            sessionId = sessionId,
            error = "Session not found: $sessionId"
        )

        return try {
            session.lastCommandAt = System.currentTimeMillis()

            ApplicationManager.getApplication().invokeAndWait {
                session.widgetWrapper.executeCommand(command)
            }

            ExecuteResult(
                 success = true,
                sessionId = session.id,
                sessionName = session.name,
                background = true  // 始终视为后台执行
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to execute command in session ${session.id}", e)
            ExecuteResult(
                success = false,
                sessionId = session.id,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 执行命令（兼容旧 API，保留但不推荐使用）
     *
     * @param sessionId 会话 ID，为空时创建新会话
     * @param command 要执行的命令
     * @param background 是否后台执行。false 时等待命令完成并返回输出
     * @param timeoutMs 前台执行时的超时时间（毫秒），默认 5 分钟
     * @deprecated 使用 executeCommandAsync 代替
     */
    @Deprecated("Use executeCommandAsync instead", ReplaceWith("executeCommandAsync(sessionId, command)"))
    fun executeCommand(
        sessionId: String?,
        command: String,
        background: Boolean = false,
        timeoutMs: Long = 300_000
    ): ExecuteResult {
        val session = getOrCreateSession(sessionId) ?: return ExecuteResult(
            success = false,
            sessionId = "",
            error = "Failed to create terminal session"
        )

        return try {
            session.isBackground = background
            session.lastCommandAt = System.currentTimeMillis()

            ApplicationManager.getApplication().invokeAndWait {
                session.widgetWrapper.executeCommand(command)
            }

            if (background) {
                // 后台执行：立即返回
                ExecuteResult(
                    success = true,
                    sessionId = session.id,
                    sessionName = session.name,
                    background = true
                )
            } else {
                // 前台执行：等待命令完成
                val settings = AgentSettingsService.getInstance()
                val maxOutputLines = settings.terminalMaxOutputLines
                val maxOutputChars = settings.terminalMaxOutputChars

                // 等待命令完成（依赖 Shell Integration）
                val waitResult = session.waitForCommandCompletion(
                    timeoutMs = timeoutMs,
                    initialDelayMs = 300,
                    pollIntervalMs = 100
                )

                when (waitResult) {
                    is CommandWaitResult.ApiUnavailable -> {
                        // Shell Integration 不可用，无法检测命令状态
                        // 返回当前输出，并告知用户
                        val fullOutput = session.getOutput()
                        return ExecuteResult(
                            success = false,
                            sessionId = session.id,
                            sessionName = session.name,
                            background = false,
                            output = fullOutput,
                            error = "Cannot detect command completion (Shell Integration unavailable). Use background=true for long-running commands, or use TerminalRead to check output."
                        )
                    }
                    is CommandWaitResult.Timeout -> {
                        return ExecuteResult(
                            success = false,
                            sessionId = session.id,
                            sessionName = session.name,
                            background = false,
                            error = "Command timed out after ${timeoutMs / 1000} seconds. Use TerminalRead to check output."
                        )
                    }
                    is CommandWaitResult.Interrupted -> {
                        return ExecuteResult(
                            success = false,
                            sessionId = session.id,
                            sessionName = session.name,
                            background = false,
                            error = "Command wait was interrupted. Use TerminalRead to check output."
                        )
                    }
                    is CommandWaitResult.Completed -> {
                        // 命令完成，读取输出（可能截断）
                        val fullOutput = session.getOutput()
                        val lines = fullOutput.split("\n")
                        val totalLines = lines.size
                        val totalChars = fullOutput.length

                        val (output, truncated) = when {
                            totalLines > maxOutputLines -> {
                                // 行数超限：取最后 maxOutputLines 行
                                lines.takeLast(maxOutputLines).joinToString("\n") to true
                            }
                            totalChars > maxOutputChars -> {
                                // 字符数超限：取最后 maxOutputChars 字符
                                fullOutput.takeLast(maxOutputChars) to true
                            }
                            else -> fullOutput to false
                        }

                        ExecuteResult(
                            success = true,
                            sessionId = session.id,
                            sessionName = session.name,
                            background = false,
                            output = output,
                            truncated = truncated,
                            totalLines = totalLines,
                            totalChars = totalChars
                        )
                    }
                }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to execute command in session ${session.id}", e)
            ExecuteResult(
                success = false,
                sessionId = session.id,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 读取会话输出
     *
     * @param sessionId 会话 ID
     * @param maxLines 最大行数
     * @param search 搜索模式（正则表达式）
     * @param contextLines 搜索结果上下文行数
     * @param waitForIdle 是否等待命令执行完成
     * @param timeoutMs 等待超时时间（毫秒）
     */
    fun readOutput(
        sessionId: String,
        maxLines: Int = 1000,
        search: String? = null,
        contextLines: Int = 2,
        waitForIdle: Boolean = false,
        timeoutMs: Long = 30_000
    ): ReadResult {
        val session = getSession(sessionId) ?: return ReadResult(
            success = false,
            sessionId = sessionId,
            error = "Session not found: $sessionId"
        )

        return try {
            // 如果需要等待命令完成
            if (waitForIdle) {
                val waitResult = session.waitForCommandCompletion(
                    timeoutMs = timeoutMs,
                    initialDelayMs = 100,
                    pollIntervalMs = 100
                )

                when (waitResult) {
                    is CommandWaitResult.Timeout -> {
                        // 超时但仍然返回当前输出
                        val output = session.getOutput(maxLines)
                        return ReadResult(
                            success = true,
                            sessionId = sessionId,
                            output = output,
                            isRunning = true,
                            lineCount = output.split("\n").size,
                            waitTimedOut = true,
                            waitMessage = "Timed out waiting for command to complete after ${timeoutMs / 1000} seconds. Returning current output."
                        )
                    }
                    is CommandWaitResult.ApiUnavailable -> {
                        // API 不可用，返回当前输出并提示
                        val output = session.getOutput(maxLines)
                        return ReadResult(
                            success = true,
                            sessionId = sessionId,
                            output = output,
                            isRunning = null,
                            lineCount = output.split("\n").size,
                            waitMessage = "Cannot detect command completion (Shell Integration unavailable). Returning current output."
                        )
                    }
                    is CommandWaitResult.Interrupted -> {
                        val output = session.getOutput(maxLines)
                        return ReadResult(
                            success = true,
                            sessionId = sessionId,
                            output = output,
                            isRunning = null,
                            lineCount = output.split("\n").size,
                            waitMessage = "Wait was interrupted. Returning current output."
                        )
                    }
                    is CommandWaitResult.Completed -> {
                        // 命令完成，继续读取
                        logger.info { "Command completed, reading output..." }
                    }
                }
            }

            val isRunning = session.hasRunningCommands()

            if (search != null) {
                val matches = session.searchOutput(search, contextLines)
                ReadResult(
                    success = true,
                    sessionId = sessionId,
                    isRunning = isRunning,
                    searchMatches = matches
                )
            } else {
                val output = session.getOutput(maxLines)
                ReadResult(
                    success = true,
                    sessionId = sessionId,
                    output = output,
                    isRunning = isRunning,
                    lineCount = output.split("\n").size
                )
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to read output from session $sessionId", e)
            ReadResult(
                success = false,
                sessionId = sessionId,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 中断当前正在执行的命令
     *
     * @param sessionId 会话 ID
     * @param signal 信号类型: SIGINT (Ctrl+C), SIGQUIT (Ctrl+\), SIGTSTP (Ctrl+Z)
     */
    fun interruptCommand(sessionId: String, signal: String = "SIGINT"): InterruptResult {
        val session = getSession(sessionId) ?: return InterruptResult(
            success = false,
            sessionId = sessionId,
            signal = signal,
            error = "Session not found: $sessionId"
        )

        return try {
            val wasRunning = session.hasRunningCommands()

            ApplicationManager.getApplication().invokeAndWait {
                session.widgetWrapper.sendInterrupt(signal)
            }

            // 等待命令停止
            Thread.sleep(100)
            val isStillRunning = session.hasRunningCommands()

            val signalDesc = when (signal.uppercase()) {
                "SIGINT" -> "SIGINT (Ctrl+C)"
                "SIGQUIT" -> "SIGQUIT (Ctrl+\\)"
                "SIGTSTP" -> "SIGTSTP (Ctrl+Z)"
                else -> signal
            }

            InterruptResult(
                success = true,
                sessionId = sessionId,
                signal = signal,
                wasRunning = wasRunning,
                isStillRunning = isStillRunning,
                message = when {
                    wasRunning == null || isStillRunning == null -> "$signalDesc sent (command status unknown)"
                    wasRunning == false -> "No command was running"
                    isStillRunning == false -> "Command stopped by $signalDesc"
                    else -> "$signalDesc sent, command may still be stopping"
                }
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to send $signal to session $sessionId", e)
            InterruptResult(
                success = false,
                sessionId = sessionId,
                signal = signal,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 终止会话
     */
    fun killSession(sessionId: String): Boolean {
        val session = sessions.remove(sessionId) ?: return false

        return try {
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    // 关闭终端 widget
                    val toolWindow = ToolWindowManager.getInstance(project)
                        .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

                    toolWindow?.contentManager?.let { contentManager ->
                        contentManager.contents.find { content ->
                            content.displayName == session.name
                        }?.let { content ->
                            contentManager.removeContent(content, true)
                        }
                    }
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn { "Failed to remove terminal content: ${e.message}" }
                }
            }
            logger.info { "Killed terminal session: $sessionId" }
            true
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to kill session $sessionId", e)
            false
        }
    }

    /**
     * 重命名会话
     */
    fun renameSession(sessionId: String, newName: String): Boolean {
        val session = sessions[sessionId] ?: return false

        return try {
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    val toolWindow = ToolWindowManager.getInstance(project)
                        .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

                    toolWindow?.contentManager?.let { contentManager ->
                        contentManager.contents.find { content ->
                            content.displayName == session.name
                        }?.let { content ->
                            content.displayName = newName
                        }
                    }
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn { "Failed to rename terminal tab: ${e.message}" }
                }
            }

            // 更新内部会话记录
            sessions[sessionId] = session.copy(name = newName)
            logger.info { "Renamed terminal session $sessionId to: $newName" }
            true
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to rename session $sessionId", e)
            false
        }
    }

    /**
     * 获取可用的 Shell 类型（使用 IDEA 检测）
     */
    fun getAvailableShellTypes(): List<ShellTypeInfo> {
        val settings = AgentSettingsService.getInstance()
        val defaultShell = settings.getEffectiveDefaultShell()
        val detectedShells = TerminalCompat.detectInstalledShells()

        return detectedShells.map { shell ->
            val normalizedName = settings.run {
                // 复用 AgentSettingsService 的标准化逻辑
                val lowerName = shell.name.lowercase()
                when {
                    lowerName.contains("git bash") -> "git-bash"
                    lowerName.contains("powershell") -> "powershell"
                    lowerName.contains("command prompt") || lowerName == "cmd" -> "cmd"
                    lowerName.contains("wsl") -> "wsl"
                    lowerName.contains("zsh") -> "zsh"
                    lowerName.contains("fish") -> "fish"
                    lowerName.contains("bash") -> "bash"
                    else -> lowerName.replace(" ", "-")
                }
            }
            ShellTypeInfo(
                name = normalizedName,
                displayName = shell.name,
                command = normalizedName,
                isDefault = normalizedName == defaultShell
            )
        }
    }

    /**
     * 获取默认 Shell 名称
     *
     * 使用用户配置的默认 shell
     */
    private fun getDefaultShellName(): String {
        val settings = AgentSettingsService.getInstance()
        val defaultShell = settings.getEffectiveDefaultShell()
        logger.info { "Using default shell: $defaultShell" }
        return defaultShell
    }

    // ==================== 后台任务追踪 ====================

    /**
     * 记录任务开始执行
     * 在 MCP 工具调用开始时调用
     */
    fun recordTaskStart(sessionId: String, toolUseId: String, command: String) {
        val startTime = System.currentTimeMillis()
        val task = TerminalBackgroundTask(
            sessionId = sessionId,
            toolUseId = toolUseId,
            command = command,
            startTime = startTime
        )
        runningTasks[toolUseId] = task
        logger.info { "📝 Recorded task start: toolUseId=$toolUseId, sessionId=$sessionId, command=${command.take(50)}..." }
        
        // 通知前端任务已启动
        notifyTaskUpdate(
            toolUseId = toolUseId,
            sessionId = sessionId,
            action = "started",
            command = command,
            isBackground = false,
            startTime = startTime
        )
    }

    /**
     * 标记任务完成（移除追踪）
     */
    fun recordTaskComplete(toolUseId: String) {
        runningTasks.remove(toolUseId)?.let { task ->
            val elapsedMs = task.getElapsedMs()
            logger.info { "✅ Task completed: toolUseId=$toolUseId, elapsed=${elapsedMs}ms" }
            
            // 通知前端任务已完成
            notifyTaskUpdate(
                toolUseId = toolUseId,
                sessionId = task.sessionId,
                action = "completed",
                command = task.command,
                isBackground = task.isBackground,
                startTime = task.startTime,
                elapsedMs = elapsedMs
            )
        }
    }

    /**
     * 将任务标记为后台执行
     * @return true 表示成功，false 表示任务不存在
     */
    fun markTaskAsBackground(toolUseId: String): Boolean {
        val task = runningTasks[toolUseId] ?: return false
        if (task.isBackground) {
            logger.info { "⚠️ Task already in background: toolUseId=$toolUseId" }
            return true
        }
        task.isBackground = true
        task.backgroundTime = System.currentTimeMillis()
        logger.info { "⏸️ Task moved to background: toolUseId=$toolUseId, sessionId=${task.sessionId}" }
        
        // 通知前端任务已后台化
        notifyTaskUpdate(
            toolUseId = toolUseId,
            sessionId = task.sessionId,
            action = "backgrounded",
            command = task.command,
            isBackground = true,
            startTime = task.startTime,
            elapsedMs = task.getElapsedMs()
        )
        return true
    }

    /**
     * 检查任务是否在后台运行
     */
    fun isTaskInBackground(toolUseId: String): Boolean {
        return runningTasks[toolUseId]?.isBackground == true
    }

    /**
     * 获取可后台化的任务列表
     * 返回运行超过指定时长且未后台化的任务
     */
    fun getBackgroundableTasks(thresholdMs: Long = 5000): List<TerminalBackgroundTask> {
        val now = System.currentTimeMillis()
        val allTasks = runningTasks.values.toList()
        val result = allTasks.filter { task ->
            !task.isBackground && (now - task.startTime) >= thresholdMs
        }
        logger.info { "📋 [getBackgroundableTasks] runningTasks=${allTasks.size}, filtered=${result.size}, threshold=${thresholdMs}ms" }
        if (allTasks.isNotEmpty()) {
            allTasks.forEach { task ->
                val elapsed = now - task.startTime
                logger.info { "  - toolUseId=${task.toolUseId}, elapsed=${elapsed}ms, isBackground=${task.isBackground}, qualifies=${!task.isBackground && elapsed >= thresholdMs}" }
            }
        }
        return result
    }

    /**
     * 获取指定会话的运行中任务
     */
    fun getRunningTaskBySession(sessionId: String): TerminalBackgroundTask? {
        return runningTasks.values.find { it.sessionId == sessionId && !it.isBackground }
    }

    /**
     * 获取指定 toolUseId 的任务
     */
    fun getTask(toolUseId: String): TerminalBackgroundTask? {
        return runningTasks[toolUseId]
    }

    /**
     * 清理所有会话
     */
    fun dispose() {
        scope.cancel()
        sessions.keys.toList().forEach { killSession(it) }
        sessions.clear()
        runningTasks.clear()
    }
}
