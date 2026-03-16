package com.asakii.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.asakii.plugin.compat.BrowseButtonCompat
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * 自动换行的 FlowLayout (保留供工具标签使用)
 */
class WrapLayout(align: Int = FlowLayout.LEFT, hgap: Int = 5, vgap: Int = 5) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)
    override fun minimumLayoutSize(target: Container): Dimension = layoutSize(target, false)

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val targetWidth = target.width
            if (targetWidth == 0) {
                return if (preferred) super.preferredLayoutSize(target) else super.minimumLayoutSize(target)
            }
            val insets = target.insets
            val maxWidth = targetWidth - (insets.left + insets.right + hgap * 2)
            var rowWidth = 0
            var rowHeight = 0
            var height = insets.top + vgap

            for (i in 0 until target.componentCount) {
                val m = target.getComponent(i)
                if (m.isVisible) {
                    val d = if (preferred) m.preferredSize else m.minimumSize
                    if (rowWidth + d.width > maxWidth) {
                        height += rowHeight + vgap
                        rowWidth = 0
                        rowHeight = 0
                    }
                    rowWidth += d.width + hgap
                    rowHeight = maxOf(rowHeight, d.height)
                }
            }
            height += rowHeight + vgap + insets.bottom
            return Dimension(targetWidth, height)
        }
    }
}

@Serializable
data class AgentsConfigData(val agents: Map<String, AgentConfigItem> = emptyMap())

@Serializable
data class AgentConfigItem(
    val enabled: Boolean = true,
    val description: String = "",
    val prompt: String = "",
    val tools: List<String> = emptyList(),
    val model: String = "",
    val selectionHint: String = ""
)

class ModelInfoRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is ModelInfo) {
            text = if (value.isBuiltIn) value.displayName else "${value.displayName} (custom)"
            toolTipText = value.modelId
        }
        return component
    }
}

/**
 * Claude Code 配置页面 - 使用 Kotlin UI DSL
 */
class ClaudeCodeConfigurable : SearchableConfigurable {

    private var mainPanel: JComponent? = null

    // General Tab 组件
    private var defaultBypassPermissionsCheckbox: JBCheckBox? = null
    private var defaultAutoCleanupContextsCheckbox: JBCheckBox? = null
    private var nodePathField: TextFieldWithBrowseButton? = null
    private var defaultModelCombo: ComboBox<ModelInfo>? = null
    private var defaultThinkingLevelCombo: ComboBox<ThinkingLevelConfig>? = null
    private var thinkTokensSpinner: JSpinner? = null
    private var ultraTokensSpinner: JSpinner? = null
    private var permissionModeCombo: ComboBox<String>? = null
    private var includePartialMessagesCheckbox: JBCheckBox? = null

    // API Configuration components
    private var authModeOAuthRadio: JRadioButton? = null
    private var authModeApiKeyRadio: JRadioButton? = null
    private var oauthStatusLabel: JBLabel? = null
    private var apiKeyPanel: JPanel? = null
    private var apiKeyField: JBPasswordField? = null
    private var baseUrlField: JBTextField? = null
    private var apiKeySourceLabel: JBLabel? = null
    private var baseUrlSourceLabel: JBLabel? = null
    private var testConnectionButton: JButton? = null
    private var testResultLabel: JBLabel? = null

    // Custom Models 组件
    private var customModelsTable: JBTable? = null
    private var customModelsTableModel: DefaultTableModel? = null

    // Agents Tab 组件
    private var exploreEnabledCheckbox: JBCheckBox? = null
    private var exploreModelCombo: ComboBox<String>? = null
    private var exploreDescriptionArea: JBTextArea? = null
    private var explorePromptArea: JBTextArea? = null
    private var exploreSelectionHintArea: JBTextArea? = null
    private var exploreToolsPanel: JPanel? = null
    private var exploreToolsList: MutableList<String> = mutableListOf()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun getId(): String = "com.asakii.settings.claudecode"
    override fun getDisplayName(): String = "Claude Code"

    override fun createComponent(): JComponent {
        val tabbedPane = JBTabbedPane()
        tabbedPane.addTab("General", createGeneralPanel())
        tabbedPane.addTab("Agents", createAgentsPanel())
        mainPanel = tabbedPane
        reset()
        return mainPanel!!
    }

