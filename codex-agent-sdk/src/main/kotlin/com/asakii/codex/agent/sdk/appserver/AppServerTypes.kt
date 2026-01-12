@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.asakii.codex.agent.sdk.appserver

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JSON-RPC 2.0 鍗忚绫诲瀷瀹氫箟 (鐢ㄤ簬 codex app-server)
 *
 * 鍙傝€? external/openai-codex/codex-rs/app-server/README.md
 */

// ============== JSON-RPC 鍩虹绫诲瀷 ==============

@Serializable
data class JsonRpcRequest(
    val method: String,
    val id: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcResponse(
    val id: String,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcNotification(
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

// ============== 鍒濆鍖?==============

@Serializable
data class ClientInfo(
    val name: String,
    val title: String? = null,
    val version: String
)

@Serializable
data class InitializeParams(
    val clientInfo: ClientInfo
)

@Serializable
data class InitializeResult(
    val userAgent: String? = null
)

// ============== Thread 鐩稿叧 ==============

@Serializable
data class ThreadStartParams(
    val model: String? = null,
    val modelProvider: String? = null,
    val cwd: String? = null,
    val approvalPolicy: String? = null,
    val sandbox: String? = null,
    val config: Map<String, JsonElement>? = null,
    val baseInstructions: String? = null,
    val developerInstructions: String? = null
)

@Serializable
data class ThreadStartResult(
    val thread: ThreadInfo
)

@Serializable
@JsonClassDiscriminator("type")
sealed class CommandAction {
    @Serializable
    @SerialName("read")
    data class Read(
        val command: String,
        val name: String,
        val path: String
    ) : CommandAction()

    @Serializable
    @SerialName("listFiles")
    data class ListFiles(
        val command: String,
        val path: String? = null
    ) : CommandAction()

    @Serializable
    @SerialName("search")
    data class Search(
        val command: String,
        val query: String? = null,
        val path: String? = null
    ) : CommandAction()

    @Serializable
    @SerialName("unknown")
    data class Unknown(
        val command: String
    ) : CommandAction()
}

@Serializable
enum class SessionSource {
    @SerialName("cli")
    Cli,
    @SerialName("vscode")
    VsCode,
    @SerialName("exec")
    Exec,
    @SerialName("appServer")
    AppServer,
    @SerialName("unknown")
    Unknown
}

@Serializable
data class GitInfo(
    val sha: String? = null,
    val branch: String? = null,
    @SerialName("originUrl")
    val originUrl: String? = null
)

@Serializable
data class ThreadInfo(
    val id: String,
    val preview: String,
    val modelProvider: String,
    val createdAt: Long,
    val path: String,
    val cwd: String,
    val cliVersion: String,
    val source: SessionSource = SessionSource.Unknown,
    val gitInfo: GitInfo? = null,
    val turns: List<TurnInfo> = emptyList()
)

@Serializable
data class ThreadResumeParams(
    val threadId: String
)

@Serializable
data class ThreadArchiveParams(
    val threadId: String
)

@Serializable
data class ThreadListParams(
    val cursor: String? = null,
    val limit: Int? = null,
    val modelProviders: List<String>? = null
)

@Serializable
data class ThreadListResult(
    val data: List<ThreadInfo>,
    val nextCursor: String? = null
)

// ============== Models ==============

@Serializable
data class ModelListParams(
    val cursor: String? = null,
    val limit: Int? = null
)

@Serializable
data class ReasoningEffortOption(
    @SerialName("reasoningEffort")
    val reasoningEffort: String,
    val description: String
)

@Serializable
data class ModelInfo(
    val id: String,
    val model: String,
    @SerialName("displayName")
    val displayName: String,
    val description: String = "",
    @SerialName("supportedReasoningEfforts")
    val supportedReasoningEfforts: List<ReasoningEffortOption> = emptyList(),
    @SerialName("defaultReasoningEffort")
    val defaultReasoningEffort: String? = null,
    @SerialName("isDefault")
    val isDefault: Boolean = false
)

@Serializable
data class ModelListResponse(
    val data: List<ModelInfo>,
    val nextCursor: String? = null
)

// ============== Turn 鐩稿叧 ==============

@Serializable
@JsonClassDiscriminator("type")
sealed class UserInput {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : UserInput()

    @Serializable
    @SerialName("image")
    data class Image(val url: String) : UserInput()

    @Serializable
    @SerialName("localImage")
    data class LocalImage(val path: String) : UserInput()
}

@Serializable
@JsonClassDiscriminator("type")
sealed class SandboxPolicy {
    @Serializable
    @SerialName("dangerFullAccess")
    object DangerFullAccess : SandboxPolicy()

    @Serializable
    @SerialName("readOnly")
    object ReadOnly : SandboxPolicy()

    @Serializable
    @SerialName("workspaceWrite")
    data class WorkspaceWrite(
        @SerialName("writableRoots")
        val writableRoots: List<String> = emptyList(),
        @SerialName("networkAccess")
        val networkAccess: Boolean = false,
        @SerialName("excludeTmpdirEnvVar")
        val excludeTmpdirEnvVar: Boolean = false
    ) : SandboxPolicy()
}

@Serializable
enum class ReasoningSummary {
    @SerialName("auto")
    Auto,
    @SerialName("concise")
    Concise,
    @SerialName("detailed")
    Detailed,
    @SerialName("none")
    None
}

@Serializable
data class TurnStartParams(
    val threadId: String,
    val input: List<UserInput>,
    val cwd: String? = null,
    val approvalPolicy: String? = null,
    val sandboxPolicy: SandboxPolicy? = null,
    val model: String? = null,
    val effort: String? = null,
    val summary: ReasoningSummary? = null
)

@Serializable
data class TurnStartResult(
    val turn: TurnInfo
)

@Serializable
enum class TurnStatus {
    @SerialName("completed")
    Completed,
    @SerialName("interrupted")
    Interrupted,
    @SerialName("failed")
    Failed,
    @SerialName("inProgress")
    InProgress
}

@Serializable
data class TurnInfo(
    val id: String,
    val status: TurnStatus,
    val items: List<ThreadItem> = emptyList(),
    val error: TurnError? = null
)

@Serializable
data class TurnError(
    val message: String,
    val codexErrorInfo: JsonElement? = null
)

@Serializable
data class TurnInterruptParams(
    val threadId: String,
    val turnId: String
)

// ============== Item 绫诲瀷 ==============

@Serializable
enum class CommandExecutionStatus {
    @SerialName("inProgress")
    InProgress,
    @SerialName("completed")
    Completed,
    @SerialName("failed")
    Failed,
    @SerialName("declined")
    Declined
}

@Serializable
enum class PatchApplyStatus {
    @SerialName("inProgress")
    InProgress,
    @SerialName("completed")
    Completed,
    @SerialName("failed")
    Failed,
    @SerialName("declined")
    Declined
}

@Serializable
@JsonClassDiscriminator("type")
sealed class PatchChangeKind {
    @Serializable
    @SerialName("add")
    object Add : PatchChangeKind()

    @Serializable
    @SerialName("delete")
    object Delete : PatchChangeKind()

    @Serializable
    @SerialName("update")
    data class Update(
        @SerialName("movePath")
        val movePath: String? = null
    ) : PatchChangeKind()
}

@Serializable
data class FileUpdateChange(
    val path: String,
    val kind: PatchChangeKind,
    val diff: String
)

@Serializable
enum class McpToolCallStatus {
    @SerialName("inProgress")
    InProgress,
    @SerialName("completed")
    Completed,
    @SerialName("failed")
    Failed
}

@Serializable
data class McpToolCallResult(
    val content: List<JsonElement> = emptyList(),
    @SerialName("structuredContent")
    val structuredContent: JsonElement? = null
)

@Serializable
data class McpToolCallError(
    val message: String
)

@Serializable
@JsonClassDiscriminator("type")
sealed class ThreadItem {
    abstract val id: String

    @Serializable
    @SerialName("userMessage")
    data class UserMessage(
        override val id: String,
        val content: List<UserInput> = emptyList()
    ) : ThreadItem()

    @Serializable
    @SerialName("agentMessage")
    data class AgentMessage(
        override val id: String,
        val text: String
    ) : ThreadItem()

    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        override val id: String,
        val summary: List<String> = emptyList(),
        val content: List<String> = emptyList()
    ) : ThreadItem()

    @Serializable
    @SerialName("commandExecution")
    data class CommandExecution(
        override val id: String,
        val command: String,
        val cwd: String,
        @SerialName("processId")
        val processId: String? = null,
        val status: CommandExecutionStatus,
        @SerialName("commandActions")
        val commandActions: List<CommandAction> = emptyList(),
        @SerialName("aggregatedOutput")
        val aggregatedOutput: String? = null,
        @SerialName("exitCode")
        val exitCode: Int? = null,
        @SerialName("durationMs")
        val durationMs: Long? = null
    ) : ThreadItem()

    @Serializable
    @SerialName("fileChange")
    data class FileChange(
        override val id: String,
        val changes: List<FileUpdateChange> = emptyList(),
        val status: PatchApplyStatus
    ) : ThreadItem()

    @Serializable
    @SerialName("mcpToolCall")
    data class McpToolCall(
        override val id: String,
        val server: String,
        val tool: String,
        val status: McpToolCallStatus,
        val arguments: JsonElement,
        val result: McpToolCallResult? = null,
        val error: McpToolCallError? = null,
        @SerialName("durationMs")
        val durationMs: Long? = null
    ) : ThreadItem()

    @Serializable
    @SerialName("webSearch")
    data class WebSearch(
        override val id: String,
        val query: String
    ) : ThreadItem()

    @Serializable
    @SerialName("imageView")
    data class ImageView(
        override val id: String,
        val path: String
    ) : ThreadItem()

    @Serializable
    @SerialName("enteredReviewMode")
    data class EnteredReviewMode(
        override val id: String,
        val review: String
    ) : ThreadItem()

    @Serializable
    @SerialName("exitedReviewMode")
    data class ExitedReviewMode(
        override val id: String,
        val review: String
    ) : ThreadItem()
}


// ============== Notifications ==============

@Serializable
data class ThreadStartedNotification(
    val thread: ThreadInfo
)

@Serializable
data class TurnStartedNotification(
    val threadId: String,
    val turn: TurnInfo
)

@Serializable
data class TurnCompletedNotification(
    val threadId: String,
    val turn: TurnInfo
)

@Serializable
data class TurnDiffUpdatedNotification(
    val threadId: String,
    val turnId: String,
    val diff: String
)

@Serializable
data class TurnPlanUpdatedNotification(
    val threadId: String,
    val turnId: String,
    val explanation: String? = null,
    val plan: List<TurnPlanStep> = emptyList()
)

@Serializable
data class TurnPlanStep(
    val step: String,
    val status: TurnPlanStepStatus
)

@Serializable
enum class TurnPlanStepStatus {
    @SerialName("pending")
    Pending,
    @SerialName("inProgress")
    InProgress,
    @SerialName("completed")
    Completed
}

@Serializable
data class ItemStartedNotification(
    val item: ThreadItem,
    val threadId: String,
    val turnId: String
)

@Serializable
data class ItemCompletedNotification(
    val item: ThreadItem,
    val threadId: String,
    val turnId: String
)

@Serializable
data class AgentMessageDeltaNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String
)

@Serializable
data class ReasoningSummaryTextDeltaNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
    val summaryIndex: Long
)

