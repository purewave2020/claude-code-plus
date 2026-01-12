package com.asakii.plugin.tools

import com.asakii.claude.agent.sdk.types.AgentDefinition
import com.asakii.plugin.utils.ResourceLoader
import com.asakii.rpc.api.*
import com.asakii.settings.AgentDefaults
import com.asakii.settings.AgentSettingsService
import kotlinx.serialization.json.*
import com.asakii.rpc.api.ActiveFileInfo
import com.asakii.server.tools.IdeToolsDefault
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.ide.util.PropertiesComponent
import com.asakii.plugin.compat.DiffEditorCompat
import com.asakii.plugin.compat.LocalizationCompat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.io.File
import java.util.Locale
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*

/**
 * IDE 工具 IDEA 实现（继承默认实现，覆盖 IDEA 特有方法）
 *
 * - 继承 IdeToolsDefault 的通用实现（文件搜索、内容读取等）
 * - 覆盖需要 IDEA Platform API 的方法（openFile、showDiff、getTheme 等）
 */
class IdeToolsImpl(
    private val project: Project
) : IdeToolsDefault(project.basePath) {
    
    private val logger = Logger.getInstance(IdeToolsImpl::class.java.name)
    private val PREFERRED_LOCALE_KEY = "com.asakii.locale"
    
    override fun openFile(path: String, line: Int, column: Int): Result<Unit> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty"))
        }
        
        return try {
            ApplicationManager.getApplication().invokeLater {
                val file = LocalFileSystem.getInstance().findFileByIoFile(File(path))
                if (file != null) {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val descriptor = if (line > 0) {
                        OpenFileDescriptor(project, file, line - 1, (column - 1).coerceAtLeast(0))
                    } else {
                        OpenFileDescriptor(project, file)
                    }
                    fileEditorManager.openTextEditor(descriptor, true)
                    logger.info { "✅ Opened file: $path (line=$line, column=$column)" }
                } else {
                    logger.warn { "⚠️ File not found: $path" }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.logError { "❌ Failed to open file: ${e.message}" }
            Result.failure(e)
        }
    }
    
    override fun showDiff(request: DiffRequest): Result<Unit> {
        if (request.filePath.isBlank()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty"))
        }
        
        return try {
            ApplicationManager.getApplication().invokeLater {
                val fileName = File(request.filePath).name
                val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
                
                val (finalOldContent, finalNewContent, finalTitle) = if (request.rebuildFromFile) {
                    val file = LocalFileSystem.getInstance().findFileByPath(request.filePath)
                        ?: throw IllegalStateException("File not found: ${request.filePath}")
                     file.refresh(false, false)
                    val currentContent = ApplicationManager.getApplication().runReadAction<String> {
                        String(file.contentsToByteArray(), file.charset)
                    }
                    
                    val edits = request.edits ?: listOf(
                        EditOperation(
                            oldString = request.oldContent,
                            newString = request.newContent,
                            replaceAll = false
                        )
                    )
                    
                    val rebuiltOldContent = rebuildBeforeContent(currentContent, edits)
                    
                    Triple(
                        rebuiltOldContent,
                        currentContent,
                        request.title ?: "File Changes: $fileName (${edits.size} edits)"
                    )
                } else {
                    Triple(
                        request.oldContent,
                        request.newContent,
                        request.title ?: "File Diff: $fileName"
                    )
                }
                
                val leftContent = DiffContentFactory.getInstance()
                    .create(project, finalOldContent, fileType)
                
                val rightContent = DiffContentFactory.getInstance()
                    .create(project, finalNewContent, fileType)
                
                val diffRequest = SimpleDiffRequest(
                    finalTitle,
                    leftContent,
                    rightContent,
                    "$fileName (before)",
                    "$fileName (after)"
                )
                
                DiffManager.getInstance().showDiff(project, diffRequest)
                
                logger.info { "✅ Showing diff for: ${request.filePath}" }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            logger.logError { "❌ Failed to show diff: ${e.message}" }
            Result.failure(e)
        }
    }
    
    /**
     * 从当前文件内容逆向重建修改前的内容
     *
     * 注意：如果文件被 linter/formatter 修改过，newString 可能无法精确匹配。
     * 此时会尝试标准化空白后再匹配，如果仍失败则抛出异常。
     */
    private fun rebuildBeforeContent(afterContent: String, operations: List<EditOperation>): String {
        var content = afterContent
        for (operation in operations.asReversed()) {
            if (operation.replaceAll) {
                if (content.contains(operation.newString)) {
                    content = content.replace(operation.newString, operation.oldString)
                } else {
                    // 尝试标准化空白后匹配
                    val normalizedNew = normalizeWhitespace(operation.newString)
                    val normalizedContent = normalizeWhitespace(content)
                    if (normalizedContent.contains(normalizedNew)) {
                        // 找到标准化匹配，使用原始 oldString 替换（保持格式）
                        content = replaceNormalized(content, operation.newString, operation.oldString)
                    } else {
                        logger.warn { "⚠️ rebuildBeforeContent: newString not found (replace_all), skipping operation" }
                        // 继续处理其他操作，不抛出异常
                    }
                }
            } else {
                val index = content.indexOf(operation.newString)
                if (index >= 0) {
                    content = buildString {
                        append(content.substring(0, index))
                        append(operation.oldString)
                        append(content.substring(index + operation.newString.length))
                    }
                } else {
                    // 尝试标准化空白后匹配
                    val fuzzyIndex = findNormalizedIndex(content, operation.newString)
                    if (fuzzyIndex >= 0) {
                        // 找到模糊匹配位置，计算实际结束位置
                        val actualEnd = findActualEndIndex(content, fuzzyIndex, operation.newString)
                        content = buildString {
                            append(content.substring(0, fuzzyIndex))
                            append(operation.oldString)
                            append(content.substring(actualEnd))
                        }
                    } else {
                        logger.warn { "⚠️ rebuildBeforeContent: newString not found, skipping operation" }
                        // 继续处理其他操作，不抛出异常
                    }
                }
            }
        }
        logger.info { "✅ Successfully rebuilt before content (${operations.size} operations)" }
        return content
    }

    /**
     * 标准化空白字符（用于模糊匹配）
     */
    private fun normalizeWhitespace(s: String): String {
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * 在标准化空白后查找子串位置
     */
    private fun findNormalizedIndex(content: String, target: String): Int {
        val normalizedTarget = normalizeWhitespace(target)
        val lines = content.lines()
        var charIndex = 0

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            // 尝试在当前行开始的多行区域中匹配
            val remainingContent = lines.drop(lineIdx).joinToString("\n")
            val normalizedRemaining = normalizeWhitespace(remainingContent)

            if (normalizedRemaining.startsWith(normalizedTarget) ||
                normalizedRemaining.contains(normalizedTarget)) {
                // 找到了匹配的起始位置
                return charIndex
            }
            charIndex += line.length + 1 // +1 for newline
        }
        return -1
    }

    /**
     * 找到实际的结束索引（考虑空白差异）
     */
    private fun findActualEndIndex(content: String, startIndex: Int, target: String): Int {
        val normalizedTarget = normalizeWhitespace(target)
        val targetNormalizedLen = normalizedTarget.length

        var normalizedCount = 0
        var actualIndex = startIndex

        while (actualIndex < content.length && normalizedCount < targetNormalizedLen) {
            val c = content[actualIndex]
            if (!c.isWhitespace() || (normalizedCount > 0 && normalizedTarget.getOrNull(normalizedCount) == ' ')) {
                normalizedCount++
            }
            actualIndex++
        }

        // 跳过尾部空白
        while (actualIndex < content.length && content[actualIndex].isWhitespace() &&
               content[actualIndex] != '\n') {
            actualIndex++
        }

        return actualIndex
    }

    /**
     * 使用标准化匹配进行替换
     */
    private fun replaceNormalized(content: String, target: String, replacement: String): String {
        val index = findNormalizedIndex(content, target)
        if (index < 0) return content

        val endIndex = findActualEndIndex(content, index, target)
        return buildString {
            append(content.substring(0, index))
            append(replacement)
            append(content.substring(endIndex))
        }
    }
    
    override fun searchFiles(query: String, maxResults: Int): Result<List<FileInfo>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }
        
        return try {
            val result = mutableListOf<FileInfo>()
            ApplicationManager.getApplication().runReadAction {
                val projectScope = GlobalSearchScope.projectScope(project)
                // 搜索所有文件类型，使用多个常见扩展名
                val commonExtensions = listOf("kt", "java", "js", "ts", "py", "md", "json", "xml", "html", "css", "gradle", "kts", "properties")
                val allFiles = mutableSetOf<VirtualFile>()
                
                // 对每个扩展名进行搜索
                for (ext in commonExtensions) {
                    val files = FilenameIndex.getAllFilesByExt(project, ext, projectScope)
                        .filter { it.name.contains(query, ignoreCase = true) }
                    allFiles.addAll(files)
                }
                
                result.addAll(allFiles.take(maxResults).mapNotNull { file: VirtualFile ->
                    val path = file.path
                    if (path.isNotEmpty()) FileInfo(path) else null
                })
            }
            Result.success(result)
        } catch (e: Exception) {
            logger.warn { "Failed to search files: ${e.message}" }
            Result.failure(e)
        }
    }
    
    override fun getFileContent(path: String, lineStart: Int?, lineEnd: Int?): Result<String> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty"))
        }
        
        return try {
            val content = ApplicationManager.getApplication().runReadAction<String?> {
                val file = LocalFileSystem.getInstance().findFileByIoFile(File(path)) ?: return@runReadAction null
                String(file.contentsToByteArray(), file.charset)
            } ?: return Result.failure(IllegalArgumentException("File not found: $path"))
            
            val result = if (lineStart != null && lineEnd != null) {
                val lines = content.lines()
                lines.subList(
                    (lineStart - 1).coerceAtLeast(0),
                    lineEnd.coerceAtMost(lines.size)
                ).joinToString("\n")
            } else {
                content
            }
            
            Result.success(result)
        } catch (e: Exception) {
            logger.logError { "Failed to get file content: ${e.message}" }
            Result.failure(e)
        }
    }
    
    override fun getRecentFiles(maxResults: Int): Result<List<FileInfo>> {
        return try {
            val files = com.intellij.openapi.application.runReadAction {
                FileEditorManager.getInstance(project).openFiles.toList()
            }
            val result = files.take(maxResults).map { file ->
                FileInfo(file.path)
            }
            Result.success(result)
        } catch (e: Exception) {
            logger.warn { "Failed to get recent files: ${e.message}" }
            Result.failure(e)
        }
    }
    
    override fun getTheme(): IdeTheme {
        return IdeTheme(
            background = colorToHex(UIUtil.getPanelBackground()),
            foreground = colorToHex(UIUtil.getLabelForeground()),
            borderColor = colorToHex(JBColor.border()),
            panelBackground = colorToHex(UIUtil.getPanelBackground()),
            textFieldBackground = colorToHex(UIUtil.getTextFieldBackground()),
            selectionBackground = colorToHex(UIUtil.getListSelectionBackground(true)),
            selectionForeground = colorToHex(UIUtil.getListSelectionForeground(true)),
            linkColor = colorToHex(JBColor.namedColor("Link.foreground", JBColor.BLUE)),
            errorColor = colorToHex(JBColor.RED),
            warningColor = colorToHex(JBColor.YELLOW),
            successColor = colorToHex(JBColor.GREEN),
            separatorColor = colorToHex(JBColor.border()),
            hoverBackground = colorToHex(JBColor.namedColor("List.hoverBackground", UIUtil.getPanelBackground())),
            accentColor = colorToHex(JBColor.namedColor("Accent.focusColor", JBColor.BLUE)),
            infoBackground = colorToHex(JBColor.namedColor("Component.infoForeground", JBColor.GRAY)),
            codeBackground = colorToHex(UIUtil.getTextFieldBackground()),
            secondaryForeground = colorToHex(JBColor.GRAY)
        )
    }
    
    private fun colorToHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }
    
    override fun getProjectPath(): String {
        return project.basePath ?: ""
    }
    
    override fun getLocale(): String {
        // 检查用户偏好设置
        val preferred = PropertiesComponent.getInstance().getValue(PREFERRED_LOCALE_KEY)
        if (!preferred.isNullOrBlank()) {
            return preferred
        }
        
        return try {
            val locale = LocalizationCompat.getLocale()
            "${locale.language}-${locale.country}"
        } catch (e: Exception) {
            val locale = Locale.getDefault()
            "${locale.language}-${locale.country}"
        }
    }
    
    override fun setLocale(locale: String): Result<Unit> {
        return try {
            PropertiesComponent.getInstance().setValue(PREFERRED_LOCALE_KEY, locale)
            logger.info { "Locale preference set to: $locale" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.warn { "Failed to set locale preference: ${e.message}" }
            Result.failure(e)
        }
    }

    override fun getAgentDefinitions(): Map<String, AgentDefinition> {
        return try {
            logger.info { "🔍 [getAgentDefinitions] 开始加载自定义代理..." }

            // 从 AgentSettingsService 读取用户配置
            val settingsService = AgentSettingsService.getInstance()
            val customAgentsJson = settingsService.customAgents

            if (customAgentsJson.isBlank() || customAgentsJson == "{}") {
                // 没有用户配置时，使用默认的 ExploreWithJetbrains 代理
                logger.info { "ℹ️ [getAgentDefinitions] 用户未配置自定义代理，使用默认配置" }
                return getDefaultAgentDefinitions()
            }

            val json = Json { ignoreUnknownKeys = true }
            val agentsConfig = json.parseToJsonElement(customAgentsJson).jsonObject
            val agents = agentsConfig["agents"]?.jsonObject ?: agentsConfig

            val result = mutableMapOf<String, AgentDefinition>()

            // 检查 JetBrains MCP 是否启用
            val jetBrainsMcpEnabled = settingsService.enableJetBrainsMcp

            for ((name, value) in agents) {
                try {
                    val agentObj = value.jsonObject

                    // 检查是否启用（默认启用）
                    val enabled = agentObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                    if (!enabled) {
                        logger.info { "⏭️ Skipping disabled agent: $name" }
                        continue
                    }

                    // ExploreWithJetbrains agent 依赖 JetBrains MCP
                    if (name == "ExploreWithJetbrains" && !jetBrainsMcpEnabled) {
                        logger.info { "⏭️ Skipping ExploreWithJetbrains: JetBrains MCP is disabled" }
                        continue
                    }

                    val description = agentObj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    val prompt = agentObj["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
                    val tools = agentObj["tools"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    // 空字符串视为 null（使用默认模型）
                    val model = agentObj["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

                    result[name] = AgentDefinition(
                        description = description,
                        prompt = prompt,
                        tools = tools,
                        model = model
                    )
                    logger.info("✅ Loaded agent: $name (tools: ${tools?.size ?: 0}, model: ${model ?: "inherit"})")
                } catch (e: Exception) {
                    logger.warn { "⚠️ Failed to parse agent '$name': ${e.message}" }
                }
            }

            if (result.isNotEmpty()) {
                logger.info { "📦 Loaded ${result.size} custom agents from settings: ${result.keys.joinToString()}" }
            } else {
                logger.warn { "⚠️ [getAgentDefinitions] 未加载到任何自定义代理" }
            }

            result
        } catch (e: Exception) {
            logger.warn { "Failed to load agent definitions: ${e.message}" }
            getDefaultAgentDefinitions()
        }
    }

    /**
     * 获取默认的代理定义
     * 当用户未配置或配置解析失败时使用
     *
     * 注意：ExploreWithJetbrains agent 只在 JetBrains MCP 启用时才可用
     */
    private fun getDefaultAgentDefinitions(): Map<String, AgentDefinition> {
        // 检查 JetBrains MCP 是否启用
        val settings = AgentSettingsService.getInstance()
        if (!settings.enableJetBrainsMcp) {
            logger.info { "⏭️ JetBrains MCP is disabled, ExploreWithJetbrains agent not available" }
            return emptyMap()
        }

        val defaultAgent = AgentDefaults.EXPLORE_WITH_JETBRAINS
        val agentDef = AgentDefinition(
            description = defaultAgent.description,
            prompt = defaultAgent.prompt,
            tools = defaultAgent.tools,
            model = null // 使用默认模型
        )
        logger.info { "📦 Using default agent: ${defaultAgent.name}" }
        return mapOf(defaultAgent.name to agentDef)
    }

    override fun getActiveEditorFile(): ActiveFileInfo? {
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
            }, com.intellij.openapi.application.ModalityState.any())
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
    private fun isDiffEditor(editor: FileEditor): Boolean {
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
    private fun handleDiffEditor(diffEditor: FileEditor, projectPath: String): ActiveFileInfo? {
        return try {
            // 尝试获取 DiffRequest（兼容不同版本的 API）
            val request = getActiveRequest(diffEditor)

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
     * 获取当前 IntelliJ 版本的主版本号
     * 例如：242 (2024.2), 243 (2024.3), 251 (2025.1)
     */
    private fun getIdeaBuildNumber(): Int {
        return try {
            val buildNumber = com.intellij.openapi.application.ApplicationInfo.getInstance().build.baselineVersion
            buildNumber
        } catch (e: Exception) {
            logger.warn { "Failed to get IDEA build number: ${e.message}" }
            242 // 默认返回 242（2024.2）作为保守值
        }
    }

    /**
     * 获取 Diff 编辑器的 activeRequest
     * 使用 DiffEditorCompat 兼容层，无需反射
     *
     * @see com.asakii.plugin.compat.DiffEditorCompat
     */
    private fun getActiveRequest(diffEditor: FileEditor): com.intellij.diff.requests.DiffRequest? {
        return DiffEditorCompat.getActiveRequest(diffEditor)
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
    private fun handleTextEditor(
        selectedEditor: com.intellij.openapi.editor.Editor?,
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
    private fun calculateRelativePath(absolutePath: String, projectPath: String): String {
        return if (projectPath.isNotEmpty() && absolutePath.startsWith(projectPath)) {
            absolutePath.removePrefix(projectPath).removePrefix("/").removePrefix("\\")
        } else {
            absolutePath
        }
    }

    /**
     * 确定文件类型
     */
    private fun determineFileType(file: VirtualFile): String {
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

    /**
     * 检查是否在 IDE 环境中运行
     *
     * IdeToolsImpl 由 jetbrains-plugin 提供，表示在 IDEA 中运行
     */
    override fun hasIdeEnvironment(): Boolean = true

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
    override fun getFontData(fontFamily: String): FontData? {
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

    /**
     * 在系统默认浏览器中打开 URL
     *
     * 使用 IntelliJ Platform 的 BrowserUtil 打开 URL，
     * 这是在 IDEA 环境中打开浏览器的推荐方式
     */
    override fun openUrl(url: String): Result<Unit> {
        logger.info { "[IDEA] Opening URL in browser: $url" }
        return try {
            BrowserUtil.browse(url)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.warn { "Failed to open URL: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * 获取文件历史内容（基于 LocalHistory 时间戳）
     *
     * 使用 IDEA LocalHistory API 查询指定时间点之前的文件内容，
     * 用于 Edit/Write 工具的历史快照恢复和 Diff 显示。
     */
    override fun getFileHistoryContent(filePath: String, beforeTimestamp: Long): String? {
        logger.info { "[IDEA] Getting file history content: $filePath (before: $beforeTimestamp)" }
        return com.asakii.plugin.services.FileHistoryService.getContentBefore(filePath, beforeTimestamp)
    }
}
