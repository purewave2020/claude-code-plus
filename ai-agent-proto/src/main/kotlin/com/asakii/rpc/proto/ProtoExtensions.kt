/**
 * Proto 类的 Kotlin 扩展属性
 *
 * 为 Protobuf 生成的类提供更友好的 Kotlin API：
 * - `xxxOrNull` 属性：将 optional 字段转换为 nullable 类型
 * - 避免每次都需要写 `if (hasXxx()) xxx else null`
 *
 * 使用示例：
 * ```kotlin
 * // 之前
 * val model = if (options.hasModel()) options.model else null
 *
 * // 之后
 * val model = options.modelOrNull
 * ```
 */
package com.asakii.rpc.proto

// ==================== ConnectOptions 扩展 ====================

/** 获取 provider，如果未设置则返回 null */
val ConnectOptions.providerOrNull: Provider?
    get() = if (hasProvider()) provider else null

/** 获取 model，如果未设置则返回 null */
val ConnectOptions.modelOrNull: String?
    get() = if (hasModel()) model else null

/** 获取 systemPrompt，如果未设置则返回 null */
val ConnectOptions.systemPromptOrNull: String?
    get() = if (hasSystemPrompt()) systemPrompt else null

/** 获取 initialPrompt，如果未设置则返回 null */
val ConnectOptions.initialPromptOrNull: String?
    get() = if (hasInitialPrompt()) initialPrompt else null

/** 获取 sessionId，如果未设置则返回 null */
val ConnectOptions.sessionIdOrNull: String?
    get() = if (hasSessionId()) sessionId else null

/** 获取 resumeSessionId，如果未设置则返回 null */
val ConnectOptions.resumeSessionIdOrNull: String?
    get() = if (hasResumeSessionId()) resumeSessionId else null

/** 获取 permissionMode，如果未设置则返回 null */
val ConnectOptions.permissionModeOrNull: PermissionMode?
    get() = if (hasPermissionMode()) permissionMode else null

/** 获取 dangerouslySkipPermissions，如果未设置则返回 null */
val ConnectOptions.dangerouslySkipPermissionsOrNull: Boolean?
    get() = if (hasDangerouslySkipPermissions()) dangerouslySkipPermissions else null

/** 获取 allowDangerouslySkipPermissions，如果未设置则返回 null */
val ConnectOptions.allowDangerouslySkipPermissionsOrNull: Boolean?
    get() = if (hasAllowDangerouslySkipPermissions()) allowDangerouslySkipPermissions else null

/** 获取 includePartialMessages，如果未设置则返回 null */
val ConnectOptions.includePartialMessagesOrNull: Boolean?
    get() = if (hasIncludePartialMessages()) includePartialMessages else null

/** 获取 continueConversation，如果未设置则返回 null */
val ConnectOptions.continueConversationOrNull: Boolean?
    get() = if (hasContinueConversation()) continueConversation else null

/** 获取 thinkingEnabled，如果未设置则返回 null */
val ConnectOptions.thinkingEnabledOrNull: Boolean?
    get() = if (hasThinkingEnabled()) thinkingEnabled else null

/** 获取 baseUrl，如果未设置则返回 null */
val ConnectOptions.baseUrlOrNull: String?
    get() = if (hasBaseUrl()) baseUrl else null

/** 获取 apiKey，如果未设置则返回 null */
val ConnectOptions.apiKeyOrNull: String?
    get() = if (hasApiKey()) apiKey else null

/** 获取 sandboxMode，如果未设置则返回 null */
val ConnectOptions.sandboxModeOrNull: SandboxMode?
    get() = if (hasSandboxMode()) sandboxMode else null

/** 获取 codexReasoningEffort，如果未设置则返回 null */
val ConnectOptions.codexReasoningEffortOrNull: String?
    get() = if (hasCodexReasoningEffort()) codexReasoningEffort else null

/** 获取 codexReasoningSummary，如果未设置则返回 null */
val ConnectOptions.codexReasoningSummaryOrNull: String?
    get() = if (hasCodexReasoningSummary()) codexReasoningSummary else null

/** 获取 replayUserMessages，如果未设置则返回 null */
val ConnectOptions.replayUserMessagesOrNull: Boolean?
    get() = if (hasReplayUserMessages()) replayUserMessages else null

/** 获取 chromeEnabled，如果未设置则返回 null */
val ConnectOptions.chromeEnabledOrNull: Boolean?
    get() = if (hasChromeEnabled()) chromeEnabled else null