@Serializable
data class ReasoningSummaryPartAddedNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val summaryIndex: Long
)

@Serializable
data class ReasoningTextDeltaNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
    val contentIndex: Long
)

@Serializable
data class CommandExecutionOutputDeltaNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String
)

@Serializable
data class FileChangeOutputDeltaNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String
)

@Serializable
data class McpToolCallProgressNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val message: String
)

@Serializable
data class ContextCompactedNotification(
    val threadId: String,
    val turnId: String
)

@Serializable
data class ErrorNotification(
    val error: TurnError,
    val willRetry: Boolean,
    val threadId: String,
    val turnId: String
)

// ============== Approval Requests (Server -> Client) ==============

@Serializable
data class ExecPolicyAmendment(
    val command: List<String>
)

@Serializable(with = ApprovalDecisionSerializer::class)
sealed class ApprovalDecision {
    object Accept : ApprovalDecision()
    object AcceptForSession : ApprovalDecision()
    data class AcceptWithExecpolicyAmendment(val execpolicyAmendment: ExecPolicyAmendment) : ApprovalDecision()
    object Decline : ApprovalDecision()
    object Cancel : ApprovalDecision()
}

object ApprovalDecisionSerializer : KSerializer<ApprovalDecision> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ApprovalDecision", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ApprovalDecision) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ApprovalDecisionSerializer can be used only with JSON")
        val element = when (value) {
            ApprovalDecision.Accept -> JsonPrimitive("accept")
            ApprovalDecision.AcceptForSession -> JsonPrimitive("acceptForSession")
            ApprovalDecision.Decline -> JsonPrimitive("decline")
            ApprovalDecision.Cancel -> JsonPrimitive("cancel")
            is ApprovalDecision.AcceptWithExecpolicyAmendment -> buildJsonObject {
                put(
                    "acceptWithExecpolicyAmendment",
                    jsonEncoder.json.encodeToJsonElement(
                        ExecPolicyAmendment.serializer(),
                        value.execpolicyAmendment
                    )
                )
            }
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): ApprovalDecision {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ApprovalDecisionSerializer can be used only with JSON")
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> when (element.content) {
                "accept" -> ApprovalDecision.Accept
                "acceptForSession" -> ApprovalDecision.AcceptForSession
                "decline" -> ApprovalDecision.Decline
                "cancel" -> ApprovalDecision.Cancel
                else -> ApprovalDecision.Cancel
            }
            is JsonObject -> {
                val entry = element.entries.firstOrNull() ?: return ApprovalDecision.Cancel
                if (entry.key == "acceptWithExecpolicyAmendment") {
                    val amendment = jsonDecoder.json.decodeFromJsonElement(
                        ExecPolicyAmendment.serializer(),
                        entry.value
                    )
                    ApprovalDecision.AcceptWithExecpolicyAmendment(amendment)
                } else {
                    ApprovalDecision.Cancel
                }
            }
            else -> ApprovalDecision.Cancel
        }
    }
}

