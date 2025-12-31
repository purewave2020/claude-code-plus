package com.asakii.settings

import com.asakii.plugin.compat.BrowseButtonCompat
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout

class CodexConfigurable : SearchableConfigurable {

    private var mainPanel: JPanel? = null
    private var codexPathField: TextFieldWithBrowseButton? = null
    private var webSearchCheckBox: JBCheckBox? = null

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
        contentPanel.add(Box.createVerticalGlue())

        mainPanel!!.add(contentPanel, BorderLayout.NORTH)

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = AgentSettingsService.getInstance()
        return codexPathField?.text?.trim() != settings.codexPath ||
            (webSearchCheckBox?.isSelected ?: false) != settings.codexWebSearchEnabled
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        settings.codexPath = codexPathField?.text?.trim() ?: ""
        settings.codexWebSearchEnabled = webSearchCheckBox?.isSelected ?: false
        settings.notifyChange()
    }

    override fun reset() {
        val settings = AgentSettingsService.getInstance()
        codexPathField?.text = settings.codexPath
        webSearchCheckBox?.isSelected = settings.codexWebSearchEnabled
    }

    override fun disposeUIResources() {
        codexPathField = null
        webSearchCheckBox = null
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

    private fun createLabeledRow(label: String, component: JComponent): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JBLabel(label))
            add(component)
        }
    }
}
