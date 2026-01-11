package com.asakii.settings

import org.jetbrains.annotations.PropertyKey
import java.util.Locale
import java.util.ResourceBundle

private const val BUNDLE = "messages.McpBundle"

/**
 * MCP 国际化资源包
 *
 * 用于加载 UI 短文本（描述、警告等）
 */
object McpBundle {

    private val bundle: ResourceBundle by lazy {
        ResourceBundle.getBundle(BUNDLE, Locale.getDefault())
    }

    /**
     * 获取本地化消息
     *
     * @param key 资源键
     * @param params 参数
     * @return 本地化字符串
     */
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return try {
            val value = bundle.getString(key)
            if (params.isEmpty()) value else String.format(value, *params)
        } catch (e: Exception) {
            key // fallback to key if not found
        }
    }
}

/**
 * MCP 提示词加载器
 *
 * 用于加载长提示词（.md 文件）
 */
object McpInstructions {

    /**
     * 根据当前语言环境获取语言目录
     */
    private fun getLanguageDir(): String {
        val locale = Locale.getDefault()
        return when (locale.language) {
            "zh" -> "zh_CN"
            "ja" -> "ja"
            "ko" -> "ko"
            else -> "en"
        }
    }

    /**
     * 加载指定 MCP 的提示词
     *
     * @param name MCP 名称（如 "jetbrains-git", "jetbrains-terminal"）
     * @return 提示词内容
     */
    fun load(name: String): String {
        val lang = getLanguageDir()
        val path = "/mcp-instructions/$lang/$name.md"

        return try {
            McpInstructions::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()
                ?: loadFallback(name)
        } catch (e: Exception) {
            loadFallback(name)
        }
    }

    /**
     * 回退到英文版本
     */
    private fun loadFallback(name: String): String {
        val fallbackPath = "/mcp-instructions/en/$name.md"
        return try {
            McpInstructions::class.java.getResourceAsStream(fallbackPath)?.bufferedReader()?.readText()
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // 预定义的 MCP 名称常量
    const val USER_INTERACTION = "user-interaction"
    const val JETBRAINS_LSP = "jetbrains-lsp"
    const val JETBRAINS_FILE = "jetbrains-file"
    const val JETBRAINS_TERMINAL = "jetbrains-terminal"
    const val JETBRAINS_GIT = "jetbrains-git"
    const val CONTEXT7 = "context7"
}
