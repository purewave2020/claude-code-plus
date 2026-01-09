package com.asakii.plugin.mcp.tools

import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.plugin.mcp.getBoolean
import com.asakii.plugin.mcp.getInt
import com.asakii.plugin.mcp.getString
import com.asakii.server.mcp.schema.ToolSchemaLoader
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import com.asakii.logging.*
import java.io.File

private val logger = getLogger("FileProblemsTool")

/**
 * 问题严重级别
 *
 * 分类说明：
 * - SYNTAX_ERROR: 语法/解析错误（PSI 解析器产生的错误，如缺少括号、分号等）
 * - ERROR: 代码错误（编译错误、类型错误等）
 * - WARNING: 警告（过时 API、潜在问题、可能的 bug）
 * - SUGGESTION: 建议（代码风格、未使用的符号、可优化项）
 */
@Serializable
enum class ProblemSeverity {
    SYNTAX_ERROR, ERROR, WARNING, SUGGESTION
}

/**
 * 分析结果：区分语法错误、编译器错误和代码检查问题
 */
private data class AnalysisResult(
    val psiFile: PsiFile?,  // 用于计算行列信息
    val syntaxErrors: List<ProblemDescriptor>,
    val highlightVisitorProblems: List<HighlightInfo>,  // 来自 HighlightVisitor（包含编译器错误）
    val inspectionProblems: List<Pair<ProblemDescriptor, HighlightDisplayLevel?>>
)

@Serializable
data class FileProblem(
    val severity: ProblemSeverity,
    val message: String,
    val line: Int,          // 1-based
    val column: Int,        // 1-based
    val endLine: Int,       // 1-based
    val endColumn: Int,     // 1-based
    val description: String? = null
)

@Serializable
data class FileProblemsResult(
    val filePath: String,
    val problems: List<FileProblem>,
    val syntaxErrorCount: Int,
    val errorCount: Int,
    val warningCount: Int,
    val suggestionCount: Int,
    val hasErrors: Boolean
)

/**
 * 文件静态错误工具
 *
 * 使用 InspectionEngine API 直接运行检查，无需打开文件
 * 参考: https://plugins.jetbrains.com/docs/intellij/code-inspections.html
 */
class FileProblemsTool(private val project: Project) {

    fun getInputSchema(): JsonObject = ToolSchemaLoader.getSchema("FileProblems")

    suspend fun execute(arguments: JsonObject): Any {
        val filePath = arguments.getString("filePath")
            ?: return ToolResult.error("Missing required parameter: filePath")
        val includeWarnings = arguments.getBoolean("includeWarnings") ?: true
        val includeSuggestions = arguments.getBoolean("includeSuggestions") ?: false
        // 兼容旧参数名
        val includeWeakWarnings = arguments.getBoolean("includeWeakWarnings") ?: includeSuggestions
        val maxProblems = (arguments.getInt("maxProblems") ?: 50).coerceAtLeast(1)

        val projectPath = project.basePath
            ?: return ToolResult.error("Cannot get project path")

        val absolutePath = File(projectPath, filePath).canonicalPath

        if (!absolutePath.startsWith(File(projectPath).canonicalPath)) {
            return ToolResult.error("File path must be within project directory")
        }

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath)
            ?: return ToolResult.error("File not found: $filePath")

        val problems = mutableListOf<FileProblem>()
        var syntaxErrorCount = 0
        var errorCount = 0
        var warningCount = 0
        var suggestionCount = 0

