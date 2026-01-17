package com.asakii.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.CardLayout
import java.awt.Font
import javax.swing.*

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
        initComponents()

        mainPanel = panel {
            row {
                comment("Configure AI-powered Git commit message generation.")
            }
            row {
                cell(enableCheckbox!!)
            }
            row {
                comment("Git Generate uses built-in Git MCP and default permissions automatically.")
            }

            separator()

            row("Backend:") {
                cell(backendCombo!!)
            }
            row("Model:") {
                cell(modelCombo!!)
            }

            group("Thinking") {
                row {
                    cell(thinkingPanel!!)
                }
            }

            row {
                cell(saveSessionCheckbox!!)
            }

            separator()

            group("System Prompt") {
                row {
                    comment("Instructions for the AI on how to generate commit messages.")
                }
                row {
                    scrollCell(systemPromptArea!!)
                        .align(Align.FILL)
                }.resizableRow()
            }

            group("User Prompt") {
                row {
                    comment("Runtime prompt sent with the code changes. Customize analysis focus here.")
                }
                row {
                    scrollCell(userPromptArea!!)
                        .align(Align.FILL)
                }.resizableRow()
            }

            row {
                button("Reset to Default") {
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
            }
        }

        backendCombo?.addActionListener { updateBackendSpecificUi() }

        reset()
        return mainPanel!!
    }

    private fun initComponents() {
        val settings = AgentSettingsService.getInstance()

        enableCheckbox = JCheckBox("Enable Git Generate").apply {
            toolTipText = "Show Git Generate in the commit message toolbar"
        }

        // Backend 选择器
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
            toolTipText = "Select backend for Git Generate"
        }

        // Model 选择器
        modelCombo = ComboBox<ModelInfo>().apply {
            renderer = ModelInfoRenderer()
            toolTipText = "Select the model to use for commit message generation"
        }

        // Thinking 设置 - 使用 CardLayout 切换
        thinkingCardLayout = CardLayout()
        thinkingPanel = JPanel(thinkingCardLayout)

        // Claude thinking panel
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
            toolTipText = "Select thinking level for Claude"
            model = DefaultComboBoxModel(settings.getAllThinkingLevels().toTypedArray())
        }
        val claudePanel = panel {
            row("Claude thinking level:") {
                cell(claudeThinkingCombo!!)
            }
        }

        // Codex reasoning panel
        codexReasoningEffortCombo = ComboBox(
            DefaultComboBoxModel(arrayOf("minimal", "low", "medium", "high", "xhigh"))
        ).apply {
            toolTipText = "Select reasoning effort for Codex"
        }
        val codexPanel = panel {
            row("Codex reasoning effort:") {
                cell(codexReasoningEffortCombo!!)
            }
        }

        thinkingPanel!!.add(claudePanel, AgentSettingsService.MCP_BACKEND_CLAUDE)
        thinkingPanel!!.add(codexPanel, AgentSettingsService.MCP_BACKEND_CODEX)

        // Save Session 复选框
        saveSessionCheckbox = JCheckBox("Save session to history").apply {
            toolTipText = "When enabled, the generation session will be saved and visible in Claude Code session history"
        }

        // System Prompt 区域
        systemPromptArea = JBTextArea(10, 70).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
        }

        // User Prompt 区域
        userPromptArea = JBTextArea(5, 70).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
        }
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
