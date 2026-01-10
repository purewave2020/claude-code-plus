package com.asakii.plugin.util

import com.intellij.openapi.project.Project
import java.io.File

/**
 * 项目路径解析工具
 *
 * 统一处理项目相对路径到绝对路径的转换。
 * 支持 Windows 和 Unix 风格路径。
 *
 * 使用方式：
 * ```kotlin
 * // 方式1：使用 Project 对象
 * val absolutePath = PathResolver.resolve(relativePath, project)
 *
 * // 方式2：使用 basePath 字符串
 * val absolutePath = PathResolver.resolve(relativePath, basePath)
 *
 * // 方式3：扩展函数
 * val absolutePath = relativePath.toAbsolutePath(project)
 * val absolutePath = relativePath.toAbsolutePath(basePath)
 * ```
 */
object PathResolver {

    /**
     * 将路径解析为绝对路径
     *
     * @param path 文件路径（可以是相对路径或绝对路径）
     * @param project IDEA 项目对象
     * @return 规范化的绝对路径
     */
    fun resolve(path: String, project: Project): String {
        val basePath = project.basePath ?: return File(path).canonicalPath
        return resolve(path, basePath)
    }

    /**
     * 将路径解析为绝对路径
     *
     * @param path 文件路径（可以是相对路径或绝对路径）
     * @param basePath 项目根目录路径
     * @return 规范化的绝对路径
     */
    fun resolve(path: String, basePath: String): String {
        val normalizedPath = path.trim()
        return if (isAbsolutePath(normalizedPath)) {
            File(normalizedPath).canonicalPath
        } else {
            File(basePath, normalizedPath).canonicalPath
        }
    }

    /**
     * 判断路径是否为绝对路径
     *
     * 支持：
     * - Unix 绝对路径：以 / 开头
     * - Windows 绝对路径：以盘符开头（如 C:, D:）
     * - Windows UNC 路径：以 \\ 开头
     *
     * @param path 文件路径
     * @return 是否为绝对路径
     */
    fun isAbsolutePath(path: String): Boolean {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return false

        // Unix 绝对路径
        if (trimmed.startsWith("/")) return true

        // Windows 盘符路径 (C:, D:, etc.)
        if (trimmed.length >= 2 && trimmed[1] == ':') return true

        // Windows UNC 路径
        if (trimmed.startsWith("\\\\")) return true

        return false
    }

    /**
     * 判断路径是否为相对路径
     */
    fun isRelativePath(path: String): Boolean = !isAbsolutePath(path)

    /**
     * 将绝对路径转换为相对于项目的路径
     *
     * @param absolutePath 绝对路径
     * @param project IDEA 项目对象
     * @return 相对路径，如果无法转换则返回原路径
     */
    fun toRelative(absolutePath: String, project: Project): String {
        val basePath = project.basePath ?: return absolutePath
        return toRelative(absolutePath, basePath)
    }

    /**
     * 将绝对路径转换为相对于项目的路径
     *
     * @param absolutePath 绝对路径
     * @param basePath 项目根目录路径
     * @return 相对路径，如果无法转换则返回原路径
     */
    fun toRelative(absolutePath: String, basePath: String): String {
        val normalizedAbsolute = File(absolutePath).canonicalPath
        val normalizedBase = File(basePath).canonicalPath

        return if (normalizedAbsolute.startsWith(normalizedBase)) {
            normalizedAbsolute.removePrefix(normalizedBase)
                .removePrefix(File.separator)
                .ifEmpty { "." }
        } else {
            absolutePath
        }
    }
}

// ==================== 扩展函数 ====================

/**
 * 将路径转换为绝对路径（基于项目）
 */
fun String.toAbsolutePath(project: Project): String = PathResolver.resolve(this, project)

/**
 * 将路径转换为绝对路径（基于 basePath）
 */
fun String.toAbsolutePath(basePath: String): String = PathResolver.resolve(this, basePath)

/**
 * 将绝对路径转换为相对路径（基于项目）
 */
fun String.toRelativePath(project: Project): String = PathResolver.toRelative(this, project)

/**
 * 将绝对路径转换为相对路径（基于 basePath）
 */
fun String.toRelativePath(basePath: String): String = PathResolver.toRelative(this, basePath)
