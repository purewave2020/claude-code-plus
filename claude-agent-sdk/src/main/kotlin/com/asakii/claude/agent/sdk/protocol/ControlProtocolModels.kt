package com.asakii.claude.agent.sdk.protocol

/**
 * Result of agents_run_all_to_background operation.
 *
 * @property count Number of agents that were backgrounded
 * @property backgroundedIds List of agent IDs that were backgrounded
 */
data class AgentsBackgroundResult(
    val count: Int,
    val backgroundedIds: List<String>
)

/**
 * Result of bash_run_to_background operation.
 *
 * @property success Whether the operation succeeded
 * @property taskId The background task ID (for tracking)
 * @property command The command that was backgrounded
 */
data class BashBackgroundResult(
    val success: Boolean,
    val taskId: String?,
    val command: String?
)

/**
 * Result of unified run_to_background operation.
 *
 * This represents the result of backgrounding tasks, handling both Bash and Agent types.
 *
 * When backgrounding a specific task (taskId provided):
 * - isBash: Whether the task was a Bash command (true) or Agent (false)
 * - success: Whether the operation succeeded
 * - taskId: The ID of the backgrounded task
 * - command: The Bash command (only for Bash tasks)
 *
 * When backgrounding all tasks (taskId not provided):
 * - bashCount: Number of Bash commands backgrounded
 * - agentCount: Number of Agents backgrounded
 * - backgroundedBashIds: List of Bash task IDs that were backgrounded
 * - backgroundedAgentIds: List of Agent IDs that were backgrounded
 */
data class UnifiedBackgroundResult(
    val success: Boolean,
    val isBash: Boolean? = null,         // For single task: whether it was Bash
    val taskId: String? = null,           // For single task: the task ID
    val command: String? = null,          // For single Bash task: the command
    val bashCount: Int = 0,               // For batch: number of Bash backgrounded
    val agentCount: Int = 0,              // For batch: number of Agents backgrounded
    val backgroundedBashIds: List<String> = emptyList(),   // For batch: Bash IDs
    val backgroundedAgentIds: List<String> = emptyList(),  // For batch: Agent IDs
    val error: String? = null             // Error message if failed
)

/**
 * CLI capabilities result.
 *
 * Contains runtime capability flags queried from the CLI.
 * Use this to check if certain features are enabled/disabled.
 *
 * @property backgroundTasksEnabled Whether background tasks are enabled.
 *           False when CLAUDE_CODE_DISABLE_BACKGROUND_TASKS env var is set to 'true' or '1'.
 */
data class CliCapabilities(
    val backgroundTasksEnabled: Boolean
)
