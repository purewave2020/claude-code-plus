package com.asakii.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import com.asakii.ai.agent.sdk.CodexFeatures

/**
 * MCP 配置页面 - 列表形式
 *
 * 使用类似 JetBrains 原生 MCP 配置页面的设计：
 * - 列表展示所有 MCP 服务器（内置 + 自定义）
 * - 双击编辑配置
 * - 在对话框中配置服务器级别（Global/Project）
 *
 * @see docs/MCP_CONFIGURABLE_DESIGN.md
 */
class McpConfigurable(private val project: Project? = null) : SearchableConfigurable {

    private var mainPanel: JPanel? = null
    private var table: JBTable? = null
    private var tableModel: McpServerTableModel? = null

    // 内置服务器配置存储
    private val builtInServers = mutableListOf<McpServerEntry>()
    // 自定义服务器配置存储
    private val customServers = mutableListOf<McpServerEntry>()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    override fun getId(): String = "com.asakii.settings.mcp"

    override fun getDisplayName(): String = "MCP"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout())

        // 顶部区域（说明 + 通知）
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(0, 0, 10, 0)
        }

        // 说明
        val descPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            val label = JBLabel("""
                <html>
                Configure MCP (Model Context Protocol) servers.
                <a href="https://modelcontextprotocol.io">Learn more</a>
                </html>
            """.trimIndent())
            add(label, BorderLayout.WEST)
        }
        topPanel.add(descPanel)
        topPanel.add(Box.createVerticalStrut(8))

        // 通知：仅对插件生效
        val noticePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            val noticeLabel = JBLabel("<html><font color='gray'>${ClaudeCodePlusBundle.message("mcp.settings.notice")}</font></html>")
            add(noticeLabel)
        }
        topPanel.add(noticePanel)

        mainPanel!!.add(topPanel, BorderLayout.NORTH)

        // 创建表格
        tableModel = McpServerTableModel()
        table = JBTable(tableModel).apply {
            setShowGrid(false)
            intercellSpacing = Dimension(0, 0)
            rowHeight = 28
            tableHeader.reorderingAllowed = false

            // Status 列渲染器（显示启用状态）
            columnModel.getColumn(0).apply {
                preferredWidth = 50
                maxWidth = 60
                cellRenderer = StatusCellRenderer()
            }

            // Name 列
            columnModel.getColumn(1).apply {
                preferredWidth = 180
            }

            // Configuration 列
            columnModel.getColumn(2).apply {
                preferredWidth = 300
                cellRenderer = ConfigurationCellRenderer()
            }

            // Backends 列
            columnModel.getColumn(3).apply {
                preferredWidth = 120
                maxWidth = 160
                cellRenderer = BackendCellRenderer()
            }

            // Level 列
            columnModel.getColumn(4).apply {
                preferredWidth = 80
                maxWidth = 100
                cellRenderer = LevelCellRenderer()
            }

            // 鼠标点击处理
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val row = rowAtPoint(e.point)
                    val col = columnAtPoint(e.point)
                    if (row < 0) return

                    // 单击 Backends 列（第 3 列）时弹出后端选择
                    if (e.clickCount == 1 && col == 3) {
                        editBackends(row, e)
                        return
                    }

                    // 双击编辑完整配置
                    if (e.clickCount == 2) {
                        editServer(row)
                    }
                }
            })
        }

        // 工具栏装饰器
        val decorator = ToolbarDecorator.createDecorator(table!!)
            .setToolbarPosition(ActionToolbarPosition.TOP)
            .setAddAction { addServer() }
            .setRemoveAction { removeServer() }
            .setEditAction { editServer(table!!.selectedRow) }
            .setEditActionUpdater {
                val row = table?.selectedRow ?: -1
                row >= 0 && !isBuiltInServer(row)
            }
            .setRemoveActionUpdater {
                val row = table?.selectedRow ?: -1
                row >= 0 && !isBuiltInServer(row)
            }

        val tablePanel = decorator.createPanel()
        mainPanel!!.add(tablePanel, BorderLayout.CENTER)

        // 底部提示
        val bottomPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(10)
            val warningLabel = JBLabel(
                "<html><font color='#B07800'>⚠ Proceed with caution and only connect to trusted servers.</font></html>"
            )
            add(warningLabel, BorderLayout.WEST)
        }
        mainPanel!!.add(bottomPanel, BorderLayout.SOUTH)

        // 加载配置
        reset()

        return mainPanel!!
    }

    /**
     * 判断是否为内置服务器
     */
    private fun isBuiltInServer(row: Int): Boolean {
        return row < builtInServers.size
    }

    /**
     * 添加新服务器
     */
    private fun addServer() {
        val dialog = McpServerDialog(project, null)
        if (dialog.showAndGet()) {
            val entry = dialog.getServerEntry()
            customServers.add(entry)
            refreshTable()
        }
    }

    /**
     * 编辑服务器
     */
    private fun editServer(row: Int) {
        if (row < 0) return

        if (row < builtInServers.size) {
            // 编辑内置服务器
            val entry = builtInServers[row]
            val dialog = BuiltInMcpServerDialog(project, entry)
            if (dialog.showAndGet()) {
                builtInServers[row] = dialog.getServerEntry()
                refreshTable()
            }
        } else {
            // 编辑自定义服务器
            val customIndex = row - builtInServers.size
            val entry = customServers[customIndex]
            val dialog = McpServerDialog(project, entry)
            if (dialog.showAndGet()) {
                customServers[customIndex] = dialog.getServerEntry()
                refreshTable()
            }
        }
    }

    /**
     * 快速编辑 Backends（单击 Backends 列时弹出）
     */
    private fun editBackends(row: Int, e: MouseEvent) {
        if (row < 0) return

        val entry = if (row < builtInServers.size) {
            builtInServers[row]
        } else {
            customServers.getOrNull(row - builtInServers.size) ?: return
        }

        // 创建弹出菜单
        val popup = JPopupMenu()
        val currentKeys = entry.enabledBackends

        val allItem = JCheckBoxMenuItem("All").apply {
            isSelected = currentKeys.contains(AgentSettingsService.MCP_BACKEND_ALL)
            addActionListener {
                updateBackends(row, setOf(AgentSettingsService.MCP_BACKEND_ALL))
            }
        }

        val claudeItem = JCheckBoxMenuItem("Claude Code").apply {
            isSelected = !currentKeys.contains(AgentSettingsService.MCP_BACKEND_ALL) &&
                currentKeys.contains(AgentSettingsService.MCP_BACKEND_CLAUDE)
            isEnabled = !allItem.isSelected
            addActionListener {
                val newKeys = mutableSetOf<String>()
                if (isSelected) newKeys.add(AgentSettingsService.MCP_BACKEND_CLAUDE)
                if (popup.components.filterIsInstance<JCheckBoxMenuItem>()
                        .find { it.text == "Codex" }?.isSelected == true) {
                    newKeys.add(AgentSettingsService.MCP_BACKEND_CODEX)
                }
                if (newKeys.isEmpty()) newKeys.add(AgentSettingsService.MCP_BACKEND_ALL)
                updateBackends(row, newKeys)
            }
        }

        val codexItem = JCheckBoxMenuItem("Codex").apply {
            isSelected = !currentKeys.contains(AgentSettingsService.MCP_BACKEND_ALL) &&
                currentKeys.contains(AgentSettingsService.MCP_BACKEND_CODEX)
            isEnabled = !allItem.isSelected
            addActionListener {
                val newKeys = mutableSetOf<String>()
                if (isSelected) newKeys.add(AgentSettingsService.MCP_BACKEND_CODEX)
                if (popup.components.filterIsInstance<JCheckBoxMenuItem>()
                        .find { it.text == "Claude Code" }?.isSelected == true) {
                    newKeys.add(AgentSettingsService.MCP_BACKEND_CLAUDE)
                }
                if (newKeys.isEmpty()) newKeys.add(AgentSettingsService.MCP_BACKEND_ALL)
                updateBackends(row, newKeys)
            }
        }

        // All 选中时禁用其他选项
        allItem.addActionListener {
            if (allItem.isSelected) {
                claudeItem.isSelected = false
                claudeItem.isEnabled = false
                codexItem.isSelected = false
                codexItem.isEnabled = false
            } else {
                claudeItem.isEnabled = true
                codexItem.isEnabled = true
            }
        }

        popup.add(allItem)
        popup.addSeparator()
        popup.add(claudeItem)
        popup.add(codexItem)

        // 在点击位置显示弹出菜单
        popup.show(e.component, e.x, e.y)
    }

    /**
     * 更新服务器的 Backends 配置
     */
    private fun updateBackends(row: Int, newBackends: Set<String>) {
        if (row < builtInServers.size) {
            builtInServers[row] = builtInServers[row].copy(enabledBackends = newBackends)
        } else {
            val customIndex = row - builtInServers.size
            if (customIndex < customServers.size) {
                customServers[customIndex] = customServers[customIndex].copy(enabledBackends = newBackends)
            }
        }
        refreshTable()
    }

    /**
     * 删除服务器
     */
    private fun removeServer() {
        val row = table?.selectedRow ?: return
        if (row < 0 || row < builtInServers.size) return

        val customIndex = row - builtInServers.size
        val entry = customServers[customIndex]

        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to remove '${entry.name}'?",
            "Remove MCP Server",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            customServers.removeAt(customIndex)
            refreshTable()
        }
    }

    /**
     * 刷新表格
     */
    private fun refreshTable() {
        tableModel?.fireTableDataChanged()
    }

    private fun normalizeBackendKeys(keys: Set<String>): Set<String> {
        val normalized = keys.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (normalized.contains(AgentSettingsService.MCP_BACKEND_ALL)) {
            return setOf(AgentSettingsService.MCP_BACKEND_ALL)
        }
        return normalized.intersect(
            setOf(
                AgentSettingsService.MCP_BACKEND_CLAUDE,
                AgentSettingsService.MCP_BACKEND_CODEX
            )
        )
    }

    private fun parseBackendKeys(
        element: kotlinx.serialization.json.JsonElement?,
        fallback: Set<String>
    ): Set<String> {
        if (element == null) return fallback
        return when (element) {
            is kotlinx.serialization.json.JsonArray -> {
                val raw = element.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
                normalizeBackendKeys(raw)
            }
            is kotlinx.serialization.json.JsonPrimitive -> {
                val raw = element.contentOrNull ?: ""
                val parsed = try {
                    json.decodeFromString<List<String>>(raw).toSet()
                } catch (_: Exception) {
                    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
                normalizeBackendKeys(parsed)
            }
            else -> fallback
        }
    }

    private fun formatBackendLabel(keys: Set<String>): String {
        val normalized = normalizeBackendKeys(keys)
        if (normalized.isEmpty()) return "-"
        if (normalized.contains(AgentSettingsService.MCP_BACKEND_ALL)) return "All"
        val labels = mutableListOf<String>()
        if (normalized.contains(AgentSettingsService.MCP_BACKEND_CLAUDE)) {
            labels.add("Claude Code")
        }
        if (normalized.contains(AgentSettingsService.MCP_BACKEND_CODEX)) {
            labels.add("Codex")
        }
        return if (labels.isEmpty()) "-" else labels.joinToString("/")
    }


    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        val mcpSettings = service<McpSettingsService>()
        val fallbackBackends = settings.getMcpEnabledBackendKeys().ifEmpty {
            setOf(AgentSettingsService.MCP_BACKEND_ALL)
        }

        // 检查内置服务器配置
        val userInteractionEntry = builtInServers.find { it.name == McpBundle.message("mcp.userInteraction.name") }
        val jetbrainsEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsIde.name") }
        val jetbrainsFileEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsFile.name") }
        val context7Entry = builtInServers.find { it.name == McpBundle.message("mcp.context7.name") }
        val terminalEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsTerminal.name") }
        val gitEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsGit.name") }

        if (userInteractionEntry?.enabled != settings.enableUserInteractionMcp ||
            jetbrainsEntry?.enabled != settings.enableJetBrainsMcp ||
            jetbrainsFileEntry?.enabled != settings.enableJetBrainsFileMcp ||
            context7Entry?.enabled != settings.enableContext7Mcp ||
            terminalEntry?.enabled != settings.enableTerminalMcp ||
            gitEntry?.enabled != settings.enableGitMcp ||
            userInteractionEntry?.enabledBackends != settings.getUserInteractionMcpBackendKeys() ||
            jetbrainsEntry?.enabledBackends != settings.getJetbrainsMcpBackendKeys() ||
            jetbrainsFileEntry?.enabledBackends != settings.getJetbrainsFileMcpBackendKeys() ||
            context7Entry?.enabledBackends != settings.getContext7McpBackendKeys() ||
            terminalEntry?.enabledBackends != settings.getTerminalMcpBackendKeys() ||
            gitEntry?.enabledBackends != settings.getGitMcpBackendKeys() ||
            context7Entry?.apiKey != settings.context7ApiKey ||
            userInteractionEntry?.instructions != settings.userInteractionInstructions ||
            jetbrainsEntry?.instructions != settings.jetbrainsInstructions ||
            jetbrainsFileEntry?.instructions != settings.jetbrainsFileInstructions ||
            context7Entry?.instructions != settings.context7Instructions ||
            terminalEntry?.instructions != settings.terminalInstructions ||
            gitEntry?.instructions != settings.gitInstructions ||
            gitEntry?.gitCommitLanguage != settings.gitCommitLanguage ||
            terminalEntry?.terminalMaxOutputLines != settings.terminalMaxOutputLines ||
            terminalEntry?.terminalMaxOutputChars != settings.terminalMaxOutputChars ||
            terminalEntry?.terminalReadTimeout != settings.terminalReadTimeout ||
            // Terminal MCP 禁用工具配置（Bash 对应 Claude Code，shell_tool 对应 Codex）
            ((terminalEntry?.disabledTools?.contains("Bash") == true) ||
                (terminalEntry?.codexDisabledFeatures?.contains(CodexFeatures.SHELL_TOOL) == true)) != settings.terminalDisableBuiltinBash ||
            // JetBrains File MCP 禁用工具配置
            (jetbrainsFileEntry?.disabledTools?.isNotEmpty() ?: false) != settings.jetbrainsFileDisableBuiltinTools ||
            jetbrainsFileEntry?.disabledTools != settings.getJetbrainsFileDisabledToolsList() ||
            // JetBrains File MCP 外部文件配置
            jetbrainsFileEntry?.fileAllowExternal != settings.jetbrainsFileAllowExternal ||
            jetbrainsFileEntry?.fileExternalRules != settings.jetbrainsFileExternalRules
        ) {
            return true
        }

        // 检查自定义服务器配置
        val savedGlobalServers = parseCustomServers(
            mcpSettings.getGlobalConfig(),
            McpServerLevel.GLOBAL,
            fallbackBackends
        )
        val savedProjectServers = parseCustomServers(
            mcpSettings.getProjectConfig(project),
            McpServerLevel.PROJECT,
            fallbackBackends
        )
        val savedCustomServers = savedGlobalServers + savedProjectServers

        if (customServers.size != savedCustomServers.size) return true

        // 比较每个自定义服务器
        for (server in customServers) {
            val saved = savedCustomServers.find { it.name == server.name && it.level == server.level }
            if (saved == null ||
                saved.jsonConfig != server.jsonConfig ||
                saved.enabled != server.enabled ||
                saved.instructions != server.instructions ||
                saved.enabledBackends != server.enabledBackends
            ) {
                return true
            }
        }

        return false
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        val mcpSettings = service<McpSettingsService>()

        // 保存内置服务器配置
        val userInteractionEntry = builtInServers.find { it.name == McpBundle.message("mcp.userInteraction.name") }
        val jetbrainsEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsIde.name") }
        val jetbrainsFileEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsFile.name") }
        val context7Entry = builtInServers.find { it.name == McpBundle.message("mcp.context7.name") }
        val terminalEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsTerminal.name") }
        val gitEntry = builtInServers.find { it.name == McpBundle.message("mcp.jetbrainsGit.name") }

        settings.enableUserInteractionMcp = userInteractionEntry?.enabled ?: true
        settings.enableJetBrainsMcp = jetbrainsEntry?.enabled ?: true
        settings.enableJetBrainsFileMcp = jetbrainsFileEntry?.enabled ?: true
        settings.enableContext7Mcp = context7Entry?.enabled ?: false
        settings.enableGitMcp = gitEntry?.enabled ?: false
        settings.gitInstructions = gitEntry?.instructions ?: ""
        settings.gitCommitLanguage = gitEntry?.gitCommitLanguage ?: "en"
        settings.enableTerminalMcp = terminalEntry?.enabled ?: false
        settings.setUserInteractionMcpBackendKeys(
            userInteractionEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setJetbrainsMcpBackendKeys(
            jetbrainsEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setJetbrainsFileMcpBackendKeys(
            jetbrainsFileEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setContext7McpBackendKeys(
            context7Entry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setTerminalMcpBackendKeys(
            terminalEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.setGitMcpBackendKeys(
            gitEntry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
        )
        settings.context7ApiKey = context7Entry?.apiKey ?: ""
        settings.userInteractionInstructions = userInteractionEntry?.instructions ?: ""
        settings.jetbrainsInstructions = jetbrainsEntry?.instructions ?: ""
        settings.jetbrainsFileInstructions = jetbrainsFileEntry?.instructions ?: ""
        settings.context7Instructions = context7Entry?.instructions ?: ""
        settings.terminalInstructions = terminalEntry?.instructions ?: ""
        settings.terminalMaxOutputLines = terminalEntry?.terminalMaxOutputLines ?: 500
        settings.terminalMaxOutputChars = terminalEntry?.terminalMaxOutputChars ?: 50000
        settings.terminalDefaultShell = terminalEntry?.terminalDefaultShell ?: ""
        settings.terminalAvailableShells = terminalEntry?.terminalAvailableShells ?: ""
        settings.terminalReadTimeout = terminalEntry?.terminalReadTimeout ?: 30
        // 保存 Terminal MCP 禁用工具配置（Bash 对应 Claude Code，shell_tool 对应 Codex）
        // 如果任一列表包含相应的禁用项，则启用禁用开关
        settings.terminalDisableBuiltinBash = (terminalEntry?.disabledTools?.contains("Bash") == true) ||
            (terminalEntry?.codexDisabledFeatures?.contains(CodexFeatures.SHELL_TOOL) == true)
        // 保存 JetBrains File MCP 禁用工具配置
        settings.jetbrainsFileDisableBuiltinTools = (jetbrainsFileEntry?.disabledTools?.isNotEmpty() ?: false)
        settings.setJetbrainsFileDisabledToolsList(jetbrainsFileEntry?.disabledTools ?: listOf("Read", "Write", "Edit"))
        // 保存超时配置
        settings.userInteractionMcpTimeout = userInteractionEntry?.toolTimeoutSec ?: 3600
        settings.jetbrainsMcpTimeout = jetbrainsEntry?.toolTimeoutSec ?: 60
        settings.jetbrainsFileMcpTimeout = jetbrainsFileEntry?.toolTimeoutSec ?: 60
        // 保存 JetBrains File MCP 外部文件配置
        settings.jetbrainsFileAllowExternal = jetbrainsFileEntry?.fileAllowExternal ?: true
        settings.jetbrainsFileExternalRules = jetbrainsFileEntry?.fileExternalRules ?: "[]"
        settings.context7McpTimeout = context7Entry?.toolTimeoutSec ?: 60
        settings.terminalMcpTimeout = terminalEntry?.toolTimeoutSec ?: 60
        settings.gitMcpTimeout = gitEntry?.toolTimeoutSec ?: 60

        // 保存 Codex 自动批准工具配置
        settings.setUserInteractionAutoApprovedTools(userInteractionEntry?.codexAutoApprovedTools ?: emptyList())
        settings.setJetbrainsLspAutoApprovedTools(jetbrainsEntry?.codexAutoApprovedTools ?: emptyList())
        settings.setJetbrainsFileAutoApprovedTools(jetbrainsFileEntry?.codexAutoApprovedTools ?: emptyList())
        settings.setTerminalAutoApprovedTools(terminalEntry?.codexAutoApprovedTools ?: emptyList())
        settings.setGitAutoApprovedTools(gitEntry?.codexAutoApprovedTools ?: emptyList())

        // 保存自定义服务器配置
        val globalServers = customServers.filter { it.level == McpServerLevel.GLOBAL }
        val projectServers = customServers.filter { it.level == McpServerLevel.PROJECT }

        mcpSettings.setGlobalConfig(buildMcpServersJson(globalServers))
        mcpSettings.setProjectConfig(project, buildMcpServersJson(projectServers))

        // 通知监听器
        settings.notifyChange()
    }

    /**
     * 构建 MCP 服务器 JSON 配置
     *
     * 存储格式：
     * {
     *   "server-name": {
     *     "config": { "command": "...", "args": [...] },  // 纯净的 MCP 配置
     *     "enabled": true,                                 // 我们的元数据
     *     "instructions": "..."                            // 我们的元数据
     *   }
     * }
     */
    private fun buildMcpServersJson(servers: List<McpServerEntry>): String {
        if (servers.isEmpty()) return ""

        val serversMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for (server in servers) {
            try {
                val parsed = json.parseToJsonElement(server.jsonConfig).jsonObject
                // 直接从顶层读取服务器配置
                for ((serverName, serverConfig) in parsed) {
                    // 构建包含 config + 元数据的完整条目
                val entryMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                entryMap["config"] = serverConfig  // 纯净的 MCP 配置
                entryMap["enabled"] = kotlinx.serialization.json.JsonPrimitive(server.enabled)
                entryMap["enabledBackends"] = kotlinx.serialization.json.JsonArray(
                    normalizeBackendKeys(server.enabledBackends)
                        .map { kotlinx.serialization.json.JsonPrimitive(it) }
                )
                if (server.instructions.isNotBlank()) {
                    entryMap["instructions"] = kotlinx.serialization.json.JsonPrimitive(server.instructions)
                }
                // 添加超时配置（0 表示永不超时）
                entryMap["toolTimeoutSec"] = kotlinx.serialization.json.JsonPrimitive(server.toolTimeoutSec)
                    serversMap[serverName] = JsonObject(entryMap)
                }
            } catch (_: Exception) {
                // 忽略解析错误
            }
        }

        if (serversMap.isEmpty()) return ""

        return json.encodeToString(JsonObject.serializer(), JsonObject(serversMap))
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()
        val mcpSettings = service<McpSettingsService>()

        val userInteractionBackends = settings.getUserInteractionMcpBackendKeys()
        val jetbrainsBackends = settings.getJetbrainsMcpBackendKeys()
        val jetbrainsFileBackends = settings.getJetbrainsFileMcpBackendKeys()
        val context7Backends = settings.getContext7McpBackendKeys()
        val terminalBackends = settings.getTerminalMcpBackendKeys()
        val gitBackends = settings.getGitMcpBackendKeys()

        // 加载内置服务器
        builtInServers.clear()
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.userInteraction.name"),
            enabled = settings.enableUserInteractionMcp,
            enabledBackends = userInteractionBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.userInteraction.description"),
            isBuiltIn = true,
            instructions = settings.userInteractionInstructions,
            defaultInstructions = McpDefaults.USER_INTERACTION_INSTRUCTIONS,
            toolTimeoutSec = settings.userInteractionMcpTimeout,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.USER_INTERACTION,
            codexAutoApprovedTools = settings.getUserInteractionAutoApprovedTools()
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.jetbrainsIde.name"),
            enabled = settings.enableJetBrainsMcp,
            enabledBackends = jetbrainsBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.jetbrainsIde.description"),
            isBuiltIn = true,
            instructions = settings.jetbrainsInstructions,
            defaultInstructions = McpDefaults.JETBRAINS_INSTRUCTIONS,
            disabledTools = listOf("Glob", "Grep"),
            toolTimeoutSec = settings.jetbrainsMcpTimeout,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.JETBRAINS_LSP,
            codexAutoApprovedTools = settings.getJetbrainsLspAutoApprovedTools()
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.jetbrainsFile.name"),
            enabled = settings.enableJetBrainsFileMcp,
            enabledBackends = jetbrainsFileBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.jetbrainsFile.description"),
            isBuiltIn = true,
            instructions = settings.jetbrainsFileInstructions,
            defaultInstructions = McpDefaults.JETBRAINS_FILE_INSTRUCTIONS,
            disabledTools = if (settings.jetbrainsFileDisableBuiltinTools) settings.getJetbrainsFileDisabledToolsList() else emptyList(),
            codexDisabledFeatures = if (settings.jetbrainsFileDisableBuiltinTools) listOf(CodexFeatures.APPLY_PATCH_FREEFORM) else emptyList(),
            hasDisableToolsToggle = true,
            toolTimeoutSec = settings.jetbrainsFileMcpTimeout,
            fileAllowExternal = settings.jetbrainsFileAllowExternal,
            fileExternalRules = settings.jetbrainsFileExternalRules,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.JETBRAINS_FILE,
            codexAutoApprovedTools = settings.getJetbrainsFileAutoApprovedTools()
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.context7.name"),
            enabled = settings.enableContext7Mcp,
            enabledBackends = context7Backends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.context7.description"),
            isBuiltIn = true,
            instructions = settings.context7Instructions,
            apiKey = settings.context7ApiKey,
            defaultInstructions = McpDefaults.CONTEXT7_INSTRUCTIONS,
            toolTimeoutSec = settings.context7McpTimeout
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.jetbrainsTerminal.name"),
            enabled = settings.enableTerminalMcp,
            enabledBackends = terminalBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.jetbrainsTerminal.description"),
            isBuiltIn = true,
            instructions = settings.terminalInstructions,
            defaultInstructions = McpDefaults.TERMINAL_INSTRUCTIONS,
            disabledTools = if (settings.terminalDisableBuiltinBash) listOf("Bash") else emptyList(),
            codexDisabledFeatures = if (settings.terminalDisableBuiltinBash) listOf(CodexFeatures.SHELL_TOOL) else emptyList(),
            hasDisableToolsToggle = true,
            terminalMaxOutputLines = settings.terminalMaxOutputLines,
            terminalMaxOutputChars = settings.terminalMaxOutputChars,
            terminalDefaultShell = settings.terminalDefaultShell,
            terminalAvailableShells = settings.terminalAvailableShells,
            terminalReadTimeout = settings.terminalReadTimeout,
            toolTimeoutSec = settings.terminalMcpTimeout,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.JETBRAINS_TERMINAL,
            codexAutoApprovedTools = settings.getTerminalAutoApprovedTools()
        ))
        builtInServers.add(McpServerEntry(
            name = McpBundle.message("mcp.jetbrainsGit.name"),
            enabled = settings.enableGitMcp,
            enabledBackends = gitBackends,
            level = McpServerLevel.BUILTIN,
            configSummary = McpBundle.message("mcp.jetbrainsGit.description"),
            isBuiltIn = true,
            instructions = settings.gitInstructions,
            defaultInstructions = McpDefaults.GIT_INSTRUCTIONS,
            toolTimeoutSec = settings.gitMcpTimeout,
            gitCommitLanguage = settings.gitCommitLanguage,
            defaultAutoApprovedTools = McpAutoApprovedDefaults.JETBRAINS_GIT,
            codexAutoApprovedTools = settings.getGitAutoApprovedTools()
        ))

        // 加载自定义服务器
        val fallbackBackends = settings.getMcpEnabledBackendKeys().ifEmpty {
            setOf(AgentSettingsService.MCP_BACKEND_ALL)
        }
        customServers.clear()
        customServers.addAll(parseCustomServers(mcpSettings.getGlobalConfig(), McpServerLevel.GLOBAL, fallbackBackends))
        customServers.addAll(parseCustomServers(mcpSettings.getProjectConfig(project), McpServerLevel.PROJECT, fallbackBackends))

        refreshTable()
    }

    /**
     * 解析自定义服务器配置
     *
     * 存储格式：
     * {
     *   "server-name": {
     *     "config": { "command": "...", "args": [...] },  // 纯净的 MCP 配置
     *     "enabled": true,                                 // 我们的元数据
     *     "instructions": "..."                            // 我们的元数据
     *   }
     * }
     */
    private fun parseCustomServers(
        jsonStr: String,
        level: McpServerLevel,
        fallbackBackends: Set<String>
    ): List<McpServerEntry> {
        if (jsonStr.isBlank()) return emptyList()

        return try {
            val parsed = json.parseToJsonElement(jsonStr).jsonObject

            // 直接从顶层读取（每个 key 是服务器名称）
            parsed.entries.map { (name, entry) ->
                val entryObj = entry.jsonObject

                // 读取纯净的 MCP 配置
                val mcpConfig = entryObj["config"]?.jsonObject ?: entryObj  // 兼容旧格式

                val command = mcpConfig["command"]?.toString()?.trim('"') ?: ""
                val url = mcpConfig["url"]?.toString()?.trim('"') ?: ""
                val serverType = mcpConfig["type"]?.toString()?.trim('"')

                // 读取我们的元数据
                val enabled = entryObj["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true
                val instructions = entryObj["instructions"]?.toString()?.trim('"') ?: ""
                val enabledBackends = parseBackendKeys(entryObj["enabledBackends"], fallbackBackends)
                val toolTimeoutSec = entryObj["toolTimeoutSec"]?.jsonPrimitive?.intOrNull ?: 60

                // 生成配置摘要
                val summary = if (serverType == "http" || url.isNotBlank()) {
                    "http: $url"
                } else {
                    "command: $command"
                }

                // jsonConfig 只保存纯净的 MCP 配置（用于编辑对话框显示）
                val pureConfig = json.encodeToString(JsonObject.serializer(), mcpConfig)

                McpServerEntry(
                    name = name,
                    enabled = enabled,
                    enabledBackends = enabledBackends,
                    level = level,
                    configSummary = summary,
                    isBuiltIn = false,
                    jsonConfig = """{"$name": $pureConfig}""",
                    instructions = instructions,
                    toolTimeoutSec = toolTimeoutSec
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun disposeUIResources() {
        table = null
        tableModel = null
        mainPanel = null
        builtInServers.clear()
        customServers.clear()
    }

    /**
     * MCP 服务器表格模型
     */
    inner class McpServerTableModel : AbstractTableModel() {
        private val columns = arrayOf("Status", "Name", "Configuration", "Backends", "Level")

        override fun getRowCount(): Int = builtInServers.size + customServers.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val entry = if (rowIndex < builtInServers.size) {
                builtInServers[rowIndex]
            } else {
                customServers[rowIndex - builtInServers.size]
            }

            return when (columnIndex) {
                0 -> entry.enabled
                1 -> entry.name
                2 -> entry.configSummary
                3 -> formatBackendLabel(entry.enabledBackends)
                4 -> entry.level
                else -> ""
            }
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                0 -> java.lang.Boolean::class.java
                4 -> McpServerLevel::class.java
                else -> String::class.java
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            // 只允许编辑 Status 列
            return columnIndex == 0
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == 0 && aValue is Boolean) {
                if (rowIndex < builtInServers.size) {
                    builtInServers[rowIndex] = builtInServers[rowIndex].copy(enabled = aValue)
                } else {
                    val customIndex = rowIndex - builtInServers.size
                    customServers[customIndex] = customServers[customIndex].copy(enabled = aValue)
                }
                fireTableCellUpdated(rowIndex, columnIndex)
            }
        }
    }

    /**
     * 状态列渲染器
     */
    inner class StatusCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val panel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
            panel.isOpaque = true
            panel.background = if (isSelected) table?.selectionBackground else table?.background

            val checkbox = JCheckBox().apply {
                this.isSelected = value as? Boolean ?: false
                this.isOpaque = false
                addActionListener {
                    tableModel?.setValueAt(this.isSelected, row, column)
                }
            }
            panel.add(checkbox)

            return panel
        }
    }

    /**
     * 配置列渲染器
     */
    inner class ConfigurationCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (component is JLabel) {
                component.foreground = if (isSelected) table?.selectionForeground else JBColor.GRAY
            }
            return component
        }
    }

    /**
     * Backends 列渲染器
     */
    inner class BackendCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (component is JLabel) {
                component.foreground = if (isSelected) table?.selectionForeground else JBColor.GRAY
            }
            return component
        }
    }

    /**
     * Level 列渲染器
     */
    inner class LevelCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val level = value as? McpServerLevel ?: McpServerLevel.GLOBAL
            val text = when (level) {
                McpServerLevel.BUILTIN -> "Built-in"
                McpServerLevel.GLOBAL -> "Global"
                McpServerLevel.PROJECT -> "Project"
            }
            return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
        }
    }
}

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
    val defaultAutoApprovedTools: List<String> = emptyList()
)

