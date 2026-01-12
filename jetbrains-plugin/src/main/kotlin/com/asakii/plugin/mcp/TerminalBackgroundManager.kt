package com.asakii.plugin.mcp

import com.asakii.logging.getLogger
import com.asakii.logging.info
import com.asakii.server.mcp.TerminalBackgroundResult
import java.util.concurrent.ConcurrentHashMap

private val logger = getLogger("TerminalBackgroundManager")

/**
 * Manager for handling terminal task background operations.
 * Extracts runToBackground logic from TerminalMcpServerProviderImpl.
 */
object TerminalBackgroundManager {

    /**
     * Move terminal tasks to background.
     *
     * @param servers Map of session ID to TerminalMcpServerImpl instances
     * @param toolUseId Optional specific task ID to background. If null, backgrounds all running tasks.
     * @return TerminalBackgroundResult with status and list of backgrounded task IDs
     */
    fun runToBackground(
        servers: ConcurrentHashMap<String, TerminalMcpServerImpl>,
        toolUseId: String?
    ): TerminalBackgroundResult {
        val backgroundedIds = mutableListOf<String>()

        if (toolUseId != null) {
            // Single task mode: background specific task
            for ((_, server) in servers) {
                if (server.sessionManager.markTaskAsBackground(toolUseId)) {
                    backgroundedIds.add(toolUseId)
                    logger.info { "⏸️ Terminal task moved to background: $toolUseId" }
                    break
                }
            }
            return if (backgroundedIds.isNotEmpty()) {
                TerminalBackgroundResult(
                    success = true,
                    backgroundedIds = backgroundedIds,
                    count = 1
                )
            } else {
                TerminalBackgroundResult(
                    success = false,
                    error = "Task not found: $toolUseId"
                )
            }
        } else {
            // Batch mode: background all running tasks
            for ((sessionId, server) in servers) {
                val tasks = server.sessionManager.getBackgroundableTasks(0) // Get all running tasks
                for (task in tasks) {
                    if (server.sessionManager.markTaskAsBackground(task.toolUseId)) {
                        backgroundedIds.add(task.toolUseId)
                        logger.info { "⏸️ Terminal task moved to background: ${task.toolUseId} (session: $sessionId)" }
                    }
                }
            }
            return TerminalBackgroundResult(
                success = true,
                backgroundedIds = backgroundedIds,
                count = backgroundedIds.size
            )
        }
    }
}