@Serializable
data class CommandExecutionRequestApprovalParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val reason: String? = null,
    val proposedExecpolicyAmendment: ExecPolicyAmendment? = null
)

@Serializable
data class CommandExecutionRequestApprovalResponse(
    val decision: ApprovalDecision
)

@Serializable
data class FileChangeRequestApprovalParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val reason: String? = null,
    val grantRoot: String? = null
)

@Serializable
data class FileChangeRequestApprovalResponse(
    val decision: ApprovalDecision
)
// ============== 璐︽埛鐩稿叧 ==============

@Serializable
data class AccountInfo(
    val type: String,  // "apiKey" or "chatgpt"
    val email: String? = null,
    val planType: String? = null
)

@Serializable
data class AccountReadResult(
    val account: AccountInfo? = null,
    val requiresOpenaiAuth: Boolean = false
)

@Serializable
data class AccountReadParams(
    val refreshToken: Boolean = false
)

// ============== MCP status ==============

@Serializable
data class ListMcpServerStatusParams(
    val cursor: String? = null,
    val limit: Int? = null
)

@Serializable
data class McpTool(
    val name: String? = null,
    val description: String? = null,
    @SerialName("inputSchema")
    val inputSchema: JsonElement? = null
)

@Serializable
data class McpResource(
    val uri: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("mimeType")
    val mimeType: String? = null
)

