package com.asakii.codex.agent.sdk.appserver

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Codex App-Server 楂樺眰 API 瀹㈡埛绔?
 *
 * 鎻愪緵涓?codex app-server 浜や簰鐨勯珮灞?API:
 * - 鍒濆鍖栨彙鎵?
 * - 绾跨▼绠＄悊 (鍒涘缓銆佹仮澶嶃€佸垪琛?
 * - 鍥炲悎绠＄悊 (寮€濮嬨€佷腑鏂?
 * - 浜嬩欢娴佸鐞?
 * - 瀹℃壒娴佺▼
 *
 * 浣跨敤绀轰緥:
 * ```kotlin
 * val client = CodexAppServerClient.create()
 * client.initialize()
 *
 * val thread = client.startThread()
 * val turn = client.startTurn(thread.id, "Hello, Codex!")
 *
 * client.events.collect { event ->
 *     when (event) {
 *         is AppServerEvent.AgentMessageDelta -> print(event.delta)
 *         is AppServerEvent.TurnCompleted -> println("\nDone!")
 *         // ...
 *     }
 * }
 *
 * client.close()
 * ```
 */
class CodexAppServerClient private constructor(
    private val process: CodexAppServerProcess,
    private val scope: CoroutineScope
) : Closeable {
    private val logger = Logger.getLogger(CodexAppServerClient::class.java.name)

    private val rpc = process.client
    private var initialized = false
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val itemCache = ConcurrentHashMap<String, ThreadItem>()

    private val _events = MutableSharedFlow<AppServerEvent>(extraBufferCapacity = 100)
    val events: SharedFlow<AppServerEvent> = _events.asSharedFlow()

    private var eventProcessingJob: Job? = null

    init {
        startEventProcessing()
    }

    private fun startEventProcessing() {
        eventProcessingJob = scope.launch {
            // 澶勭悊閫氱煡浜嬩欢
            launch {
                rpc.notifications.collect { notification ->
                    processNotification(notification)
                }
            }

            // 澶勭悊鏈嶅姟鍣ㄨ姹?(瀹℃壒)
            launch {
                rpc.serverRequests.collect { request ->
                    processServerRequest(request)
                }
            }
        }
    }

    private suspend fun processNotification(notification: JsonRpcNotification) {
        val event = try {
            when (notification.method) {
                "thread/started" -> decodeParams<ThreadStartedNotification>(notification.params)
                    ?.let { AppServerEvent.ThreadStarted(it.thread) }
                "turn/started" -> decodeParams<TurnStartedNotification>(notification.params)
                    ?.let { AppServerEvent.TurnStarted(it.threadId, it.turn) }
                "turn/completed" -> decodeParams<TurnCompletedNotification>(notification.params)
                    ?.let { AppServerEvent.TurnCompleted(it.threadId, it.turn) }
                "item/started" -> decodeParams<ItemStartedNotification>(notification.params)
                    ?.let {
                        itemCache[it.item.id] = it.item
                        AppServerEvent.ItemStarted(it.threadId, it.turnId, it.item)
                    }
                "item/completed" -> decodeParams<ItemCompletedNotification>(notification.params)
                    ?.let {
                        itemCache[it.item.id] = it.item
                        AppServerEvent.ItemCompleted(it.threadId, it.turnId, it.item)
                    }
                "item/agentMessage/delta" -> decodeParams<AgentMessageDeltaNotification>(notification.params)
                    ?.let { AppServerEvent.AgentMessageDelta(it.threadId, it.turnId, it.itemId, it.delta) }
                "item/reasoning/summaryTextDelta" -> decodeParams<ReasoningSummaryTextDeltaNotification>(notification.params)
                    ?.let {
                        AppServerEvent.ReasoningDelta(
                            it.threadId,
                            it.turnId,
                            it.itemId,
                            it.delta
                        )
                    }
                "item/reasoning/textDelta" -> decodeParams<ReasoningTextDeltaNotification>(notification.params)
                    ?.let {
                        AppServerEvent.ReasoningDelta(
                            it.threadId,
                            it.turnId,
                            it.itemId,
                            it.delta
                        )
                    }
                "item/commandExecution/outputDelta" -> decodeParams<CommandExecutionOutputDeltaNotification>(notification.params)
                    ?.let { AppServerEvent.CommandOutputDelta(it.threadId, it.turnId, it.itemId, it.delta) }
                "thread/tokenUsage/updated" -> decodeParams<ThreadTokenUsageUpdatedNotification>(notification.params)
                    ?.let { AppServerEvent.TokenUsageUpdated(it.threadId, it.turnId, it.tokenUsage) }
                "error" -> decodeParams<ErrorNotification>(notification.params)
                    ?.let {
                        AppServerEvent.Error(
                            threadId = it.threadId,
                            turnId = it.turnId,
                            message = it.error.message,
                            willRetry = it.willRetry
                        )
                    }
                else -> null
            }
        } catch (e: Exception) {
            logger.warning("Failed to parse notification ${notification.method}: ${e.message}")
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Notification params: ${notification.params}")
            }
            null
        }

        event?.let { _events.emit(it) }
    }

    private inline fun <reified T> decodeParams(params: JsonElement?): T? {
        return params?.let { json.decodeFromJsonElement<T>(it) }
    }

    private suspend fun processServerRequest(request: ServerRequest) {
        val event = when (request) {
            is ServerRequest.CommandApproval -> {
                val cached = itemCache[request.params.itemId] as? ThreadItem.CommandExecution
                AppServerEvent.CommandApprovalRequired(
                    requestId = request.requestId,
                    rawId = request.rawId,
                    itemId = request.params.itemId,
                    threadId = request.params.threadId,
                    turnId = request.params.turnId,
                    command = cached?.command,
                    cwd = cached?.cwd,
                    reason = request.params.reason,
                    proposedExecpolicyAmendment = request.params.proposedExecpolicyAmendment
                )
            }
            is ServerRequest.FileChangeApproval -> {
                val cached = itemCache[request.params.itemId] as? ThreadItem.FileChange
                AppServerEvent.FileChangeApprovalRequired(
                    requestId = request.requestId,
                    rawId = request.rawId,
                    itemId = request.params.itemId,
                    threadId = request.params.threadId,
                    turnId = request.params.turnId,
                    changes = cached?.changes ?: emptyList(),
                    reason = request.params.reason,
                    grantRoot = request.params.grantRoot
                )
            }
        }
        _events.emit(event)
    }

    // ============== 鍒濆鍖?==============

    /**
     * 鍒濆鍖栬繛鎺?(蹇呴』棣栧厛璋冪敤)
     */
    suspend fun initialize(
        clientName: String = "claude-code-plus",
        clientTitle: String = "Claude Code Plus",
        clientVersion: String = "1.0.0"
    ): InitializeResult {
        if (initialized) {
            throw CodexAppServerException("Already initialized")
        }

        val params = InitializeParams(
            clientInfo = ClientInfo(
                name = clientName,
                title = clientTitle,
                version = clientVersion
            )
        )

        val result: InitializeResult = rpc.request("initialize", params)
        rpc.notify("initialized")
        initialized = true

        return result
    }

    // ============== 绾跨▼绠＄悊 ==============

    /**
     * 创建新线程
     *
     * @param model 模型名称
     * @param modelProvider 模型提供商
     * @param cwd 工作目录
     * @param approvalPolicy 审批策略
     * @param sandbox 沙箱模式
     * @param config 配置覆盖 (key=value 形式，如 mcp_servers.test.url)
     * @param baseInstructions 基础指令
     * @param developerInstructions 开发者指令
     */
    suspend fun startThread(
        model: String? = null,
        modelProvider: String? = null,
        cwd: String? = null,
        approvalPolicy: String? = null,
        sandbox: String? = null,
        config: Map<String, kotlinx.serialization.json.JsonElement>? = null,
        baseInstructions: String? = null,
        developerInstructions: String? = null
    ): ThreadInfo {
        checkInitialized()

        val params = ThreadStartParams(
            model = model,
            modelProvider = modelProvider,
            cwd = cwd,
            approvalPolicy = approvalPolicy,
            sandbox = sandbox,
            config = config,
            baseInstructions = baseInstructions,
            developerInstructions = developerInstructions
        )

        val result: ThreadStartResult = rpc.request("thread/start", params)
        return result.thread
    }

    /**
     * 鎭㈠宸叉湁绾跨▼
     */
    suspend fun resumeThread(threadId: String): ThreadInfo {
        checkInitialized()

        val params = ThreadResumeParams(threadId = threadId)
        val result: ThreadStartResult = rpc.request("thread/resume", params)
        return result.thread
    }

    /**
     * 褰掓。绾跨▼
     */
    suspend fun archiveThread(threadId: String) {
        checkInitialized()

        val params = ThreadArchiveParams(threadId = threadId)
        rpc.requestUnit("thread/archive", params)
    }

    /**
     * 鍒楀嚭鎵€鏈夌嚎绋?
     */
    suspend fun listThreads(
        cursor: String? = null,
        limit: Int? = null,
        modelProviders: List<String>? = null
    ): ThreadListResult {
        checkInitialized()

        val params = ThreadListParams(
            cursor = cursor,
            limit = limit,
            modelProviders = modelProviders
        )

        return rpc.request("thread/list", params)
    }

    // ============== Models ==============

    /**
     * List available models.
     */
    suspend fun listModels(
        cursor: String? = null,
        limit: Int? = null
    ): ModelListResponse {
        checkInitialized()

        val params = ModelListParams(
            cursor = cursor,
            limit = limit
        )

        return rpc.request("model/list", params)
    }

    // ============== MCP status ==============

    /**
     * List MCP server status.
     */
    suspend fun listMcpServerStatus(
        cursor: String? = null,
        limit: Int? = null
    ): ListMcpServerStatusResponse {
        checkInitialized()

        val params = ListMcpServerStatusParams(
            cursor = cursor,
            limit = limit
        )

        return rpc.request("mcpServerStatus/list", params)
    }

    /**
     * Start OAuth login for an MCP server.
     */
    suspend fun startMcpOauthLogin(
        name: String,
        scopes: List<String>? = null,
        timeoutSecs: Long? = null
    ): McpServerOauthLoginResponse {
        checkInitialized()

        val params = McpServerOauthLoginParams(
            name = name,
            scopes = scopes,
            timeoutSecs = timeoutSecs
        )

        return rpc.request("mcpServer/oauth/login", params)
    }

    // ============== 鍥炲悎绠＄悊 ==============

    /**
     * 寮€濮嬫柊鍥炲悎 (鍙戦€佺敤鎴锋秷鎭?
     */
    suspend fun startTurn(
        threadId: String,
        message: String,
        images: List<String> = emptyList(),
        cwd: String? = null,
        model: String? = null,
        approvalPolicy: String? = null,
        sandboxPolicy: SandboxPolicy? = null,
        effort: String? = null,
        summary: ReasoningSummary? = null
    ): TurnInfo {
        checkInitialized()

        val input = buildList {
            add(UserInput.Text(message))
            images.forEach { add(UserInput.LocalImage(it)) }
        }

        val params = TurnStartParams(
            threadId = threadId,
            input = input,
            cwd = cwd,
            model = model,
            approvalPolicy = approvalPolicy,
            sandboxPolicy = sandboxPolicy,
            effort = effort,
            summary = summary
        )

        val result: TurnStartResult = rpc.request("turn/start", params)
        return result.turn
    }

    /**
     * 涓柇褰撳墠鍥炲悎
     */
    suspend fun interruptTurn(threadId: String, turnId: String) {
        checkInitialized()

        val params = TurnInterruptParams(
            threadId = threadId,
            turnId = turnId
        )

        rpc.requestUnit("turn/interrupt", params)
    }

    // ============== 瀹℃壒鍝嶅簲 ==============

    /**
     * 鎺ュ彈鍛戒护鎵ц
     */
    suspend fun acceptCommand(rawId: JsonElement, forSession: Boolean = false) {
        val decision = if (forSession) {
            ApprovalDecision.AcceptForSession
        } else {
            ApprovalDecision.Accept
        }
        val response = CommandExecutionRequestApprovalResponse(decision = decision)
        rpc.respondToServerRequest(rawId, response)
    }

    /**
     * 拒绝命令执行
     */
    suspend fun declineCommand(rawId: JsonElement) {
        val response = CommandExecutionRequestApprovalResponse(decision = ApprovalDecision.Decline)
        rpc.respondToServerRequest(rawId, response)
    }

    /**
     * 接受文件修改
     */
    suspend fun acceptFileChange(rawId: JsonElement) {
        val response = FileChangeRequestApprovalResponse(decision = ApprovalDecision.Accept)
        rpc.respondToServerRequest(rawId, response)
    }

    /**
     * 拒绝文件修改
     */
    suspend fun declineFileChange(rawId: JsonElement) {
        val response = FileChangeRequestApprovalResponse(decision = ApprovalDecision.Decline)
        rpc.respondToServerRequest(rawId, response)
    }

    // ============== 账户相关 ==============

    /**
     * 读取账户信息
     */
    suspend fun readAccount(refreshToken: Boolean = false): AccountReadResult {
        checkInitialized()

        val params = AccountReadParams(refreshToken = refreshToken)
        return rpc.request("account/read", params)
    }

    // ============== 辅助方法 ==============

    private fun checkInitialized() {
        if (!initialized) {
            throw CodexAppServerException("Not initialized. Call initialize() first.")
        }
    }

    val isAlive: Boolean get() = process.isAlive

    override fun close() {
        eventProcessingJob?.cancel()
        process.close()
    }

    companion object {
        /**
         * 创建并启动 Codex App-Server 客户端
         *
         * @param codexPath Codex 可执行文件路径，null 则自动查找
         * @param workingDirectory 工作目录
         * @param env 环境变量
         * @param configOverrides 配置覆盖，格式为 key -> value（支持 dotted path，如 mcp_servers.xxx.url -> http://...）
         * @param scope 协程作用域
         */
        fun create(
            codexPath: Path? = null,
            workingDirectory: Path? = null,
            env: Map<String, String> = emptyMap(),
            configOverrides: Map<String, String> = emptyMap(),
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ): CodexAppServerClient {
            val process = CodexAppServerProcess.spawn(
                codexPath = codexPath,
                workingDirectory = workingDirectory,
                env = env,
                configOverrides = configOverrides,
                scope = scope
            )

            return CodexAppServerClient(process, scope)
        }
    }
}


/**
 * App-Server 浜嬩欢
 */
sealed class AppServerEvent {
    data class ThreadStarted(val thread: ThreadInfo) : AppServerEvent()

    data class TurnStarted(val threadId: String, val turn: TurnInfo) : AppServerEvent()
    data class TurnCompleted(val threadId: String, val turn: TurnInfo) : AppServerEvent()

    data class ItemStarted(val threadId: String, val turnId: String, val item: ThreadItem) : AppServerEvent()
    data class ItemCompleted(val threadId: String, val turnId: String, val item: ThreadItem) : AppServerEvent()

    data class AgentMessageDelta(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val delta: String
    ) : AppServerEvent()

    data class ReasoningDelta(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val delta: String
    ) : AppServerEvent()

    data class CommandOutputDelta(
        val threadId: String,
        val turnId: String,
        val itemId: String,
        val delta: String
    ) : AppServerEvent()

    data class CommandApprovalRequired(
        val requestId: String,
        /** 原始的 id JsonElement（保留 Codex 发送的类型：整数或字符串） */
        val rawId: JsonElement,
        val itemId: String,
        val threadId: String,
        val turnId: String,
        val command: String?,
        val cwd: String?,
        val reason: String?,
        val proposedExecpolicyAmendment: ExecPolicyAmendment?
    ) : AppServerEvent()

    data class FileChangeApprovalRequired(
        val requestId: String,
        /** 原始的 id JsonElement（保留 Codex 发送的类型：整数或字符串） */
        val rawId: JsonElement,
        val itemId: String,
        val threadId: String,
        val turnId: String,
        val changes: List<FileUpdateChange>,
        val reason: String?,
        val grantRoot: String?
    ) : AppServerEvent()

    data class TokenUsageUpdated(
        val threadId: String,
        val turnId: String,
        val usage: ThreadTokenUsage
    ) : AppServerEvent()

    data class Error(
        val threadId: String,
        val turnId: String,
        val message: String,
        val willRetry: Boolean
    ) : AppServerEvent()
}