private class McpBackendSelection(initialKeys: Set<String>) {
    private val backendAllCheckbox = JBCheckBox("All")
    private val backendClaudeCheckbox = JBCheckBox("Claude Code")
    private val backendCodexCheckbox = JBCheckBox("Codex")
    private var updating = false

    val panel: JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        alignmentX = JPanel.LEFT_ALIGNMENT
        add(JBLabel("Enabled in:"))
        add(Box.createHorizontalStrut(8))
        add(backendAllCheckbox)
        add(Box.createHorizontalStrut(8))
        add(backendClaudeCheckbox)
        add(Box.createHorizontalStrut(8))
        add(backendCodexCheckbox)
    }

    val hint: JBLabel = JBLabel(
        "<html><font color='gray' size='-1'>Select which backends can use this MCP server. Choosing All clears other selections.</font></html>"
    ).apply {
        alignmentX = JPanel.LEFT_ALIGNMENT
    }

    init {
        applySelection(initialKeys)
        backendAllCheckbox.addActionListener { handleSelection(backendAllCheckbox) }
        backendClaudeCheckbox.addActionListener { handleSelection(backendClaudeCheckbox) }
        backendCodexCheckbox.addActionListener { handleSelection(backendCodexCheckbox) }
    }

    fun getSelectedKeys(): Set<String> {
        return if (backendAllCheckbox.isSelected) {
            setOf(AgentSettingsService.MCP_BACKEND_ALL)
        } else {
            val selected = mutableSetOf<String>()
            if (backendClaudeCheckbox.isSelected) selected.add(AgentSettingsService.MCP_BACKEND_CLAUDE)
            if (backendCodexCheckbox.isSelected) selected.add(AgentSettingsService.MCP_BACKEND_CODEX)
            selected
        }
    }

    private fun handleSelection(source: JCheckBox) {
        if (updating) return
        updating = true
        try {
            if (source == backendAllCheckbox) {
                if (backendAllCheckbox.isSelected) {
                    backendClaudeCheckbox.isSelected = false
                    backendCodexCheckbox.isSelected = false
                }
            } else if (source.isSelected) {
                backendAllCheckbox.isSelected = false
            }
        } finally {
            updating = false
        }
    }

    private fun applySelection(keys: Set<String>) {
        updating = true
        try {
            val normalized = keys.map { it.trim().lowercase() }.toSet()
            val isAll = normalized.contains(AgentSettingsService.MCP_BACKEND_ALL)
            backendAllCheckbox.isSelected = isAll
            backendClaudeCheckbox.isSelected = !isAll && normalized.contains(AgentSettingsService.MCP_BACKEND_CLAUDE)
            backendCodexCheckbox.isSelected = !isAll && normalized.contains(AgentSettingsService.MCP_BACKEND_CODEX)
        } finally {
            updating = false
        }
    }
}