        try {
            // 使用 InspectionEngine 直接运行检查，无需打开文件
            val analysisResult = runInspectionsOnFile(virtualFile, includeWarnings, includeWeakWarnings)

            logger.debug { "📊 Found ${analysisResult.syntaxErrors.size} syntax errors, ${analysisResult.highlightVisitorProblems.size} highlight visitor problems and ${analysisResult.inspectionProblems.size} inspection problems for $filePath" }

            // 处理 ProblemDescriptor 需要在 ReadAction 中，因为访问 PSI 元素
            com.intellij.openapi.application.runReadAction {
                // 1. 处理语法错误（始终包含）
                for (descriptor in analysisResult.syntaxErrors) {
                    if (problems.size >= maxProblems) break
                    syntaxErrorCount++
                    addProblemFromDescriptor(descriptor, ProblemSeverity.SYNTAX_ERROR, problems)
                }

                // 2. 处理 HighlightVisitor 检测到的问题（编译器错误等）
                for (highlightInfo in analysisResult.highlightVisitorProblems) {
                    if (problems.size >= maxProblems) break

                    val severity = classifyHighlightInfo(highlightInfo.severity)

                    when (severity) {
                        ProblemSeverity.SYNTAX_ERROR -> {
                            // 语法错误已在上面处理
                            continue
                        }
                        ProblemSeverity.ERROR -> {
                            errorCount++
                        }
                        ProblemSeverity.WARNING -> {
                            if (!includeWarnings) continue
                            warningCount++
                        }
                        ProblemSeverity.SUGGESTION -> {
                            if (!includeWeakWarnings) continue
                            suggestionCount++
                        }
                    }

                    addProblemFromHighlightInfo(highlightInfo, severity, problems, analysisResult.psiFile)
                }

                // 3. 处理代码检查问题
                for ((descriptor, inspectionLevel) in analysisResult.inspectionProblems) {
                    if (problems.size >= maxProblems) break

                    val severity = classifyProblem(descriptor.highlightType, inspectionLevel)

                    // 根据过滤条件决定是否包含
                    when (severity) {
                        ProblemSeverity.SYNTAX_ERROR -> {
                            // 语法错误已在上面处理
                            continue
                        }
                        ProblemSeverity.ERROR -> {
                            errorCount++
                        }
                        ProblemSeverity.WARNING -> {
                            if (!includeWarnings) continue
                            warningCount++
                        }
                        ProblemSeverity.SUGGESTION -> {
                            if (!includeWeakWarnings) continue
                            suggestionCount++
                        }
                    }

                    addProblemFromDescriptor(descriptor, severity, problems)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Analysis error for $filePath" }
            return ToolResult.error("Analysis error: ${e.message}")
        }

        val sortedProblems = problems.sortedWith(
            compareBy({ it.severity.ordinal }, { it.line }, { it.column })
        )

        val sb = StringBuilder()
        sb.appendLine("## 📄 File: `$filePath`")
        sb.appendLine()

        if (sortedProblems.isEmpty()) {
            sb.appendLine("✅ **No issues found**")
        } else {
            sb.appendLine("| Severity | Location | Message |")
            sb.appendLine("|----------|----------|---------|")
            sortedProblems.forEach { problem ->
                val icon = when (problem.severity) {
                    ProblemSeverity.SYNTAX_ERROR -> "🚫"
                    ProblemSeverity.ERROR -> "❌"
                    ProblemSeverity.WARNING -> "⚠️"
                    ProblemSeverity.SUGGESTION -> "💡"
                }
                val location = "${problem.line}:${problem.column}"
                // 转义 Markdown 表格中的特殊字符
                val escapedMessage = problem.message.replace("|", "\\|").replace("\n", " ")
                sb.appendLine("| $icon | `$location` | $escapedMessage |")
            }
        }

        sb.appendLine()
        sb.appendLine("---")
        val parts = mutableListOf<String>()
        if (syntaxErrorCount > 0) parts.add("🚫 **$syntaxErrorCount** syntax errors")
        if (errorCount > 0) parts.add("❌ **$errorCount** errors")
        if (warningCount > 0) parts.add("⚠️ **$warningCount** warnings")
        if (suggestionCount > 0) parts.add("💡 **$suggestionCount** suggestions")
        if (parts.isEmpty()) {
            sb.append("📊 No problems")
        } else {
            sb.append("📊 Summary: ${parts.joinToString(" | ")}")
        }

        return sb.toString()
    }

    /**
     * 使用 InspectionEngine 和 HighlightVisitor 直接在文件上运行检查
     * 无需打开文件，直接通过 PsiFile 运行
     */
    private fun runInspectionsOnFile(
        virtualFile: com.intellij.openapi.vfs.VirtualFile,
        includeWarnings: Boolean,
        includeWeakWarnings: Boolean
    ): AnalysisResult {
        return try {
            // 使用 EmptyProgressIndicator 静默运行，避免显示弹窗
            val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
            val computation: () -> AnalysisResult = {
                ReadAction.compute<AnalysisResult, Exception> {
                    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                        ?: return@compute AnalysisResult(null, emptyList(), emptyList(), emptyList())

                    runInspectionsOnPsiFile(psiFile, includeWarnings, includeWeakWarnings)
                }
            }
            ProgressManager.getInstance().runProcess(computation, indicator)
                ?: AnalysisResult(null, emptyList(), emptyList(), emptyList())
        } catch (e: Exception) {
            logger.error(e) { "❌ Error running inspections" }
            AnalysisResult(null, emptyList(), emptyList(), emptyList())
        }
    }

    /**
     * 在 PsiFile 上运行所有启用的检查
     * 返回结构化的分析结果，区分语法错误、编译器错误和代码检查问题
     */
    private fun runInspectionsOnPsiFile(
        psiFile: PsiFile,
        includeWarnings: Boolean,
        includeWeakWarnings: Boolean
    ): AnalysisResult {
        val inspectionManager = InspectionManager.getInstance(project)
        val context = inspectionManager.createNewGlobalContext()

        // 获取当前项目的检查配置
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile as? InspectionProfileImpl
            ?: return AnalysisResult(psiFile, emptyList(), emptyList(), emptyList())

        // 1. 收集 PSI 语法错误（解析器级别的错误，最重要）
        val syntaxErrors = collectPsiSyntaxErrors(psiFile, inspectionManager)
        logger.debug { "📊 Found ${syntaxErrors.size} PSI syntax errors" }

        // 2. 运行 HighlightVisitor 分析（检测编译器错误，如类型不匹配）
        val highlightVisitorProblems = runHighlightVisitors(psiFile)
        logger.debug { "📊 Found ${highlightVisitorProblems.size} highlight visitor problems" }

        // 3. 运行代码检查（LocalInspectionTool），并保存每个检查的配置级别
        val inspectionProblems = mutableListOf<Pair<ProblemDescriptor, HighlightDisplayLevel?>>()
        val toolsList = profile.getAllEnabledInspectionTools(project)

        for (tools in toolsList) {
            val toolWrapper = tools.tool

            // 只运行 LocalInspectionTool（文件级别的检查）
            if (toolWrapper !is LocalInspectionToolWrapper) continue

            // 获取配置的严重级别
            val configuredLevel = tools.defaultState.level
            val isWarning = configuredLevel == HighlightDisplayLevel.WARNING
            val isWeakWarning = configuredLevel == HighlightDisplayLevel.WEAK_WARNING ||
                               configuredLevel == HighlightDisplayLevel.DO_NOT_SHOW

            // 根据过滤条件决定是否运行此检查
            if (!includeWarnings && isWarning) continue
            if (!includeWeakWarnings && isWeakWarning) continue

            try {
                val descriptors = InspectionEngine.runInspectionOnFile(psiFile, toolWrapper, context)
                // 将每个问题与其检查的配置级别关联
                descriptors.forEach { descriptor ->
                    inspectionProblems.add(descriptor to configuredLevel)
                }
            } catch (e: Exception) {
                logger.debug { "⚠️ Inspection ${toolWrapper.shortName} failed: ${e.message}" }
            }
        }

        return AnalysisResult(psiFile, syntaxErrors, highlightVisitorProblems, inspectionProblems)
    }

    /**
     * 运行 HighlightVisitor 分析
     *
     * HighlightVisitor 可以检测编译器级别的错误，如：
     * - 类型不匹配
     * - 未解析的引用
     * - 无效的方法调用
     *
     * 这些错误不需要文件在编辑器中打开就能检测到
     */
    private fun runHighlightVisitors(psiFile: PsiFile): List<HighlightInfo> {
        // 如果在 dumb mode 中，跳过需要索引的分析
        if (DumbService.isDumb(project)) {
            logger.debug { "⚠️ Skipping HighlightVisitor analysis in dumb mode" }
            return emptyList()
        }

        val problems = mutableListOf<HighlightInfo>()

        try {
            // 创建 HighlightInfoHolder 来收集问题
            val holder = HighlightInfoHolder(psiFile)

            // 获取所有注册的 HighlightVisitor
            val visitors = HighlightVisitor.EP_HIGHLIGHT_VISITOR.getExtensions(project)

            for (visitor in visitors) {
                // 检查 visitor 是否适合当前文件
                if (!visitor.suitableForFile(psiFile)) continue

                try {
                    // 克隆 visitor 以确保线程安全
                    val clonedVisitor = visitor.clone()

                    // 运行分析 - 确保回调也在 ReadAction 中执行
                    clonedVisitor.analyze(psiFile, true, holder) {
                        ReadAction.run<Exception> {
                            // 访问所有元素
                            psiFile.accept(object : PsiRecursiveElementVisitor() {
                                override fun visitElement(element: com.intellij.psi.PsiElement) {
                                    clonedVisitor.visit(element)
                                    super.visitElement(element)
                                }
                            })
                        }
                    }
                } catch (e: Exception) {
                    logger.debug { "⚠️ HighlightVisitor ${visitor.javaClass.simpleName} failed: ${e.message}" }
                }
            }

            // 从 holder 中提取所有问题
            for (i in 0 until holder.size()) {
                val info = holder[i]
                problems.add(info)
            }
        } catch (e: Exception) {
            logger.error(e) { "❌ Error running HighlightVisitor analysis" }
        }

        return problems
    }

    /**
     * 收集 PSI 语法错误
     *
     * PSI 语法错误是解析器在解析代码时产生的错误，例如：
     * - 缺少分号、括号不匹配
     * - 意外的 token
     * - 不完整的语句
     *
     * 这些错误不需要文件在编辑器中打开就能检测到
     */
    private fun collectPsiSyntaxErrors(
        psiFile: PsiFile,
        inspectionManager: InspectionManager
    ): List<ProblemDescriptor> {
        val problems = mutableListOf<ProblemDescriptor>()

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitErrorElement(element: PsiErrorElement) {
                super.visitErrorElement(element)

                // 创建 ProblemDescriptor 来表示语法错误
                val descriptor = inspectionManager.createProblemDescriptor(
                    element,
                    element.errorDescription,
                    false,  // onTheFly = false，因为这不是在编辑时运行的
                    emptyArray(),  // 没有快速修复
                    ProblemHighlightType.ERROR  // 语法错误总是 ERROR 级别
                )
                problems.add(descriptor)
            }
        })

