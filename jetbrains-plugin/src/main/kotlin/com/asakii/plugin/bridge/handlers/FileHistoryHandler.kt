package com.asakii.plugin.bridge.handlers

import com.asakii.logging.*
import com.asakii.plugin.mcp.tools.FileChangeLabelCache
import com.asakii.plugin.services.FileHistoryService
import com.asakii.rpc.api.JetBrainsApi
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.asakii.rpc.proto.JetBrainsBatchRollbackRequest as ProtoBatchRollbackRequest
import com.asakii.rpc.proto.JetBrainsBatchRollbackEvent
import com.asakii.rpc.proto.RollbackStatus
import com.asakii.rpc.proto.JetBrainsOperationResponse

/**
 * 文件历史和回滚处理器
 * 处理: getOriginalContent, getFileHistoryContent, rollbackFile, batchRollback
 */
class FileHistoryHandler(private val jetbrainsApi: JetBrainsApi) {
    private val logger = getLogger("FileHistoryHandler")

    fun handleGetOriginalContent(dataBytes: ByteArray): Payload {
        return try {
            val toolUseId = String(dataBytes, Charsets.UTF_8)
            logger.info { "📄 [JetBrains] getOriginalContent: toolUseId=$toolUseId" }

            val content = FileChangeLabelCache.getOriginalContent(toolUseId)

            val responseBuilder = com.asakii.rpc.proto.JetBrainsGetOriginalContentResponse.newBuilder()
                .setSuccess(true)
                .setFound(content != null)

            if (content != null) {
                responseBuilder.setContent(content)
            }

            buildPayload { data(responseBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getOriginalContent failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleGetFileHistoryContent(dataBytes: ByteArray): Payload {
        return try {
            val req = com.asakii.rpc.proto.JetBrainsGetFileHistoryContentRequest.parseFrom(dataBytes)
            logger.info { "📄 [JetBrains] getFileHistoryContent: ${req.filePath} (before: ${req.beforeTimestamp})" }

            val absolutePath = resolvePath(req.filePath)
            val content = FileHistoryService.getContentBefore(absolutePath, req.beforeTimestamp)

            val responseBuilder = com.asakii.rpc.proto.JetBrainsGetFileHistoryContentResponse.newBuilder()
                .setSuccess(true)
                .setFound(content != null)

            if (content != null) {
                responseBuilder.setContent(content)
            }

            buildPayload { data(responseBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getFileHistoryContent failed: ${e.message}" }
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    fun handleRollbackFile(dataBytes: ByteArray): Payload {
        return try {
            val req = com.asakii.rpc.proto.JetBrainsRollbackFileRequest.parseFrom(dataBytes)
            logger.info { "↩️ [JetBrains] rollbackFile: ${req.filePath} (before: ${req.beforeTimestamp})" }

            val absolutePath = resolvePath(req.filePath)
            
            val result = if (req.beforeTimestamp == 0L) {
                logger.info { "↩️ [JetBrains] deleteFile (rollback new file): $absolutePath" }
                FileHistoryService.deleteFile(absolutePath)
            } else {
                FileHistoryService.rollbackToTimestamp(absolutePath, req.beforeTimestamp)
            }

            val responseBuilder = com.asakii.rpc.proto.JetBrainsRollbackFileResponse.newBuilder()
                .setSuccess(result.success)

            if (result.error != null) {
                responseBuilder.setError(result.error)
            }

            buildPayload { data(responseBuilder.build().toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] rollbackFile failed: ${e.message}" }
            val response = com.asakii.rpc.proto.JetBrainsRollbackFileResponse.newBuilder()
                .setSuccess(false)
                .setError(e.message ?: "Unknown error")
                .build()
            buildPayload { data(response.toByteArray()) }
        }
    }

    fun handleBatchRollback(dataBytes: ByteArray): Flow<Payload> = flow {
        val req = ProtoBatchRollbackRequest.parseFrom(dataBytes)
        logger.info { "↩️ [JetBrains] batchRollback: ${req.itemsCount} items" }

        for (item in req.itemsList) {
            val filePath = item.filePath
            val beforeTimestamp = item.beforeTimestamp
            val toolUseId = item.toolUseId

            emit(buildRollbackEvent(filePath, toolUseId, RollbackStatus.ROLLBACK_STARTED, null))

            try {
                val absolutePath = resolvePath(filePath)

                val result = if (beforeTimestamp == 0L) {
                    logger.info { "↩️ [JetBrains] deleteFile (rollback new file): $absolutePath" }
                    FileHistoryService.deleteFile(absolutePath)
                } else {
                    FileHistoryService.rollbackToTimestamp(absolutePath, beforeTimestamp)
                }

                if (result.success) {
                    logger.info { "✅ [JetBrains] rollback success: $filePath ($toolUseId)" }
                    emit(buildRollbackEvent(filePath, toolUseId, RollbackStatus.ROLLBACK_SUCCESS, null))
                } else {
                    logger.warn { "❌ [JetBrains] rollback failed: $filePath - ${result.error}" }
                    emit(buildRollbackEvent(filePath, toolUseId, RollbackStatus.ROLLBACK_FAILED, result.error))
                }
            } catch (e: Exception) {
                logger.error { "❌ [JetBrains] rollback exception: $filePath - ${e.message}" }
                emit(buildRollbackEvent(filePath, toolUseId, RollbackStatus.ROLLBACK_FAILED, e.message ?: "Unknown error"))
            }
        }
    }

    private fun buildRollbackEvent(
        filePath: String,
        toolUseId: String,
        status: RollbackStatus,
        error: String?
    ): Payload {
        val builder = JetBrainsBatchRollbackEvent.newBuilder()
            .setFilePath(filePath)
            .setToolUseId(toolUseId)
            .setStatus(status)

        if (error != null) {
            builder.setError(error)
        }

        return buildPayload { data(builder.build().toByteArray()) }
    }

    private fun resolvePath(path: String): String {
        val projectPath = jetbrainsApi.project.getPath()
        return com.asakii.plugin.util.PathResolver.resolve(path, projectPath)
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
