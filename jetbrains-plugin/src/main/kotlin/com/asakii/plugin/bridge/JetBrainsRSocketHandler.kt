package com.asakii.plugin.bridge

import com.asakii.plugin.mcp.tools.FileChangeLabelCache
import com.asakii.rpc.api.*
import com.asakii.rpc.proto.ActiveFileChangedNotify
import com.asakii.rpc.proto.IdeThemeProto
import com.asakii.rpc.proto.GetIdeSettingsResponse
import com.asakii.rpc.proto.IdeSettings
import com.asakii.rpc.proto.IdeSettingsChangedNotify
import com.asakii.rpc.proto.JetBrainsGetLocaleResponse
import com.asakii.rpc.proto.JetBrainsGetProjectPathResponse
import com.asakii.rpc.proto.JetBrainsGetThemeResponse
import com.asakii.rpc.proto.JetBrainsOperationResponse
import com.asakii.rpc.proto.JetBrainsSessionCommandType as ProtoSessionCommandType
import com.asakii.rpc.proto.ServerCallRequest
import com.asakii.rpc.proto.SessionCommandNotify
import com.asakii.rpc.proto.SessionCommandType
import com.asakii.rpc.proto.ThemeChangedNotify
import com.asakii.settings.AgentSettingsService
import com.asakii.server.JetBrainsRSocketHandlerProvider
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.*
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import com.asakii.rpc.proto.JetBrainsOpenFileRequest as ProtoOpenFileRequest
import com.asakii.rpc.proto.JetBrainsShowDiffRequest as ProtoShowDiffRequest
import com.asakii.rpc.proto.JetBrainsShowMultiEditDiffRequest as ProtoShowMultiEditDiffRequest
import com.asakii.rpc.proto.JetBrainsShowEditPreviewRequest as ProtoShowEditPreviewRequest
import com.asakii.rpc.proto.JetBrainsShowMarkdownRequest as ProtoShowMarkdownRequest
import com.asakii.rpc.proto.JetBrainsShowEditFullDiffRequest as ProtoShowEditFullDiffRequest
import com.asakii.rpc.proto.JetBrainsSetLocaleRequest as ProtoSetLocaleRequest
import com.asakii.rpc.proto.JetBrainsSessionState as ProtoSessionState
import com.asakii.rpc.proto.JetBrainsSessionCommand as ProtoSessionCommand

/**
 * JetBrains IDE 集成 RSocket 处理器
 *
 * 职责：
 * 1. 处理前端调用（openFile, showDiff, getTheme 等）
 * 2. 支持反向调用（pushThemeChange, pushSessionCommand 等）
 *
 * 路由表（前端 → 后端）：
 * - jetbrains.openFile: 打开文件
 * - jetbrains.showDiff: 显示 Diff
 * - jetbrains.showMultiEditDiff: 显示多编辑 Diff
 * - jetbrains.getTheme: 获取主题
 * - jetbrains.getActiveFile: 获取当前活跃文件
 * - jetbrains.getSettings: 获取 IDE 设置
 * - jetbrains.getLocale: 获取语言
 * - jetbrains.setLocale: 设置语言
 * - jetbrains.getProjectPath: 获取项目路径
 * - jetbrains.reportSessionState: 上报会话状态
 *
 * 反向调用路由（后端 → 前端）：
 * - jetbrains.onThemeChanged: 主题变化
 * - jetbrains.onSessionCommand: 会话命令
 * - jetbrains.onActiveFileChanged: 活跃文件变化
 */