    private fun createGeneralPanel(): JComponent {
        // 初始化组件
        defaultBypassPermissionsCheckbox = JBCheckBox("Default bypass permissions")
        defaultAutoCleanupContextsCheckbox = JBCheckBox("Default auto cleanup contexts")
        includePartialMessagesCheckbox = JBCheckBox("Include partial messages in stream").apply {
            isSelected = true
            isEnabled = false
        }
        permissionModeCombo = ComboBox(arrayOf("default", "acceptEdits", "plan", "bypassPermissions"))

        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        nodePathField = TextFieldWithBrowseButton().apply {
            BrowseButtonCompat.addBrowseFolderListener(this, "Select Node.js Executable",
                "Choose the path to node executable", null, descriptor)
            (textField as? JBTextField)?.let { tf ->
                tf.emptyText.text = "Detecting Node.js..."
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                    val nodeInfo = AgentSettingsService.detectNodeInfo()
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        tf.emptyText.text = nodeInfo?.let {
                            if (it.version != null) "${it.path} (${it.version})" else it.path
                        } ?: "Auto-detect from system PATH"
                    }
                }
            }
        }

        defaultModelCombo = ComboBox<ModelInfo>().apply { renderer = ModelInfoRenderer() }
        refreshModelCombo()

        defaultThinkingLevelCombo = ComboBox<ThinkingLevelConfig>().apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is ThinkingLevelConfig) {
                        text = value.name
                        toolTipText = "${value.tokens} tokens"
                    }
                    return c
                }
            }
        }

        thinkTokensSpinner = JSpinner(SpinnerNumberModel(2048, 1, 128000, 256)).apply {
            editor = JSpinner.NumberEditor(this, "#")
            addChangeListener { updateThinkingLevelCombo() }
        }
        ultraTokensSpinner = JSpinner(SpinnerNumberModel(8096, 1, 128000, 256)).apply {
            editor = JSpinner.NumberEditor(this, "#")
            addChangeListener { updateThinkingLevelCombo() }
        }

        // 自定义模型表格
        customModelsTableModel = object : DefaultTableModel(arrayOf("Display Name", "Model ID"), 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        customModelsTable = JBTable(customModelsTableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            tableHeader.reorderingAllowed = false
        }

        // Initialize auth mode radio buttons
        authModeOAuthRadio = JRadioButton("Claude Account (OAuth)")
        authModeApiKeyRadio = JRadioButton("API Key")
        val authModeGroup = ButtonGroup().apply {
            add(authModeOAuthRadio)
            add(authModeApiKeyRadio)
        }

        // OAuth status components
        oauthStatusLabel = JBLabel("").apply { font = font.deriveFont(11f) }

        // API Key panel (shown/hidden based on auth mode)
        apiKeyField = JBPasswordField().apply {
            columns = 30
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateApiKeySourceLabel()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateApiKeySourceLabel()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateApiKeySourceLabel()
            })
        }
        baseUrlField = JBTextField().apply {
            columns = 30
            emptyText.text = "https://api.anthropic.com (default)"
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateBaseUrlSourceLabel()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateBaseUrlSourceLabel()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateBaseUrlSourceLabel()
            })
        }
        apiKeySourceLabel = JBLabel("").apply { font = font.deriveFont(11f) }
        baseUrlSourceLabel = JBLabel("").apply { font = font.deriveFont(11f) }
        testConnectionButton = JButton("Test Connection").apply {
            addActionListener { testApiConnection() }
        }
        testResultLabel = JBLabel("").apply { font = font.deriveFont(11f) }

        // Build the API key sub-panel as a regular JPanel
        apiKeyPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            val apiKeyRow = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("API Key:"))
                add(apiKeyField)
            }
            val apiKeySourceRow = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(apiKeySourceLabel)
            }
            val baseUrlRow = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("Base URL:"))
                add(baseUrlField)
            }
            val baseUrlSourceRow = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(baseUrlSourceLabel)
            }
            add(apiKeyRow)
            add(apiKeySourceRow)
            add(baseUrlRow)
            add(baseUrlSourceRow)
        }

        // OAuth status panel
        val oauthLoginHint = JBLabel("Please run 'claude login' in terminal to authenticate").apply {
            font = font.deriveFont(11f)
            foreground = JBColor(0x808080, 0x999999)
        }
        val oauthPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            val statusRow = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(oauthStatusLabel)
            }
            val hintRow = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(oauthLoginHint)
            }
            add(statusRow)
            add(hintRow)
        }

        // Auth mode toggle behavior
        fun updateAuthModeVisibility() {
            val isApiKey = authModeApiKeyRadio?.isSelected == true
            apiKeyPanel?.isVisible = isApiKey
            oauthPanel.isVisible = !isApiKey
        }
        authModeOAuthRadio!!.addActionListener { updateAuthModeVisibility(); refreshOAuthStatus() }
        authModeApiKeyRadio!!.addActionListener { updateAuthModeVisibility() }

        // Auth mode selection panel (avoid DSL buttonsGroup requirement)
        val authModePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Authentication:"))
            add(authModeOAuthRadio)
            add(authModeApiKeyRadio)
        }

        // Combined API config panel
        val apiConfigPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(authModePanel)
            add(oauthPanel)
            add(apiKeyPanel)
            val testRow = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(testConnectionButton)
                add(testResultLabel)
            }
            add(testRow)
        }

        return panel {
            group("API Configuration") {
                row {
                    cell(apiConfigPanel).align(AlignX.FILL)
                }
            }

            group("Default Permissions") {
                row { cell(defaultBypassPermissionsCheckbox!!) }
                row { comment("Skip confirmation dialogs for file edits and bash commands.") }
                row { cell(defaultAutoCleanupContextsCheckbox!!) }
                row { comment("Enabled contexts are cleared after send; disabled contexts stay.") }
                row("Permission Mode:") { cell(permissionModeCombo!!).columns(COLUMNS_MEDIUM) }
                row { comment("default = Ask for each action | bypassPermissions = Auto-approve all") }
                row { cell(includePartialMessagesCheckbox!!) }
            }

            group("Runtime Settings") {
                row("Node.js path:") {
                    cell(nodePathField!!).align(AlignX.FILL).resizableColumn()
                }
                row { comment("Path to Node.js executable. Leave empty to auto-detect from system PATH.") }
                row("Default model:") { cell(defaultModelCombo!!).columns(COLUMNS_MEDIUM) }
                row { comment("Opus 4.6 = Most capable | Sonnet 4.6 = Balanced | Haiku 4.6 = Fastest") }
            }

            collapsibleGroup("Custom Models") {
                row {
                    cell(JBScrollPane(customModelsTable).apply {
                        preferredSize = Dimension(500, 100)
                    }).align(Align.FILL).resizableColumn()
                }.resizableRow()
                row {
                    button("Add") { showCustomModelDialog(null, null) { d, m ->
                        customModelsTableModel?.addRow(arrayOf(d, m))
                        refreshModelCombo()
                    }}
                    val editButton = button("Edit") {
                        val row = customModelsTable!!.selectedRow
                        if (row >= 0) {
                            val name = customModelsTableModel?.getValueAt(row, 0) as? String ?: ""
                            val id = customModelsTableModel?.getValueAt(row, 1) as? String ?: ""
                            showCustomModelDialog(name, id) { d, m ->
                                customModelsTableModel?.setValueAt(d, row, 0)
                                customModelsTableModel?.setValueAt(m, row, 1)
                                refreshModelCombo()
                            }
                        }
                    }
                    val removeButton = button("Remove") {
                        val row = customModelsTable!!.selectedRow
                        if (row >= 0) {
                            customModelsTableModel?.removeRow(row)
                            refreshModelCombo()
                        }
                    }
                    // 初始禁用按钮，通过选择监听器更新
                    editButton.component.isEnabled = false
                    removeButton.component.isEnabled = false
                    customModelsTable!!.selectionModel.addListSelectionListener {
                        val hasSelection = customModelsTable!!.selectedRow >= 0
                        editButton.component.isEnabled = hasSelection
                        removeButton.component.isEnabled = hasSelection
                    }
                }
            }

            group("Thinking Configuration") {
                row("Default thinking:") { cell(defaultThinkingLevelCombo!!).columns(COLUMNS_MEDIUM) }
                row {
                    label("Think tokens:")
                    cell(thinkTokensSpinner!!)
                    label("Ultra tokens:").gap(RightGap.SMALL)
                    cell(ultraTokensSpinner!!)
                }
            }
        }
    }

    private fun createAgentsPanel(): JComponent {
        // 初始化 Agent 组件
        exploreEnabledCheckbox = JBCheckBox("Enable")
        exploreModelCombo = ComboBox(arrayOf("(inherit)", "opus", "sonnet", "haiku"))
        exploreDescriptionArea = JBTextArea(2, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true; wrapStyleWord = true
        }
        explorePromptArea = JBTextArea(6, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true; wrapStyleWord = true
        }
        exploreSelectionHintArea = JBTextArea(3, 60).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true; wrapStyleWord = true
        }
        exploreToolsPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4))

        // 更新依赖状态
        fun updateDependency() {
            val mcpEnabled = AgentSettingsService.getInstance().enableJetBrainsMcp
            exploreEnabledCheckbox?.isEnabled = mcpEnabled
            exploreModelCombo?.isEnabled = mcpEnabled && (exploreEnabledCheckbox?.isSelected == true)
        }
        updateDependency()
        exploreEnabledCheckbox!!.addActionListener { updateDependency() }

        // 工具输入
        val toolCombo = ComboBox(DefaultComboBoxModel(KnownTools.ALL.toTypedArray())).apply {
            isEditable = true
        }

        fun addTool() {
            val name = (toolCombo.editor.item?.toString() ?: "").trim()
            if (name.isNotEmpty() && !exploreToolsList.contains(name)) {
                addToolTag(name)
                toolCombo.editor.item = ""
            }
        }

        return panel {
            row { comment("Configure custom agents that extend Claude's capabilities.") }
            row {
                comment(ClaudeCodePlusBundle.message("agents.settings.notice")).applyToComponent {
                    foreground = JBColor.GRAY
                }
            }

            collapsibleGroup("ExploreWithJetbrains") {
                row {
                    cell(exploreEnabledCheckbox!!)
                    label("Model:").gap(RightGap.SMALL)
                    cell(exploreModelCombo!!).columns(COLUMNS_SHORT)
                }
                row {
                    val mcpEnabled = AgentSettingsService.getInstance().enableJetBrainsMcp
                    if (!mcpEnabled) {
                        comment("Requires JetBrains MCP to be enabled").applyToComponent {
                            foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                        }
                    }
                }

                separator().topGap(TopGap.SMALL)

                row("Description:") {}
                row {
                    scrollCell(exploreDescriptionArea!!).align(Align.FILL).resizableColumn()
                }

                row("System Prompt:") {}
                row {
                    scrollCell(explorePromptArea!!).align(Align.FILL).resizableColumn()
                }.resizableRow()

                row("Appended System Prompt:") {}
                row { comment("Appended to CLI's system prompt. Tells AI when/how to use this agent.") }
                row {
                    scrollCell(exploreSelectionHintArea!!).align(Align.FILL).resizableColumn()
                }

                separator().topGap(TopGap.SMALL)

                row("Allowed Tools:") {}
                row {
                    cell(toolCombo).columns(COLUMNS_MEDIUM)
                    button("+") { addTool() }
                    comment("Select or type, then click +")
                }
                row {
                    cell(JBScrollPane(exploreToolsPanel).apply {
                        preferredSize = Dimension(600, 80)
                        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                    }).align(AlignX.FILL)
                }

                separator().topGap(TopGap.SMALL)

                row {
                    button("Reset to Default") {
                        exploreDescriptionArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.description
                        explorePromptArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt
                        exploreSelectionHintArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint
                        setTools(AgentDefaults.EXPLORE_WITH_JETBRAINS.tools)
                        exploreModelCombo?.selectedItem = "(inherit)"
                    }
                }
            }.apply { expanded = true }
        }
    }

    private fun addToolTag(toolName: String) {
        if (exploreToolsList.contains(toolName)) return
        exploreToolsList.add(toolName)

        val tagPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBUI.CurrentTheme.ActionButton.hoverBorder(), 1, true),
                JBUI.Borders.empty(2, 6, 2, 4)
            )
            background = JBUI.CurrentTheme.ActionButton.hoverBackground()
            isOpaque = true
        }
        val label = JBLabel(toolName).apply { font = font.deriveFont(11f) }
        val removeBtn = JBLabel("×").apply {
            font = font.deriveFont(Font.BOLD, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        removeBtn.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                exploreToolsList.remove(toolName)
                exploreToolsPanel?.remove(tagPanel)
                exploreToolsPanel?.revalidate()
                exploreToolsPanel?.repaint()
            }
        })
        tagPanel.add(label)
        tagPanel.add(removeBtn)
        exploreToolsPanel?.add(tagPanel)
        exploreToolsPanel?.revalidate()
        exploreToolsPanel?.repaint()
    }

    private fun setTools(tools: List<String>) {
        exploreToolsList.clear()
        exploreToolsPanel?.removeAll()
        tools.forEach { addToolTag(it) }
    }

    private fun getTools(): List<String> = exploreToolsList.toList()

    private fun refreshModelCombo() {
        val current = defaultModelCombo?.selectedItem as? ModelInfo
        val settings = AgentSettingsService.getInstance()
        val builtIn = settings.getAllAvailableModels().filter { it.isBuiltIn }
        val custom = getCustomModelsFromTable()
        val all = builtIn + custom
        defaultModelCombo?.model = DefaultComboBoxModel(all.toTypedArray())
        current?.let { c -> all.find { it.modelId == c.modelId }?.let { defaultModelCombo?.selectedItem = it } }
    }

    private fun getCustomModelsFromTable(): List<ModelInfo> {
        val model = customModelsTableModel ?: return emptyList()
        return (0 until model.rowCount).mapNotNull { i ->
            val name = model.getValueAt(i, 0) as? String ?: return@mapNotNull null
            val id = model.getValueAt(i, 1) as? String ?: return@mapNotNull null
            ModelInfo(name, id, false)
        }
    }

    private fun showCustomModelDialog(currentName: String?, currentId: String?, onSave: (String, String) -> Unit) {
        val nameField = JBTextField(currentName ?: "", 20)
        val idField = JBTextField(currentId ?: "", 30)

        val dialogPanel = panel {
            row("Display Name:") { cell(nameField).align(AlignX.FILL) }
            row("Model ID:") { cell(idField).align(AlignX.FILL) }
            row { comment("Model ID examples: claude-sonnet-4-6") }
        }

        val result = JOptionPane.showConfirmDialog(mainPanel, dialogPanel,
            if (currentName != null) "Edit Custom Model" else "Add Custom Model",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)

        if (result == JOptionPane.OK_OPTION) {
            val name = nameField.text.trim()
            val id = idField.text.trim()
            if (name.isNotEmpty() && id.isNotEmpty()) {
                onSave(name, id)
            }
        }
    }

    private fun updateThinkingLevelCombo() {
        val current = (defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig)?.id
        val levels = buildAllThinkingLevels()
        defaultThinkingLevelCombo?.model = DefaultComboBoxModel(levels.toTypedArray())
        defaultThinkingLevelCombo?.selectedItem = levels.find { it.id == current } ?: levels.find { it.id == "ultra" }
    }

    private fun buildAllThinkingLevels() = listOf(
        ThinkingLevelConfig("off", "Off", 0, false),
        ThinkingLevelConfig("think", "Think", thinkTokensSpinner?.value as? Int ?: 2048, false),
        ThinkingLevelConfig("ultra", "Ultra", ultraTokensSpinner?.value as? Int ?: 8096, false)
    )

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        val selectedModel = defaultModelCombo?.selectedItem as? ModelInfo
        val tableModels = getCustomModelsFromTable().map { it.displayName to it.modelId }
        val savedModels = settings.getCustomModels().map { it.displayName to it.modelId }

        val generalModified = nodePathField?.text != settings.nodePath ||
            selectedModel?.modelId != settings.defaultModel ||
            tableModels != savedModels ||
            (defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig)?.id != settings.defaultThinkingLevelId ||
            (thinkTokensSpinner?.value as? Int ?: 2048) != settings.thinkTokens ||
            (ultraTokensSpinner?.value as? Int ?: 8096) != settings.ultraTokens ||
            permissionModeCombo?.selectedItem != settings.permissionMode ||
            defaultBypassPermissionsCheckbox?.isSelected != settings.defaultBypassPermissions ||
            defaultAutoCleanupContextsCheckbox?.isSelected != settings.claudeDefaultAutoCleanupContexts

        val config = parseAgentsConfig(settings.customAgents)
        val explore = config.agents["ExploreWithJetbrains"]
        val effectiveEnabled = explore?.enabled ?: true
        val effectiveModel = explore?.model?.ifBlank { "(inherit)" } ?: "(inherit)"
        val effectiveDesc = explore?.description?.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.description }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.description
        val effectivePrompt = explore?.prompt?.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt
        val effectiveHint = explore?.selectionHint?.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint }
            ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint
        val effectiveTools = explore?.tools?.takeIf { it.isNotEmpty() } ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.tools

        val agentsModified = exploreEnabledCheckbox?.isSelected != effectiveEnabled ||
            exploreModelCombo?.selectedItem != effectiveModel ||
            exploreDescriptionArea?.text != effectiveDesc ||
            explorePromptArea?.text != effectivePrompt ||
            exploreSelectionHintArea?.text != effectiveHint ||
            getTools() != effectiveTools

        val authModeModified = getSelectedAuthMode() != settings.claudeAuthMode
        val apiKeyModified = String(apiKeyField?.password ?: charArrayOf()) != settings.claudeApiKey
        val baseUrlModified = (baseUrlField?.text ?: "") != settings.claudeBaseUrl

        return generalModified || agentsModified || authModeModified || apiKeyModified || baseUrlModified
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        settings.nodePath = nodePathField?.text ?: ""

        val selectedModel = defaultModelCombo?.selectedItem as? ModelInfo
        settings.defaultModel = selectedModel?.modelId ?: settings.getAllAvailableModels().firstOrNull { it.isBuiltIn }?.modelId
            ?: "claude-opus-4-6"

        val existing = settings.getCustomModels().associateBy { it.modelId }
        val custom = getCustomModelsFromTable().map { m ->
            val id = existing[m.modelId]?.id ?: "custom_${System.currentTimeMillis()}_${m.modelId.hashCode().toUInt()}"
            CustomModelConfig(id, m.displayName, m.modelId)
        }
        settings.setCustomModels(custom)

        settings.defaultThinkingLevelId = (defaultThinkingLevelCombo?.selectedItem as? ThinkingLevelConfig)?.id ?: "ultra"
        settings.thinkTokens = thinkTokensSpinner?.value as? Int ?: 2048
        settings.ultraTokens = ultraTokensSpinner?.value as? Int ?: 8096
        settings.permissionMode = permissionModeCombo?.selectedItem as? String ?: "default"
        settings.includePartialMessages = true
        settings.defaultBypassPermissions = defaultBypassPermissionsCheckbox?.isSelected ?: false
        settings.claudeDefaultAutoCleanupContexts = defaultAutoCleanupContextsCheckbox?.isSelected ?: false

        val agentModel = exploreModelCombo?.selectedItem as? String ?: "(inherit)"
        val exploreConfig = AgentConfigItem(
            enabled = exploreEnabledCheckbox?.isSelected ?: true,
            description = exploreDescriptionArea?.text ?: "",
            prompt = explorePromptArea?.text ?: "",
            tools = getTools(),
            model = if (agentModel == "(inherit)") "" else agentModel,
            selectionHint = exploreSelectionHintArea?.text ?: ""
        )
        settings.customAgents = json.encodeToString(AgentsConfigData(mapOf("ExploreWithJetbrains" to exploreConfig)))

        settings.claudeAuthMode = getSelectedAuthMode()
        settings.claudeApiKey = String(apiKeyField?.password ?: charArrayOf())
        settings.claudeBaseUrl = baseUrlField?.text?.trim() ?: ""

        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()
        // Restore auth mode
        when (settings.claudeAuthMode) {
            "api_key" -> authModeApiKeyRadio?.isSelected = true
            else -> authModeOAuthRadio?.isSelected = true
        }
        apiKeyField?.text = settings.claudeApiKey
        baseUrlField?.text = settings.claudeBaseUrl
        updateApiKeySourceLabel()
        updateBaseUrlSourceLabel()
        refreshOAuthStatus()
        // Update panel visibility after restoring auth mode
        val isApiKey = authModeApiKeyRadio?.isSelected == true
        apiKeyPanel?.isVisible = isApiKey
        // OAuth panel visibility: find sibling - oauthStatusLabel's grandparent
        oauthStatusLabel?.parent?.parent?.isVisible = !isApiKey

        nodePathField?.text = settings.nodePath

        customModelsTableModel?.rowCount = 0
        settings.getCustomModels().forEach { customModelsTableModel?.addRow(arrayOf(it.displayName, it.modelId)) }

        refreshModelCombo()
        val saved = settings.defaultModel
        val all = settings.getAllAvailableModels()
        defaultModelCombo?.selectedItem = all.find { it.modelId == saved } ?: all.firstOrNull { it.isBuiltIn }

        thinkTokensSpinner?.value = settings.thinkTokens
        ultraTokensSpinner?.value = settings.ultraTokens
        updateThinkingLevelCombo()
        defaultThinkingLevelCombo?.selectedItem = buildAllThinkingLevels().find { it.id == settings.defaultThinkingLevelId }
            ?: buildAllThinkingLevels().find { it.id == "ultra" }
        permissionModeCombo?.selectedItem = settings.permissionMode
        includePartialMessagesCheckbox?.isSelected = true
        defaultBypassPermissionsCheckbox?.isSelected = settings.defaultBypassPermissions
        defaultAutoCleanupContextsCheckbox?.isSelected = settings.claudeDefaultAutoCleanupContexts

        val config = parseAgentsConfig(settings.customAgents)
        val explore = config.agents["ExploreWithJetbrains"]
        if (explore != null && (explore.description.isNotBlank() || explore.prompt.isNotBlank())) {
            exploreEnabledCheckbox?.isSelected = explore.enabled
            exploreModelCombo?.selectedItem = explore.model.ifBlank { "(inherit)" }
            exploreDescriptionArea?.text = explore.description.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.description }
            explorePromptArea?.text = explore.prompt.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt }
            exploreSelectionHintArea?.text = explore.selectionHint.ifBlank { AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint }
            setTools(explore.tools.takeIf { it.isNotEmpty() } ?: AgentDefaults.EXPLORE_WITH_JETBRAINS.tools)
        } else {
            exploreEnabledCheckbox?.isSelected = true
            exploreModelCombo?.selectedItem = "(inherit)"
            exploreDescriptionArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.description
            explorePromptArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.prompt
            exploreSelectionHintArea?.text = AgentDefaults.EXPLORE_WITH_JETBRAINS.selectionHint
            setTools(AgentDefaults.EXPLORE_WITH_JETBRAINS.tools)
        }
    }

    private fun parseAgentsConfig(jsonStr: String): AgentsConfigData {
        return try {
            if (jsonStr.isBlank() || jsonStr == "{}") AgentsConfigData()
            else json.decodeFromString<AgentsConfigData>(jsonStr)
        } catch (e: Exception) { AgentsConfigData() }
    }

    private fun updateApiKeySourceLabel() {
        val fieldHasValue = apiKeyField?.password?.isNotEmpty() == true
        val (text, color) = when {
            fieldHasValue -> "Source: Plugin settings" to JBColor.foreground()
            System.getenv("ANTHROPIC_API_KEY")?.isNotBlank() == true ->
                "Source: System environment variable" to JBColor.foreground()
            else -> "Not configured" to JBColor(0xFF6B6B, 0xFF6B6B)
        }
        apiKeySourceLabel?.text = text
        apiKeySourceLabel?.foreground = color
    }

    private fun updateBaseUrlSourceLabel() {
        val fieldHasValue = baseUrlField?.text?.isNotBlank() == true
        val (text, color) = when {
            fieldHasValue -> "Source: Plugin settings" to JBColor.foreground()
            System.getenv("ANTHROPIC_BASE_URL")?.isNotBlank() == true ->
                "Source: System environment variable" to JBColor.foreground()
            else -> "Using default" to JBColor.GRAY
        }
        baseUrlSourceLabel?.text = text
        baseUrlSourceLabel?.foreground = color
    }

    private fun testApiConnection() {
        testResultLabel?.text = "Testing..."
        testResultLabel?.foreground = JBColor.BLUE
        testConnectionButton?.isEnabled = false

        // If OAuth mode, verify by running a lightweight CLI command
        if (authModeOAuthRadio?.isSelected == true) {
            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                // First check credential file
                val status = checkOAuthStatus()
                if (status !is OAuthStatus.LoggedIn) {
                    javax.swing.SwingUtilities.invokeLater {
                        when (status) {
                            OAuthStatus.Expired -> {
                                testResultLabel?.text = "OAuth token expired, please re-authenticate"
                                testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                            }
                            else -> {
                                testResultLabel?.text = "Not logged in via OAuth"
                                testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                            }
                        }
                        testConnectionButton?.isEnabled = true
                    }
                    return@executeOnPooledThread
                }

                // Token file exists and not expired, verify with actual API call
                try {
                    val isWindows = System.getProperty("os.name").lowercase().contains("win")
                    val command = if (isWindows) {
                        listOf("cmd", "/c", "claude", "--version")
                    } else {
                        listOf("claude", "--version")
                    }
                    val process = ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    val finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
                    val exitCode = if (finished) process.exitValue() else -1

                    javax.swing.SwingUtilities.invokeLater {
                        if (finished && exitCode == 0) {
                            val version = output.trim().take(50)
                            testResultLabel?.text = "OAuth token valid (CLI: $version)"
                            testResultLabel?.foreground = JBColor(0x59A869, 0x59A869)
                        } else {
                            testResultLabel?.text = "OAuth token exists but CLI verification failed"
                            testResultLabel?.foreground = JBColor(0xE5C07B, 0xE5C07B)
                        }
                        testConnectionButton?.isEnabled = true
                    }
                    if (!finished) process.destroyForcibly()
                } catch (e: Exception) {
                    javax.swing.SwingUtilities.invokeLater {
                        val expiresDate = java.text.SimpleDateFormat("yyyy-MM-dd").format(
                            java.util.Date((status as OAuthStatus.LoggedIn).expiresAt)
                        )
                        testResultLabel?.text = "OAuth token valid (expires: $expiresDate), CLI not found for verification"
                        testResultLabel?.foreground = JBColor(0xE5C07B, 0xE5C07B)
                        testConnectionButton?.isEnabled = true
                    }
                }
            }
            return
        }

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val apiKey = String(apiKeyField?.password ?: charArrayOf()).ifBlank {
                    System.getenv("ANTHROPIC_API_KEY") ?: ""
                }
                if (apiKey.isBlank()) {
                    javax.swing.SwingUtilities.invokeLater {
                        testResultLabel?.text = "No API key configured"
                        testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                        testConnectionButton?.isEnabled = true
                    }
                    return@executeOnPooledThread
                }

                val baseUrl = baseUrlField?.text?.trim()?.ifBlank { null }
                    ?: System.getenv("ANTHROPIC_BASE_URL")
                    ?: "https://api.anthropic.com"

                val url = java.net.URL("${baseUrl.trimEnd('/')}/v1/models")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("x-api-key", apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                javax.swing.SwingUtilities.invokeLater {
                    when {
                        responseCode == 200 -> {
                            testResultLabel?.text = "Connection successful"
                            testResultLabel?.foreground = JBColor(0x59A869, 0x59A869)
                        }
                        responseCode == 401 -> {
                            testResultLabel?.text = "Invalid API key (401)"
                            testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                        }
                        responseCode == 403 -> {
                            testResultLabel?.text = "Access denied (403)"
                            testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                        }
                        else -> {
                            testResultLabel?.text = "Unexpected response: $responseCode"
                            testResultLabel?.foreground = JBColor(0xE5C07B, 0xE5C07B)
                        }
                    }
                    testConnectionButton?.isEnabled = true
                }
                conn.disconnect()
            } catch (e: Exception) {
                javax.swing.SwingUtilities.invokeLater {
                    testResultLabel?.text = "Connection failed: ${e.message}"
                    testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                    testConnectionButton?.isEnabled = true
                }
            }
        }
    }

    private fun getSelectedAuthMode(): String {
        return if (authModeApiKeyRadio?.isSelected == true) "api_key" else "oauth"
    }

    private fun refreshOAuthStatus() {
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            val status = checkOAuthStatus()
            javax.swing.SwingUtilities.invokeLater {
                val (text, color) = when (status) {
                    is OAuthStatus.LoggedIn -> {
                        val expiresDate = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(status.expiresAt))
                        "Logged in (token expires: $expiresDate)" to JBColor(0x59A869, 0x59A869)
                    }
                    OAuthStatus.Expired -> "Token expired, please re-authenticate" to JBColor(0xFF6B6B, 0xFF6B6B)
                    OAuthStatus.NotLoggedIn -> "Not logged in" to JBColor(0xFF6B6B, 0xFF6B6B)
                }
                oauthStatusLabel?.text = text
                oauthStatusLabel?.foreground = color
            }
        }
    }

    private fun checkOAuthStatus(): OAuthStatus {
        try {
            val credFile = java.nio.file.Path.of(System.getProperty("user.home"), ".claude", ".credentials.json")
            if (!java.nio.file.Files.exists(credFile)) return OAuthStatus.NotLoggedIn
            val content = java.nio.file.Files.readString(credFile)
            val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(content)
            val oauth = jsonElement.jsonObject["claudeAiOauth"]?.jsonObject ?: return OAuthStatus.NotLoggedIn
            val expiresAt = oauth["expiresAt"]?.jsonPrimitive?.longOrNull ?: return OAuthStatus.NotLoggedIn
            return if (expiresAt > System.currentTimeMillis()) {
                OAuthStatus.LoggedIn(expiresAt)
            } else {
                OAuthStatus.Expired
            }
        } catch (_: Exception) {
            return OAuthStatus.NotLoggedIn
        }
    }



    private sealed class OAuthStatus {
        data class LoggedIn(val expiresAt: Long) : OAuthStatus()
        data object Expired : OAuthStatus()
        data object NotLoggedIn : OAuthStatus()
    }

    override fun disposeUIResources() {
        authModeOAuthRadio = null
        authModeApiKeyRadio = null
        oauthStatusLabel = null
        apiKeyPanel = null
        apiKeyField = null
        baseUrlField = null
        apiKeySourceLabel = null
        baseUrlSourceLabel = null
        testConnectionButton = null
        testResultLabel = null
        nodePathField = null
        defaultModelCombo = null
        defaultThinkingLevelCombo = null
        thinkTokensSpinner = null
        ultraTokensSpinner = null
        permissionModeCombo = null
        includePartialMessagesCheckbox = null
        defaultBypassPermissionsCheckbox = null
        defaultAutoCleanupContextsCheckbox = null
        customModelsTable = null
        customModelsTableModel = null
        exploreEnabledCheckbox = null
        exploreModelCombo = null
        exploreDescriptionArea = null
        explorePromptArea = null
        exploreSelectionHintArea = null
        exploreToolsPanel = null
        exploreToolsList.clear()
        mainPanel = null
    }
}
