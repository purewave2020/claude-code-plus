package com.asakii.plugin.compat

import com.intellij.DynamicBundle
import java.util.Locale

/**
 * 本地化兼容层 - 适用于 2024.1 ~ 2025.2
 *
 * 使用 DynamicBundle.getLocale() 获取 IDEA 的实际语言设置
 * 而不是 JVM 的默认语言
 */
object LocalizationCompat {

    /**
     * 获取当前语言环境
     * @return Locale 对象
     */
    fun getLocale(): Locale {
        return try {
            DynamicBundle.getLocale()
        } catch (e: Exception) {
            // 回退到系统默认语言
            Locale.getDefault()
        }
    }
}
