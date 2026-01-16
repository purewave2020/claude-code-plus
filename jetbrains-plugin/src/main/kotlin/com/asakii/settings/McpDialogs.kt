package com.asakii.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import com.asakii.ai.agent.sdk.CodexFeatures

/**
 * 对话框统一间距常量
 */
private object DialogSpacing {
    const val SECTION_GAP = 16      // 分组之间的间距
    const val ITEM_GAP = 8          // 同组项目之间的间距
    const val LABEL_GAP = 4         // 标签与内容之间的间距
}

/**
 * MCP Backend 选择组件
 * 用于选择 MCP 服务器在哪些后端中启用（All、Claude Code、Codex）
 */
internal class McpBackendSelection(initialKeys: Set<String>) {
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
        "<html><div style='width: 500px'><font color='gray' size='-1'>Select which backends can use this MCP server. Choosing All clears other selections.</font></div></html>"
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

/**
 * 递归设置组件及其子组件的启用状态
 */
internal fun setEnabledRecursively(component: Component, enabled: Boolean) {
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
    private val instructionsClaudeArea = JBTextArea(
        entry.instructionsClaude.ifBlank { entry.defaultInstructions },
        10, 50
    ).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val instructionsCodexArea = JBTextArea(
        entry.instructionsCodex.ifBlank { entry.defaultInstructions },
        10, 50
    ).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val apiKeyField = JBTextField(entry.apiKey, 25)

    // 禁用工具配置（标签选择）
    private val disabledToolsList = mutableListOf<String>()
    private var disabledToolsPanel: JPanel? = null
    private val defaultDisabledTools = entry.defaultDisabledTools

    // Codex 禁用功能配置
    private val codexDisabledFeaturesList = mutableListOf<String>()
    private var codexDisabledFeaturesPanel: JPanel? = null
    private val defaultCodexDisabledFeatures = entry.defaultCodexDisabledFeatures

    // 自动批准工具配置
    private val autoApprovedToolsList = mutableListOf<String>()
    private var autoApprovedToolsPanel: JPanel? = null
    private val defaultAutoApprovedTools = entry.defaultAutoApprovedTools

    // JetBrains Terminal MCP 特有的配置
    private val maxOutputLinesField = JBTextField(entry.terminalMaxOutputLines.toString(), 6)
    private val maxOutputCharsField = JBTextField(entry.terminalMaxOutputChars.toString(), 8)
    private val readTimeoutField = JBTextField(entry.terminalReadTimeout.toString(), 4)
    private val defaultShellCombo = ComboBox<String>()
    private val availableShellCheckboxes = mutableMapOf<String, JBCheckBox>()
    private val allShellTypes = listOf("powershell", "cmd", "git-bash", "wsl")

    // JetBrains File MCP: 外部访问配置
    private val fileAllowExternalCheckbox = JBCheckBox("Allow external file access", entry.fileAllowExternal)
    private val externalRulesListModel = DefaultListModel<String>()
    private var externalRulesList: JBList<String>? = null

    // Git MCP: Commit 语言配置
    private val commitLanguageOptions = listOf(
        "en" to "English",
        "zh" to "中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "auto" to "Auto (detect from system)"
    )
    private val commitLanguageCombo = ComboBox(commitLanguageOptions.map { it.second }.toTypedArray())

    // 工具调用超时配置
    private val toolTimeoutField = JBTextField(entry.toolTimeoutSec.toString(), 6)

    init {
        title = "Configure ${entry.name}"
        init()

        // 初始化 Git MCP 语言选择
        val currentLang = entry.gitCommitLanguage.ifBlank { "en" }
        val langIndex = commitLanguageOptions.indexOfFirst { it.first == currentLang }
        if (langIndex >= 0) {
            commitLanguageCombo.selectedIndex = langIndex
        }
    }

    private fun updateDefaultShellCombo() {
        val model = DefaultComboBoxModel<String>()
        availableShellCheckboxes.forEach { (shellType, checkbox) ->
            if (checkbox.isSelected) {
                model.addElement(shellType)
            }
        }
        defaultShellCombo.model = model
    }

    private fun updateExternalRulesState() {
        externalRulesList?.isEnabled = fileAllowExternalCheckbox.isSelected
    }

    override fun createCenterPanel(): JComponent {
        val tabbedPane = JBTabbedPane()

        val generalContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }

        // ========== 基本设置组 ==========
        val enableBackendPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(enableCheckbox)
            add(Box.createHorizontalStrut(20))
            backendSelection.panel.components.forEach { add(it) }
        }
        generalContent.add(enableBackendPanel)
        generalContent.add(Box.createVerticalStrut(DialogSpacing.LABEL_GAP))
        generalContent.add(backendSelection.hint)
        generalContent.add(Box.createVerticalStrut(DialogSpacing.SECTION_GAP))

