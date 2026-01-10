package com.asakii.plugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.asakii.logging.*

private val logger = getLogger("VirtualFileResolver")

/**
 * 通用的虚拟文件解析器
 *
 * 支持多种路径格式：
 * - 相对路径（相对于项目根目录）: frontend/src/App.vue
 * - 绝对路径: C:/path/to/file.java
 * - jar://...!/... (IDEA 标准格式)
 * - C:/path.jar!/... (简化格式)
 * - jrt://module/... (JDK 9+ 模块)
 * - JDK 源码: C:/jdk/lib/src.zip!/java.base/java/lang/String.java
 */
object VirtualFileResolver {

    /**
     * 解析文件路径为 VirtualFile
     *
     * @param path 文件路径（支持多种格式）
     * @param project 项目实例（用于解析相对路径，可为 null）
     * @return VirtualFile 或 null（如果找不到）
     */
    fun resolve(path: String, project: Project? = null): VirtualFile? {
        // 0. 如果是相对路径，先尝试在项目根目录下查找
        if (project != null && isRelativePath(path)) {
            val projectBasePath = project.basePath
            if (projectBasePath != null) {
                val absolutePath = "$projectBasePath/$path".replace("\\", "/")
                VirtualFileManager.getInstance().findFileByUrl("file://$absolutePath")?.let {
                    logger.info { "Resolved relative path '$path' to '${it.path}'" }
                    return it
                }
            }
        }

        // 1. 尝试直接作为 URL 解析
        VirtualFileManager.getInstance().findFileByUrl(path)?.let { return it }

        // 2. 处理包含 !/ 的 JAR 路径
        if (path.contains("!/")) {
            // 转换为标准 jar:// URL 格式
            val jarUrl = if (path.startsWith("jar://")) {
                path
            } else {
                // C:/path/to.jar!/inner/path -> jar://C:/path/to.jar!/inner/path
                "jar://${path.replace("\\", "/")}"
            }

            VirtualFileManager.getInstance().findFileByUrl(jarUrl)?.let { return it }

            // 尝试使用 JarFileSystem
            val parts = path.split("!/", limit = 2)
            if (parts.size == 2) {
                val jarPath = parts[0].removePrefix("jar://").replace("\\", "/")
                val innerPath = parts[1]

                val jarFile = VirtualFileManager.getInstance().findFileByUrl("file://$jarPath")
                    ?: VirtualFileManager.getInstance().findFileByUrl(jarPath)

                if (jarFile != null) {
                    val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile)
                    jarRoot?.findFileByRelativePath(innerPath)?.let { return it }
                }
            }
        }

        // 3. 尝试 jrt:// (Java Runtime) 格式
        if (path.startsWith("jrt://") || path.contains("/lib/src.zip!/")) {
            VirtualFileManager.getInstance().findFileByUrl(path)?.let { return it }
        }

        // 4. 尝试作为普通文件路径
        val normalizedPath = path.replace("\\", "/")
        VirtualFileManager.getInstance().findFileByUrl("file://$normalizedPath")?.let { return it }

        return null
    }

    /**
     * 判断是否为相对路径
     */
    private fun isRelativePath(path: String): Boolean {
        // 绝对路径的特征：
        // - Windows: C:/ D:\ 等
        // - Unix: / 开头
        // - URL: file:// jar:// jrt:// 等
        return !path.matches(Regex("^[a-zA-Z]:[/\\\\].*")) &&
                !path.startsWith("/") &&
                !path.contains("://")
    }
}
