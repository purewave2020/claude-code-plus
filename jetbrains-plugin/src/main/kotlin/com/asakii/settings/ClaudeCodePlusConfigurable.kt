package com.asakii.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.panel
import java.awt.Font
import javax.swing.*

/**
 * Claude Code Plus 主配置页
 *
 * 作为父配置页，包含两个子页面（在 plugin.xml 中注册）：
 * - MCP: MCP 服务器配置（内置 + 自定义）
 * - Claude Code: 通用配置（Node.js、模型、思考级别、权限、Agents）
 */
class ClaudeCodePlusConfigurable : SearchableConfigurable {

    private data class BackendOption(val key: String, val label: String) {
        override fun toString(): String = label
    }

    private val backendOptions = listOf(
        BackendOption(AgentSettingsService.MCP_BACKEND_CLAUDE, "Claude Code"),
        BackendOption(AgentSettingsService.MCP_BACKEND_CODEX, "Codex")
    )

    private var mainPanel: JPanel? = null
    private var defaultBackendCombo: ComboBox<BackendOption>? = null

    override fun getId(): String = "com.asakii.settings"

    override fun getDisplayName(): String = "Claude Code Plus"

    override fun createComponent(): JComponent {
        defaultBackendCombo = ComboBox(DefaultComboBoxModel(backendOptions.toTypedArray()))

        mainPanel = panel {
            row {
                label("Claude Code Plus").applyToComponent {
                    font = font.deriveFont(Font.BOLD, 18f)
                }
            }

            group("Default Backend") {
                row("Use for new sessions:") {
                    cell(defaultBackendCombo!!)
                }
                row {
                    comment("Applies when initializing or creating new sessions.")
                }
            }

            group("") {
                row {
                    text("""
                        Welcome to Claude Code Plus settings!<br><br>
                        Configure the plugin using the sub-pages:<br>
                        <ul>
                            <li><b>MCP</b> - MCP server configuration (built-in and custom servers)</li>
                            <li><b>Claude Code</b> - Runtime settings, agents, and permissions</li>
                            <li><b>Codex</b> - Codex CLI path and runtime configuration</li>
                            <li><b>Git Generate</b> - AI-powered commit message generation</li>
                        </ul>
                        <br>
                        Select a sub-page from the left panel to configure specific settings.
                    """.trimIndent())
                }
            }
        }

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val selected = getSelectedBackendKey()
        val settings = AgentSettingsService.getInstance()
        return selected != settings.defaultBackendType
    }

    override fun apply() {
        val settings = AgentSettingsService.getInstance()
        val selected = getSelectedBackendKey()
        if (selected != settings.defaultBackendType) {
            settings.defaultBackendType = selected
            settings.notifyChange()
        }
    }

    override fun reset() {
        selectBackend(AgentSettingsService.getInstance().defaultBackendType)
    }

    override fun disposeUIResources() {
        mainPanel = null
        defaultBackendCombo = null
    }

    private fun getSelectedBackendKey(): String {
        val option = defaultBackendCombo?.selectedItem as? BackendOption
        return option?.key ?: AgentSettingsService.MCP_BACKEND_CLAUDE
    }

    private fun selectBackend(key: String) {
        val normalized = key.trim().lowercase()
        val target = backendOptions.firstOrNull { it.key == normalized } ?: backendOptions.first()
        defaultBackendCombo?.selectedItem = target
    }
}
