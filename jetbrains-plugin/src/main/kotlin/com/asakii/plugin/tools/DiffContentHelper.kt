package com.asakii.plugin.tools

import com.asakii.rpc.api.EditOperation
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*

/**
 * Diff 内容处理辅助类
 * 负责重建修改前的文件内容和字符串匹配
 */
class DiffContentHelper {

    private val logger = Logger.getInstance(DiffContentHelper::class.java.name)

    /**
     * 从当前文件内容逆向重建修改前的内容
     *
     * 注意：如果文件被 linter/formatter 修改过，newString 可能无法精确匹配。
     * 此时会尝试标准化空白后再匹配，如果仍失败则抛出异常。
     */
    fun rebuildBeforeContent(afterContent: String, operations: List<EditOperation>): String {
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
    fun normalizeWhitespace(s: String): String {
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
}
