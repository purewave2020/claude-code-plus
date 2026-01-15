package com.asakii.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.DefaultListCellRenderer

/**
 * Git Generate 独立配置页面
 *
 * 在 Settings > Tools > Claude Code Plus > Git Generate 下显示
 * 用于配置 Git commit message 自动生成功能
 */
class GitGenerateConfigurable : SearchableConfigurable {

    private var mainPanel: JPanel? = null

    // UI 组件
    private var systemPromptArea: JBTextArea? = null
    private var userPromptArea: JBTextArea? = null
    private var enableCheckbox: JCheckBox? = null
    private var backendCombo: ComboBox<BackendOption>? = null
    private var saveSessionCheckbox: JCheckBox? = null
    private var modelCombo: ComboBox<ModelInfo>? = null
    private var claudeThinkingCombo: ComboBox<ThinkingLevelConfig>? = null
    private var codexReasoningEffortCombo: ComboBox<String>? = null
    private var thinkingCardLayout: CardLayout? = null
    private var thinkingPanel: JPanel? = null

    private data class BackendOption(val id: String, val label: String)

    override fun getId(): String = "claude-code-plus.git-generate"

    override fun getDisplayName(): String = "Git Generate"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout())

        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = JBUI.Borders.empty(8, 10, 8, 10)

        // 标题说明
        contentPanel.add(createDescription("Configure AI-powered Git commit message generation."))
        contentPanel.add(Box.createVerticalStrut(8))

        enableCheckbox = JCheckBox("Enable Git Generate").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            toolTipText = "Show Git Generate in the commit message toolbar"
        }
        contentPanel.add(enableCheckbox)
        contentPanel.add(Box.createVerticalStrut(8))

        contentPanel.add(createDescription("Git Generate uses built-in Git MCP and default permissions automatically."))
        contentPanel.add(Box.createVerticalStrut(16))

        val settings = AgentSettingsService.getInstance()

        // Backend 选择器
        val backendPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        backendPanel.add(JBLabel("Backend: "))
        val backendOptions = arrayOf(
            BackendOption(AgentSettingsService.MCP_BACKEND_CLAUDE, "Claude"),
            BackendOption(AgentSettingsService.MCP_BACKEND_CODEX, "Codex")
        )
        backendCombo = ComboBox(DefaultComboBoxModel(backendOptions)).apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is BackendOption) {
                        text = value.label
                    }
                    return component
                }
            }
            preferredSize = Dimension(200, preferredSize.height)
            toolTipText = "Select backend for Git Generate"
        }
        backendPanel.add(backendCombo)
        contentPanel.add(backendPanel)
        contentPanel.add(Box.createVerticalStrut(8))

        // Model 选择器
        val modelPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        modelPanel.add(JBLabel("Model: "))
        modelCombo = ComboBox<ModelInfo>().apply {
            renderer = ModelInfoRenderer()
            preferredSize = Dimension(240, preferredSize.height)
            toolTipText = "Select the model to use for commit message generation"
        }
        modelPanel.add(modelCombo)
        contentPanel.add(modelPanel)
        contentPanel.add(Box.createVerticalStrut(8))

        // Thinking 设置
        contentPanel.add(createSectionTitle("Thinking"))
        contentPanel.add(createDescription("Configure thinking level for the selected backend."))
        contentPanel.add(Box.createVerticalStrut(4))
        thinkingPanel = JPanel().apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            thinkingCardLayout = CardLayout()
            layout = thinkingCardLayout
        }

        val claudeThinkingPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        claudeThinkingPanel.add(JBLabel("Claude thinking level: "))
        claudeThinkingCombo = ComboBox<ThinkingLevelConfig>().apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is ThinkingLevelConfig) {
                        text = value.name
                    }
                    return component
                }
            }
            preferredSize = Dimension(200, preferredSize.height)
            toolTipText = "Select thinking level for Claude"
        }
        claudeThinkingCombo?.model = DefaultComboBoxModel(settings.getAllThinkingLevels().toTypedArray())
        claudeThinkingPanel.add(claudeThinkingCombo)

        val codexThinkingPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        codexThinkingPanel.add(JBLabel("Codex reasoning effort: "))
        codexReasoningEffortCombo = ComboBox(
            DefaultComboBoxModel(arrayOf("minimal", "low", "medium", "high", "xhigh"))
        ).apply {
            preferredSize = Dimension(200, preferredSize.height)
            toolTipText = "Select reasoning effort for Codex"
        }
        codexThinkingPanel.add(codexReasoningEffortCombo)

        thinkingPanel!!.add(claudeThinkingPanel, AgentSettingsService.MCP_BACKEND_CLAUDE)
        thinkingPanel!!.add(codexThinkingPanel, AgentSettingsService.MCP_BACKEND_CODEX)
        contentPanel.add(thinkingPanel)
        contentPanel.add(Box.createVerticalStrut(16))

        // Save Session 复选框
        saveSessionCheckbox = JCheckBox("Save session to history").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            toolTipText = "When enabled, the generation session will be saved and visible in Claude Code session history"
        }
        contentPanel.add(saveSessionCheckbox)
        contentPanel.add(Box.createVerticalStrut(16))

        // System Prompt 区域
        contentPanel.add(createSectionTitle("System Prompt"))
        contentPanel.add(createDescription("Instructions for the AI on how to generate commit messages."))
        contentPanel.add(Box.createVerticalStrut(4))

        systemPromptArea = JBTextArea(10, 70).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
        }
        val systemPromptScrollPane = JBScrollPane(systemPromptArea).apply {
            preferredSize = Dimension(700, 200)
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        contentPanel.add(systemPromptScrollPane)
        contentPanel.add(Box.createVerticalStrut(16))

        // User Prompt 区域
        contentPanel.add(createSectionTitle("User Prompt"))
        contentPanel.add(createDescription("Runtime prompt sent with the code changes. Customize analysis focus here."))
        contentPanel.add(Box.createVerticalStrut(4))

        userPromptArea = JBTextArea(5, 70).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
        }
        val userPromptScrollPane = JBScrollPane(userPromptArea).apply {
            preferredSize = Dimension(700, 100)
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        contentPanel.add(userPromptScrollPane)
        contentPanel.add(Box.createVerticalStrut(16))

        // 按钮行
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }

        val resetButton = JButton("Reset to Default").apply {
            toolTipText = "Reset all fields to their default values"
        }
        resetButton.addActionListener {
            val defaults = AgentSettingsService.getInstance()
            systemPromptArea?.text = GitGenerateDefaults.SYSTEM_PROMPT
            userPromptArea?.text = GitGenerateDefaults.USER_PROMPT
            enableCheckbox?.isSelected = false
            selectBackendOption(AgentSettingsService.MCP_BACKEND_CLAUDE)
            refreshModelCombo(AgentSettingsService.MCP_BACKEND_CLAUDE, null, useSettingsFallback = false)
            claudeThinkingCombo?.selectedItem = defaults.getThinkingLevelById("ultra")
            codexReasoningEffortCombo?.selectedItem = "xhigh"
            saveSessionCheckbox?.isSelected = false
        }
        buttonPanel.add(resetButton)

        contentPanel.add(buttonPanel)
        contentPanel.add(Box.createVerticalGlue())

        // 包装滚动面板
        val scrollPane = JBScrollPane(contentPanel).apply {
            border = null
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        mainPanel!!.add(scrollPane, BorderLayout.CENTER)

        backendCombo?.addActionListener { updateBackendSpecificUi() }

        reset()
        return mainPanel!!
    }

    private fun getSelectedBackendId(): String {
        return (backendCombo?.selectedItem as? BackendOption)?.id ?: AgentSettingsService.MCP_BACKEND_CLAUDE
    }

    private fun updateBackendSpecificUi(preferredModelId: String? = null) {
        val backendId = getSelectedBackendId()
        refreshModelCombo(backendId, preferredModelId, useSettingsFallback = true)
        thinkingPanel?.let { panel -> thinkingCardLayout?.show(panel, backendId) }
    }

    private fun refreshModelCombo(backendId: String, preferredModelId: String?, useSettingsFallback: Boolean) {
        val settings = AgentSettingsService.getInstance()
        val models = if (backendId == AgentSettingsService.MCP_BACKEND_CODEX) {
            settings.getAllCodexModels()
        } else {
            settings.getAllAvailableModels()
        }
        modelCombo?.model = DefaultComboBoxModel(models.toTypedArray())
        val selectedId = preferredModelId?.takeIf { it.isNotBlank() }
            ?: if (useSettingsFallback) settings.gitGenerateModel else ""
        val selectedModel = models.firstOrNull { it.modelId == selectedId }
            ?: models.firstOrNull { it.isBuiltIn }
            ?: models.firstOrNull()
        modelCombo?.selectedItem = selectedModel
    }

    private fun selectBackendOption(backendId: String) {
        val model = backendCombo?.model as? DefaultComboBoxModel<BackendOption> ?: return
        val option = (0 until model.size)
            .map { model.getElementAt(it) }
            .firstOrNull { it.id == backendId }
            ?: model.getElementAt(0)
        backendCombo?.selectedItem = option
    }

    private fun createSectionTitle(text: String): JComponent {
        return JBLabel("<html><b>$text</b></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            font = font.deriveFont(13f)
        }
    }

    private fun createDescription(text: String): JComponent {
        return JBLabel("<html><font color='#6B7280'>$text</font></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
    }

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()

        val effectiveSystemPrompt = settings.effectiveGitGenerateSystemPrompt
        val effectiveUserPrompt = settings.effectiveGitGenerateUserPrompt
        val currentBackendId = getSelectedBackendId()
        val selectedModel = modelCombo?.selectedItem as? ModelInfo
        val currentModelId = selectedModel?.modelId ?: ""
        val currentClaudeThinkingId = (claudeThinkingCombo?.selectedItem as? ThinkingLevelConfig)?.id ?: ""
        val currentCodexEffort = codexReasoningEffortCombo?.selectedItem as? String ?: ""

        return enableCheckbox?.isSelected != settings.gitGenerateEnabled ||
            currentBackendId != settings.gitGenerateBackend ||
            systemPromptArea?.text != effectiveSystemPrompt ||
            userPromptArea?.text != effectiveUserPrompt ||
            currentModelId != settings.gitGenerateModel ||
            currentClaudeThinkingId != settings.gitGenerateClaudeThinkingLevelId ||
            currentCodexEffort != settings.gitGenerateCodexReasoningEffort ||
            saveSessionCheckbox?.isSelected != settings.gitGenerateSaveSession
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()

        // 如果内容与默认值相同，存储空字符串
        val currentSystemPrompt = systemPromptArea?.text ?: ""
        val currentUserPrompt = userPromptArea?.text ?: ""

        settings.gitGenerateSystemPrompt = if (currentSystemPrompt.trim() == GitGenerateDefaults.SYSTEM_PROMPT.trim()) "" else currentSystemPrompt
        settings.gitGenerateUserPrompt = if (currentUserPrompt.trim() == GitGenerateDefaults.USER_PROMPT.trim()) "" else currentUserPrompt

        // 保存模型设置
        val selectedModel = modelCombo?.selectedItem as? ModelInfo
        settings.gitGenerateModel = selectedModel?.modelId ?: ""

        settings.gitGenerateEnabled = enableCheckbox?.isSelected ?: false
        settings.gitGenerateBackend = getSelectedBackendId()
        settings.gitGenerateClaudeThinkingLevelId =
            (claudeThinkingCombo?.selectedItem as? ThinkingLevelConfig)?.id ?: "ultra"
        settings.gitGenerateCodexReasoningEffort =
            codexReasoningEffortCombo?.selectedItem as? String ?: settings.gitGenerateCodexReasoningEffort

        // 保存 Save Session 设置
        settings.gitGenerateSaveSession = saveSessionCheckbox?.isSelected ?: false

        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()

        systemPromptArea?.text = settings.gitGenerateSystemPrompt.ifBlank { GitGenerateDefaults.SYSTEM_PROMPT }
        userPromptArea?.text = settings.gitGenerateUserPrompt.ifBlank { GitGenerateDefaults.USER_PROMPT }
        enableCheckbox?.isSelected = settings.gitGenerateEnabled
        saveSessionCheckbox?.isSelected = settings.gitGenerateSaveSession

        selectBackendOption(settings.gitGenerateBackend)
        updateBackendSpecificUi(preferredModelId = settings.gitGenerateModel)
        claudeThinkingCombo?.selectedItem =
            settings.getThinkingLevelById(settings.effectiveGitGenerateClaudeThinkingLevelId)
        codexReasoningEffortCombo?.selectedItem = settings.effectiveGitGenerateCodexReasoningEffort
    }

    override fun disposeUIResources() {
        systemPromptArea = null
        userPromptArea = null
        enableCheckbox = null
        backendCombo = null
        saveSessionCheckbox = null
        modelCombo = null
        claudeThinkingCombo = null
        codexReasoningEffortCombo = null
        thinkingCardLayout = null
        thinkingPanel = null
        mainPanel = null
    }
}