        // Toggle backend selection enabled state
        fun updateBackendSelectionState(enabled: Boolean) {
            setEnabledRecursively(backendSelection.panel, enabled)
            backendSelection.hint.isEnabled = enabled
        }
        updateBackendSelectionState(enableCheckbox.isSelected)
        enableCheckbox.addActionListener { updateBackendSelectionState(enableCheckbox.isSelected) }

        // ========== 服务器特定配置组 ==========
        // Context7 MCP API key
        if (entry.name == "Context7 MCP") {
            val apiKeyPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("API Key:"))
                add(apiKeyField)
                add(JBLabel("<html><font color='gray' size='-1'>(optional, for authenticated access)</font></html>"))
            }
            generalContent.add(apiKeyPanel)
            generalContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))
        }

        // JetBrains File MCP external access
        if (entry.name == "JetBrains File MCP") {
            val currentRules = try {
                Json.decodeFromString<List<String>>(entry.fileExternalRules)
            } catch (_: Exception) { emptyList() }
            currentRules.forEach { externalRulesListModel.addElement(it) }

            externalRulesList = JBList(externalRulesListModel).apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                visibleRowCount = 4
            }

            val listPanel = JPanel(BorderLayout())
            val scrollPane = JBScrollPane(externalRulesList).apply {
                preferredSize = Dimension(400, 80)
            }
            listPanel.add(scrollPane, BorderLayout.CENTER)

            val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
            buttonPanel.add(JButton("+").apply {
                preferredSize = Dimension(36, 28)
                toolTipText = "Add path rule"
                addActionListener {
                    val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    val chosen = FileChooser.chooseFile(descriptor, project, null)
                    if (chosen != null) {
                        val path = chosen.path
                        if (!externalRulesListModel.contains(path)) {
                            externalRulesListModel.addElement(path)
                        }
                    }
                }
            })
            buttonPanel.add(JButton("-").apply {
                preferredSize = Dimension(36, 28)
                toolTipText = "Remove selected rule"
                addActionListener {
                    val selected = externalRulesList?.selectedIndex ?: -1
                    if (selected >= 0) {
                        externalRulesListModel.remove(selected)
                    }
                }
            })
            listPanel.add(buttonPanel, BorderLayout.SOUTH)

            val (headerPanel, contentWrapper) = createCollapsibleSection(
                title = "External Access",
                contentPanel = listPanel,
                initiallyExpanded = currentRules.isNotEmpty(),
                extraHeaderComponents = listOf(fileAllowExternalCheckbox)
            )

            generalContent.add(headerPanel)
            generalContent.add(contentWrapper)
            generalContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))

            updateExternalRulesState()
            fileAllowExternalCheckbox.addActionListener { updateExternalRulesState() }
        }

        // JetBrains Terminal MCP shell configuration
        if (entry.name == "JetBrains Terminal MCP") {
            val configuredShells = entry.terminalAvailableShells.trim()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val useAllShells = configuredShells.isEmpty()

            // 第一行：Default Shell
            val defaultShellPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Default Shell:"))
                add(defaultShellCombo)
            }
            generalContent.add(defaultShellPanel)
            generalContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))

            // 第二行：Available Shells
            val availableShellsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Available Shells:").apply {
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
            generalContent.add(availableShellsPanel)

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
            generalContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))
        }

        // JetBrains Terminal MCP truncate and read timeout
        if (entry.name == "JetBrains Terminal MCP") {
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
            generalContent.add(truncateAndTimeoutPanel)
            generalContent.add(Box.createVerticalStrut(DialogSpacing.SECTION_GAP))
        }

        // Git MCP commit language
        if (entry.name == McpBundle.message("mcp.jetbrainsGit.name")) {
            val commitLangPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(JBLabel("Commit Message Language:"))
                add(commitLanguageCombo)
            }
            generalContent.add(commitLangPanel)
            generalContent.add(Box.createVerticalStrut(DialogSpacing.LABEL_GAP))
            generalContent.add(JBLabel(
                "<html><div style='width: 500px'><font color='gray' size='-1'>AI will generate commit messages in this language.</font></div></html>"
            ).apply { alignmentX = JPanel.LEFT_ALIGNMENT })
            generalContent.add(Box.createVerticalStrut(DialogSpacing.SECTION_GAP))
        }

        // ========== 超时设置组 ==========
        val toolTimeoutPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Tool Call Timeout:"))
            add(toolTimeoutField)
            add(JBLabel("seconds"))
            add(JBLabel("<html><font color='gray' size='-1'>(min 1s)</font></html>"))
        }
        generalContent.add(toolTimeoutPanel)
        generalContent.add(Box.createVerticalStrut(DialogSpacing.SECTION_GAP))

        // ========== 系统提示词组 ==========

        val resetCommonButton = JButton("Reset to Default").apply {
            addActionListener { instructionsArea.text = entry.defaultInstructions }
        }
        val commonScrollPane = JBScrollPane(instructionsArea).apply {
            minimumSize = Dimension(550, 180)
            preferredSize = Dimension(550, 240)
        }
        val (commonHeader, commonContent) = createCollapsibleSection(
            title = "Common Appended System Prompt:",
            contentPanel = commonScrollPane,
            initiallyExpanded = entry.instructions.isNotBlank(),
            extraHeaderComponents = listOf(resetCommonButton)
        )
        generalContent.add(commonHeader)
        generalContent.add(commonContent)

        val generalPanel = JBScrollPane(generalContent).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(600, 580)
        }
        tabbedPane.addTab("General", generalPanel)

        val claudeContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }
        val claudeHeaderPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("<html><b>Appended System Prompt (Claude Code Override)</b></html>"), BorderLayout.WEST)
            add(JButton("Reset to Default").apply {
                toolTipText = "Reset to default prompt"
                addActionListener { instructionsClaudeArea.text = entry.defaultInstructions }
            }, BorderLayout.EAST)
        }
        claudeContent.add(claudeHeaderPanel)
        claudeContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))
        claudeContent.add(JBLabel(
            "<html><div style='width: 500px'><font color='#6B7280' size='-1'>Customize the system prompt for Claude Code. " +
            "Edit to override, or reset to use the default prompt.</font></div></html>"
        ).apply { alignmentX = JPanel.LEFT_ALIGNMENT })
        claudeContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))
        val claudeScrollPane = JBScrollPane(instructionsClaudeArea).apply {
            minimumSize = Dimension(550, 200)
            preferredSize = Dimension(550, 300)
        }
        claudeContent.add(claudeScrollPane)
        claudeContent.add(Box.createVerticalStrut(DialogSpacing.SECTION_GAP))

        // Disabled tools (Claude Code)
        if (defaultDisabledTools.isNotEmpty() || entry.hasDisableToolsToggle) {
            disabledToolsList.addAll(entry.disabledTools)

            disabledToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            entry.disabledTools.forEach { toolName ->
                addDisabledToolTag(toolName)
            }

            val inputField = JBTextField(20)
            inputField.addActionListener {
                val text = inputField.text.trim()
                if (text.isNotBlank() && !disabledToolsList.contains(text)) {
                    addDisabledToolTag(text)
                    inputField.text = ""
                }
            }

            val (headerPanel, contentWrapper) = createCollapsibleTagPanel(
                title = "Disabled Tools:",
                titleColor = JBColor(0xE53935, 0xEF5350),
                inputField = inputField,
                onAdd = { text ->
                    if (text.isNotBlank() && !disabledToolsList.contains(text)) {
                        addDisabledToolTag(text)
                        true
                    } else false
                },
                onReset = {
                    disabledToolsList.clear()
                    disabledToolsPanel?.removeAll()
                    defaultDisabledTools.forEach { addDisabledToolTag(it) }
                    disabledToolsPanel?.revalidate()
                    disabledToolsPanel?.repaint()
                },
                resetTooltip = "Reset to default disabled tools",
                contentPanel = disabledToolsPanel!!,
                initiallyExpanded = entry.disabledTools.isNotEmpty()
            )
            claudeContent.add(headerPanel)
            claudeContent.add(contentWrapper)
            claudeContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))
        }

        val claudePanel = JBScrollPane(claudeContent).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(600, 580)
        }
        tabbedPane.addTab("Claude Code", claudePanel)

        val codexContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
        }
        val codexHeaderPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("<html><b>Appended System Prompt (Codex Override)</b></html>"), BorderLayout.WEST)
            add(JButton("Reset to Default").apply {
                toolTipText = "Reset to default prompt"
                addActionListener { instructionsCodexArea.text = entry.defaultInstructions }
            }, BorderLayout.EAST)
        }
        codexContent.add(codexHeaderPanel)
        codexContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))
        codexContent.add(JBLabel(
            "<html><div style='width: 500px'><font color='#6B7280' size='-1'>Customize the system prompt for Codex. " +
            "Edit to override, or reset to use the default prompt.</font></div></html>"
        ).apply { alignmentX = JPanel.LEFT_ALIGNMENT })
        codexContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))
        val codexScrollPane = JBScrollPane(instructionsCodexArea).apply {
            minimumSize = Dimension(550, 200)
            preferredSize = Dimension(550, 300)
        }
        codexContent.add(codexScrollPane)
        codexContent.add(Box.createVerticalStrut(DialogSpacing.SECTION_GAP))

        // Codex disabled features
        if (defaultCodexDisabledFeatures.isNotEmpty()) {
            codexDisabledFeaturesList.addAll(entry.codexDisabledFeatures)

            codexDisabledFeaturesPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            entry.codexDisabledFeatures.forEach { featureName ->
                addCodexDisabledFeatureTag(featureName)
            }

            val inputField = JBTextField(20)
            inputField.addActionListener {
                val text = inputField.text.trim()
                if (text.isNotBlank() && !codexDisabledFeaturesList.contains(text)) {
                    addCodexDisabledFeatureTag(text)
                    inputField.text = ""
                }
            }

            val availableFeatures = listOf(
                CodexFeatures.SHELL_TOOL,
                CodexFeatures.APPLY_PATCH_FREEFORM,
                CodexFeatures.UNIFIED_EXEC,
                CodexFeatures.VIEW_IMAGE_TOOL,
                CodexFeatures.WEB_SEARCH_REQUEST,
                CodexFeatures.SKILLS
            ).joinToString(", ")

            val (headerPanel, contentWrapper) = createCollapsibleTagPanel(
                title = "Codex Disabled Features:",
                titleColor = JBColor(0x388E3C, 0x81C784),
                inputField = inputField.apply {
                    toolTipText = "Available: $availableFeatures"
                },
                onAdd = { text ->
                    if (text.isNotBlank() && !codexDisabledFeaturesList.contains(text)) {
                        addCodexDisabledFeatureTag(text)
                        true
                    } else false
                },
                onReset = {
                    codexDisabledFeaturesList.clear()
                    codexDisabledFeaturesPanel?.removeAll()
                    defaultCodexDisabledFeatures.forEach { addCodexDisabledFeatureTag(it) }
                    codexDisabledFeaturesPanel?.revalidate()
                    codexDisabledFeaturesPanel?.repaint()
                },
                resetTooltip = "Reset to default disabled features",
                contentPanel = codexDisabledFeaturesPanel!!,
                initiallyExpanded = entry.codexDisabledFeatures.isNotEmpty()
            )
            codexContent.add(headerPanel)
            codexContent.add(contentWrapper)
            codexContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))
        }

        // Codex auto-approved tools
        if (defaultAutoApprovedTools.isNotEmpty()) {
            autoApprovedToolsList.addAll(entry.codexAutoApprovedTools)

            autoApprovedToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4)).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            entry.codexAutoApprovedTools.forEach { toolName ->
                addAutoApprovedToolTag(toolName)
            }

            val inputField = JBTextField(20)
            inputField.addActionListener {
                val text = inputField.text.trim()
                if (text.isNotBlank() && !autoApprovedToolsList.contains(text)) {
                    addAutoApprovedToolTag(text)
                    inputField.text = ""
                }
            }

            val (headerPanel, contentWrapper) = createCollapsibleTagPanel(
                title = "Auto-Approved Tools (Codex):",
                titleColor = JBColor(0x7B1FA2, 0xBA68C8),
                inputField = inputField,
                onAdd = { text ->
                    if (text.isNotBlank() && !autoApprovedToolsList.contains(text)) {
                        addAutoApprovedToolTag(text)
                        true
                    } else false
                },
                onReset = {
                    autoApprovedToolsList.clear()
                    autoApprovedToolsPanel?.removeAll()
                    defaultAutoApprovedTools.forEach { addAutoApprovedToolTag(it) }
                    autoApprovedToolsPanel?.revalidate()
                    autoApprovedToolsPanel?.repaint()
                },
                resetTooltip = "Reset to default auto-approved tools",
                contentPanel = autoApprovedToolsPanel!!,
                initiallyExpanded = entry.codexAutoApprovedTools.isNotEmpty()
            )
            codexContent.add(headerPanel)
            codexContent.add(contentWrapper)
            codexContent.add(Box.createVerticalStrut(DialogSpacing.ITEM_GAP))
        }

        val codexPanel = JBScrollPane(codexContent).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(600, 580)
        }
        tabbedPane.addTab("Codex", codexPanel)

        tabbedPane.preferredSize = Dimension(620, 620)
        return tabbedPane
    }

    /**
     * 创建可折叠标签面板
     * @param title 标题文本
     * @param titleColor 标题颜色
     * @param inputField 输入框
     * @param onAdd 添加回调，返回 true 表示添加成功
     * @param onReset 重置回调
     * @param resetTooltip 重置按钮提示
     * @param contentPanel 内容面板（标签区域）
     * @param initiallyExpanded 初始是否展开
     * @return Pair<HeaderPanel, ContentWrapper>
     */
    private fun createCollapsibleTagPanel(
        title: String,
        titleColor: Color,
        inputField: JBTextField,
        onAdd: (String) -> Boolean,
        onReset: () -> Unit,
        resetTooltip: String,
        contentPanel: JComponent,
        initiallyExpanded: Boolean = true
    ): Pair<JPanel, JPanel> {
        var isExpanded = initiallyExpanded

        // 内容包装面板（用于显示/隐藏）
        val contentWrapper = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(contentPanel, BorderLayout.CENTER)
            isVisible = isExpanded
        }

        // 折叠按钮
        val collapseButton = JBLabel().apply {
            icon = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = if (isExpanded) "Collapse" else "Expand"
        }

        // 标题标签
        val titleLabel = JBLabel(title).apply {
            foreground = titleColor
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        // 点击标题或按钮时切换展开状态
        val toggleAction = {
            isExpanded = !isExpanded
            contentWrapper.isVisible = isExpanded
            collapseButton.icon = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
            collapseButton.toolTipText = if (isExpanded) "Collapse" else "Expand"
            // 触发父容器重新布局
            contentWrapper.parent?.revalidate()
            contentWrapper.parent?.repaint()
        }

        collapseButton.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { toggleAction() }
        })
        titleLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { toggleAction() }
        })

        // 标题行面板
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(collapseButton)
            add(titleLabel)
            add(inputField.apply {
                toolTipText = "Type name, then click + or press Enter"
            })
            add(JButton("+").apply {
                preferredSize = Dimension(36, inputField.preferredSize.height)
                toolTipText = "Add to list"
                addActionListener {
                    val text = inputField.text.trim()
                    if (onAdd(text)) {
                        inputField.text = ""
                        // 添加成功后自动展开
                        if (!isExpanded) {
                            toggleAction()
                        }
                    }
                }
            })
            add(JButton("Reset").apply {
                toolTipText = resetTooltip
                addActionListener { onReset() }
            })
        }

        return Pair(headerPanel, contentWrapper)
    }

    /**
     * 创建通用可折叠区域
     * @param title 标题文本
     * @param titleColor 标题颜色（可选）
     * @param contentPanel 内容面板
     * @param initiallyExpanded 初始是否展开
     * @param extraHeaderComponents 额外的标题栏组件（如 Reset 按钮）
     * @return Pair<HeaderPanel, ContentWrapper>
     */
    private fun createCollapsibleSection(
        title: String,
        titleColor: Color? = null,
        contentPanel: JComponent,
        initiallyExpanded: Boolean = true,
        extraHeaderComponents: List<JComponent> = emptyList()
    ): Pair<JPanel, JPanel> {
        var isExpanded = initiallyExpanded

        // 内容包装面板
        val contentWrapper = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(contentPanel, BorderLayout.CENTER)
            isVisible = isExpanded
        }

        // 折叠按钮
        val collapseButton = JBLabel().apply {
            icon = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = if (isExpanded) "Collapse" else "Expand"
        }

        // 标题标签
        val titleLabel = JBLabel(title).apply {
            titleColor?.let { foreground = it }
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        // 切换展开状态
        val toggleAction = {
            isExpanded = !isExpanded
            contentWrapper.isVisible = isExpanded
            collapseButton.icon = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
            collapseButton.toolTipText = if (isExpanded) "Collapse" else "Expand"
            contentWrapper.parent?.revalidate()
            contentWrapper.parent?.repaint()
        }

        collapseButton.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { toggleAction() }
        })
        titleLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { toggleAction() }
        })

        // 标题行面板
        val headerPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            
            // 左侧：折叠按钮 + 标题
            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(collapseButton)
                add(titleLabel)
            }
            add(leftPanel, BorderLayout.WEST)
            
            // 右侧：额外组件
            if (extraHeaderComponents.isNotEmpty()) {
                val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                    extraHeaderComponents.forEach { add(it) }
                }
                add(rightPanel, BorderLayout.EAST)
            }
        }

        return Pair(headerPanel, contentWrapper)
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
        val customClaudeInstructions = if (instructionsClaudeArea.text.trim() == entry.defaultInstructions.trim()) {
            ""
        } else {
            instructionsClaudeArea.text
        }
        val customCodexInstructions = if (instructionsCodexArea.text.trim() == entry.defaultInstructions.trim()) {
            ""
        } else {
            instructionsCodexArea.text
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
            instructionsClaude = customClaudeInstructions,
            instructionsCodex = customCodexInstructions,
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
    private val instructionsClaudeArea = JBTextArea(entry?.instructionsClaude ?: "", 2, 50).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val instructionsCodexArea = JBTextArea(entry?.instructionsCodex ?: "", 2, 50).apply {
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
        val tabbedPane = JBTabbedPane()

        val generalContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }

        val enablePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(enableCheckbox)
        }
        generalContent.add(enablePanel)
        generalContent.add(Box.createVerticalStrut(8))

        generalContent.add(backendSelection.panel)
        generalContent.add(Box.createVerticalStrut(4))
        generalContent.add(backendSelection.hint)
        generalContent.add(Box.createVerticalStrut(10))

        fun updateBackendSelectionState(enabled: Boolean) {
            setEnabledRecursively(backendSelection.panel, enabled)
            backendSelection.hint.isEnabled = enabled
        }
        updateBackendSelectionState(enableCheckbox.isSelected)
        enableCheckbox.addActionListener { updateBackendSelectionState(enableCheckbox.isSelected) }

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

        val placeholderText = """{"server-name": {"command": "...", "args": [...]}}"""
        val placeholderColor = JBColor(0x999999, 0x666666)

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

        val hintLabel = JBLabel("<html><font color='gray' size='-1'>HTTP: {\"name\": {\"type\": \"http\", \"url\": \"https://...\"}}</font></html>").apply {
            border = JBUI.Borders.emptyTop(4)
        }
        jsonPanel.add(hintLabel, BorderLayout.SOUTH)

        generalContent.add(jsonPanel)
        generalContent.add(Box.createVerticalStrut(12))

        val promptLabel = JBLabel("Common Appended System Prompt (optional):").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        generalContent.add(promptLabel)
        generalContent.add(Box.createVerticalStrut(5))

        val instructionsScrollPane = JBScrollPane(instructionsArea).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            preferredSize = Dimension(500, 80)
        }
        generalContent.add(instructionsScrollPane)
        generalContent.add(Box.createVerticalStrut(12))

        val timeoutLabel = JBLabel("Tool Call Timeout:").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        generalContent.add(timeoutLabel)
        generalContent.add(Box.createVerticalStrut(5))

        val timeoutPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Timeout:"))
            add(toolTimeoutField)
            add(JBLabel("seconds"))
            add(Box.createHorizontalStrut(8))
            add(JBLabel("<html><font color='gray' size='-1'>(minimum 1 second)</font></html>"))
        }
        generalContent.add(timeoutPanel)
        generalContent.add(Box.createVerticalStrut(12))

        val levelPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Server level:"))
            add(globalRadio)
            add(projectRadio)
        }
        generalContent.add(levelPanel)

        val levelHintLabel = JBLabel("<html><font color='gray' size='-1'>Global: all projects | Project: current project only</font></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            border = JBUI.Borders.emptyLeft(85)
        }
        generalContent.add(levelHintLabel)
        generalContent.add(Box.createVerticalStrut(10))

        val warningPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            background = JBColor(0xFFF3CD, 0x3D3000)
            border = JBUI.Borders.empty(8)
            add(JBLabel("<html><font color='#856404'>Warning: Proceed with caution and only connect to trusted servers.</font></html>"))
        }
        generalContent.add(warningPanel)

        val generalPanel = JBScrollPane(generalContent).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(550, 520)
        }
        tabbedPane.addTab("General", generalPanel)

        val claudeContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }
        val claudeHeaderPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Appended System Prompt (Claude Code Override):"), BorderLayout.WEST)
            add(JButton("Use Common").apply {
                addActionListener { instructionsClaudeArea.text = "" }
            }, BorderLayout.EAST)
        }
        claudeContent.add(claudeHeaderPanel)
        claudeContent.add(Box.createVerticalStrut(4))
        claudeContent.add(JBLabel(
            "<html><font color='gray' size='-1'>Leave empty to use common prompt.</font></html>"
        ).apply { alignmentX = JPanel.LEFT_ALIGNMENT })
        claudeContent.add(Box.createVerticalStrut(6))
        val claudeScrollPane = JBScrollPane(instructionsClaudeArea).apply {
            preferredSize = Dimension(500, 200)
        }
        claudeContent.add(claudeScrollPane)

        val claudePanel = JBScrollPane(claudeContent).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(550, 520)
        }
        tabbedPane.addTab("Claude Code", claudePanel)

        val codexContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }
        val codexHeaderPanel = JPanel(BorderLayout()).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel("Appended System Prompt (Codex Override):"), BorderLayout.WEST)
            add(JButton("Use Common").apply {
                addActionListener { instructionsCodexArea.text = "" }
            }, BorderLayout.EAST)
        }
        codexContent.add(codexHeaderPanel)
        codexContent.add(Box.createVerticalStrut(4))
        codexContent.add(JBLabel(
            "<html><font color='gray' size='-1'>Leave empty to use common prompt.</font></html>"
        ).apply { alignmentX = JPanel.LEFT_ALIGNMENT })
        codexContent.add(Box.createVerticalStrut(6))
        val codexScrollPane = JBScrollPane(instructionsCodexArea).apply {
            preferredSize = Dimension(500, 200)
        }
        codexContent.add(codexScrollPane)

        val codexPanel = JBScrollPane(codexContent).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(550, 520)
        }
        tabbedPane.addTab("Codex", codexPanel)

        tabbedPane.preferredSize = Dimension(580, 560)
        return tabbedPane
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
            instructionsClaude = instructionsClaudeArea.text.trim(),
            instructionsCodex = instructionsCodexArea.text.trim(),
            toolTimeoutSec = (toolTimeoutField.text.toIntOrNull() ?: 60).coerceAtLeast(1)
        )
    }
}

