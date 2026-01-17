package com.asakii.settings

import com.asakii.ai.agent.sdk.CodexFeatures
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * 内置 MCP 服务器编辑对话框
 * 使用 Kotlin UI DSL 实现紧凑布局
 */
class BuiltInMcpServerDialog(
    private val project: Project?,
    private val entry: McpServerEntry
) : DialogWrapper(project) {

    // 基本设置
    private val enableCheckbox = JBCheckBox("Enable", entry.enabled)
    private val backendAllCheckbox = JBCheckBox("All")
    private val backendClaudeCheckbox = JBCheckBox("Claude Code")
    private val backendCodexCheckbox = JBCheckBox("Codex")
    private var updatingBackend = false

    // 系统提示词 (Claude Code / Codex 分开)
    private val instructionsClaudeArea = JBTextArea(
        entry.instructionsClaude.ifBlank { entry.defaultInstructions }, 10, 60
    ).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val instructionsCodexArea = JBTextArea(
        entry.instructionsCodex.ifBlank { entry.defaultInstructions }, 10, 60
    ).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }

    // Context7 MCP
    private val apiKeyField = JBTextField(entry.apiKey, 25)

    // JetBrains Terminal MCP
    private val maxOutputLinesField = JBTextField(entry.terminalMaxOutputLines.toString(), 6)
    private val maxOutputCharsField = JBTextField(entry.terminalMaxOutputChars.toString(), 8)
    private val readTimeoutField = JBTextField(entry.terminalReadTimeout.toString(), 4)
    private val defaultShellCombo = ComboBox<String>()
    private val availableShellCheckboxes = mutableMapOf<String, JBCheckBox>()
    private val allShellTypes = listOf("powershell", "cmd", "git-bash", "wsl")

    // JetBrains File MCP
    private val fileAllowExternalCheckbox = JBCheckBox("Allow external file access", entry.fileAllowExternal)
    private val externalRulesListModel = DefaultListModel<String>()
    private var externalRulesList: JBList<String>? = null

    // JetBrains Git MCP
    private val commitLanguageOptions = listOf(
        "en" to "English",
        "zh" to "中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "auto" to "Auto (detect from system)"
    )
    private val commitLanguageCombo = ComboBox(commitLanguageOptions.map { it.second }.toTypedArray())

    // 工具超时
    private val toolTimeoutField = JBTextField(entry.toolTimeoutSec.toString(), 6)

    // 禁用工具
    private val disabledToolsList = mutableListOf<String>()
    private var disabledToolsPanel: JPanel? = null
    private val defaultDisabledTools = entry.defaultDisabledTools

    // Codex 禁用功能
    private val codexDisabledFeaturesList = mutableListOf<String>()
    private var codexDisabledFeaturesPanel: JPanel? = null
    private val defaultCodexDisabledFeatures = entry.defaultCodexDisabledFeatures

    // Codex 自动批准工具
    private val autoApprovedToolsList = mutableListOf<String>()
    private var autoApprovedToolsPanel: JPanel? = null
    private val defaultAutoApprovedTools = entry.defaultAutoApprovedTools

    init {
        title = "Configure ${entry.name}"
        initBackendSelection()
        initGitLanguage()
        init()
    }

    private fun initBackendSelection() {
        val keys = entry.enabledBackends.map { it.trim().lowercase() }.toSet()
        val isAll = keys.contains(AgentSettingsService.MCP_BACKEND_ALL)
        backendAllCheckbox.isSelected = isAll
        backendClaudeCheckbox.isSelected = !isAll && keys.contains(AgentSettingsService.MCP_BACKEND_CLAUDE)
        backendCodexCheckbox.isSelected = !isAll && keys.contains(AgentSettingsService.MCP_BACKEND_CODEX)

        backendAllCheckbox.addActionListener { handleBackendSelection(backendAllCheckbox) }
        backendClaudeCheckbox.addActionListener { handleBackendSelection(backendClaudeCheckbox) }
        backendCodexCheckbox.addActionListener { handleBackendSelection(backendCodexCheckbox) }
    }

    private fun handleBackendSelection(source: JCheckBox) {
        if (updatingBackend) return
        updatingBackend = true
        try {
            if (source == backendAllCheckbox && backendAllCheckbox.isSelected) {
                backendClaudeCheckbox.isSelected = false
                backendCodexCheckbox.isSelected = false
            } else if (source.isSelected) {
                backendAllCheckbox.isSelected = false
            }
        } finally {
            updatingBackend = false
        }
    }

    private fun getSelectedBackends(): Set<String> {
        return if (backendAllCheckbox.isSelected) {
            setOf(AgentSettingsService.MCP_BACKEND_ALL)
        } else {
            val selected = mutableSetOf<String>()
            if (backendClaudeCheckbox.isSelected) selected.add(AgentSettingsService.MCP_BACKEND_CLAUDE)
            if (backendCodexCheckbox.isSelected) selected.add(AgentSettingsService.MCP_BACKEND_CODEX)
            selected
        }
    }

    private fun initGitLanguage() {
        val currentLang = entry.gitCommitLanguage.ifBlank { "en" }
        val langIndex = commitLanguageOptions.indexOfFirst { it.first == currentLang }
        if (langIndex >= 0) {
            commitLanguageCombo.selectedIndex = langIndex
        }
    }

    private fun updateDefaultShellCombo() {
        val model = DefaultComboBoxModel<String>()
        availableShellCheckboxes.forEach { (shellType, checkbox) ->
            if (checkbox.isSelected) model.addElement(shellType)
        }
        defaultShellCombo.model = model
    }

    override fun createCenterPanel(): JComponent {
        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab("General", createGeneralTab())
        tabbedPane.addTab("Claude Code", createClaudeTab())
        tabbedPane.addTab("Codex", createCodexTab())
        return tabbedPane
    }

    private fun createGeneralTab(): JComponent {
        return panel {
            // 启用和后端选择
            row {
                cell(enableCheckbox)
                label("Enabled in:").gap(RightGap.SMALL)
                cell(backendAllCheckbox)
                cell(backendClaudeCheckbox)
                cell(backendCodexCheckbox)
            }
            row {
                comment("Select which backends can use this MCP server. Choosing All clears other selections.")
            }

            separator().topGap(TopGap.SMALL)

            // Context7 MCP - API Key
            if (entry.name == "Context7 MCP") {
                row("API Key:") {
                    cell(apiKeyField).columns(COLUMNS_MEDIUM)
                    comment("(optional, for authenticated access)")
                }
            }

            // JetBrains File MCP - External Access
            if (entry.name == "JetBrains File MCP") {
                initExternalRules()
                row {
                    cell(fileAllowExternalCheckbox)
                }
                collapsibleGroup("External Access Rules") {
                    row {
                        cell(JBScrollPane(externalRulesList).apply {
                            preferredSize = Dimension(400, 80)
                        }).align(AlignX.FILL)
                    }
                    row {
                        button("+") {
                            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                            val chosen = FileChooser.chooseFile(descriptor, project, null)
                            if (chosen != null && !externalRulesListModel.contains(chosen.path)) {
                                externalRulesListModel.addElement(chosen.path)
                            }
                        }
                        button("-") {
                            val selected = externalRulesList?.selectedIndex ?: -1
                            if (selected >= 0) externalRulesListModel.remove(selected)
                        }
                    }
                }.apply {
                    expanded = try {
                        Json.decodeFromString<List<String>>(entry.fileExternalRules).isNotEmpty()
                    } catch (_: Exception) { false }
                }
            }

            // JetBrains Terminal MCP - Shell Config
            if (entry.name == "JetBrains Terminal MCP") {
                initTerminalShells()
                row("Default Shell:") {
                    cell(defaultShellCombo).columns(COLUMNS_SHORT)
                }
                row("Available Shells:") {
                    for (shellType in allShellTypes) {
                        cell(availableShellCheckboxes[shellType]!!)
                    }
                }
                row {
                    label("Max lines:")
                    cell(maxOutputLinesField).columns(COLUMNS_TINY)
                    label("Max chars:").gap(RightGap.SMALL)
                    cell(maxOutputCharsField).columns(COLUMNS_TINY)
                    label("Read timeout:").gap(RightGap.SMALL)
                    cell(readTimeoutField).columns(COLUMNS_TINY)
                    label("sec")
                }
            }

            // JetBrains Git MCP - Commit Language
            if (entry.name == McpBundle.message("mcp.jetbrainsGit.name")) {
                row("Commit Message Language:") {
                    cell(commitLanguageCombo).columns(COLUMNS_SHORT)
                }
                row {
                    comment("AI will generate commit messages in this language.")
                }
            }

            separator().topGap(TopGap.SMALL)

            // 工具超时
            row("Tool Call Timeout:") {
                cell(toolTimeoutField).columns(COLUMNS_TINY)
                label("seconds")
                comment("(min 1s)")
            }

        }.apply {
            preferredSize = Dimension(580, 500)
        }
    }

    private fun initExternalRules() {
        try {
            Json.decodeFromString<List<String>>(entry.fileExternalRules).forEach {
                externalRulesListModel.addElement(it)
            }
        } catch (_: Exception) {}
        externalRulesList = JBList(externalRulesListModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = 4
        }
    }

    private fun initTerminalShells() {
        val configuredShells = entry.terminalAvailableShells.trim()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val useAllShells = configuredShells.isEmpty()

        for (shellType in allShellTypes) {
            val isChecked = useAllShells || configuredShells.contains(shellType)
            availableShellCheckboxes[shellType] = JBCheckBox(shellType, isChecked).apply {
                addActionListener { updateDefaultShellCombo() }
            }
        }

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
    }

    private fun createClaudeTab(): JComponent {
        // 初始化禁用工具
        if (defaultDisabledTools.isNotEmpty() || entry.hasDisableToolsToggle) {
            disabledToolsList.addAll(entry.disabledTools)
            disabledToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4))
            entry.disabledTools.forEach { addDisabledToolTag(it) }
        }

        return panel {
            row {
                label("Appended System Prompt (Claude Code Override)").bold()
                link("Reset to Default") {
                    instructionsClaudeArea.text = entry.defaultInstructions
                }.align(AlignX.RIGHT)
            }
            row {
                comment("Customize the system prompt for Claude Code. Edit to override, or reset to use the default prompt.")
            }
            row {
                scrollCell(instructionsClaudeArea)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()

            // 禁用工具
            if (defaultDisabledTools.isNotEmpty() || entry.hasDisableToolsToggle) {
                separator().topGap(TopGap.SMALL)
                val inputField = JBTextField(15)
                row {
                    label("Disabled Tools:").applyToComponent {
                        foreground = JBColor(0xE53935, 0xEF5350)
                    }
                    cell(inputField).columns(COLUMNS_SHORT)
                    button("+") {
                        val text = inputField.text.trim()
                        if (text.isNotBlank() && !disabledToolsList.contains(text)) {
                            addDisabledToolTag(text)
                            inputField.text = ""
                        }
                    }
                    button("Reset") {
                        disabledToolsList.clear()
                        disabledToolsPanel?.removeAll()
                        defaultDisabledTools.forEach { addDisabledToolTag(it) }
                        disabledToolsPanel?.revalidate()
                        disabledToolsPanel?.repaint()
                    }
                }
                row {
                    cell(disabledToolsPanel!!).align(AlignX.FILL)
                }
            }
        }.apply {
            preferredSize = Dimension(580, 450)
        }
    }

    private fun createCodexTab(): JComponent {
        // 初始化 Codex 禁用功能
        if (defaultCodexDisabledFeatures.isNotEmpty()) {
            codexDisabledFeaturesList.addAll(entry.codexDisabledFeatures)
            codexDisabledFeaturesPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4))
            entry.codexDisabledFeatures.forEach { addCodexDisabledFeatureTag(it) }
        }

        // 初始化自动批准工具
        if (defaultAutoApprovedTools.isNotEmpty()) {
            autoApprovedToolsList.addAll(entry.codexAutoApprovedTools)
            autoApprovedToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4))
            entry.codexAutoApprovedTools.forEach { addAutoApprovedToolTag(it) }
        }

        return panel {
            row {
                label("Appended System Prompt (Codex Override)").bold()
                link("Reset to Default") {
                    instructionsCodexArea.text = entry.defaultInstructions
                }.align(AlignX.RIGHT)
            }
            row {
                comment("Customize the system prompt for Codex. Edit to override, or reset to use the default prompt.")
            }
            row {
                scrollCell(instructionsCodexArea)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()

            // Codex 禁用功能
            if (defaultCodexDisabledFeatures.isNotEmpty()) {
                separator().topGap(TopGap.SMALL)
                val availableFeatures = listOf(
                    CodexFeatures.SHELL_TOOL, CodexFeatures.APPLY_PATCH_FREEFORM,
                    CodexFeatures.UNIFIED_EXEC, CodexFeatures.VIEW_IMAGE_TOOL,
                    CodexFeatures.WEB_SEARCH_REQUEST, CodexFeatures.SKILLS
                ).joinToString(", ")

                val inputField = JBTextField(15).apply {
                    toolTipText = "Available: $availableFeatures"
                }
                row {
                    label("Disabled Features:").applyToComponent {
                        foreground = JBColor(0x388E3C, 0x81C784)
                    }
                    cell(inputField).columns(COLUMNS_SHORT)
                    button("+") {
                        val text = inputField.text.trim()
                        if (text.isNotBlank() && !codexDisabledFeaturesList.contains(text)) {
                            addCodexDisabledFeatureTag(text)
                            inputField.text = ""
                        }
                    }
                    button("Reset") {
                        codexDisabledFeaturesList.clear()
                        codexDisabledFeaturesPanel?.removeAll()
                        defaultCodexDisabledFeatures.forEach { addCodexDisabledFeatureTag(it) }
                        codexDisabledFeaturesPanel?.revalidate()
                        codexDisabledFeaturesPanel?.repaint()
                    }
                }
                row {
                    cell(codexDisabledFeaturesPanel!!).align(AlignX.FILL)
                }
            }

            // 自动批准工具
            if (defaultAutoApprovedTools.isNotEmpty()) {
                separator().topGap(TopGap.SMALL)
                val inputField = JBTextField(15)
                row {
                    label("Auto-Approved Tools:").applyToComponent {
                        foreground = JBColor(0x7B1FA2, 0xBA68C8)
                    }
                    cell(inputField).columns(COLUMNS_SHORT)
                    button("+") {
                        val text = inputField.text.trim()
                        if (text.isNotBlank() && !autoApprovedToolsList.contains(text)) {
                            addAutoApprovedToolTag(text)
                            inputField.text = ""
                        }
                    }
                    button("Reset") {
                        autoApprovedToolsList.clear()
                        autoApprovedToolsPanel?.removeAll()
                        defaultAutoApprovedTools.forEach { addAutoApprovedToolTag(it) }
                        autoApprovedToolsPanel?.revalidate()
                        autoApprovedToolsPanel?.repaint()
                    }
                }
                row {
                    cell(autoApprovedToolsPanel!!).align(AlignX.FILL)
                }
            }
        }.apply {
            preferredSize = Dimension(580, 450)
        }
    }

    private fun addDisabledToolTag(toolName: String) {
        if (!disabledToolsList.contains(toolName)) {
            disabledToolsList.add(toolName)
        }
        val tag = createTag(toolName, JBUI.CurrentTheme.ActionButton.hoverBorder(),
            JBUI.CurrentTheme.ActionButton.hoverBackground()) {
            disabledToolsList.remove(toolName)
            disabledToolsPanel?.remove(it)
            disabledToolsPanel?.revalidate()
            disabledToolsPanel?.repaint()
        }
        disabledToolsPanel?.add(tag)
        disabledToolsPanel?.revalidate()
        disabledToolsPanel?.repaint()
    }

    private fun addCodexDisabledFeatureTag(featureName: String) {
        if (!codexDisabledFeaturesList.contains(featureName)) {
            codexDisabledFeaturesList.add(featureName)
        }
        val tag = createTag(featureName, JBColor(0x388E3C, 0x81C784), JBColor(0xE8F5E9, 0x1B3B1B)) {
            codexDisabledFeaturesList.remove(featureName)
            codexDisabledFeaturesPanel?.remove(it)
            codexDisabledFeaturesPanel?.revalidate()
            codexDisabledFeaturesPanel?.repaint()
        }
        codexDisabledFeaturesPanel?.add(tag)
        codexDisabledFeaturesPanel?.revalidate()
        codexDisabledFeaturesPanel?.repaint()
    }

    private fun addAutoApprovedToolTag(toolName: String) {
        if (!autoApprovedToolsList.contains(toolName)) {
            autoApprovedToolsList.add(toolName)
        }
        val tag = createTag(toolName, JBColor(0x7B1FA2, 0xBA68C8), JBColor(0xF3E5F5, 0x2D1B2E)) {
            autoApprovedToolsList.remove(toolName)
            autoApprovedToolsPanel?.remove(it)
            autoApprovedToolsPanel?.revalidate()
            autoApprovedToolsPanel?.repaint()
        }
        autoApprovedToolsPanel?.add(tag)
        autoApprovedToolsPanel?.revalidate()
        autoApprovedToolsPanel?.repaint()
    }

    private fun createTag(text: String, borderColor: Color, bgColor: Color, onRemove: (JPanel) -> Unit): JPanel {
        val tagPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                JBUI.Borders.empty(2, 6, 2, 4)
            )
            background = bgColor
            isOpaque = true
        }
        val label = JBLabel(text).apply { font = font.deriveFont(11f) }
        val removeBtn = JBLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Remove"
        }
        removeBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { onRemove(tagPanel) }
        })
        tagPanel.add(label)
        tagPanel.add(removeBtn)
        return tagPanel
    }

    fun getServerEntry(): McpServerEntry {
        // Common instructions removed - only backend-specific instructions are used
        val customClaudeInstructions = if (instructionsClaudeArea.text.trim() == entry.defaultInstructions.trim()) "" else instructionsClaudeArea.text
        val customCodexInstructions = if (instructionsCodexArea.text.trim() == entry.defaultInstructions.trim()) "" else instructionsCodexArea.text

        val selectedShells = if (entry.name == "JetBrains Terminal MCP") {
            availableShellCheckboxes.filter { it.value.isSelected }.keys.toList()
        } else emptyList()

        val availableShellsValue = if (entry.name == "JetBrains Terminal MCP") {
            if (selectedShells.size == allShellTypes.size) "" else selectedShells.joinToString(",")
        } else entry.terminalAvailableShells

        return entry.copy(
            enabled = enableCheckbox.isSelected,
            enabledBackends = getSelectedBackends(),
            instructions = "",  // Common instructions removed
            instructionsClaude = customClaudeInstructions,
            instructionsCodex = customCodexInstructions,
            apiKey = if (entry.name == "Context7 MCP") apiKeyField.text else entry.apiKey,
            disabledTools = if (defaultDisabledTools.isNotEmpty() || entry.hasDisableToolsToggle) disabledToolsList.toList() else entry.disabledTools,
            codexDisabledFeatures = if (defaultCodexDisabledFeatures.isNotEmpty() || entry.hasDisableToolsToggle) codexDisabledFeaturesList.toList() else entry.codexDisabledFeatures,
            terminalMaxOutputLines = if (entry.name == "JetBrains Terminal MCP") maxOutputLinesField.text.toIntOrNull() ?: 500 else entry.terminalMaxOutputLines,
            terminalMaxOutputChars = if (entry.name == "JetBrains Terminal MCP") maxOutputCharsField.text.toIntOrNull() ?: 50000 else entry.terminalMaxOutputChars,
            terminalDefaultShell = if (entry.name == "JetBrains Terminal MCP") defaultShellCombo.selectedItem as? String ?: "" else entry.terminalDefaultShell,
            terminalAvailableShells = availableShellsValue,
            terminalReadTimeout = if (entry.name == "JetBrains Terminal MCP") readTimeoutField.text.toIntOrNull() ?: 30 else entry.terminalReadTimeout,
            toolTimeoutSec = (toolTimeoutField.text.toIntOrNull() ?: 60).coerceAtLeast(1),
            fileAllowExternal = if (entry.name == "JetBrains File MCP") fileAllowExternalCheckbox.isSelected else entry.fileAllowExternal,
            fileExternalRules = if (entry.name == "JetBrains File MCP") {
                Json.encodeToString((0 until externalRulesListModel.size()).map { externalRulesListModel.getElementAt(it) })
            } else entry.fileExternalRules,
            gitCommitLanguage = if (entry.name == McpBundle.message("mcp.jetbrainsGit.name")) {
                val idx = commitLanguageCombo.selectedIndex
                if (idx >= 0 && idx < commitLanguageOptions.size) commitLanguageOptions[idx].first else "en"
            } else entry.gitCommitLanguage,
            codexAutoApprovedTools = if (defaultAutoApprovedTools.isNotEmpty()) autoApprovedToolsList.toList() else entry.codexAutoApprovedTools
        )
    }
}

