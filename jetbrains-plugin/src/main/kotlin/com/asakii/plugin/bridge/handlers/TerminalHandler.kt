package com.asakii.plugin.bridge.handlers

import com.asakii.logging.*
import com.asakii.plugin.mcp.TerminalMcpServerImpl
import com.asakii.server.mcp.TerminalMcpServerProvider
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.asakii.rpc.proto.JetBrainsTerminalBackgroundRequest as ProtoTerminalBackgroundRequest
import com.asakii.rpc.proto.JetBrainsTerminalBackgroundEvent
import com.asakii.rpc.proto.TerminalBackgroundStatus
import com.asakii.rpc.proto.JetBrainsGetBackgroundableTerminalsResponse
import com.asakii.rpc.proto.JetBrainsBackgroundableTerminal

/**
 * 终端后台执行处理器
 * 处理: getBackgroundableTerminals, terminalBackground
 */
class TerminalHandler(private val terminalMcpServerProvider: TerminalMcpServerProvider?) {
    private val logger = getLogger("TerminalHandler")

    /**
     * 获取可后台化的终端任务
     */
    fun handleGetBackgroundableTerminals(dataBytes: ByteArray): Payload {
        return try {
            val terminalServer = terminalMcpServerProvider?.getServer() as? TerminalMcpServerImpl
            if (terminalServer == null) {
                logger.warn { "⚠️ [JetBrains] Terminal MCP Server not available" }
                return buildPayload {
                    data(JetBrainsGetBackgroundableTerminalsResponse.newBuilder()
                        .setSuccess(false)
                        .setError("Terminal MCP Server not available")
                        .build().toByteArray())
                }
            }

            val tasks = terminalServer.sessionManager.getBackgroundableTasks()
            logger.info { "📋 [JetBrains] getBackgroundableTerminals: returning ${tasks.size} tasks to frontend" }

            val response = JetBrainsGetBackgroundableTerminalsResponse.newBuilder()
                .setSuccess(true)
                .addAllTerminals(tasks.map { task ->
                    JetBrainsBackgroundableTerminal.newBuilder()
                        .setSessionId(task.sessionId)
                        .setToolUseId(task.toolUseId)
                        .setCommand(task.command)
                        .setStartTime(task.startTime)
                        .setElapsedMs(task.getElapsedMs())
                        .build()
                })
                .build()

            buildPayload { data(response.toByteArray()) }
        } catch (e: Exception) {
            logger.error { "❌ [JetBrains] getBackgroundableTerminals failed: ${e.message}" }
            buildPayload {
                data(JetBrainsGetBackgroundableTerminalsResponse.newBuilder()
                    .setSuccess(false)
                    .setError(e.message ?: "Unknown error")
                    .build().toByteArray())
            }
        }
    }

    /**
     * 批量后台终端任务（流式返回结果）
     */
    fun handleTerminalBackground(dataBytes: ByteArray): Flow<Payload> = flow {
        val req = ProtoTerminalBackgroundRequest.parseFrom(dataBytes)
        logger.info { "⏸️ [JetBrains] terminalBackground: ${req.itemsCount} items" }

        val terminalServer = terminalMcpServerProvider?.getServer() as? TerminalMcpServerImpl
        if (terminalServer == null) {
            logger.warn { "⚠️ [JetBrains] Terminal MCP Server not available" }
            emit(buildTerminalBackgroundEvent("", "", TerminalBackgroundStatus.TERMINAL_BG_FAILED, "Terminal MCP Server not available"))
            return@flow
        }

        for (item in req.itemsList) {
            val sessionId = item.sessionId
            val toolUseId = item.toolUseId

            emit(buildTerminalBackgroundEvent(sessionId, toolUseId, TerminalBackgroundStatus.TERMINAL_BG_STARTED, null))

            try {
                val success = terminalServer.sessionManager.markTaskAsBackground(toolUseId)
                
                if (success) {
                    logger.info { "✅ [JetBrains] terminal background success: $toolUseId" }
                    emit(buildTerminalBackgroundEvent(sessionId, toolUseId, TerminalBackgroundStatus.TERMINAL_BG_SUCCESS, null))
                } else {
                    logger.warn { "❌ [JetBrains] terminal background failed: $toolUseId - Task not found" }
                    emit(buildTerminalBackgroundEvent(sessionId, toolUseId, TerminalBackgroundStatus.TERMINAL_BG_FAILED, "Task not found"))
                }
            } catch (e: Exception) {
                logger.error { "❌ [JetBrains] terminal background exception: $toolUseId - ${e.message}" }
                emit(buildTerminalBackgroundEvent(sessionId, toolUseId, TerminalBackgroundStatus.TERMINAL_BG_FAILED, e.message ?: "Unknown error"))
            }
        }
    }

    private fun buildTerminalBackgroundEvent(
        sessionId: String,
        toolUseId: String,
        status: TerminalBackgroundStatus,
        error: String?
    ): Payload {
        val builder = JetBrainsTerminalBackgroundEvent.newBuilder()
            .setSessionId(sessionId)
            .setToolUseId(toolUseId)
            .setStatus(status)

        if (error != null) {
            builder.setError(error)
        }

        return buildPayload { data(builder.build().toByteArray()) }
    }
}