        return problems
    }

    /**
     * 根据 HighlightSeverity 分类问题
     */
    private fun classifyHighlightInfo(severity: HighlightSeverity): ProblemSeverity {
        return when {
            severity >= HighlightSeverity.ERROR -> ProblemSeverity.ERROR
            severity >= HighlightSeverity.WARNING -> ProblemSeverity.WARNING
            severity >= HighlightSeverity.WEAK_WARNING -> ProblemSeverity.SUGGESTION
            else -> ProblemSeverity.SUGGESTION
        }
    }

    /**
     * 根据 ProblemHighlightType 和 Inspection 配置级别分类问题
     *
     * 分类规则：
     * - ERROR / GENERIC_ERROR: 始终是 ERROR
     * - GENERIC_ERROR_OR_WARNING: 根据 Inspection 配置级别决定
     * - WARNING / LIKE_DEPRECATED / LIKE_MARKED_FOR_REMOVAL: WARNING
     * - WEAK_WARNING / INFORMATION / LIKE_UNUSED_SYMBOL 等: SUGGESTION
     */
    private fun classifyProblem(
        highlightType: ProblemHighlightType,
        configuredLevel: HighlightDisplayLevel?
    ): ProblemSeverity {
        return when (highlightType) {
            // 明确的错误类型
            ProblemHighlightType.ERROR,
            ProblemHighlightType.GENERIC_ERROR -> ProblemSeverity.ERROR

            // 动态类型：根据 Inspection 配置级别决定
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING -> {
                when (configuredLevel) {
                    HighlightDisplayLevel.ERROR -> ProblemSeverity.ERROR
                    HighlightDisplayLevel.WARNING -> ProblemSeverity.WARNING
                    HighlightDisplayLevel.WEAK_WARNING,
                    HighlightDisplayLevel.DO_NOT_SHOW -> ProblemSeverity.SUGGESTION
                    else -> ProblemSeverity.WARNING  // 默认作为警告
                }
            }

            // 警告类型
            ProblemHighlightType.WARNING -> ProblemSeverity.WARNING

            // 过时/废弃代码 - 作为警告
            ProblemHighlightType.LIKE_DEPRECATED,
            ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL -> ProblemSeverity.WARNING

            // 弱警告和建议类型
            ProblemHighlightType.WEAK_WARNING,
            ProblemHighlightType.INFORMATION,
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            ProblemHighlightType.POSSIBLE_PROBLEM -> ProblemSeverity.SUGGESTION

            // 其他未知类型默认作为建议
            else -> ProblemSeverity.SUGGESTION
        }
    }

    /**
     * 从 ProblemDescriptor 创建 FileProblem 并添加到列表
     */
    private fun addProblemFromDescriptor(
        descriptor: ProblemDescriptor,
        severity: ProblemSeverity,
        problems: MutableList<FileProblem>
    ) {
        val psiElement = descriptor.psiElement
        val textRange = descriptor.textRangeInElement ?: psiElement?.textRange
        val document = psiElement?.containingFile?.viewProvider?.document

        val (line, column, endLine, endColumn) = if (document != null && textRange != null) {
            try {
                val startLine = document.getLineNumber(textRange.startOffset) + 1
                val startCol = textRange.startOffset - document.getLineStartOffset(startLine - 1) + 1
                val endL = document.getLineNumber(textRange.endOffset) + 1
                val endCol = textRange.endOffset - document.getLineStartOffset(endL - 1) + 1
                listOf(startLine, startCol, endL, endCol)
            } catch (e: Exception) {
                listOf(1, 1, 1, 1)
            }
        } else {
            listOf(1, 1, 1, 1)
        }

        problems.add(FileProblem(
            severity = severity,
            message = descriptor.descriptionTemplate ?: "Unknown issue",
            line = line,
            column = column,
            endLine = endLine,
            endColumn = endColumn,
            description = descriptor.toString()
        ))
    }

    /**
     * 从 HighlightInfo 创建 FileProblem 并添加到列表
     */
    private fun addProblemFromHighlightInfo(
        info: HighlightInfo,
        severity: ProblemSeverity,
        problems: MutableList<FileProblem>,
        psiFile: PsiFile?
    ) {
        val document = psiFile?.viewProvider?.document

        // 通过 offset 计算行列信息
        val (line, column, endLine, endColumn) = if (document != null) {
            try {
                val startLine = document.getLineNumber(info.startOffset) + 1
                val startCol = info.startOffset - document.getLineStartOffset(startLine - 1) + 1
                val endL = document.getLineNumber(info.endOffset) + 1
                val endCol = info.endOffset - document.getLineStartOffset(endL - 1) + 1
                listOf(startLine, startCol, endL, endCol)
            } catch (e: Exception) {
                listOf(1, 1, 1, 1)
            }
        } else {
            listOf(1, 1, 1, 1)
        }

        problems.add(FileProblem(
            severity = severity,
            message = info.description ?: "Unknown issue",
            line = line,
            column = column,
            endLine = endLine,
            endColumn = endColumn,
            description = info.toolTip
        ))
    }
}
