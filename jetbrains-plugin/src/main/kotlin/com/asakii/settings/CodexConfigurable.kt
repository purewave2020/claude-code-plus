package com.asakii.settings

import com.asakii.plugin.compat.BrowseButtonCompat
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class CodexConfigurable : SearchableConfigurable {

    private var mainPanel: JPanel? = null
    private var codexPathField: TextFieldWithBrowseButton? = null
    private var webSearchCheckBox: JBCheckBox? = null
    private var defaultModelCombo: ComboBox<ModelInfo>? = null
    private var customModelsTable: JBTable? = null
    private var customModelsTableModel: DefaultTableModel? = null
    private var addModelButton: JButton? = null
    private var editModelButton: JButton? = null
    private var removeModelButton: JButton? = null

    override fun getId(): String = "com.asakii.settings.codex"

    override fun getDisplayName(): String = "Codex"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout())

        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = JBUI.Borders.empty(8, 10, 8, 10)

        contentPanel.add(createSectionTitle("Runtime Settings"))
        contentPanel.add(createDescription("Configure Codex CLI location for the backend."))
        contentPanel.add(Box.createVerticalStrut(8))

        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        codexPathField = TextFieldWithBrowseButton().apply {
            BrowseButtonCompat.addBrowseFolderListener(
                this,
                "Select Codex Executable",
                "Choose the path to codex executable",
                null,
                descriptor
            )
            toolTipText = "Leave empty to auto-detect from system PATH"
            preferredSize = Dimension(450, preferredSize.height)
            (textField as? JBTextField)?.let { tf ->
                tf.emptyText.text = "Detecting Codex..."
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                    val codexInfo = AgentSettingsService.detectCodexInfo()
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        tf.emptyText.text = codexInfo?.let {
                            if (it.version != null) "${it.path} (${it.version})" else it.path
                        } ?: "Auto-detect from system PATH (Codex not found)"
                    }
                }
            }
        }

        contentPanel.add(createLabeledRow("Codex path:", codexPathField!!))
        contentPanel.add(createDescription("  Path to Codex executable. Leave empty to auto-detect from system PATH."))
        contentPanel.add(Box.createVerticalStrut(8))

        webSearchCheckBox = JBCheckBox("Enable web search").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        contentPanel.add(webSearchCheckBox)
        contentPanel.add(createDescription("  Allow Codex to request web searches (features.web_search_request)."))
        contentPanel.add(Box.createVerticalStrut(8))

        contentPanel.add(createSeparator())
        contentPanel.add(createSectionTitle("Model Settings"))
        contentPanel.add(createDescription("Configure default model and custom models for Codex."))

        defaultModelCombo = ComboBox<ModelInfo>().apply {
            renderer = ModelInfoRenderer()
            toolTipText = "Default model for new Codex sessions"
        }
        refreshModelCombo()
        contentPanel.add(createLabeledRow("Default model:", defaultModelCombo!!))
        contentPanel.add(createDescription("  gpt-5.2-codex = Codex optimized | gpt-5.2 = Base model"))
        contentPanel.add(Box.createVerticalStrut(8))

        contentPanel.add(createSectionTitle("Custom Models"))
        contentPanel.add(createDescription("Add custom models with display name and model ID."))
        contentPanel.add(Box.createVerticalStrut(4))

        customModelsTableModel = object : DefaultTableModel(arrayOf("Display Name", "Model ID"), 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        customModelsTable = JBTable(customModelsTableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            tableHeader.reorderingAllowed = false
            columnModel.getColumn(0).preferredWidth = 150
            columnModel.getColumn(1).preferredWidth = 300
        }

        val tableScrollPane = JBScrollPane(customModelsTable).apply {
            preferredSize = Dimension(500, 100)
            minimumSize = Dimension(400, 80)
        }

        addModelButton = JButton("Add").apply {
            toolTipText = "Add a new custom model"
        }
        editModelButton = JButton("Edit").apply {
            toolTipText = "Edit selected custom model"
            isEnabled = false
        }
        removeModelButton = JButton("Remove").apply {
            toolTipText = "Remove selected custom model"
            isEnabled = false
        }

        customModelsTable!!.selectionModel.addListSelectionListener {
            val hasSelection = customModelsTable!!.selectedRow >= 0
            editModelButton?.isEnabled = hasSelection
            removeModelButton?.isEnabled = hasSelection
        }

        addModelButton!!.addActionListener {
            showCustomModelDialog(null, null) { displayName, modelId ->
                customModelsTableModel?.addRow(arrayOf(displayName, modelId))
                refreshModelCombo()
            }
        }
        editModelButton!!.addActionListener {
            val selectedRow = customModelsTable!!.selectedRow
            if (selectedRow >= 0) {
                val currentName = customModelsTableModel?.getValueAt(selectedRow, 0) as? String ?: ""
                val currentModelId = customModelsTableModel?.getValueAt(selectedRow, 1) as? String ?: ""
                showCustomModelDialog(currentName, currentModelId) { displayName, modelId ->
                    customModelsTableModel?.setValueAt(displayName, selectedRow, 0)
                    customModelsTableModel?.setValueAt(modelId, selectedRow, 1)
                    refreshModelCombo()
                }
            }
        }
        removeModelButton!!.addActionListener {
            val selectedRow = customModelsTable!!.selectedRow
            if (selectedRow >= 0) {
                customModelsTableModel?.removeRow(selectedRow)
                refreshModelCombo()
            }
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        buttonPanel.add(addModelButton)
        buttonPanel.add(editModelButton)
        buttonPanel.add(removeModelButton)

        val customModelsPanel = JPanel(BorderLayout(0, 4)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(tableScrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
        contentPanel.add(customModelsPanel)
        contentPanel.add(Box.createVerticalGlue())

        mainPanel!!.add(contentPanel, BorderLayout.NORTH)

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        return codexPathField?.text?.trim() != settings.codexPath ||
            (webSearchCheckBox?.isSelected ?: false) != settings.codexWebSearchEnabled ||
            (defaultModelCombo?.selectedItem as? ModelInfo)?.id != settings.codexDefaultModelId ||
            getCustomModelsFromTable().map { CustomModelConfig(it.id, it.displayName, it.modelId) } != settings.getCodexCustomModels()
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        settings.codexPath = codexPathField?.text?.trim() ?: ""
        settings.codexWebSearchEnabled = webSearchCheckBox?.isSelected ?: false
        val selectedModel = defaultModelCombo?.selectedItem as? ModelInfo
        settings.codexDefaultModelId = selectedModel?.id ?: settings.getCodexBuiltInModels().first().id
        val customModels = getCustomModelsFromTable().map {
            CustomModelConfig(it.id, it.displayName, it.modelId)
        }
        settings.setCodexCustomModels(customModels)
        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()
        codexPathField?.text = settings.codexPath
        webSearchCheckBox?.isSelected = settings.codexWebSearchEnabled
        customModelsTableModel?.rowCount = 0
        settings.getCodexCustomModels().forEach { model ->
            customModelsTableModel?.addRow(arrayOf(model.displayName, model.modelId))
        }
        refreshModelCombo()
        val savedDefaultModelId = settings.codexDefaultModelId
        val allModels = settings.getAllCodexModels()
        val matchingModel = allModels.find { it.id == savedDefaultModelId }
        if (matchingModel != null) {
            defaultModelCombo?.selectedItem = matchingModel
        } else {
            defaultModelCombo?.selectedItem = allModels.firstOrNull { it.isBuiltIn }
        }
    }

    override fun disposeUIResources() {
        codexPathField = null
        webSearchCheckBox = null
        defaultModelCombo = null
        customModelsTable = null
        customModelsTableModel = null
        addModelButton = null
        editModelButton = null
        removeModelButton = null
        mainPanel = null
    }

    private fun createSectionTitle(text: String): JComponent {
        return JBLabel("<html><b>$text</b></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            border = JBUI.Borders.emptyTop(5)
        }
    }

    private fun createDescription(text: String): JComponent {
        return JBLabel("<html><font color='gray' size='-1'>$text</font></html>").apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
    }

    private fun createSeparator(): JComponent {
        return JSeparator().apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 1)
        }
    }

    private fun createLabeledRow(label: String, component: JComponent): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel(label))
            add(component)
        }
    }

    private fun refreshModelCombo() {
        val currentSelection = defaultModelCombo?.selectedItem as? ModelInfo
        val settings = AgentSettingsService.getInstance()
        val allModels = settings.getCodexBuiltInModels() + getCustomModelsFromTable()
        defaultModelCombo?.model = DefaultComboBoxModel(allModels.toTypedArray())

        if (currentSelection != null) {
            val matchingModel = allModels.find { it.id == currentSelection.id }
            if (matchingModel != null) {
                defaultModelCombo?.selectedItem = matchingModel
            }
        }
    }

    private fun getCustomModelsFromTable(): List<ModelInfo> {
        val tableModel = customModelsTableModel ?: return emptyList()
        val result = mutableListOf<ModelInfo>()
        for (i in 0 until tableModel.rowCount) {
            val displayName = tableModel.getValueAt(i, 0) as? String ?: continue
            val modelId = tableModel.getValueAt(i, 1) as? String ?: continue
            if (displayName.isBlank() || modelId.isBlank()) continue
            result.add(ModelInfo(modelId, displayName, modelId, isBuiltIn = false))
        }
        return result
    }

    private fun showCustomModelDialog(
        currentDisplayName: String?,
        currentModelId: String?,
        onSave: (displayName: String, modelId: String) -> Unit
    ) {
        val isEdit = currentDisplayName != null

        val dialogPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0
        gbc.gridy = 0
        dialogPanel.add(JBLabel("Display Name:"), gbc)

        val displayNameField = JBTextField(20).apply {
            text = currentDisplayName ?: ""
            toolTipText = "Name shown in the model selector (e.g., 'My Codex Model')"
        }
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        dialogPanel.add(displayNameField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        dialogPanel.add(JBLabel("Model ID:"), gbc)

        val modelIdField = JBTextField(30).apply {
            text = currentModelId ?: ""
            toolTipText = "Codex model ID (e.g., 'gpt-5.2-codex')"
        }
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        dialogPanel.add(modelIdField, gbc)

        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        dialogPanel.add(JBLabel("<html><font color='gray' size='-1'>Model ID examples: gpt-5.2-codex, gpt-5.2</font></html>"), gbc)

        val result = JOptionPane.showConfirmDialog(
            mainPanel,
            dialogPanel,
            if (isEdit) "Edit Custom Model" else "Add Custom Model",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            val displayName = displayNameField.text.trim()
            val modelId = modelIdField.text.trim()

            if (displayName.isNotEmpty() && modelId.isNotEmpty()) {
                onSave(displayName, modelId)
            } else {
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "Both Display Name and Model ID are required.",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }
}
