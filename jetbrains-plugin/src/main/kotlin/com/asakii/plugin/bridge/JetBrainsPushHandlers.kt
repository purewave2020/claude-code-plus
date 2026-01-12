package com.asakii.plugin.bridge

import com.asakii.rpc.api.*
import com.asakii.rpc.proto.ActiveFileChangedNotify
import com.asakii.rpc.proto.IdeSettings
import com.asakii.rpc.proto.IdeSettingsChangedNotify
import com.asakii.rpc.proto.ServerCallRequest
import com.asakii.rpc.proto.SessionCommandNotify
import com.asakii.rpc.proto.SessionCommandType
import com.asakii.rpc.proto.ThemeChangedNotify
import com.asakii.settings.AgentSettingsService
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.io.Buffer
import kotlinx.io.write
import com.asakii.logging.*
import java.util.concurrent.ConcurrentHashMap

/**
 * JetBrains IDE 推送处理器
 * 
 * 负责将 IDE 事件推送到前端客户端
 */
class JetBrainsPushHandlers(
    private val connectedClients: ConcurrentHashMap<String, RSocket>
) {
    private val logger = getLogger("JetBrainsPushHandlers")
    private var callIdCounter = 0

    /**
     * 推送主题变化到前端（使用统一的 client.call 路由）
     * 广播给所有连接的客户端
     */
    suspend fun pushThemeChanged(theme: JetBrainsIdeTheme) {
        val clients = connectedClients.values.toList()
        if (clients.isEmpty()) {
            logger.warn { "⚠️ [JetBrains RSocket] 无客户端连接，跳过主题推送" }
            return
        }

        try {
            val themeNotify = ThemeChangedNotify.newBuilder()
                .setBackground(theme.background)
                .setForeground(theme.foreground)
                .setBorderColor(theme.borderColor)
                .setPanelBackground(theme.panelBackground)
                .setTextFieldBackground(theme.textFieldBackground)
                .setSelectionBackground(theme.selectionBackground)
                .setSelectionForeground(theme.selectionForeground)
                .setLinkColor(theme.linkColor)
                .setErrorColor(theme.errorColor)
                .setWarningColor(theme.warningColor)
                .setSuccessColor(theme.successColor)
                .setSeparatorColor(theme.separatorColor)
                .setHoverBackground(theme.hoverBackground)
                .setAccentColor(theme.accentColor)
                .setInfoBackground(theme.infoBackground)
                .setCodeBackground(theme.codeBackground)
                .setSecondaryForeground(theme.secondaryForeground)
                .setFontFamily(theme.fontFamily)
                .setFontSize(theme.fontSize)
                .setEditorFontFamily(theme.editorFontFamily)
                .setEditorFontSize(theme.editorFontSize)
                .build()

            val callId = "jb-${++callIdCounter}"
            val serverCall = ServerCallRequest.newBuilder()
                .setCallId(callId)
                .setMethod("onThemeChanged")
                .setThemeChanged(themeNotify)
                .build()

            val serverCallBytes = serverCall.toByteArray()

            clients.forEach { requester ->
                try {
                    val payload = buildPayloadWithRoute("client.call", serverCallBytes)
                    requester.fireAndForget(payload)
                } catch (e: Exception) {
                    logger.warn { "⚠️ [JetBrains RSocket] 推送主题给客户端失败: ${e.message}" }
                }
            }
            logger.info { "📤 [JetBrains RSocket] → pushThemeChanged (to ${clients.size} clients)" }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains RSocket] pushThemeChanged failed: ${e.message}" }
        }
    }

    /**
     * 推送设置变更到前端（使用统一的 client.call 路由）
     * 广播给所有连接的客户端
     */
    suspend fun pushSettingsChanged(settings: AgentSettingsService) {
        val clients = connectedClients.values.toList()
        if (clients.isEmpty()) {
            logger.warn { "⚠️ [JetBrains RSocket] 无客户端连接，跳过设置推送" }
            return
        }

        try {
            val thinkingLevelsProto = settings.getAllThinkingLevels().map { level: com.asakii.settings.ThinkingLevelConfig ->
                com.asakii.rpc.proto.ThinkingLevelConfig.newBuilder()
                    .setId(level.id)
                    .setName(level.name)
                    .setTokens(level.tokens)
                    .setIsCustom(level.isCustom)
                    .build()
            }

            val defaultModelInfo = settings.getModelById(settings.defaultModel)
            val defaultModelName = defaultModelInfo?.displayName ?: settings.defaultModel
            val ideSettings = IdeSettings.newBuilder()
                .setDefaultModelId(settings.defaultModel)
                .setDefaultModelName(defaultModelName)
                .setDefaultBypassPermissions(settings.defaultBypassPermissions)
                .setClaudeDefaultAutoCleanupContexts(settings.claudeDefaultAutoCleanupContexts)
                .setCodexDefaultAutoCleanupContexts(settings.codexDefaultAutoCleanupContexts)
                .setEnableUserInteractionMcp(settings.enableUserInteractionMcp)
                .setEnableJetbrainsMcp(settings.enableJetBrainsMcp)
                .setIncludePartialMessages(settings.includePartialMessages)
                .setDefaultThinkingLevel(settings.defaultThinkingLevel)
                .setDefaultThinkingTokens(settings.defaultThinkingTokens)
                .setDefaultThinkingLevelId(settings.defaultThinkingLevelId)
                .addAllThinkingLevels(thinkingLevelsProto)
                .setPermissionMode(settings.permissionMode)
                .setCodexDefaultModelId(settings.codexDefaultModelId)
                .setCodexDefaultReasoningEffort(settings.codexDefaultReasoningEffort)
                .setCodexDefaultReasoningSummary(settings.codexDefaultReasoningSummary)
                .setCodexDefaultSandboxMode(settings.codexDefaultSandboxMode)
                .build()

            val settingsNotify = IdeSettingsChangedNotify.newBuilder()
                .setSettings(ideSettings)
                .build()

            val callId = "jb-${++callIdCounter}"
            val serverCall = ServerCallRequest.newBuilder()
                .setCallId(callId)
                .setMethod("onSettingsChanged")
                .setSettingsChanged(settingsNotify)
                .build()

            val serverCallBytes = serverCall.toByteArray()

            clients.forEach { requester ->
                try {
                    val payload = buildPayloadWithRoute("client.call", serverCallBytes)
                    requester.fireAndForget(payload)
                } catch (e: Exception) {
                    logger.warn { "⚠️ [JetBrains RSocket] 推送设置给客户端失败: ${e.message}" }
                }
            }
            logger.info { "📤 [JetBrains RSocket] → pushSettingsChanged (to ${clients.size} clients)" }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains RSocket] pushSettingsChanged failed: ${e.message}" }
        }
    }

    /**
     * 推送会话命令到前端（使用统一的 client.call 路由）
     * 广播给所有连接的客户端
     */
    suspend fun pushSessionCommand(command: JetBrainsSessionCommand) {
        val clients = connectedClients.values.toList()
        if (clients.isEmpty()) {
            logger.warn { "⚠️ [JetBrains RSocket] 无客户端连接，跳过命令推送" }
            return
        }

        try {
            val cmdType = when (command.type) {
                JetBrainsSessionCommandType.SWITCH -> SessionCommandType.SESSION_CMD_SWITCH
                JetBrainsSessionCommandType.CREATE -> SessionCommandType.SESSION_CMD_CREATE
                JetBrainsSessionCommandType.CLOSE -> SessionCommandType.SESSION_CMD_CLOSE
                JetBrainsSessionCommandType.RENAME -> SessionCommandType.SESSION_CMD_RENAME
                JetBrainsSessionCommandType.TOGGLE_HISTORY -> SessionCommandType.SESSION_CMD_TOGGLE_HISTORY
                JetBrainsSessionCommandType.SET_LOCALE -> SessionCommandType.SESSION_CMD_SET_LOCALE
                JetBrainsSessionCommandType.DELETE -> SessionCommandType.SESSION_CMD_DELETE
                JetBrainsSessionCommandType.RESET -> SessionCommandType.SESSION_CMD_RESET
                else -> SessionCommandType.SESSION_CMD_UNSPECIFIED
            }

            val cmdNotify = SessionCommandNotify.newBuilder().setType(cmdType)
            command.sessionId?.let { cmdNotify.setSessionId(it) }
            command.newName?.let { cmdNotify.setNewName(it) }
            command.locale?.let { cmdNotify.setLocale(it) }

            val callId = "jb-${++callIdCounter}"
            val serverCall = ServerCallRequest.newBuilder()
                .setCallId(callId)
                .setMethod("onSessionCommand")
                .setSessionCommand(cmdNotify.build())
                .build()

            val serverCallBytes = serverCall.toByteArray()

            clients.forEach { requester ->
                try {
                    val payload = buildPayloadWithRoute("client.call", serverCallBytes)
                    requester.fireAndForget(payload)
                } catch (e: Exception) {
                    logger.warn { "⚠️ [JetBrains RSocket] 推送命令给客户端失败: ${e.message}" }
                }
            }
            logger.info { "📤 [JetBrains RSocket] → pushSessionCommand: ${command.type} (to ${clients.size} clients)" }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains RSocket] pushSessionCommand failed: ${e.message}" }
        }
    }

    /**
     * 推送终端任务更新到前端（使用统一的 client.call 路由）
     * 广播给所有连接的客户端
     */
    suspend fun pushTerminalTaskUpdate(
        toolUseId: String,
        sessionId: String,
        action: String,
        command: String,
        isBackground: Boolean,
        startTime: Long,
        elapsedMs: Long? = null
    ) {
        val clients = connectedClients.values.toList()
        if (clients.isEmpty()) {
            logger.debug { "⚠️ [JetBrains RSocket] 无客户端连接，跳过终端任务推送" }
            return
        }

        try {
            val taskAction = when (action) {
                "started" -> com.asakii.rpc.proto.TerminalTaskAction.TERMINAL_TASK_STARTED
                "completed" -> com.asakii.rpc.proto.TerminalTaskAction.TERMINAL_TASK_COMPLETED
                "backgrounded" -> com.asakii.rpc.proto.TerminalTaskAction.TERMINAL_TASK_BACKGROUNDED
                else -> com.asakii.rpc.proto.TerminalTaskAction.TERMINAL_TASK_STARTED
            }

            val notifyBuilder = com.asakii.rpc.proto.TerminalTaskUpdateNotify.newBuilder()
                .setToolUseId(toolUseId)
                .setSessionId(sessionId)
                .setAction(taskAction)
                .setCommand(command)
                .setIsBackground(isBackground)
                .setStartTime(startTime)

            elapsedMs?.let { notifyBuilder.setElapsedMs(it) }

            val callId = "jb-${++callIdCounter}"
            val serverCall = ServerCallRequest.newBuilder()
                .setCallId(callId)
                .setMethod("onTerminalTaskUpdate")
                .setTerminalTaskUpdate(notifyBuilder.build())
                .build()

            val serverCallBytes = serverCall.toByteArray()

            clients.forEach { requester ->
                try {
                    val payload = buildPayloadWithRoute("client.call", serverCallBytes)
                    requester.fireAndForget(payload)
                } catch (e: Exception) {
                    logger.warn { "⚠️ [JetBrains RSocket] 推送终端任务给客户端失败: ${e.message}" }
                }
            }
            logger.debug { "📤 [JetBrains RSocket] → pushTerminalTaskUpdate: $action (toolUseId=$toolUseId, to ${clients.size} clients)" }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains RSocket] pushTerminalTaskUpdate failed: ${e.message}" }
        }
    }

    /**
     * 推送活跃文件变更到前端（使用统一的 client.call 路由）
     * 广播给所有连接的客户端
     */
    suspend fun pushActiveFileChanged(activeFile: ActiveFileInfo?) {
        val clients = connectedClients.values.toList()
        if (clients.isEmpty()) {
            logger.warn { "⚠️ [JetBrains RSocket] 无客户端连接，跳过活跃文件推送" }
            return
        }

        try {
            val notifyBuilder = ActiveFileChangedNotify.newBuilder()
                .setHasActiveFile(activeFile != null)

            if (activeFile != null) {
                notifyBuilder.setPath(activeFile.path)
                notifyBuilder.setRelativePath(activeFile.relativePath)
                notifyBuilder.setName(activeFile.name)
                activeFile.line?.let { notifyBuilder.setLine(it) }
                activeFile.column?.let { notifyBuilder.setColumn(it) }
                notifyBuilder.setHasSelection(activeFile.hasSelection)
                activeFile.startLine?.let { notifyBuilder.setStartLine(it) }
                activeFile.startColumn?.let { notifyBuilder.setStartColumn(it) }
                activeFile.endLine?.let { notifyBuilder.setEndLine(it) }
                activeFile.endColumn?.let { notifyBuilder.setEndColumn(it) }
            }

            val callId = "jb-${++callIdCounter}"
            val serverCall = ServerCallRequest.newBuilder()
                .setCallId(callId)
                .setMethod("onActiveFileChanged")
                .setActiveFileChanged(notifyBuilder.build())
                .build()

            val serverCallBytes = serverCall.toByteArray()

            clients.forEach { requester ->
                try {
                    val payload = buildPayloadWithRoute("client.call", serverCallBytes)
                    requester.fireAndForget(payload)
                } catch (e: Exception) {
                    logger.warn { "⚠️ [JetBrains RSocket] 推送给客户端失败: ${e.message}" }
                }
            }

            if (activeFile != null) {
                logger.info("📤 [JetBrains RSocket] → pushActiveFileChanged: ${activeFile.relativePath} (to ${clients.size} clients)" +
                    if (activeFile.hasSelection) " (selection: ${activeFile.startLine}:${activeFile.startColumn} - ${activeFile.endLine}:${activeFile.endColumn})" else "")
            } else {
                logger.info { "📤 [JetBrains RSocket] → pushActiveFileChanged: null (no active file, to ${clients.size} clients)" }
            }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains RSocket] pushActiveFileChanged failed: ${e.message}" }
        }
    }

    /**
     * 构建带路由的 Payload
     */
    private fun buildPayloadWithRoute(route: String, data: ByteArray): Payload {
        val routeBytes = route.toByteArray(Charsets.UTF_8)
        val metadata = ByteArray(1 + routeBytes.size)
        metadata[0] = routeBytes.size.toByte()
        System.arraycopy(routeBytes, 0, metadata, 1, routeBytes.size)

        val metadataBuffer = Buffer().apply { write(metadata) }
        val dataBuffer = Buffer().apply { write(data) }

        return buildPayload {
            data(dataBuffer)
            metadata(metadataBuffer)
        }
    }
}
