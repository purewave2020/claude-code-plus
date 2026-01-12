package com.asakii.plugin.tools

import com.asakii.rpc.api.FontData
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*
import java.io.File

/**
 * 字体处理辅助类
 * 负责查找和加载 IDEA/JBR 内置字体
 */
class FontHelper {

    private val logger = Logger.getInstance(FontHelper::class.java.name)

    /**
     * IDEA/JBR 内置字体名称到文件名的映射表
     * 只包含 IDEA 内置字体，系统字体让浏览器自己找
     */
    private val fontNameMapping = mapOf(
        // JetBrains 字体
        "jetbrains mono" to "JetBrainsMono-Regular",
        "jetbrainsmono" to "JetBrainsMono-Regular",
        "fira code" to "FiraCode-Regular",
        "firacode" to "FiraCode-Regular",
        // JBR 内置字体
        "droid sans" to "DroidSans",
        "droidsans" to "DroidSans",
        "droid sans mono" to "DroidSansMono",
        "droidsansmono" to "DroidSansMono",
        "droid serif" to "DroidSerif-Regular",
        "droidserif" to "DroidSerif-Regular",
        "inconsolata" to "Inconsolata",
        "inter" to "Inter-Regular",
    )

    /**
     * 获取字体文件数据
     *
     * 从系统字体目录中查找指定字体并返回其二进制数据
     * 支持 TrueType (.ttf) 和 OpenType (.otf) 字体
     */
    fun getFontData(fontFamily: String): FontData? {
        return try {
            // 标准化字体名称（移除空格、转小写）
            val normalizedName = fontFamily.lowercase().replace(" ", "")

            // 查找映射表中的文件名
            val mappedFileName = fontNameMapping[normalizedName]
            logger.info { "🔤 [Font] Looking for: $fontFamily (normalized: $normalizedName, mapped: $mappedFileName)" }

            // 只搜索 IDEA/JBR 内置字体目录（系统字体让浏览器自己找）
            val fontDirs = mutableListOf<File>()

            try {
                val ideaHome = PathManager.getHomePath()
                val jbrFontsDir = File(ideaHome, "jbr/lib/fonts")
                if (jbrFontsDir.exists()) {
                    fontDirs.add(jbrFontsDir)
                    logger.info { "🔤 [Font] JBR fonts dir: ${jbrFontsDir.absolutePath}" }
                } else {
                    logger.warn { "🔤 [Font] JBR fonts dir not found: ${jbrFontsDir.absolutePath}" }
                }
            } catch (e: Exception) {
                logger.warn { "Failed to get IDEA home path: ${e.message}" }
            }

            // 搜索字体文件
            for (fontDir in fontDirs) {
                val fontFile = findFontFile(fontDir, normalizedName, mappedFileName)
                if (fontFile != null) {
                    val extension = fontFile.extension.lowercase()
                    val format = when (extension) {
                        "ttf" -> "truetype"
                        "otf" -> "opentype"
                        "woff" -> "woff"
                        "woff2" -> "woff2"
                        else -> "truetype"
                    }
                    val mimeType = when (extension) {
                        "ttf" -> "font/ttf"
                        "otf" -> "font/otf"
                        "woff" -> "font/woff"
                        "woff2" -> "font/woff2"
                        else -> "font/ttf"
                    }

                    logger.info { "✅ Found font file: ${fontFile.absolutePath}" }
                    return FontData(
                        fontFamily = fontFamily,
                        data = fontFile.readBytes(),
                        format = format,
                        mimeType = mimeType
                    )
                }
            }

            logger.info { "⚠️ Font not found: $fontFamily" }
            null
        } catch (e: Exception) {
            logger.warn { "Failed to get font data: ${e.message}" }
            null
        }
    }

    /**
     * 在目录中递归搜索字体文件
     * @param dir 搜索目录
     * @param normalizedName 标准化的字体名称（小写，无空格）
     * @param mappedFileName 映射表中的文件名（可为空）
     */
    private fun findFontFile(dir: File, normalizedName: String, mappedFileName: String?): File? {
        val fontExtensions = setOf("ttf", "otf", "woff", "woff2")

        // 遍历目录（包括子目录）
        val files = dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in fontExtensions }
            .toList()

        // 1. 首先尝试使用映射的文件名精确匹配
        if (mappedFileName != null) {
            val mappedLower = mappedFileName.lowercase()
            for (file in files) {
                val fileName = file.nameWithoutExtension.lowercase()
                if (fileName == mappedLower || fileName.startsWith(mappedLower)) {
                    return file
                }
            }
        }

        // 2. 尝试标准化名称精确匹配
        for (file in files) {
            val fileName = file.nameWithoutExtension.lowercase().replace(" ", "").replace("-", "").replace("_", "")
            if (fileName == normalizedName ||
                fileName == normalizedName.replace("-", "") ||
                fileName.startsWith(normalizedName)) {
                return file
            }
        }

        // 3. 尝试匹配常见变体
        val variants = listOf(
            normalizedName,
            "${normalizedName}regular",
            "${normalizedName}-regular",
            "${normalizedName}_regular",
            "${normalizedName}medium",
            "${normalizedName}-medium",
        )

        for (file in files) {
            val fileName = file.nameWithoutExtension.lowercase().replace(" ", "").replace("-", "").replace("_", "")
            if (variants.any { fileName.contains(it) }) {
                return file
            }
        }

        return null
    }
}
