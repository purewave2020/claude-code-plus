package com.asakii.plugin.bridge.handlers

import com.asakii.logging.*
import com.asakii.rpc.api.*
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import com.asakii.rpc.proto.JetBrainsOpenFileRequest as ProtoOpenFileRequest
import com.asakii.rpc.proto.JetBrainsShowDiffRequest as ProtoShowDiffRequest
import com.asakii.rpc.proto.JetBrainsShowMultiEditDiffRequest as ProtoShowMultiEditDiffRequest
import com.asakii.rpc.proto.JetBrainsShowEditPreviewRequest as ProtoShowEditPreviewRequest
import com.asakii.rpc.proto.JetBrainsShowEditFullDiffRequest as ProtoShowEditFullDiffRequest
import com.asakii.rpc.proto.JetBrainsShowMarkdownRequest as ProtoShowMarkdownRequest
import com.asakii.rpc.proto.JetBrainsOperationResponse

/**
 * 文件操作处理器
 * 处理: openFile, showDiff, showMultiEditDiff, showEditPreviewDiff, showEditFullDiff, showMarkdown
 */
class FileOperationHandler(private val jetbrainsApi: JetBrainsApi) {
    private val logger = getLogger("FileOperationHandler")

    fun handleOpenFile(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoOpenFileRequest.parseFrom(dataBytes)
            logger.info { "📂 [JetBrains] openFile: ${req.filePath}" }

            val request = JetBrainsOpenFileRequest(
                filePath = req.filePath,
                line = if (req.hasLine()) req.line else null,
                column = if (req.hasColumn()) req.column else null,
                startOffset = if (req.hasStartOffset()) req.startOffset else null,
                endOffset = if (req.hasEndOffset()) req.endOffset else null
            )

            val result = jetbrainsApi.file.openFile(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] openFile failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleShowDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowDiffRequest.parseFrom(dataBytes)
            logger.info { "📝 [JetBrains] showDiff: ${req.filePath}" }

            val request = JetBrainsShowDiffRequest(
                filePath = req.filePath,
                oldContent = req.oldContent,
                newContent = req.newContent,
                title = if (req.hasTitle()) req.title else null
            )

            val result = jetbrainsApi.file.showDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showDiff failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleShowMultiEditDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowMultiEditDiffRequest.parseFrom(dataBytes)
            logger.info { "📝 [JetBrains] showMultiEditDiff: ${req.filePath} (${req.editsCount} edits)" }

            val request = JetBrainsShowMultiEditDiffRequest(
                filePath = req.filePath,
                edits = req.editsList.map { edit ->
                    JetBrainsEditOperation(
                        oldString = edit.oldString,
                        newString = edit.newString,
                        replaceAll = edit.replaceAll
                    )
                },
                currentContent = if (req.hasCurrentContent()) req.currentContent else null
            )

            val result = jetbrainsApi.file.showMultiEditDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showMultiEditDiff failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleShowEditPreviewDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowEditPreviewRequest.parseFrom(dataBytes)
            logger.info { "👀 [JetBrains] showEditPreviewDiff: ${req.filePath} (${req.editsCount} edits)" }

            val request = JetBrainsShowEditPreviewRequest(
                filePath = req.filePath,
                edits = req.editsList.map { edit ->
                    JetBrainsEditOperation(
                        oldString = edit.oldString,
                        newString = edit.newString,
                        replaceAll = edit.replaceAll
                    )
                },
                title = if (req.hasTitle()) req.title else null
            )

            val result = jetbrainsApi.file.showEditPreviewDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showEditPreviewDiff failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleShowEditFullDiff(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowEditFullDiffRequest.parseFrom(dataBytes)
            logger.info { "📝 [JetBrains] showEditFullDiff: ${req.filePath}" }

            val request = JetBrainsShowEditFullDiffRequest(
                filePath = req.filePath,
                oldString = req.oldString,
                newString = req.newString,
                replaceAll = req.replaceAll,
                title = if (req.hasTitle()) req.title else null,
                originalContent = if (req.hasOriginalContent()) req.originalContent else null
            )

            val result = jetbrainsApi.file.showEditFullDiff(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showEditFullDiff failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleShowMarkdown(dataBytes: ByteArray): Payload {
        return try {
            val req = ProtoShowMarkdownRequest.parseFrom(dataBytes)
            logger.info("📄 [JetBrains] showMarkdown: ${req.title ?: "Plan Preview"}")

            val request = JetBrainsShowMarkdownRequest(
                content = req.content,
                title = if (req.hasTitle()) req.title else null
            )

            val result = jetbrainsApi.file.showMarkdown(request)
            buildOperationResponse(result.isSuccess, result.exceptionOrNull()?.message)
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] showMarkdown failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private fun buildOperationResponse(success: Boolean, error: String?): Payload {
        val response = JetBrainsOperationResponse.newBuilder().apply {
            this.success = success
            error?.let { this.error = it }
        }.build()
        return buildPayload { data(response.toByteArray()) }
    }

    private fun buildErrorResponse(error: String): Payload {
        return buildOperationResponse(false, error)
    }
}
