package com.asakii.plugin.bridge.handlers

import com.asakii.logging.*
import com.asakii.rpc.api.*
import com.asakii.rpc.proto.IdeThemeProto
import com.asakii.rpc.proto.GetIdeSettingsResponse
import com.asakii.rpc.proto.IdeSettings
import com.asakii.rpc.proto.OptionConfig as OptionConfigProto
import com.asakii.settings.OptionConfig
import com.asakii.rpc.proto.JetBrainsGetLocaleResponse
import com.asakii.rpc.proto.JetBrainsGetProjectPathResponse
import com.asakii.rpc.proto.JetBrainsGetThemeResponse
import com.asakii.rpc.proto.JetBrainsOperationResponse
import com.asakii.settings.AgentSettingsService
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import com.asakii.rpc.proto.JetBrainsSetLocaleRequest as ProtoSetLocaleRequest
import com.asakii.rpc.proto.JetBrainsSessionState as ProtoSessionState
import com.asakii.rpc.proto.ActiveFileChangedNotify

/**
 * IDE 配置和状态处理器
 * 处理: getTheme, getActiveFile, getSettings, getLocale, setLocale, getProjectPath, reportSessionState
 */
class ConfigHandler(private val jetbrainsApi: JetBrainsApi) {
    private val logger = getLogger("ConfigHandler")

    fun handleGetTheme(): Payload {
        return try {
            val theme = jetbrainsApi.theme.get()
                ?: return buildErrorResponse("Theme not available")
            logger.info { "🎨 [JetBrains] getTheme" }

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
            logger.error { "❌ [JetBrains] getTheme failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleGetActiveFile(): Payload {
        return try {
            val activeFile = jetbrainsApi.file.getActiveFile()
            logger.info("📂 [JetBrains] getActiveFile: ${activeFile?.relativePath ?: "null"}")

            val notifyBuilder = ActiveFileChangedNotify.newBuilder()
                .setHasActiveFile(activeFile != null)

            if (activeFile != null) {
                notifyBuilder.setPath(activeFile.path)
                notifyBuilder.setRelativePath(activeFile.relativePath)
                notifyBuilder.setName(activeFile.name)
                activeFile.line?.let { line: Int -> notifyBuilder.setLine(line) }
                activeFile.column?.let { col: Int -> notifyBuilder.setColumn(col) }
                notifyBuilder.setHasSelection(activeFile.hasSelection)
                activeFile.startLine?.let { line: Int -> notifyBuilder.setStartLine(line) }
                activeFile.startColumn?.let { col: Int -> notifyBuilder.setStartColumn(col) }
                activeFile.endLine?.let { line: Int -> notifyBuilder.setEndLine(line) }
                activeFile.endColumn?.let { col: Int -> notifyBuilder.setEndColumn(col) }
                activeFile.selectedContent?.let { content: String -> notifyBuilder.setSelectedContent(content) }
            }

            buildPayload { data(notifyBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getActiveFile failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleGetSettings(): Payload {
        return try {
            val settings = AgentSettingsService.getInstance()
            logger.info { "⚙️ [JetBrains] getSettings" }

            val thinkingLevelsProto = settings.getAllThinkingLevels().map { level: com.asakii.settings.ThinkingLevelConfig ->
                com.asakii.rpc.proto.ThinkingLevelConfig.newBuilder()
                    .setId(level.id)
                    .setName(level.name)
                    .setTokens(level.tokens)
                    .setIsCustom(level.isCustom)
                    .build()
            }

            val codexEffortOptionsProto = settings.getCodexReasoningEffortOptions().map { it.toProto() }
            val codexSummaryOptionsProto = settings.getCodexReasoningSummaryOptions().map { it.toProto() }
            val codexSandboxOptionsProto = settings.getCodexSandboxModeOptions().map { it.toProto() }
            val permissionModeOptionsProto = settings.getPermissionModeOptions().map { it.toProto() }

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
                .addAllCodexReasoningEffortOptions(codexEffortOptionsProto)
                .addAllCodexReasoningSummaryOptions(codexSummaryOptionsProto)
                .addAllCodexSandboxModeOptions(codexSandboxOptionsProto)
                .addAllPermissionModeOptions(permissionModeOptionsProto)
                .build()

            val response = GetIdeSettingsResponse.newBuilder()
                .setSettings(ideSettings)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getSettings failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleGetLocale(): Payload {
        return try {
            val locale = jetbrainsApi.locale.get()
            logger.info { "🌐 [JetBrains] getLocale: $locale" }

            val response = JetBrainsGetLocaleResponse.newBuilder()
                .setLocale(locale)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getLocale failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleSetLocale(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoSetLocaleRequest.parseFrom(dataBytes)
            logger.info { "🌐 [JetBrains] setLocale: ${req.locale}" }

            val result = jetbrainsApi.locale.set(req.locale)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] setLocale failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleGetProjectPath(): Payload {
        return try {
            val projectPath = jetbrainsApi.project.getPath()
            logger.info { "📁 [JetBrains] getProjectPath: $projectPath" }

            val response = JetBrainsGetProjectPathResponse.newBuilder()
                .setProjectPath(projectPath)
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getProjectPath failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleReportSessionState(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoSessionState.parseFrom(dataBytes)
            logger.info { "📊 [JetBrains] reportSessionState: ${req.sessionsCount} sessions" }

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
            logger.error { "❌ [JetBrains] reportSessionState failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
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

/**
 * 将 OptionConfig 转换为 Proto 格式
 */
private fun OptionConfig.toProto(): OptionConfigProto {
    return OptionConfigProto.newBuilder()
        .setId(this.id)
        .setLabel(this.label)
        .setDescription(this.description)
        .setIsDefault(this.isDefault)
        .build()
}
