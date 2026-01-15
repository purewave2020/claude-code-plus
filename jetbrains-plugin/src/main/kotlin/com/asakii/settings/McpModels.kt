package com.asakii.settings

/**
 * MCP 服务器级别
 */
enum class McpServerLevel {
    BUILTIN,  // 内置
    GLOBAL,   // 全局
    PROJECT   // 项目
}

/**
 * MCP 服务器条目
 */
data class McpServerEntry(
    val name: String,
    val enabled: Boolean = true,
    val enabledBackends: Set<String> = setOf(AgentSettingsService.MCP_BACKEND_ALL),
    val level: McpServerLevel = McpServerLevel.GLOBAL,
    val configSummary: String = "",
    val isBuiltIn: Boolean = false,
    val jsonConfig: String = "",
    val instructions: String = "",
    val instructionsClaude: String = "",
    val instructionsCodex: String = "",
    val apiKey: String = "",
    /** 启用此 MCP 时禁用的 Claude Code 内置工具列表 */
    val disabledTools: List<String> = emptyList(),
    /** 启用此 MCP 时禁用的 Codex features（如 "shell_tool"） */
    val codexDisabledFeatures: List<String> = emptyList(),
    /** 默认系统提示词（内置 MCP 使用，只读） */
    val defaultInstructions: String = "",
    /** 是否有关联的禁用工具开关（如 JetBrains Terminal MCP 的 terminalDisableBuiltinBash） */
    val hasDisableToolsToggle: Boolean = false,
    /** JetBrains Terminal MCP: 输出最大行数 */
    val terminalMaxOutputLines: Int = 500,
    /** JetBrains Terminal MCP: 输出最大字符数 */
    val terminalMaxOutputChars: Int = 50000,
    /** JetBrains Terminal MCP: 默认 shell（空字符串表示使用系统默认） */
    val terminalDefaultShell: String = "",
    /** JetBrains Terminal MCP: 可用 shell 列表（逗号分隔） */
    val terminalAvailableShells: String = "",
    /** JetBrains Terminal MCP: TerminalRead 默认超时时间（秒） */
    val terminalReadTimeout: Int = 30,
    /** 工具调用超时时间（秒），最小 1 秒，默认 60 秒 */
    val toolTimeoutSec: Int = 60,
    /** JetBrains File MCP: 是否允许访问外部文件 */
    val fileAllowExternal: Boolean = true,
    /** JetBrains File MCP: 外部路径规则（JSON 序列化） */
    val fileExternalRules: String = "[]",
    /** Git MCP: Commit 消息语言 (en, zh, ja, ko, auto) */
    val gitCommitLanguage: String = "en",
    /** Codex 模式下自动批准的 MCP 工具（无需用户确认） */
    val codexAutoApprovedTools: List<String> = emptyList(),
    /** 默认自动批准的工具列表（内置 MCP 使用，只读） */
    val defaultAutoApprovedTools: List<String> = emptyList(),
    /** 默认禁用的 Claude Code 内置工具列表（内置 MCP 使用，只读） */
    val defaultDisabledTools: List<String> = emptyList(),
    /** 默认禁用的 Codex 功能列表（内置 MCP 使用，只读） */
    val defaultCodexDisabledFeatures: List<String> = emptyList()
)