/**
 * 自定义 MCP 服务器编辑对话框
 * 使用 Kotlin UI DSL 实现紧凑布局
 */
class McpServerDialog(
    private val project: Project?,
    private val entry: McpServerEntry?
) : DialogWrapper(project) {

    private val enableCheckbox = JBCheckBox("Enable", entry?.enabled ?: true)
    private val backendAllCheckbox = JBCheckBox("All")
    private val backendClaudeCheckbox = JBCheckBox("Claude Code")
    private val backendCodexCheckbox = JBCheckBox("Codex")
    private var updatingBackend = false

    private val jsonArea = JBTextArea(3, 50).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        text = entry?.jsonConfig ?: ""
    }
    private val instructionsClaudeArea = JBTextArea(entry?.instructionsClaude ?: "", 4, 50).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val instructionsCodexArea = JBTextArea(entry?.instructionsCodex ?: "", 4, 50).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    private val globalRadio = JBRadioButton("Global", entry?.level != McpServerLevel.PROJECT)
    private val projectRadio = JBRadioButton("Project", entry?.level == McpServerLevel.PROJECT)
    private val toolTimeoutField = JBTextField((entry?.toolTimeoutSec ?: 60).toString(), 6)

    init {
        title = if (entry == null) "New MCP Server" else "Edit MCP Server"
        ButtonGroup().apply {
            add(globalRadio)
            add(projectRadio)
        }
        initBackendSelection()
        init()
    }

    private fun initBackendSelection() {
        val keys = (entry?.enabledBackends ?: setOf(AgentSettingsService.MCP_BACKEND_ALL))
            .map { it.trim().lowercase() }.toSet()
        val isAll = keys.contains(AgentSettingsService.MCP_BACKEND_ALL)
        backendAllCheckbox.isSelected = isAll
        backendClaudeCheckbox.isSelected = !isAll && keys.contains(AgentSettingsService.MCP_BACKEND_CLAUDE)
        backendCodexCheckbox.isSelected = !isAll && keys.contains(AgentSettingsService.MCP_BACKEND_CODEX)

        backendAllCheckbox.addActionListener { handleBackendSelection(backendAllCheckbox) }
        backendClaudeCheckbox.addActionListener { handleBackendSelection(backendClaudeCheckbox) }
        backendCodexCheckbox.addActionListener { handleBackendSelection(backendCodexCheckbox) }
    }

    private fun handleBackendSelection(source: JCheckBox) {
        if (updatingBackend) return
        updatingBackend = true
        try {
            if (source == backendAllCheckbox && backendAllCheckbox.isSelected) {
                backendClaudeCheckbox.isSelected = false
                backendCodexCheckbox.isSelected = false
            } else if (source.isSelected) {
                backendAllCheckbox.isSelected = false
            }
        } finally {
            updatingBackend = false
        }
    }

    private fun getSelectedBackends(): Set<String> {
        return if (backendAllCheckbox.isSelected) {
            setOf(AgentSettingsService.MCP_BACKEND_ALL)
        } else {
            val selected = mutableSetOf<String>()
            if (backendClaudeCheckbox.isSelected) selected.add(AgentSettingsService.MCP_BACKEND_CLAUDE)
            if (backendCodexCheckbox.isSelected) selected.add(AgentSettingsService.MCP_BACKEND_CODEX)
            selected
        }
    }

    override fun createCenterPanel(): JComponent {
        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab("General", createGeneralTab())
        tabbedPane.addTab("Claude Code", createClaudeTab())
        tabbedPane.addTab("Codex", createCodexTab())
        return tabbedPane
    }

    private fun createGeneralTab(): JComponent {
        return panel {
            row {
                cell(enableCheckbox)
            }
            row {
                label("Enabled in:").gap(RightGap.SMALL)
                cell(backendAllCheckbox)
                cell(backendClaudeCheckbox)
                cell(backendCodexCheckbox)
            }
            row {
                comment("Select which backends can use this MCP server.")
            }

            separator().topGap(TopGap.SMALL)

            row {
                label("JSON Configuration:").bold()
                link("Format") { formatJson() }.align(AlignX.RIGHT)
                link("Copy") {
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(java.awt.datatransfer.StringSelection(jsonArea.text), null)
                }
            }
            row {
                scrollCell(jsonArea)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()
            row {
                comment("""HTTP: {"name": {"type": "http", "url": "https://..."}}""")
            }

            separator().topGap(TopGap.SMALL)

            row("Tool Call Timeout:") {
                cell(toolTimeoutField).columns(COLUMNS_TINY)
                label("seconds")
                comment("(minimum 1 second)")
            }

            separator().topGap(TopGap.SMALL)

            row("Server Level:") {
                cell(globalRadio)
                cell(projectRadio)
            }
            row {
                comment("Global: all projects | Project: current project only")
            }

            row {
                cell(JPanel().apply {
                    background = JBColor(0xFFF3CD, 0x3D3000)
                    border = JBUI.Borders.empty(6)
                    add(JBLabel("<html><font color='#856404'>⚠ Warning: Only connect to trusted servers.</font></html>"))
                }).align(AlignX.FILL)
            }.topGap(TopGap.SMALL)
        }.apply {
            preferredSize = Dimension(550, 480)
        }
    }

    private fun createClaudeTab(): JComponent {
        return panel {
            row {
                label("Appended System Prompt (Claude Code)").bold()
                link("Clear") { instructionsClaudeArea.text = "" }.align(AlignX.RIGHT)
            }
            row {
                comment("Customize the system prompt for Claude Code. Leave empty to skip.")
            }
            row {
                scrollCell(instructionsClaudeArea)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()
        }.apply {
            preferredSize = Dimension(550, 400)
        }
    }

    private fun createCodexTab(): JComponent {
        return panel {
            row {
                label("Appended System Prompt (Codex)").bold()
                link("Clear") { instructionsCodexArea.text = "" }.align(AlignX.RIGHT)
            }
            row {
                comment("Customize the system prompt for Codex. Leave empty to skip.")
            }
            row {
                scrollCell(instructionsCodexArea)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()
        }.apply {
            preferredSize = Dimension(550, 400)
        }
    }

    private fun formatJson() {
        try {
            val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
            val parsed = json.parseToJsonElement(jsonArea.text.trim())
            jsonArea.text = json.encodeToString(JsonElement.serializer(), parsed)
        } catch (_: Exception) {}
    }

    override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
        val jsonText = jsonArea.text.trim()
        if (jsonText.isBlank()) {
            return com.intellij.openapi.ui.ValidationInfo("JSON configuration cannot be empty", jsonArea)
        }

        return try {
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.parseToJsonElement(jsonText).jsonObject

            if (parsed.isEmpty()) {
                return com.intellij.openapi.ui.ValidationInfo("Configuration must contain at least one server", jsonArea)
            }

            for ((serverName, serverConfig) in parsed) {
                if (serverName.isBlank()) {
                    return com.intellij.openapi.ui.ValidationInfo("Server name cannot be empty", jsonArea)
                }

                val config = serverConfig.jsonObject
                val hasCommand = config.containsKey("command")
                val hasUrl = config.containsKey("url")
                val serverType = config["type"]?.toString()?.trim('"')

                if (serverType == "http" && !hasUrl) {
                    return com.intellij.openapi.ui.ValidationInfo("HTTP server '$serverName' must have 'url' field", jsonArea)
                }
                if (serverType != "http" && !hasCommand) {
                    return com.intellij.openapi.ui.ValidationInfo("Server '$serverName' must have 'command' field", jsonArea)
                }
                if (hasCommand) {
                    val command = config["command"]?.toString()?.trim('"') ?: ""
                    if (command.isBlank()) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'command' cannot be empty", jsonArea)
                    }
                }
                if (hasUrl) {
                    val url = config["url"]?.toString()?.trim('"') ?: ""
                    if (url.isBlank()) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'url' cannot be empty", jsonArea)
                    }
                }
                if (config.containsKey("args")) {
                    try {
                        config["args"]?.let {
                            if (it !is JsonArray) {
                                return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'args' must be an array", jsonArea)
                            }
                        }
                    } catch (_: Exception) {
                        return com.intellij.openapi.ui.ValidationInfo("Server '$serverName': 'args' must be an array", jsonArea)
                    }
                }
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
        val serverName = parsed.keys.firstOrNull() ?: "unknown"
        val serverConfig = parsed[serverName]?.jsonObject
        val serverType = serverConfig?.get("type")?.toString()?.trim('"')
        val summary = if (serverType == "http") {
            "http: ${serverConfig["url"]?.toString()?.trim('"') ?: ""}"
        } else {
            "command: ${serverConfig?.get("command")?.toString()?.trim('"') ?: ""}"
        }

        return McpServerEntry(
            name = serverName,
            enabled = enableCheckbox.isSelected,
            enabledBackends = getSelectedBackends(),
            level = if (projectRadio.isSelected) McpServerLevel.PROJECT else McpServerLevel.GLOBAL,
            configSummary = summary,
            isBuiltIn = false,
            jsonConfig = jsonText,
            instructions = "",  // Common instructions removed
            instructionsClaude = instructionsClaudeArea.text.trim(),
            instructionsCodex = instructionsCodexArea.text.trim(),
            toolTimeoutSec = (toolTimeoutField.text.toIntOrNull() ?: 60).coerceAtLeast(1)
        )
    }
}