@Serializable
data class McpResourceTemplate(
    @SerialName("uriTemplate")
    val uriTemplate: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("mimeType")
    val mimeType: String? = null
)

@Serializable
data class McpServerStatus(
    val name: String,
    val tools: Map<String, McpTool> = emptyMap(),
    val resources: List<McpResource> = emptyList(),
    @SerialName("resourceTemplates")
    val resourceTemplates: List<McpResourceTemplate> = emptyList(),
    @SerialName("authStatus")
    val authStatus: String? = null
)

@Serializable
data class ListMcpServerStatusResponse(
    val data: List<McpServerStatus>,
    val nextCursor: String? = null
)

@Serializable
data class McpServerOauthLoginParams(
    val name: String,
    val scopes: List<String>? = null,
    @SerialName("timeoutSecs")
    val timeoutSecs: Long? = null
)

@Serializable
data class McpServerOauthLoginResponse(
    @SerialName("authorizationUrl")
    val authorizationUrl: String
)

@Serializable
data class RateLimits(
    val primary: RateLimitInfo? = null,
    val secondary: RateLimitInfo? = null
)

@Serializable
data class RateLimitInfo(
    val usedPercent: Int,
    val windowDurationMins: Int,
    val resetsAt: Long
)


// ============== Usage ==============

@Serializable
data class TokenUsageBreakdown(
    val totalTokens: Long,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val reasoningOutputTokens: Long
)

@Serializable
data class ThreadTokenUsage(
    val total: TokenUsageBreakdown,
    val last: TokenUsageBreakdown,
    @SerialName("modelContextWindow")
    val modelContextWindow: Long? = null
)

@Serializable
data class ThreadTokenUsageUpdatedNotification(
    val threadId: String,
    val turnId: String,
    val tokenUsage: ThreadTokenUsage
)