class JetBrainsRSocketHandler(
    private val jetbrainsApi: JetBrainsApi
) : JetBrainsRSocketHandlerProvider {
    private val logger = KotlinLogging.logger {}

    // 客户端 requester（用于反向调用）
    private var clientRequester: RSocket? = null

    // 连接的客户端集合（支持多客户端）
    private val connectedClients = ConcurrentHashMap<String, RSocket>()

    /**
     * 创建 RSocket 请求处理器
     */
    override fun createHandler(): RSocket {
        logger.info("🔌 [JetBrains RSocket] 创建请求处理器")

        return RSocketRequestHandler {
            requestResponse { request ->
                val route = extractRoute(request)
                val dataBytes = request.data.readByteArray()
                logger.info("📨 [JetBrains RSocket] ← $route")

                when (route) {
                    "jetbrains.openFile" -> handleOpenFile(dataBytes)
                    "jetbrains.showDiff" -> handleShowDiff(dataBytes)
                    "jetbrains.showMultiEditDiff" -> handleShowMultiEditDiff(dataBytes)
                    "jetbrains.showEditPreviewDiff" -> handleShowEditPreviewDiff(dataBytes)
                    "jetbrains.showEditFullDiff" -> handleShowEditFullDiff(dataBytes)
                    "jetbrains.showMarkdown" -> handleShowMarkdown(dataBytes)
                    "jetbrains.getTheme" -> handleGetTheme()
                    "jetbrains.getActiveFile" -> handleGetActiveFile()
                    "jetbrains.getSettings" -> handleGetSettings()
                    "jetbrains.getLocale" -> handleGetLocale()
                    "jetbrains.setLocale" -> handleSetLocale(dataBytes)
                    "jetbrains.getProjectPath" -> handleGetProjectPath()
                    "jetbrains.reportSessionState" -> handleReportSessionState(dataBytes)
                    "jetbrains.getOriginalContent" -> handleGetOriginalContent(dataBytes)
                    "jetbrains.getFileHistoryContent" -> handleGetFileHistoryContent(dataBytes)
                    "jetbrains.rollbackFile" -> handleRollbackFile(dataBytes)
                    else -> {
                        logger.warn("⚠️ [JetBrains RSocket] Unknown route: $route")
                        buildErrorResponse("Unknown route: $route")
                    }
                }
            }
        }
    }

    /**
     * 设置客户端 requester（用于反向调用）
     */
    override fun setClientRequester(clientId: String, requester: RSocket) {
        this.clientRequester = requester
        connectedClients[clientId] = requester
        logger.info("🔗 [JetBrains RSocket] 客户端已连接: $clientId")
    }

    /**
     * 移除客户端
     */
    override fun removeClient(clientId: String) {
        connectedClients.remove(clientId)
        if (connectedClients.isEmpty()) {
            clientRequester = null
        }
        logger.info("🔌 [JetBrains RSocket] 客户端已断开: $clientId")
    }

    // ==================== 前端调用处理 ====================

    private fun handleOpenFile(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoOpenFileRequest.parseFrom(dataBytes)
            logger.info("📂 [JetBrains] openFile: ${req.filePath}")

            val request = com.asakii.rpc.api.JetBrainsOpenFileRequest(
                filePath = req.filePath,
                line = if (req.hasLine()) req.line else null,
                column = if (req.hasColumn()) req.column else null,
                startOffset = if (req.hasStartOffset()) req.startOffset else null,
                endOffset = if (req.hasEndOffset()) req.endOffset else null
            )

            val result = jetbrainsApi.file.openFile(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] openFile failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowDiffRequest.parseFrom(dataBytes)
            logger.info("📝 [JetBrains] showDiff: ${req.filePath}")

            val request = com.asakii.rpc.api.JetBrainsShowDiffRequest(
                filePath = req.filePath,
                oldContent = req.oldContent,
                newContent = req.newContent,
                title = if (req.hasTitle()) req.title else null
            )

            val result = jetbrainsApi.file.showDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] showDiff failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowMultiEditDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowMultiEditDiffRequest.parseFrom(dataBytes)
            logger.info("📝 [JetBrains] showMultiEditDiff: ${req.filePath} (${req.editsCount} edits)")

            val request = com.asakii.rpc.api.JetBrainsShowMultiEditDiffRequest(
                filePath = req.filePath,
                edits = req.editsList.map { edit ->
                    com.asakii.rpc.api.JetBrainsEditOperation(
                        oldString = edit.oldString,
                        newString = edit.newString,
                        replaceAll = edit.replaceAll
                    )
                },
                currentContent = if (req.hasCurrentContent()) req.currentContent else null
            )

            val result = jetbrainsApi.file.showMultiEditDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] showMultiEditDiff failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowEditPreviewDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowEditPreviewRequest.parseFrom(dataBytes)
            logger.info("👀 [JetBrains] showEditPreviewDiff: ${req.filePath} (${req.editsCount} edits)")

            val request = com.asakii.rpc.api.JetBrainsShowEditPreviewRequest(
                filePath = req.filePath,
                edits = req.editsList.map { edit ->
                    com.asakii.rpc.api.JetBrainsEditOperation(
                        oldString = edit.oldString,
                        newString = edit.newString,
                        replaceAll = edit.replaceAll
                    )
                },
                title = if (req.hasTitle()) req.title else null
            )

            val result = jetbrainsApi.file.showEditPreviewDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] showEditPreviewDiff failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowMarkdown(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowMarkdownRequest.parseFrom(dataBytes)
            logger.info("📄 [JetBrains] showMarkdown: ${req.title ?: "Plan Preview"}")

            val request = com.asakii.rpc.api.JetBrainsShowMarkdownRequest(
                content = req.content,
                title = if (req.hasTitle()) req.title else null
            )

            val result = jetbrainsApi.file.showMarkdown(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] showMarkdown failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleShowEditFullDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowEditFullDiffRequest.parseFrom(dataBytes)
            logger.info("📝 [JetBrains] showEditFullDiff: ${req.filePath}")

            val request = com.asakii.rpc.api.JetBrainsShowEditFullDiffRequest(
                filePath = req.filePath,
                oldString = req.oldString,
                newString = req.newString,
                replaceAll = req.replaceAll,
                title = if (req.hasTitle()) req.title else null,
                originalContent = if (req.hasOriginalContent()) req.originalContent else null
            )

            val result = jetbrainsApi.file.showEditFullDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] showEditFullDiff failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetSettings(): Payload {
        return try {
            val settings = AgentSettingsService.getInstance()
            logger.info("⚙️ [JetBrains] getSettings")

            // 转换思考级别列表为 Proto 格式
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

            val response = GetIdeSettingsResponse.newBuilder()
                .setSettings(ideSettings)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] getSettings failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetActiveFile(): Payload {
        return try {
            val activeFile = jetbrainsApi.file.getActiveFile()
            logger.info("📂 [JetBrains] getActiveFile: ${activeFile?.relativePath ?: "null"}")

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
                activeFile.selectedContent?.let { notifyBuilder.setSelectedContent(it) }
            }

            buildPayload { data(notifyBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] getActiveFile failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetTheme(): Payload {
        return try {
            val theme = jetbrainsApi.theme.get()
                ?: return buildErrorResponse("Theme not available")
            logger.info("🎨 [JetBrains] getTheme")

            val protoTheme = IdeThemeProto.newBuilder()
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

            val response = JetBrainsGetThemeResponse.newBuilder()
                .setTheme(protoTheme)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] getTheme failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetLocale(): Payload {
        return try {
            val locale = jetbrainsApi.locale.get()
            logger.info("🌐 [JetBrains] getLocale: $locale")

            val response = JetBrainsGetLocaleResponse.newBuilder()
                .setLocale(locale)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] getLocale failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleSetLocale(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoSetLocaleRequest.parseFrom(dataBytes)
            logger.info("🌐 [JetBrains] setLocale: ${req.locale}")

            val result = jetbrainsApi.locale.set(req.locale)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] setLocale failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleGetProjectPath(): Payload {
        return try {
            val projectPath = jetbrainsApi.project.getPath()
            logger.info("📁 [JetBrains] getProjectPath: $projectPath")

            val response = JetBrainsGetProjectPathResponse.newBuilder()
                .setProjectPath(projectPath)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] getProjectPath failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun handleReportSessionState(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoSessionState.parseFrom(dataBytes)
            logger.info("📊 [JetBrains] reportSessionState: ${req.sessionsCount} sessions")

            val state = JetBrainsSessionState(
                sessions = req.sessionsList.map { session ->
                    JetBrainsSessionSummary(
                        id = session.id,
                        title = session.title,
                        sessionId = if (session.hasSessionId()) session.sessionId else null,
                        isGenerating = session.isGenerating,
                        isConnected = session.isConnected,
                        isConnecting = session.isConnecting
                    )
                },
                activeSessionId = if (req.hasActiveSessionId()) req.activeSessionId else null
            )

            jetbrainsApi.session.receiveState(state)
            buildOperationResponse(true, null)
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] reportSessionState failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    /**
     * 获取文件修改前的原始内容
     * 使用 LocalHistory Label 缓存
     */
    private fun handleGetOriginalContent(dataBytes: ByteArray): Payload {
        return try {
            // dataBytes 直接是 toolUseId 字符串
            val toolUseId = String(dataBytes, Charsets.UTF_8)
            logger.info("📄 [JetBrains] getOriginalContent: toolUseId=$toolUseId")

            val content = FileChangeLabelCache.getOriginalContent(toolUseId)

            // 构建响应：success + content（可能为 null）
            val responseBuilder = com.asakii.rpc.proto.JetBrainsGetOriginalContentResponse.newBuilder()
                .setSuccess(true)
                .setFound(content != null)

            if (content != null) {
                responseBuilder.setContent(content)
            }

            buildPayload { data(responseBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] getOriginalContent failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    /**
     * 获取文件历史内容（基于时间戳查询 LocalHistory）
     * 用于历史会话加载时的 Diff 显示
     */
    private fun handleGetFileHistoryContent(dataBytes: ByteArray): Payload {
        return try {
            val req = com.asakii.rpc.proto.JetBrainsGetFileHistoryContentRequest.parseFrom(dataBytes)
            logger.info("📄 [JetBrains] getFileHistoryContent: ${req.filePath} (before: ${req.beforeTimestamp})")

            val content = com.asakii.plugin.services.FileHistoryService.getContentBefore(
                req.filePath,
                req.beforeTimestamp
            )

            val responseBuilder = com.asakii.rpc.proto.JetBrainsGetFileHistoryContentResponse.newBuilder()
                .setSuccess(true)
                .setFound(content != null)

            if (content != null) {
                responseBuilder.setContent(content)
            }

            buildPayload { data(responseBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] getFileHistoryContent failed: ${e.message}")
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    /**
     * 回滚文件到指定时间戳之前的版本
     * 用于前端文件回滚功能
     */
    private fun handleRollbackFile(dataBytes: ByteArray): Payload {
        return try {
            val req = com.asakii.rpc.proto.JetBrainsRollbackFileRequest.parseFrom(dataBytes)
            logger.info("↩️ [JetBrains] rollbackFile: ${req.filePath} (before: ${req.beforeTimestamp})")

            val result = com.asakii.plugin.services.FileHistoryService.rollbackToTimestamp(
                req.filePath,
                req.beforeTimestamp
            )

            val responseBuilder = com.asakii.rpc.proto.JetBrainsRollbackFileResponse.newBuilder()
                .setSuccess(result.success)

            if (result.error != null) {
                responseBuilder.setError(result.error)
            }

            buildPayload { data(responseBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error("❌ [JetBrains] rollbackFile failed: ${e.message}")
            val response = com.asakii.rpc.proto.JetBrainsRollbackFileResponse.newBuilder()
                .setSuccess(false)
                .setError(e.message ?: "Unknown error")
                .build()
            buildPayload { data(response.toByteArray()) }
        }
    }

    // ==================== 反向调用（后端 → 前端）====================

    // 调用 ID 计数器
    private var callIdCounter = 0

    /**
     * 推送主题变化到前端（使用统一的 client.call 路由）
     * 广播给所有连接的客户端
     */
    suspend fun pushThemeChanged(theme: JetBrainsIdeTheme) {
        val clients = connectedClients.values.toList()
        if (clients.isEmpty()) {
            logger.warn("⚠️ [JetBrains RSocket] 无客户端连接，跳过主题推送")
            return
        }

        try {
            // 构建 ThemeChangedNotify
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

            // 包装为 ServerCallRequest
            val callId = "jb-${++callIdCounter}"
            val serverCall = ServerCallRequest.newBuilder()
                .setCallId(callId)
                .setMethod("onThemeChanged")
                .setThemeChanged(themeNotify)
                .build()

            val serverCallBytes = serverCall.toByteArray()

            // 广播给所有连接的客户端
            // 注意：每个客户端需要独立的 Payload，因为 Buffer 会被消费
            clients.forEach { requester ->
                try {
                    val payload = buildPayloadWithRoute("client.call", serverCallBytes)
                    requester.fireAndForget(payload)
                } catch (e: Exception) {
                    logger.warn("⚠️ [JetBrains RSocket] 推送主题给客户端失败: ${e.message}")
                }
            }
            logger.info("📤 [JetBrains RSocket] → pushThemeChanged (to ${clients.size} clients)")
        } catch (e: Exception) {
            logger.error("❌ [JetBrains RSocket] pushThemeChanged failed: ${e.message}")
        }
    }

    /**
     * 推送设置变更到前端（使用统一的 client.call 路由）
     * 广播给所有连接的客户端
     */
    suspend fun pushSettingsChanged(settings: AgentSettingsService) {
        val clients = connectedClients.values.toList()
        if (clients.isEmpty()) {
            logger.warn("⚠️ [JetBrains RSocket] 无客户端连接，跳过设置推送")
            return
        }

        try {
            // 转换思考级别列表为 Proto 格式
            val thinkingLevelsProto = settings.getAllThinkingLevels().map { level: com.asakii.settings.ThinkingLevelConfig ->
                com.asakii.rpc.proto.ThinkingLevelConfig.newBuilder()
                    .setId(level.id)
                    .setName(level.name)
                    .setTokens(level.tokens)
                    .setIsCustom(level.isCustom)
                    .build()
            }

            // 构建 IdeSettings
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

            // 构建 IdeSettingsChangedNotify
            val settingsNotify = IdeSettingsChangedNotify.newBuilder()
                .setSettings(ideSettings)
                .build()

            // 包装为 ServerCallRequest
            val callId = "jb-${++callIdCounter}"
            val serverCall = ServerCallRequest.newBuilder()
                .setCallId(callId)
                .setMethod("onSettingsChanged")
                .setSettingsChanged(settingsNotify)
                .build()

            val serverCallBytes = serverCall.toByteArray()

            // 广播给所有连接的客户端
            // 注意：每个客户端需要独立的 Payload，因为 Buffer 会被消费
            clients.forEach { requester ->
                try {
                    val payload = buildPayloadWithRoute("client.call", serverCallBytes)
                    requester.fireAndForget(payload)
                } catch (e: Exception) {
                    logger.warn("⚠️ [JetBrains RSocket] 推送设置给客户端失败: ${e.message}")
                }
            }
            logger.info("📤 [JetBrains RSocket] → pushSettingsChanged (to ${clients.size} clients)")
        } catch (e: Exception) {
            logger.error("❌ [JetBrains RSocket] pushSettingsChanged failed: ${e.message}")
        }
    }

    /**
     * 推送会话命令到前端（使用统一的 client.call 路由）
     * 广播给所有连接的客户端
     */
    suspend fun pushSessionCommand(command: JetBrainsSessionCommand) {
        val clients = connectedClients.values.toList()
        if (clients.isEmpty()) {
            logger.warn("⚠️ [JetBrains RSocket] 无客户端连接，跳过命令推送")
            return
        }

        try {
            // 转换为新的 SessionCommandType
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

            // 构建 SessionCommandNotify
            val cmdNotify = SessionCommandNotify.newBuilder().setType(cmdType)
            command.sessionId?.let { cmdNotify.setSessionId(it) }
            command.newName?.let { cmdNotify.setNewName(it) }
            command.locale?.let { cmdNotify.setLocale(it) }

            // 包装为 ServerCallRequest
            val callId = "jb-${++callIdCounter}"
            val serverCall = ServerCallRequest.newBuilder()
                .setCallId(callId)
                .setMethod("onSessionCommand")
                .setSessionCommand(cmdNotify.build())
                .build()

            val serverCallBytes = serverCall.toByteArray()

            // 广播给所有连接的客户端
            // 注意：每个客户端需要独立的 Payload，因为 Buffer 会被消费
            clients.forEach { requester ->
                try {
                    val payload = buildPayloadWithRoute("client.call", serverCallBytes)
                    requester.fireAndForget(payload)
                } catch (e: Exception) {
                    logger.warn("⚠️ [JetBrains RSocket] 推送命令给客户端失败: ${e.message}")
                }
            }
            logger.info("📤 [JetBrains RSocket] → pushSessionCommand: ${command.type} (to ${clients.size} clients)")
        } catch (e: Exception) {
            logger.error("❌ [JetBrains RSocket] pushSessionCommand failed: ${e.message}")
        }
    }

    /**
     * 推送活跃文件变更到前端（使用统一的 client.call 路由）
     * 广播给所有连接的客户端
     */
    suspend fun pushActiveFileChanged(activeFile: ActiveFileInfo?) {
        val clients = connectedClients.values.toList()
        if (clients.isEmpty()) {
            logger.warn("⚠️ [JetBrains RSocket] 无客户端连接，跳过活跃文件推送")
            return
        }

        try {
            // 构建 ActiveFileChangedNotify
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

            // 包装为 ServerCallRequest
            val callId = "jb-${++callIdCounter}"
            val serverCall = ServerCallRequest.newBuilder()
                .setCallId(callId)
                .setMethod("onActiveFileChanged")
                .setActiveFileChanged(notifyBuilder.build())
                .build()

            val serverCallBytes = serverCall.toByteArray()

            // 广播给所有连接的客户端
            // 注意：每个客户端需要独立的 Payload，因为 Buffer 会被消费
            clients.forEach { requester ->
                try {
                    val payload = buildPayloadWithRoute("client.call", serverCallBytes)
                    requester.fireAndForget(payload)
                } catch (e: Exception) {
                    logger.warn("⚠️ [JetBrains RSocket] 推送给客户端失败: ${e.message}")
                }
            }

            if (activeFile != null) {
                logger.info("📤 [JetBrains RSocket] → pushActiveFileChanged: ${activeFile.relativePath} (to ${clients.size} clients)" +
                    if (activeFile.hasSelection) " (selection: ${activeFile.startLine}:${activeFile.startColumn} - ${activeFile.endLine}:${activeFile.endColumn})" else "")
            } else {
                logger.info("📤 [JetBrains RSocket] → pushActiveFileChanged: null (no active file, to ${clients.size} clients)")
            }
        } catch (e: Exception) {
            logger.error("❌ [JetBrains RSocket] pushActiveFileChanged failed: ${e.message}")
        }
    }

    // ==================== 辅助方法 ====================

    private fun extractRoute(payload: Payload): String {
        val metadata = payload.metadata ?: throw IllegalArgumentException("Missing metadata")
        val metadataBytes = metadata.readByteArray()
        if (metadataBytes.isEmpty()) {
            throw IllegalArgumentException("Empty metadata")
        }

        val length = metadataBytes[0].toInt() and 0xFF
        return String(metadataBytes, 1, length, Charsets.UTF_8)
    }

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

    private fun buildOperationResponse(success: Boolean, error: String?): Payload {
        val response = JetBrainsOperationResponse.newBuilder().apply {
            this.success = success
            error?.let { this.error = it }
        }.build()

        return buildPayload { data(response.toByteArray()) }
    }

    private fun buildErrorResponse(error: String): Payload {
        return buildOperationResponse(false, error)
    }
}