private fun setEnabledRecursively(component: Component, enabled: Boolean) {
    component.isEnabled = enabled
    if (component is Container) {
        component.components.forEach { child ->
            setEnabledRecursively(child, enabled)
        }
    }
}

/**
 * 内置 MCP 服务器编辑对话框
 *
 * 显示：
 * - 启用开关
 * - 默认系统提示词（只读，可折叠）
 * - 禁用工具信息
 * - 自定义追加提示词
 */
class BuiltInMcpServerDialog(
    private val project: Project?,
    private val entry: McpServerEntry
) : DialogWrapper(project) {

    private val enableCheckbox = JBCheckBox("Enable", entry.enabled)
    private val backendSelection = McpBackendSelection(entry.enabledBackends)
    private val instructionsArea = JBTextArea(
        entry.instructions.ifBlank { entry.defaultInstructions },
        10, 50
    ).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val apiKeyField = JBTextField(entry.apiKey, 25)

    // 禁用工具配置（标签选择）
    private val defaultDisabledTools = entry.disabledTools.toList()
    private val disabledToolsList = entry.disabledTools.toMutableList()
    private var disabledToolsPanel: JPanel? = null
    private val disabledToolInput = JBTextField(20)

    // Codex 禁用 features 配置（标签选择）
    private val defaultCodexDisabledFeatures = entry.codexDisabledFeatures.toList()
    private val codexDisabledFeaturesList = entry.codexDisabledFeatures.toMutableList()
    private var codexDisabledFeaturesPanel: JPanel? = null
    private val codexDisabledFeaturesInput = JBTextField(20)

    // Codex 自动批准工具配置（标签选择）
    private val defaultAutoApprovedTools = entry.defaultAutoApprovedTools.toList()
    private val autoApprovedToolsList = entry.codexAutoApprovedTools.ifEmpty { entry.defaultAutoApprovedTools }.toMutableList()
    private var autoApprovedToolsPanel: JPanel? = null
    private val autoApprovedToolsInput = JBTextField(20)

    // JetBrains Terminal MCP 截断配置
    private val maxOutputLinesField = JBTextField(entry.terminalMaxOutputLines.toString(), 8)
    private val maxOutputCharsField = JBTextField(entry.terminalMaxOutputChars.toString(), 8)
    // JetBrains Terminal MCP 超时配置
    private val readTimeoutField = JBTextField(entry.terminalReadTimeout.toString(), 6)

    // 通用工具调用超时配置（0 表示永不超时）
    private val toolTimeoutField = JBTextField(entry.toolTimeoutSec.toString(), 6)

    // JetBrains File MCP 外部文件配置
    private val fileAllowExternalCheckbox = JBCheckBox("Allow external files", entry.fileAllowExternal)
    // 外部路径规则列表
    private val externalRulesListModel = DefaultListModel<ExternalPathRule>().apply {
        try {
            val rules = Json.decodeFromString<List<ExternalPathRule>>(entry.fileExternalRules)
            rules.forEach { addElement(it) }
        } catch (_: Exception) { }
    }
    private val externalRulesList = JBList(externalRulesListModel).apply {
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is ExternalPathRule) {
                    val icon = if (value.type == ExternalPathRuleType.INCLUDE) "✓" else "✗"
                    val typeLabel = if (value.type == ExternalPathRuleType.INCLUDE) "Include" else "Exclude"
                    text = "$icon [$typeLabel] ${value.path}"
                }
                return this
            }
        }
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    // JetBrains Terminal MCP Shell 配置
    // 动态检测已安装的 shell
    private val allShellTypes = AgentSettingsService.getInstance().detectInstalledShells()
    private val defaultShellCombo = ComboBox<String>()
    private val availableShellCheckboxes = mutableMapOf<String, JBCheckBox>()

    // Git MCP Commit 语言配置
    private val commitLanguageOptions = listOf(
        "en" to "English",
        "zh_CN" to "简体中文 (Simplified Chinese)",
        "zh_TW" to "繁體中文 (Traditional Chinese)",
        "ja" to "日本語 (Japanese)",
        "ko" to "한국어 (Korean)"
    )
    private val commitLanguageCombo = ComboBox(commitLanguageOptions.map { it.second }.toTypedArray()).apply {
        // 根据 entry 的值设置选中项
        val currentLang = entry.gitCommitLanguage
        val index = commitLanguageOptions.indexOfFirst { it.first == currentLang }
        selectedIndex = if (index >= 0) index else 0
    }

    /**
     * 更新 Default Shell 下拉框的选项
     * 只显示在 Available Shells 中勾选的 shell
     */
    private fun updateDefaultShellCombo() {
        val currentSelection = defaultShellCombo.selectedItem as? String
        val enabledShells = availableShellCheckboxes
            .filter { it.value.isSelected }
            .keys
            .toList()

        defaultShellCombo.removeAllItems()
        for (shell in enabledShells) {
            defaultShellCombo.addItem(shell)
        }

        // 恢复之前的选择，如果仍然可用
        if (currentSelection != null && enabledShells.contains(currentSelection)) {
            defaultShellCombo.selectedItem = currentSelection
        } else if (enabledShells.isNotEmpty()) {
            defaultShellCombo.selectedIndex = 0
        }
    }

    /**
     * 创建目录选择按钮
     */
    private fun createBrowseButton(targetField: JBTextField): JButton {
        return JButton("...").apply {
            preferredSize = Dimension(30, targetField.preferredSize.height)
            toolTipText = "Browse for directory"
            addActionListener {
                val fileChooser = JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Select Directory"
                    // 如果当前有路径，从该路径开始
                    val currentPath = targetField.text.trim()
                    if (currentPath.isNotEmpty()) {
                        val currentDir = java.io.File(currentPath)
                        if (currentDir.exists()) {
                            currentDirectory = currentDir
                        }
                    }
                }
                if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    targetField.text = fileChooser.selectedFile.absolutePath
                }
            }
        }
    }

    init {
        title = "Edit ${entry.name}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)

        // 顶部固定内容面板
        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // 启用复选框 + Backends 选择器（合并到一行）
        val enableAndBackendsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(enableCheckbox)
            add(Box.createHorizontalStrut(16))
            add(JBLabel("Backends:"))
            backendSelection.panel.components.forEach { add(it) }
        }
        topPanel.add(enableAndBackendsPanel)
        topPanel.add(Box.createVerticalStrut(2))
        topPanel.add(backendSelection.hint.apply { alignmentX = JPanel.LEFT_ALIGNMENT })
        topPanel.add(Box.createVerticalStrut(6))

        // 根据启用状态更新 Backends 选择器
        fun updateBackendSelectionState(enabled: Boolean) {
            backendSelection.panel.components.forEach { it.isEnabled = enabled }
            backendSelection.hint.isEnabled = enabled
        }
        updateBackendSelectionState(enableCheckbox.isSelected)
        enableCheckbox.addActionListener { updateBackendSelectionState(enableCheckbox.isSelected) }

        // Context7 的 API Key
        if (entry.name == "Context7 MCP") {
            val apiKeyPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("API Key (optional):"))
                add(apiKeyField)
            }
            topPanel.add(apiKeyPanel)
            topPanel.add(Box.createVerticalStrut(8))
        }

        // 禁用工具配置（标签选择界面）- Claude Code built-in tools（紧凑布局）
        if (defaultDisabledTools.isNotEmpty() || entry.hasDisableToolsToggle) {
            // 标题行：标签 + 输入框 + 按钮 + Reset
            val disabledToolsHeaderPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Disables Claude Code tools:").apply {
                    foreground = JBColor(0x1976D2, 0x6BA3D6)
                })
                add(disabledToolInput.apply {
                    toolTipText = "Type tool name, then click + or press Enter"
                })
                add(JButton("+").apply {
                    preferredSize = Dimension(36, disabledToolInput.preferredSize.height)
                    toolTipText = "Add tool to disable"
                    addActionListener {
                        val toolName = disabledToolInput.text.trim()
                        if (toolName.isNotEmpty() && !disabledToolsList.contains(toolName)) {
                            addDisabledToolTag(toolName)
                            disabledToolInput.text = ""
                        }
                    }
                })
                add(JButton("Reset").apply {
                    toolTipText = "Reset to default: ${defaultDisabledTools.joinToString(", ").ifEmpty { "(none)" }}"
                    addActionListener {
                        disabledToolsList.clear()
                        disabledToolsPanel?.removeAll()
                        defaultDisabledTools.forEach { addDisabledToolTag(it) }
                        disabledToolsPanel?.revalidate()
                        disabledToolsPanel?.repaint()
                    }
                })
            }
            topPanel.add(disabledToolsHeaderPanel)
            topPanel.add(Box.createVerticalStrut(2))

            // 标签流式布局面板（减小高度）
            disabledToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 2)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            disabledToolsList.forEach { addDisabledToolTag(it) }

            val toolsScrollPane = JBScrollPane(disabledToolsPanel).apply {
                preferredSize = Dimension(550, 64)  // 增加高度以显示多行标签
                alignmentX = JPanel.LEFT_ALIGNMENT
                border = BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
                horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }
            topPanel.add(toolsScrollPane)
            topPanel.add(Box.createVerticalStrut(6))

            // Enter 键支持
            disabledToolInput.addActionListener {
                val toolName = disabledToolInput.text.trim()
                if (toolName.isNotEmpty() && !disabledToolsList.contains(toolName)) {
                    addDisabledToolTag(toolName)
                    disabledToolInput.text = ""
                }
            }
        }

        // Codex disabled features 配置（紧凑布局）
        if (defaultCodexDisabledFeatures.isNotEmpty() || entry.hasDisableToolsToggle) {
            // 标题行：标签 + 输入框 + 按钮 + Reset
            val codexHeaderPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Disables Codex features:").apply {
                    foreground = JBColor(0x388E3C, 0x81C784)
                })
                add(codexDisabledFeaturesInput.apply {
                    toolTipText = "Type feature name, then click + or press Enter"
                })
                add(JButton("+").apply {
                    preferredSize = Dimension(36, codexDisabledFeaturesInput.preferredSize.height)
                    toolTipText = "Add Codex feature to disable"
                    addActionListener {
                        val featureName = codexDisabledFeaturesInput.text.trim()
                        if (featureName.isNotEmpty() && !codexDisabledFeaturesList.contains(featureName)) {
                            addCodexDisabledFeatureTag(featureName)
                            codexDisabledFeaturesInput.text = ""
                        }
                    }
                })
                add(JButton("Reset").apply {
                    toolTipText = "Reset to default: ${defaultCodexDisabledFeatures.joinToString(", ").ifEmpty { "(none)" }}"
                    addActionListener {
                        codexDisabledFeaturesList.clear()
                        codexDisabledFeaturesPanel?.removeAll()
                        defaultCodexDisabledFeatures.forEach { addCodexDisabledFeatureTag(it) }
                        codexDisabledFeaturesPanel?.revalidate()
                        codexDisabledFeaturesPanel?.repaint()
                    }
                })
            }
            topPanel.add(codexHeaderPanel)
            topPanel.add(Box.createVerticalStrut(2))

            // 标签流式布局面板（减小高度）
            codexDisabledFeaturesPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 2)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            codexDisabledFeaturesList.forEach { addCodexDisabledFeatureTag(it) }

            val codexScrollPane = JBScrollPane(codexDisabledFeaturesPanel).apply {
                preferredSize = Dimension(550, 64)  // 增加高度以显示多行标签
                alignmentX = JPanel.LEFT_ALIGNMENT
                border = BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
                horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }
            topPanel.add(codexScrollPane)
            topPanel.add(Box.createVerticalStrut(6))

            // Enter 键支持
            codexDisabledFeaturesInput.addActionListener {
                val featureName = codexDisabledFeaturesInput.text.trim()
                if (featureName.isNotEmpty() && !codexDisabledFeaturesList.contains(featureName)) {
                    addCodexDisabledFeatureTag(featureName)
                    codexDisabledFeaturesInput.text = ""
                }
            }
        }

        // Codex 自动批准工具配置（紧凑布局）
        if (defaultAutoApprovedTools.isNotEmpty()) {
            // 标题行：标签 + 输入框 + 按钮 + Reset
            val autoApprovedHeaderPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Auto-approved Tools:").apply {
                    foreground = JBColor(0x7B1FA2, 0xBA68C8)  // 紫色，区分于其他配置
                    toolTipText = "Tools that will be auto-approved for both Claude Code and Codex modes"
                })
                add(autoApprovedToolsInput.apply {
                    toolTipText = "Type tool name, then click + or press Enter"
                })
                add(JButton("+").apply {
                    preferredSize = Dimension(36, autoApprovedToolsInput.preferredSize.height)
                    toolTipText = "Add tool to auto-approve list"
                    addActionListener {
                        val toolName = autoApprovedToolsInput.text.trim()
                        if (toolName.isNotEmpty() && !autoApprovedToolsList.contains(toolName)) {
                            addAutoApprovedToolTag(toolName)
                            autoApprovedToolsInput.text = ""
                        }
                    }
                })
                add(JButton("Reset").apply {
                    toolTipText = "Reset to default: ${defaultAutoApprovedTools.joinToString(", ").ifEmpty { "(none)" }}"
                    addActionListener {
                        autoApprovedToolsList.clear()
                        autoApprovedToolsPanel?.removeAll()
                        defaultAutoApprovedTools.forEach { addAutoApprovedToolTag(it) }
                        autoApprovedToolsPanel?.revalidate()
                        autoApprovedToolsPanel?.repaint()
                    }
                })
            }
            topPanel.add(autoApprovedHeaderPanel)
            topPanel.add(Box.createVerticalStrut(2))

            // 标签流式布局面板
            autoApprovedToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 2)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            autoApprovedToolsList.forEach { addAutoApprovedToolTag(it) }

            val autoApprovedScrollPane = JBScrollPane(autoApprovedToolsPanel).apply {
                preferredSize = Dimension(550, 64)  // 增加高度以显示多行标签
                alignmentX = JPanel.LEFT_ALIGNMENT
                border = BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
                horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }
            topPanel.add(autoApprovedScrollPane)
            topPanel.add(Box.createVerticalStrut(2))

            // 提示信息
            topPanel.add(JBLabel("<html><font color='gray' size='-1'>ℹ️ Tools in this list are auto-approved without user confirmation (applies to both Claude Code and Codex)</font></html>").apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            })
            topPanel.add(Box.createVerticalStrut(6))

            // Enter 键支持
            autoApprovedToolsInput.addActionListener {
                val toolName = autoApprovedToolsInput.text.trim()
                if (toolName.isNotEmpty() && !autoApprovedToolsList.contains(toolName)) {
                    addAutoApprovedToolTag(toolName)
                    autoApprovedToolsInput.text = ""
                }
            }
        }

        // JetBrains File MCP 的外部文件配置
        if (entry.name == "JetBrains File MCP") {
            val externalFilesLabel = JBLabel("External Files Access:").apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            topPanel.add(externalFilesLabel)
            topPanel.add(Box.createVerticalStrut(4))

            // 允许外部文件复选框
            val allowExternalPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(fileAllowExternalCheckbox)
            }
            topPanel.add(allowExternalPanel)
            topPanel.add(Box.createVerticalStrut(4))

            // 外部路径规则面板
            val externalRulesPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                alignmentX = JPanel.LEFT_ALIGNMENT
            }

            val rulesLabel = JBLabel("Path rules (Exclude > Include priority):").apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                foreground = JBColor(0x666666, 0x999999)
                font = font.deriveFont(11f)
            }
            externalRulesPanel.add(rulesLabel)
            externalRulesPanel.add(Box.createVerticalStrut(4))

            // 规则列表
            val listScrollPane = JBScrollPane(externalRulesList).apply {
                preferredSize = Dimension(400, 100)
                minimumSize = Dimension(300, 80)
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            externalRulesPanel.add(listScrollPane)
            externalRulesPanel.add(Box.createVerticalStrut(4))

            // 按钮面板
            val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT

                // + Include 按钮
                add(JButton("+ Include").apply {
                    toolTipText = "Add a directory to the include list"
                    addActionListener {
                        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        descriptor.title = "Select Directory to Include"
                        val chosen = FileChooser.chooseFile(descriptor, null, null)
                        if (chosen != null) {
                            val rule = ExternalPathRule(chosen.path, ExternalPathRuleType.INCLUDE)
                            // 检查是否已存在相同规则
                            val exists = (0 until externalRulesListModel.size()).any {
                                val existing = externalRulesListModel.getElementAt(it)
                                existing.path == rule.path && existing.type == rule.type
                            }
                            if (!exists) {
                                externalRulesListModel.addElement(rule)
                            }
                        }
                    }
                })

                // + Exclude 按钮
                add(JButton("+ Exclude").apply {
                    toolTipText = "Add a directory to the exclude list"
                    addActionListener {
                        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        descriptor.title = "Select Directory to Exclude"
                        val chosen = FileChooser.chooseFile(descriptor, null, null)
                        if (chosen != null) {
                            val rule = ExternalPathRule(chosen.path, ExternalPathRuleType.EXCLUDE)
                            // 检查是否已存在相同规则
                            val exists = (0 until externalRulesListModel.size()).any {
                                val existing = externalRulesListModel.getElementAt(it)
                                existing.path == rule.path && existing.type == rule.type
                            }
                            if (!exists) {
                                externalRulesListModel.addElement(rule)
                            }
                        }
                    }
                })

                // Remove 按钮
                add(JButton("Remove").apply {
                    toolTipText = "Remove selected rule"
                    addActionListener {
                        val selectedIndex = externalRulesList.selectedIndex
                        if (selectedIndex >= 0) {
                            externalRulesListModel.remove(selectedIndex)
                        }
                    }
                })
            }
            externalRulesPanel.add(buttonsPanel)

            topPanel.add(externalRulesPanel)
            topPanel.add(Box.createVerticalStrut(4))

            val externalHintLabel = JBLabel(
                "<html><font color='#666666' size='2'>Empty rules = allow all external paths. " +
                "Exclude rules take priority over Include rules.</font></html>"
            ).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            topPanel.add(externalHintLabel)
            topPanel.add(Box.createVerticalStrut(8))

            // 根据复选框状态启用/禁用规则面板
            fun updateExternalRulesState() {
                val enabled = fileAllowExternalCheckbox.isSelected
                setEnabledRecursively(externalRulesPanel, enabled)
            }
            updateExternalRulesState()
            fileAllowExternalCheckbox.addActionListener { updateExternalRulesState() }
        }

        // JetBrains Terminal MCP 的 Shell 配置（紧凑布局：合并到一行）
        if (entry.name == "JetBrains Terminal MCP") {
            // 解析已配置的可用 shells
            val configuredShells = entry.terminalAvailableShells.trim()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val useAllShells = configuredShells.isEmpty()

            // Shell 配置合并到一行：Default Shell + Available Shells
            val shellConfigPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Default Shell:"))
                add(defaultShellCombo)
                add(Box.createHorizontalStrut(12))
                add(JBLabel("Shells:").apply {
                    foreground = JBColor(0x666666, 0x999999)
                })
                for (shellType in allShellTypes) {
                    val isChecked = useAllShells || configuredShells.contains(shellType)
                    val checkbox = JBCheckBox(shellType, isChecked).apply {
                        addActionListener { updateDefaultShellCombo() }
                    }
                    availableShellCheckboxes[shellType] = checkbox
                    add(checkbox)
                }
            }
            topPanel.add(shellConfigPanel)

            // 初始化 Default Shell 下拉框
            updateDefaultShellCombo()
            val savedDefaultShell = entry.terminalDefaultShell
            val effectiveDefaultShell = if (savedDefaultShell.isNotBlank()) {
                savedDefaultShell
            } else {
                AgentSettingsService.getInstance().getEffectiveDefaultShell()
            }
            if ((defaultShellCombo.model as? DefaultComboBoxModel<*>)?.getIndexOf(effectiveDefaultShell) != -1) {
                defaultShellCombo.selectedItem = effectiveDefaultShell
            }
            topPanel.add(Box.createVerticalStrut(6))
        }

        // JetBrains Terminal MCP 的截断配置 + 超时配置（紧凑布局）
        if (entry.name == "JetBrains Terminal MCP") {
            // 第一行：Max lines + Max chars + Read timeout
            val truncateAndTimeoutPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Max lines:"))
                add(maxOutputLinesField)
                add(Box.createHorizontalStrut(8))
                add(JBLabel("Max chars:"))
                add(maxOutputCharsField)
                add(Box.createHorizontalStrut(8))
                add(JBLabel("Read timeout:"))
                add(readTimeoutField)
                add(JBLabel("sec"))
            }
            topPanel.add(truncateAndTimeoutPanel)
            topPanel.add(Box.createVerticalStrut(4))
        }

        // Git MCP Commit 语言配置
        if (entry.name == McpBundle.message("mcp.jetbrainsGit.name")) {
            val commitLangPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Commit Message Language:"))
                add(commitLanguageCombo)
                add(JBLabel("<html><font color='gray' size='-1'>(AI will generate commit messages in this language)</font></html>"))
            }
            topPanel.add(commitLangPanel)
            topPanel.add(Box.createVerticalStrut(4))
        }

        // 第二行：Tool Call Timeout
        val toolTimeoutPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Tool Call Timeout:"))
            add(toolTimeoutField)
            add(JBLabel("seconds"))
            add(JBLabel("<html><font color='gray' size='-1'>(min 1s)</font></html>"))
        }
        topPanel.add(toolTimeoutPanel)
        topPanel.add(Box.createVerticalStrut(6))

        // 系统提示词标签（与 Reset 按钮同行，更紧凑）
        val promptHeaderPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Appended System Prompt:"), BorderLayout.WEST)
            add(JButton("Reset to Default").apply {
                addActionListener { instructionsArea.text = entry.defaultInstructions }
            }, BorderLayout.EAST)
        }
        topPanel.add(promptHeaderPanel)
        topPanel.add(Box.createVerticalStrut(4))

        // 中间可伸缩的提示词区域（增加最小高度，充分利用节省的空间）
        val customScrollPane = JBScrollPane(instructionsArea).apply {
            minimumSize = Dimension(550, 200)
            preferredSize = Dimension(550, 300)
        }

        // 使用 BorderLayout 布局：顶部固定，中间伸缩
        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(customScrollPane, BorderLayout.CENTER)
        panel.preferredSize = Dimension(600, 580)
        return panel
    }

    /**
     * 添加禁用工具标签
     */
    private fun addDisabledToolTag(toolName: String) {
        if (!disabledToolsList.contains(toolName)) {
            disabledToolsList.add(toolName)
        }

        val tagPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBUI.CurrentTheme.ActionButton.hoverBorder(), 1, true),
                JBUI.Borders.empty(2, 6, 2, 4)
            )
            background = JBUI.CurrentTheme.ActionButton.hoverBackground()
            isOpaque = true
        }

        val label = JBLabel(toolName).apply {
            font = font.deriveFont(11f)
        }

        val removeBtn = JBLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Remove"
        }
        removeBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                disabledToolsList.remove(toolName)
                disabledToolsPanel?.remove(tagPanel)
                disabledToolsPanel?.revalidate()
                disabledToolsPanel?.repaint()
            }
        })

        tagPanel.add(label)
        tagPanel.add(removeBtn)
        disabledToolsPanel?.add(tagPanel)
        disabledToolsPanel?.revalidate()
        disabledToolsPanel?.repaint()
    }

    /**
     * 添加 Codex 禁用 feature 标签
     */
    private fun addCodexDisabledFeatureTag(featureName: String) {
        if (!codexDisabledFeaturesList.contains(featureName)) {
            codexDisabledFeaturesList.add(featureName)
        }

        val tagPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor(0x388E3C, 0x81C784), 1, true),  // 绿色边框
                JBUI.Borders.empty(2, 6, 2, 4)
            )
            background = JBColor(0xE8F5E9, 0x1B3B1B)  // 浅绿色背景
            isOpaque = true
        }

        val label = JBLabel(featureName).apply {
            font = font.deriveFont(11f)
        }

        val removeBtn = JBLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Remove"
        }
        removeBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                codexDisabledFeaturesList.remove(featureName)
                codexDisabledFeaturesPanel?.remove(tagPanel)
                codexDisabledFeaturesPanel?.revalidate()
                codexDisabledFeaturesPanel?.repaint()
            }
        })

        tagPanel.add(label)
        tagPanel.add(removeBtn)
        codexDisabledFeaturesPanel?.add(tagPanel)
        codexDisabledFeaturesPanel?.revalidate()
        codexDisabledFeaturesPanel?.repaint()
    }

    /**
     * 添加自动批准工具标签
     */
    private fun addAutoApprovedToolTag(toolName: String) {
        if (!autoApprovedToolsList.contains(toolName)) {
            autoApprovedToolsList.add(toolName)
        }

        val tagPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor(0x7B1FA2, 0xBA68C8), 1, true),  // 紫色边框
                JBUI.Borders.empty(2, 6, 2, 4)
            )
            background = JBColor(0xF3E5F5, 0x2D1B2E)  // 浅紫色背景
            isOpaque = true
        }

        val label = JBLabel(toolName).apply {
            font = font.deriveFont(11f)
        }

        val removeBtn = JBLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Remove"
        }
        removeBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                autoApprovedToolsList.remove(toolName)
                autoApprovedToolsPanel?.remove(tagPanel)
                autoApprovedToolsPanel?.revalidate()
                autoApprovedToolsPanel?.repaint()
            }
        })

        tagPanel.add(label)
        tagPanel.add(removeBtn)
        autoApprovedToolsPanel?.add(tagPanel)
        autoApprovedToolsPanel?.revalidate()
        autoApprovedToolsPanel?.repaint()
    }

    fun getServerEntry(): McpServerEntry {
        // 如果内容与默认值相同，存储空字符串（表示使用默认值）
        val customInstructions = if (instructionsArea.text.trim() == entry.defaultInstructions.trim()) {
            ""
        } else {
            instructionsArea.text
        }

        // 获取选中的可用 shells
        val selectedShells = if (entry.name == "JetBrains Terminal MCP") {
            availableShellCheckboxes
                .filter { it.value.isSelected }
                .keys
                .toList()
        } else emptyList()

        // 如果全部选中，存储空字符串（表示使用全部）
        val availableShellsValue = if (entry.name == "JetBrains Terminal MCP") {
            if (selectedShells.size == allShellTypes.size) "" else selectedShells.joinToString(",")
        } else entry.terminalAvailableShells

        return entry.copy(
            enabled = enableCheckbox.isSelected,
            enabledBackends = backendSelection.getSelectedKeys(),
            instructions = customInstructions,
            apiKey = if (entry.name == "Context7 MCP") apiKeyField.text else entry.apiKey,
            disabledTools = if (defaultDisabledTools.isNotEmpty() || entry.hasDisableToolsToggle) disabledToolsList.toList() else entry.disabledTools,
            codexDisabledFeatures = if (defaultCodexDisabledFeatures.isNotEmpty() || entry.hasDisableToolsToggle) codexDisabledFeaturesList.toList() else entry.codexDisabledFeatures,
            terminalMaxOutputLines = if (entry.name == "JetBrains Terminal MCP") {
                maxOutputLinesField.text.toIntOrNull() ?: 500
            } else entry.terminalMaxOutputLines,
            terminalMaxOutputChars = if (entry.name == "JetBrains Terminal MCP") {
                maxOutputCharsField.text.toIntOrNull() ?: 50000
            } else entry.terminalMaxOutputChars,
            terminalDefaultShell = if (entry.name == "JetBrains Terminal MCP") {
                defaultShellCombo.selectedItem as? String ?: ""
            } else entry.terminalDefaultShell,
            terminalAvailableShells = availableShellsValue,
            terminalReadTimeout = if (entry.name == "JetBrains Terminal MCP") {
                readTimeoutField.text.toIntOrNull() ?: 30
            } else entry.terminalReadTimeout,
            toolTimeoutSec = (toolTimeoutField.text.toIntOrNull() ?: 60).coerceAtLeast(1),
            fileAllowExternal = if (entry.name == "JetBrains File MCP") {
                fileAllowExternalCheckbox.isSelected
            } else entry.fileAllowExternal,
            fileExternalRules = if (entry.name == "JetBrains File MCP") {
                val rules = (0 until externalRulesListModel.size()).map { externalRulesListModel.getElementAt(it) }
                Json.encodeToString(rules)
            } else entry.fileExternalRules,
            gitCommitLanguage = if (entry.name == McpBundle.message("mcp.jetbrainsGit.name")) {
                val selectedIndex = commitLanguageCombo.selectedIndex
                if (selectedIndex >= 0 && selectedIndex < commitLanguageOptions.size) {
                    commitLanguageOptions[selectedIndex].first
                } else "en"
            } else entry.gitCommitLanguage,
            codexAutoApprovedTools = if (defaultAutoApprovedTools.isNotEmpty()) autoApprovedToolsList.toList() else entry.codexAutoApprovedTools
        )
    }
}

