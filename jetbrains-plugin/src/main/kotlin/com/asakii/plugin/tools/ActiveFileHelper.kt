package com.asakii.plugin.tools

import com.asakii.plugin.compat.DiffEditorCompat
import com.asakii.rpc.api.ActiveFileInfo
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.asakii.plugin.logging.*
import java.io.File

/**
 * 活动文件处理辅助类
 * 负责获取当前编辑器中的文件信息
 */
class ActiveFileHelper(private val project: Project) {

    private val logger = Logger.getInstance(ActiveFileHelper::class.java.name)

    /**
     * 获取当前活动编辑器的文件信息
     */
    fun getActiveEditorFile(): ActiveFileInfo? {
        return try {
            var result: ActiveFileInfo? = null
            // 使用 ModalityState.any() 避免模态对话框导致的阻塞
            ApplicationManager.getApplication().invokeAndWait({
                ApplicationManager.getApplication().runReadAction {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val selectedFileEditor = fileEditorManager.selectedEditor
                    val selectedTextEditor = fileEditorManager.selectedTextEditor
                    val selectedFile = fileEditorManager.selectedFiles.firstOrNull()
                    val projectPath = project.basePath ?: ""

                    // 检查是否是 Diff 编辑器（使用反射兼容不同版本的 IntelliJ）
                    if (selectedFileEditor != null && isDiffEditor(selectedFileEditor)) {
                        result = handleDiffEditor(selectedFileEditor, projectPath)
                        return@runReadAction
                    }

                    // 处理普通文件
                    if (selectedFile != null) {
                        val absolutePath = selectedFile.path
                        val relativePath = calculateRelativePath(absolutePath, projectPath)
                        val fileName = selectedFile.name

                        // 检查文件类型
                        val fileType = determineFileType(selectedFile)

                        when (fileType) {
                            "image", "binary" -> {
                                // 图片和二进制文件：只返回路径，不获取内容
                                result = ActiveFileInfo(
                                    path = absolutePath,
                                    relativePath = relativePath,
                                    name = fileName,
                                    fileType = fileType
                                )
                            }
                            else -> {
                                // 文本文件：获取光标位置和选区信息
                                result = handleTextEditor(
                                    selectedTextEditor,
                                    absolutePath,
                                    relativePath,
                                    fileName,
                                    fileType
                                )
                            }
                        }
                    }
                }
            }, ModalityState.any())
            result
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "Failed to get active editor file: ${e.message}" }
            null
        }
    }

    /**
     * 检查 FileEditor 是否是 Diff 编辑器
     * 使用反射兼容不同版本的 IntelliJ：
     * - 2024.2 及之前：DiffRequestProcessorEditor
     * - 2024.3+：DiffEditorViewerFileEditor
     */
    fun isDiffEditor(editor: FileEditor): Boolean {
        val className = editor.javaClass.name
        return className.contains("DiffRequestProcessorEditor") ||
               className.contains("DiffEditorViewerFileEditor") ||
               className.contains("DiffFileEditorBase")
    }

    /**
     * 处理 Diff 编辑器,获取 Diff 内容
     * 使用反射兼容不同版本的 IntelliJ API：
     * - 2024.2 及之前：DiffRequestProcessorEditor.getProcessor().activeRequest
     * - 2024.3+：DiffEditorViewerFileEditor.editorViewer
     */
    fun handleDiffEditor(diffEditor: FileEditor, projectPath: String): ActiveFileInfo? {
        return try {
            // 尝试获取 DiffRequest（兼容不同版本的 API）
            val request = DiffEditorCompat.getActiveRequest(diffEditor)

            // 支持所有 ContentDiffRequest 类型（SimpleDiffRequest 是其子类）
            if (request is ContentDiffRequest) {
                val contents = request.contents
                val title = request.title ?: "Diff"
                val contentTitles = request.contentTitles

                // 获取左侧（旧）和右侧（新）内容
                val oldContent = (contents.getOrNull(0) as? DocumentContent)?.document?.text
                val newContent = (contents.getOrNull(1) as? DocumentContent)?.document?.text

                // 尝试从多个来源获取文件路径
                val filePath = extractFilePathFromDiff(contentTitles, title, contents)
                val relativePath = calculateRelativePath(filePath, projectPath)

                ActiveFileInfo(
                    path = filePath,
                    relativePath = relativePath,
                    name = File(filePath).name,
                    fileType = "diff",
                    diffOldContent = oldContent,
                    diffNewContent = newContent,
                    diffTitle = title
                )
            } else {
                // 对于非 ContentDiffRequest 类型，尝试从虚拟文件获取信息
                val virtualFile = diffEditor.file
                val filePath = virtualFile?.let { extractFilePathFromVirtualFile(it) }

                if (filePath != null) {
                    val relativePath = calculateRelativePath(filePath, projectPath)
                    ActiveFileInfo(
                        path = filePath,
                        relativePath = relativePath,
                        name = File(filePath).name,
                        fileType = "diff",
                        diffTitle = request?.title ?: virtualFile.name
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn { "Failed to handle diff editor: ${e.message}" }
            null
        }
    }

    /**
     * 从 Diff 内容中提取文件路径
     */
    private fun extractFilePathFromDiff(
        contentTitles: List<String?>,
        title: String,
        contents: List<com.intellij.diff.contents.DiffContent>
    ): String {
        // 1. 尝试从 contentTitles 获取路径
        val pathFromTitles = contentTitles.asSequence()
            .filterNotNull()
            .firstOrNull { it.contains("/") || it.contains("\\") }
        if (pathFromTitles != null) return pathFromTitles

        // 2. 尝试从 DiffContent 的 VirtualFile 获取路径
        for (content in contents) {
            if (content is DocumentContent) {
                val file = content.highlightFile
                if (file != null && file.path.isNotEmpty()) {
                    return file.path
                }
            }
        }

        // 3. 从标题中提取文件名
        val fileNameFromTitle = extractFileNameFromTitle(title)
        if (fileNameFromTitle != null) return fileNameFromTitle

        // 4. 最后回退到标题本身
        return title
    }

    /**
     * 从标题中提取文件名
     * 例如: "Commit: SubprocessTransport.kt" -> "SubprocessTransport.kt"
     */
    private fun extractFileNameFromTitle(title: String): String? {
        // 匹配常见的标题模式
        val patterns = listOf(
            Regex("""(?:Commit|Changes|Diff):\s*(.+)"""),  // "Commit: file.kt"
            Regex("""(.+?)\s+vs\s+.+"""),                   // "file.kt vs HEAD"
            Regex("""(.+?)\s*\(.+\)""")                     // "file.kt (before)"
        )

        for (pattern in patterns) {
            val match = pattern.find(title)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotEmpty() && extracted != title) {
                    return extracted
                }
            }
        }
        return null
    }

    /**
     * 从虚拟文件中提取实际文件路径
     */
    private fun extractFilePathFromVirtualFile(virtualFile: VirtualFile): String? {
        // 某些 diff 虚拟文件可能包含原始文件的引用
        val name = virtualFile.name

        // 跳过明显的虚拟文件名
        if (name.contains("DiffVirtualFile") || name.contains("Preview")) {
            return null
        }

        return virtualFile.path
    }

    /**
     * 处理文本编辑器，获取光标位置和选区信息
     */
    fun handleTextEditor(
        selectedEditor: Editor?,
        absolutePath: String,
        relativePath: String,
        fileName: String,
        fileType: String
    ): ActiveFileInfo {
        // 获取光标位置
        val caret = selectedEditor?.caretModel?.primaryCaret
        val line = caret?.logicalPosition?.line?.plus(1) // 转换为 1-based
        val column = caret?.logicalPosition?.column?.plus(1) // 转换为 1-based

        // 获取选区信息
        val selectionModel = selectedEditor?.selectionModel
        val hasSelection = selectionModel?.hasSelection() == true
        var startLine: Int? = null
        var startColumn: Int? = null
        var endLine: Int? = null
        var endColumn: Int? = null
        var selectedContent: String? = null

        // 智能转换: 如果 hasSelection 为 true,则 selectionModel 和 selectedEditor 必然非空
        if (hasSelection) {
            val editor = selectedEditor!!
            val selection = selectionModel!!
            val document = editor.document
            val selectionStart = selection.selectionStart
            val selectionEnd = selection.selectionEnd

            startLine = document.getLineNumber(selectionStart) + 1 // 转换为 1-based
            startColumn = selectionStart - document.getLineStartOffset(startLine - 1) + 1
            endLine = document.getLineNumber(selectionEnd) + 1 // 转换为 1-based
            endColumn = selectionEnd - document.getLineStartOffset(endLine - 1) + 1

            // 获取选中的文本内容
            selectedContent = selection.selectedText
        }

        return ActiveFileInfo(
            path = absolutePath,
            relativePath = relativePath,
            name = fileName,
            line = line,
            column = column,
            hasSelection = hasSelection,
            startLine = startLine,
            startColumn = startColumn,
            endLine = endLine,
            endColumn = endColumn,
            selectedContent = selectedContent,
            fileType = fileType
        )
    }

    /**
     * 计算相对路径
     */
    fun calculateRelativePath(absolutePath: String, projectPath: String): String {
        return if (projectPath.isNotEmpty() && absolutePath.startsWith(projectPath)) {
            absolutePath.removePrefix(projectPath).removePrefix("/").removePrefix("\\")
        } else {
            absolutePath
        }
    }

    /**
     * 确定文件类型
     */
    fun determineFileType(file: VirtualFile): String {
        val extension = file.extension?.lowercase() ?: ""

        // 常见图片扩展名
        val imageExtensions = setOf(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif"
        )

        // 常见二进制文件扩展名
        val binaryExtensions = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "tar", "gz", "rar", "7z",
            "exe", "dll", "so", "dylib",
            "class", "jar", "war",
            "mp3", "mp4", "avi", "mov", "wav", "flac",
            "ttf", "otf", "woff", "woff2"
        )

        return when {
            extension in imageExtensions -> "image"
            extension in binaryExtensions -> "binary"
            file.fileType.isBinary -> "binary"
            else -> "text"
        }
    }
}
