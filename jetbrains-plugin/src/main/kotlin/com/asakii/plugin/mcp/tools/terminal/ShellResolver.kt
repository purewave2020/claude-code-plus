package com.asakii.plugin.mcp.tools.terminal

import com.asakii.plugin.compat.TerminalCompat
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*

/**
 * Shell 解析器
 *
 * 使用 IDEA 检测到的 shell 列表，通过名称查找对应路径。
 * 不再使用硬编码枚举。
 */
object ShellResolver {
    private val logger = Logger.getInstance("com.asakii.plugin.mcp.tools.terminal.ShellResolver")

    /**
     * 根据 shell 名称获取可执行路径
     *
     * @param shellName shell 名称（如 "git-bash", "powershell", "bash"）
     * @return shell 路径，找不到时返回 null
     */
    fun getShellPath(shellName: String): String? {
        val detectedShells = TerminalCompat.detectInstalledShells()

        // 尝试精确匹配
        val matched = detectedShells.find { shell ->
            normalizeShellName(shell.name).equals(shellName, ignoreCase = true) ||
            shell.name.equals(shellName, ignoreCase = true)
        }

        if (matched != null) {
            logger.info { "Found shell '$shellName' at path: ${matched.path}" }
            return matched.path
        }

        logger.warn { "Shell '$shellName' not found in detected shells: ${detectedShells.map { it.name }}" }
        return null
    }

    /**
     * 获取 shell 命令列表（用于 IDEA Terminal API）
     */
    fun getShellCommand(shellName: String): List<String>? {
        val path = getShellPath(shellName) ?: return null
        return listOf(path)
    }

    /**
     * 标准化 shell 名称
     */
    private fun normalizeShellName(name: String): String {
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("git bash") -> "git-bash"
            lowerName.contains("powershell") -> "powershell"
            lowerName.contains("command prompt") || lowerName == "cmd" -> "cmd"
            lowerName.contains("wsl") || lowerName.contains("ubuntu") || lowerName.contains("debian") -> "wsl"
            lowerName.contains("zsh") -> "zsh"
            lowerName.contains("fish") -> "fish"
            lowerName.contains("bash") -> "bash"
            else -> lowerName.replace(" ", "-")
        }
    }
}