/**
 * 自定义 MCP 服务器编辑对话框
 */
class McpServerDialog(
    private val project: Project?,
    private val entry: McpServerEntry?
) : DialogWrapper(project) {

    private val enableCheckbox = JBCheckBox("Enable", entry?.enabled ?: true)
    private val backendSelection = McpBackendSelection(
        entry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL)
    )
    private val jsonArea = JBTextArea(3, 50).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        text = entry?.jsonConfig ?: ""
    }
    private val instructionsArea = JBTextArea(entry?.instructions ?: "", 2, 50).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val levelGroup = ButtonGroup()
    private val globalRadio = JBRadioButton("Global", entry?.level != McpServerLevel.PROJECT)
    private val projectRadio = JBRadioButton("Project", entry?.level == McpServerLevel.PROJECT)

    // 工具调用超时配置（0 表示永不超时）
    private val toolTimeoutField = JBTextField((entry?.toolTimeoutSec ?: 60).toString(), 6)

    init {
        title = if (entry == null) "New MCP Server" else "Edit MCP Server"
        levelGroup.add(globalRadio)
        levelGroup.add(projectRadio)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // Enable checkbox
        val enablePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(enableCheckbox)
        }
        contentPanel.add(enablePanel)
        contentPanel.add(Box.createVerticalStrut(10))

        contentPanel.add(backendSelection.panel)
        contentPanel.add(Box.createVerticalStrut(4))
        contentPanel.add(backendSelection.hint)
        contentPanel.add(Box.createVerticalStrut(10))

        fun updateBackendSelectionState(enabled: Boolean) {
            setEnabledRecursively(backendSelection.panel, enabled)
            backendSelection.hint.isEnabled = enabled
        }
        updateBackendSelectionState(enableCheckbox.isSelected)
        enableCheckbox.addActionListener { updateBackendSelectionState(enableCheckbox.isSelected) }

        // JSON 配置区域
        val jsonPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        val topPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(5)
            add(JBLabel("JSON configuration:"), BorderLayout.WEST)

            val formatButton = JButton(AllIcons.Actions.Preview).apply {
                toolTipText = "Format JSON"
                preferredSize = Dimension(28, 28)
                addActionListener { formatJson() }
            }
            val copyButton = JButton(AllIcons.Actions.Copy).apply {
                toolTipText = "Copy to clipboard"
                preferredSize = Dimension(28, 28)
                addActionListener {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(java.awt.datatransfer.StringSelection(jsonArea.text), null)
                }
            }
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0))
            buttonPanel.add(formatButton)
            buttonPanel.add(copyButton)
            add(buttonPanel, BorderLayout.EAST)
        }
        jsonPanel.add(topPanel, BorderLayout.NORTH)

        // 为 JSON 区域添加 placeholder
        val placeholderText = """{"server-name": {"command": "...", "args": [...]}}"""
        val placeholderColor = JBColor(0x999999, 0x666666)

        // 自定义绘制 placeholder
        val jsonAreaWithPlaceholder = object : JPanel(BorderLayout()) {
            init {
                add(jsonArea, BorderLayout.CENTER)
                jsonArea.isOpaque = false
            }

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                if (jsonArea.text.isEmpty() && !jsonArea.isFocusOwner) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    g2.color = placeholderColor
                    g2.font = jsonArea.font
                    val fm = g2.fontMetrics
                    g2.drawString(placeholderText, jsonArea.insets.left + 2, fm.ascent + jsonArea.insets.top + 2)
                }
            }
        }
        jsonArea.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) { jsonAreaWithPlaceholder.repaint() }
            override fun focusLost(e: java.awt.event.FocusEvent?) { jsonAreaWithPlaceholder.repaint() }
        })

        val jsonScrollPane = JBScrollPane(jsonAreaWithPlaceholder).apply {
            preferredSize = Dimension(500, 60)
        }
        jsonPanel.add(jsonScrollPane, BorderLayout.CENTER)

        // 简洁的格式提示
        val hintLabel = JBLabel("<html><font color='gray' size='-1'>HTTP: {\"name\": {\"type\": \"http\", \"url\": \"https://...\"}}</font></html>").apply {
            border = JBUI.Borders.emptyTop(4)
        }
        jsonPanel.add(hintLabel, BorderLayout.SOUTH)

        contentPanel.add(jsonPanel)
        contentPanel.add(Box.createVerticalStrut(15))

        // Appended System Prompt
        val promptLabel = JBLabel("Appended System Prompt (optional):").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        contentPanel.add(promptLabel)
        contentPanel.add(Box.createVerticalStrut(5))

        val instructionsScrollPane = JBScrollPane(instructionsArea).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            preferredSize = Dimension(500, 60)
        }
        contentPanel.add(instructionsScrollPane)
        contentPanel.add(Box.createVerticalStrut(15))

        // Tool call timeout
        val timeoutLabel = JBLabel("Tool Call Timeout:").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        contentPanel.add(timeoutLabel)
        contentPanel.add(Box.createVerticalStrut(5))

        val timeoutPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Timeout:"))
            add(toolTimeoutField)
            add(JBLabel("seconds"))
            add(Box.createHorizontalStrut(8))
            add(JBLabel("<html><font color='gray' size='-1'>(minimum 1 second)</font></html>"))
        }
        contentPanel.add(timeoutPanel)
        contentPanel.add(Box.createVerticalStrut(15))

        // Server level
        val levelPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Server level:"))
            add(globalRadio)
            add(projectRadio)
        }
        contentPanel.add(levelPanel)

        val levelHintLabel = JBLabel("<html><font color='gray' size='-1'>Global: all projects | Project: current project only</font></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            border = JBUI.Borders.emptyLeft(85)
        }
        contentPanel.add(levelHintLabel)
        contentPanel.add(Box.createVerticalStrut(10))

        // 警告
        val warningPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            background = JBColor(0xFFF3CD, 0x3D3000)
            border = JBUI.Borders.empty(8)
            add(JBLabel("<html><font color='#856404'>⚠ Proceed with caution and only connect to trusted servers.</font></html>"))
        }
        contentPanel.add(warningPanel)

        panel.add(contentPanel, BorderLayout.CENTER)
        panel.preferredSize = Dimension(550, panel.preferredSize.height)

        return panel
    }

    /**
     * 格式化 JSON
     */
    private fun formatJson() {
        try {
            val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
            val parsed = json.parseToJsonElement(jsonArea.text.trim())
            jsonArea.text = json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)
        } catch (_: Exception) {
            // 忽略格式化错误
        }
    }

    override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
        val jsonText = jsonArea.text.trim()
        if (jsonText.isBlank()) {
            return com.intellij.openapi.ui.ValidationInfo("JSON configuration cannot be empty", jsonArea)
        }

        return try {
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.parseToJsonElement(jsonText).jsonObject

            // 直接验证顶层对象（每个 key 是服务器名称）
            if (parsed.isEmpty()) {
                return com.intellij.openapi.ui.ValidationInfo("Configuration must contain at least one server", jsonArea)
            }

            // 验证每个服务器配置
            for ((serverName, serverConfig) in parsed) {
                if (serverName.isBlank()) {
                    return com.intellij.openapi.ui.ValidationInfo("Server name cannot be empty", jsonArea)
                }

                val config = serverConfig.jsonObject
                val hasCommand = config.containsKey("command")
                val hasUrl = config.containsKey("url")
                val serverType = config["type"]?.toString()?.trim('"')

                // HTTP 类型必须有 url
                if (serverType == "http" && !hasUrl) {
                    return com.intellij.openapi.ui.ValidationInfo("HTTP server '$serverName' must have 'url' field", jsonArea)
                }

                // STDIO 类型（默认）必须有 command
                if (serverType != "http" && !hasCommand) {
                    return com.intellij.openapi.ui.ValidationInfo("Server '$serverName' must have 'command' field (or 'type: http' with 'url')", jsonArea)
                }

                // 验证 command 不为空
                if (hasCommand) {
                    val command = config["command"]?.toString()?.trim('"') ?: ""
                    if (command.isBlank()) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'command' cannot be empty", jsonArea)
                    }
                }

                // 验证 url 不为空
                if (hasUrl) {
                    val url = config["url"]?.toString()?.trim('"') ?: ""
                    if (url.isBlank()) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'url' cannot be empty", jsonArea)
                    }
                }

                // 验证 args 是数组（如果存在）
                if (config.containsKey("args")) {
                    try {
                        config["args"]?.let {
                            if (it !is kotlinx.serialization.json.JsonArray) {
                                return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'args' must be an array", jsonArea)
                            }
                        }
                    } catch (_: Exception) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'args' must be an array", jsonArea)
                    }
                }

                // 验证 env 是对象（如果存在）
                if (config.containsKey("env")) {
                    try {
                        config["env"]?.jsonObject
                    } catch (_: Exception) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'env' must be an object", jsonArea)
                    }
                }
            }

            null
        } catch (e: Exception) {
            com.intellij.openapi.ui.ValidationInfo("Invalid JSON: ${e.message}", jsonArea)
        }
    }

    fun getServerEntry(): McpServerEntry {
        val jsonText = jsonArea.text.trim()
        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.parseToJsonElement(jsonText).jsonObject

        // 直接从顶层读取（每个 key 是服务器名称）
        val serverName = parsed.keys.firstOrNull() ?: "unknown"
        val serverConfig = parsed[serverName]?.jsonObject

        // 生成配置摘要
        val serverType = serverConfig?.get("type")?.toString()?.trim('"')
        val summary = if (serverType == "http") {
            val url = serverConfig["url"]?.toString()?.trim('"') ?: ""
            "http: $url"
        } else {
            val command = serverConfig?.get("command")?.toString()?.trim('"') ?: ""
            "command: $command"
        }

        return McpServerEntry(
            name = serverName,
            enabled = enableCheckbox.isSelected,
            enabledBackends = backendSelection.getSelectedKeys(),
            level = if (projectRadio.isSelected) McpServerLevel.PROJECT else McpServerLevel.GLOBAL,
            configSummary = summary,
            isBuiltIn = false,
            jsonConfig = jsonText,
            instructions = instructionsArea.text.trim(),
            toolTimeoutSec = (toolTimeoutField.text.toIntOrNull() ?: 60).coerceAtLeast(1)
        )
    }
}