/** 获取 connectId，如果未设置则返回 null */
val ConnectOptions.connectIdOrNull: String?
    get() = if (hasConnectId()) connectId else null

// ==================== ConnectResult 扩展 ====================

/** 获取 model，如果未设置则返回 null */
val ConnectResult.modelOrNull: String?
    get() = if (hasModel()) model else null

/** 获取 capabilities，如果未设置则返回 null */
val ConnectResult.capabilitiesOrNull: Capabilities?
    get() = if (hasCapabilities()) capabilities else null

/** 获取 cwd，如果未设置则返回 null */
val ConnectResult.cwdOrNull: String?
    get() = if (hasCwd()) cwd else null

/** 获取 connectId，如果未设置则返回 null */
val ConnectResult.connectIdOrNull: String?
    get() = if (hasConnectId()) connectId else null

// ==================== Provider 枚举扩展 ====================

/** 转换为字符串标识（用于日志和调试） */
val Provider.stringValue: String
    get() = when (this) {
        Provider.PROVIDER_CLAUDE -> "claude"
        Provider.PROVIDER_CODEX -> "codex"
        Provider.PROVIDER_UNSPECIFIED, Provider.UNRECOGNIZED -> "unknown"
    }

/** 从字符串创建 Provider */
fun providerFromString(value: String): Provider = when (value.lowercase()) {
    "claude" -> Provider.PROVIDER_CLAUDE
    "codex" -> Provider.PROVIDER_CODEX
    else -> Provider.PROVIDER_UNSPECIFIED
}

// ==================== PermissionMode 枚举扩展 ====================

/** 转换为字符串标识 */
val PermissionMode.stringValue: String
    get() = when (this) {
        PermissionMode.PERMISSION_MODE_DEFAULT -> "default"
        PermissionMode.PERMISSION_MODE_PLAN -> "plan"
        PermissionMode.PERMISSION_MODE_ACCEPT_EDITS -> "accept-edits"
        PermissionMode.PERMISSION_MODE_BYPASS_PERMISSIONS -> "bypass-permissions"
        PermissionMode.PERMISSION_MODE_UNSPECIFIED, PermissionMode.UNRECOGNIZED -> "default"
    }

/** 从字符串创建 PermissionMode */
fun permissionModeFromString(value: String): PermissionMode = when (value.lowercase()) {
    "default" -> PermissionMode.PERMISSION_MODE_DEFAULT
    "plan" -> PermissionMode.PERMISSION_MODE_PLAN
    "accept-edits" -> PermissionMode.PERMISSION_MODE_ACCEPT_EDITS
    "bypass-permissions" -> PermissionMode.PERMISSION_MODE_BYPASS_PERMISSIONS
    else -> PermissionMode.PERMISSION_MODE_DEFAULT
}

// ==================== SandboxMode 枚举扩展 ====================

/** 转换为字符串标识 */
val SandboxMode.stringValue: String
    get() = when (this) {
        SandboxMode.SANDBOX_MODE_READ_ONLY -> "read-only"
        SandboxMode.SANDBOX_MODE_WORKSPACE_WRITE -> "workspace-write"
        SandboxMode.SANDBOX_MODE_DANGER_FULL_ACCESS -> "danger-full-access"
        SandboxMode.SANDBOX_MODE_UNSPECIFIED, SandboxMode.UNRECOGNIZED -> "read-only"
    }

/** 从字符串创建 SandboxMode */
fun sandboxModeFromString(value: String): SandboxMode = when (value.lowercase()) {
    "read-only" -> SandboxMode.SANDBOX_MODE_READ_ONLY
    "workspace-write" -> SandboxMode.SANDBOX_MODE_WORKSPACE_WRITE
    "danger-full-access" -> SandboxMode.SANDBOX_MODE_DANGER_FULL_ACCESS
    else -> SandboxMode.SANDBOX_MODE_READ_ONLY
}

// ==================== SessionStatus 枚举扩展 ====================

/** 转换为字符串标识 */
val SessionStatus.stringValue: String
    get() = when (this) {
        SessionStatus.SESSION_STATUS_CONNECTED -> "connected"
        SessionStatus.SESSION_STATUS_DISCONNECTED -> "disconnected"
        SessionStatus.SESSION_STATUS_INTERRUPTED -> "interrupted"
        SessionStatus.SESSION_STATUS_MODEL_CHANGED -> "model_changed"
        SessionStatus.SESSION_STATUS_UNSPECIFIED, SessionStatus.UNRECOGNIZED -> "unknown"
    }
