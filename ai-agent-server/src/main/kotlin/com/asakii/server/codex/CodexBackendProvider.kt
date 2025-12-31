package com.asakii.server.codex

import com.asakii.codex.agent.sdk.appserver.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Codex 鍚庣鎻愪緵鑰?
 *
 * 浣跨敤 codex app-server 妯″紡鎻愪緵瀹屾暣鐨?Codex 鍔熻兘锛?
 * 1. 绠＄悊 Codex app-server 杩涚▼鐢熷懡鍛ㄦ湡
 * 2. 绠＄悊 JSON-RPC 2.0 閫氫俊
 * 3. 鎻愪緵缁熶竴鐨勪細璇濈鐞嗘帴鍙?
 * 4. 浜嬩欢鏄犲皠鍜岃浆鍙?
 * 5. 澶勭悊 Approval 璇锋眰鍥炶皟
 *
 * 鍙傝€冿細external/openai-codex/codex-rs/app-server/README.md
 */
class CodexBackendProvider(
    private val workingDirectory: String,
    private val codexPath: String? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val logger = LoggerFactory.getLogger(CodexBackendProvider::class.java)

    // App-Server 瀹㈡埛绔?
    private var appServerClient: CodexAppServerClient? = null

    // 杩愯鐘舵€?
    private val isRunning = AtomicBoolean(false)

    // 浼氳瘽绠＄悊锛歵hreadId -> ThreadState
    private val threads = ConcurrentHashMap<String, ThreadState>()

    // 浜嬩欢娴侊細鐢ㄤ簬鍚戝閮ㄥ彂閫佷簨浠?
    private val _events = MutableSharedFlow<CodexEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<CodexEvent> = _events.asSharedFlow()

    // Approval 璇锋眰鍥炶皟
    private val _approvalRequests = Channel<ApprovalRequest>(Channel.UNLIMITED)
    val approvalRequests: Flow<ApprovalRequest> = _approvalRequests.receiveAsFlow()

    // 浜嬩欢澶勭悊浠诲姟
    private var eventProcessingJob: Job? = null

    /**
     * 浼氳瘽鐘舵€?
     */
    data class ThreadState(
        val threadId: String,
        val config: ThreadConfig,
        var isActive: Boolean = true,
        var currentTurnId: String? = null
    )

    /**
     * 浼氳瘽閰嶇疆
     */
    data class ThreadConfig(
        val model: String? = null,
        val cwd: String? = null,
        val approvalPolicy: String? = null,  // "never", "unlessTrusted", "always"
        val sandbox: String? = null  // "readOnly", "workspaceWrite", "dangerFullAccess"
    )

    /**
     * Codex 浜嬩欢锛堢粺涓€鏍煎紡锛?
     */
    sealed class CodexEvent {
        data class ThreadCreated(val threadId: String, val thread: ThreadInfo) : CodexEvent()
        data class ThreadResumed(val threadId: String, val thread: ThreadInfo) : CodexEvent()
        data class ThreadArchived(val threadId: String) : CodexEvent()

        data class TurnStarted(val threadId: String, val turnId: String, val turn: TurnInfo) : CodexEvent()
        data class TurnCompleted(val threadId: String, val turnId: String, val turn: TurnInfo) : CodexEvent()
        data class TurnInterrupted(val threadId: String, val turnId: String) : CodexEvent()
        data class TurnError(val threadId: String, val turnId: String, val error: String) : CodexEvent()

        data class ItemStarted(val item: ThreadItem) : CodexEvent()
        data class ItemCompleted(val item: ThreadItem) : CodexEvent()

        data class StreamingContent(
            val threadId: String,
            val itemId: String,
            val contentType: String,  // "text", "reasoning", "command_output"
            val content: String
        ) : CodexEvent()

        data class CommandApprovalRequired(
            val requestId: String,
            val threadId: String,
            val turnId: String,
            val command: String?,
            val cwd: String?,
            val reason: String?,
            val proposedExecpolicyAmendment: ExecPolicyAmendment?
        ) : CodexEvent()

        data class FileChangeApprovalRequired(
            val requestId: String,
            val threadId: String,
            val turnId: String,
            val changes: List<FileUpdateChange>,
            val reason: String?,
            val grantRoot: String?
        ) : CodexEvent()

        data class TokenUsage(
            val threadId: String,
            val turnId: String,
            val usage: ThreadTokenUsage
        ) : CodexEvent()

        data class Error(val message: String, val cause: Throwable? = null) : CodexEvent()
    }

    /**
     * Approval 璇锋眰 (淇濇寔鍚戝悗鍏煎)
     */
    data class ApprovalRequest(
        val threadId: String,
        val turnId: String,
        val requestId: String,
        val type: ApprovalType,
        val command: String? = null,
        val changes: List<FileUpdateChange>? = null,
        val reason: String? = null
    )

    enum class ApprovalType {
        COMMAND, FILE_CHANGE
    }

    /**
     * 鍚姩鍚庣
     */
    suspend fun start() {
        if (isRunning.getAndSet(true)) {
            throw IllegalStateException("CodexBackendProvider is already running")
        }

        logger.info("Starting Codex backend provider (app-server mode)...")

        try {
            // 1. 鍚姩 App-Server 瀹㈡埛绔?
            val codexPathObj = codexPath?.let { Paths.get(it) }
            val workingDirPath = Paths.get(workingDirectory)

            appServerClient = CodexAppServerClient.create(
                codexPath = codexPathObj,
                workingDirectory = workingDirPath,
                scope = scope
            )

            logger.info("Codex app-server process started")

            // 2. 鎵ц鍒濆鍖栨彙鎵?
            val initResult = appServerClient!!.initialize(
                clientName = "claude-code-plus",
                clientTitle = "Claude Code Plus",
                clientVersion = "1.0.0"
            )

            logger.info("Codex app-server initialized, userAgent: ${initResult.userAgent}")

            // 3. 鍚姩浜嬩欢鐩戝惉
            eventProcessingJob = scope.launch {
                listenToAppServerEvents()
            }

            logger.info("Codex backend provider started successfully")

        } catch (e: Exception) {
            isRunning.set(false)
            appServerClient?.close()
            appServerClient = null
            logger.error("Failed to start Codex backend provider", e)
            throw e
        }
    }

    /**
     * 鍋滄鍚庣
     */
    suspend fun stop() {
        if (!isRunning.getAndSet(false)) {
            logger.warn("CodexBackendProvider is not running")
            return
        }

        logger.info("Stopping Codex backend provider...")

        try {
            // 1. 鍙栨秷浜嬩欢澶勭悊
            eventProcessingJob?.cancel()

            // 2. 鍏抽棴 App-Server 瀹㈡埛绔?
            appServerClient?.close()
            appServerClient = null

            // 3. 娓呯悊鐘舵€?
            threads.clear()
            _approvalRequests.close()

            logger.info("Codex backend provider stopped successfully")

        } catch (e: Exception) {
            logger.error("Error while stopping Codex backend provider", e)
            throw e
        }
    }

    /**
     * 鍒涘缓鏂扮嚎绋嬶紙浼氳瘽锛?
     */
    suspend fun createThread(config: ThreadConfig = ThreadConfig()): String {
        ensureRunning()

        logger.info("Creating new thread with config: $config")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        val thread = client.startThread(
            model = config.model,
            cwd = config.cwd ?: workingDirectory,
            approvalPolicy = config.approvalPolicy,
            sandbox = config.sandbox
        )

        // 淇濆瓨绾跨▼鐘舵€?
        threads[thread.id] = ThreadState(
            threadId = thread.id,
            config = config,
            isActive = true
        )

        // 鍙戦€佷簨浠?
        _events.emit(CodexEvent.ThreadCreated(thread.id, thread))

        logger.info("Thread created successfully: ${thread.id}")

        return thread.id
    }

    /**
     * 鎭㈠绾跨▼
     */
    suspend fun resumeThread(threadId: String): ThreadInfo {
        ensureRunning()

        logger.info("Resuming thread: $threadId")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        val thread = client.resumeThread(threadId)

        // 鏇存柊绾跨▼鐘舵€?
        val existing = threads[threadId]
        if (existing != null) {
            existing.isActive = true
        } else {
            threads[threadId] = ThreadState(
                threadId = threadId,
                config = ThreadConfig(),
                isActive = true
            )
        }

        // 鍙戦€佷簨浠?
        _events.emit(CodexEvent.ThreadResumed(threadId, thread))

        logger.info("Thread resumed successfully: $threadId")

        return thread
    }

    /**
     * 褰掓。绾跨▼
     */
    suspend fun archiveThread(threadId: String) {
        ensureRunning()

        logger.info("Archiving thread: $threadId")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        client.archiveThread(threadId)

        // 鏇存柊鏈湴鐘舵€?
        threads[threadId]?.isActive = false

        // 鍙戦€佷簨浠?
        _events.emit(CodexEvent.ThreadArchived(threadId))

        logger.info("Thread archived successfully: $threadId")
    }

    /**
     * 鍒楀嚭鎵€鏈夌嚎绋?
     */
    suspend fun listThreads(
        cursor: String? = null,
        limit: Int? = null
    ): ThreadListResult {
        ensureRunning()

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")
        return client.listThreads(cursor = cursor, limit = limit)
    }

    /**
     * 寮€濮嬫柊鐨勫璇濊疆娆?
     */
    suspend fun startTurn(
        threadId: String,
        input: String,
        images: List<String> = emptyList(),
        cwd: String? = null,
        model: String? = null
    ): String {
        ensureRunning()

        val thread = threads[threadId]
            ?: throw IllegalArgumentException("Thread not found: $threadId")

        if (!thread.isActive) {
            throw IllegalStateException("Thread is not active: $threadId")
        }

        logger.info("Starting turn for thread: $threadId")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        val turn = client.startTurn(
            threadId = threadId,
            message = input,
            images = images,
            cwd = cwd ?: workingDirectory,
            model = model
        )

        // 鏇存柊绾跨▼鐘舵€?
        thread.currentTurnId = turn.id

        // 鍙戦€佷簨浠?
        _events.emit(CodexEvent.TurnStarted(threadId, turn.id, turn))

        logger.info("Turn started successfully: ${turn.id} for thread: $threadId")

        return turn.id
    }

    /**
     * 涓柇褰撳墠杞
     */
    suspend fun interruptTurn(threadId: String) {
        ensureRunning()

        val thread = threads[threadId]
            ?: throw IllegalArgumentException("Thread not found: $threadId")

        val turnId = thread.currentTurnId
            ?: throw IllegalStateException("No active turn for thread: $threadId")

        logger.info("Interrupting turn: $turnId for thread: $threadId")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        client.interruptTurn(threadId, turnId)

        // 鍙戦€佷簨浠?
        _events.emit(CodexEvent.TurnInterrupted(threadId, turnId))

        logger.info("Turn interrupted successfully: $turnId")
    }

    /**
     * 鍝嶅簲鍛戒护瀹℃壒
     */
    suspend fun respondToCommandApproval(requestId: String, approved: Boolean, forSession: Boolean = false) {
        ensureRunning()

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        if (approved) {
            client.acceptCommand(requestId, forSession)
        } else {
            client.declineCommand(requestId)
        }

        logger.info("Command approval response sent: $requestId, approved=$approved")
    }

    /**
     * 鍝嶅簲鏂囦欢淇敼瀹℃壒
     */
    suspend fun respondToFileChangeApproval(requestId: String, approved: Boolean) {
        ensureRunning()

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        if (approved) {
            client.acceptFileChange(requestId)
        } else {
            client.declineFileChange(requestId)
        }

        logger.info("File change approval response sent: $requestId, approved=$approved")
    }

    /**
     * 鑾峰彇绾跨▼鐘舵€?
     */
    fun getThreadState(threadId: String): ThreadState? {
        return threads[threadId]
    }

    /**
     * 鑾峰彇鎵€鏈夋椿璺冪嚎绋?
     */
    fun getActiveThreads(): List<ThreadState> {
        return threads.values.filter { it.isActive }
    }

    /**
     * 鐩戝惉 App-Server 浜嬩欢骞惰浆鎹负缁熶竴鏍煎紡
     */
    private suspend fun listenToAppServerEvents() {
        val client = appServerClient ?: return

        try {
            client.events.collect { event ->
                try {
                    mapAndEmitEvent(event)
                } catch (e: Exception) {
                    logger.error("Error processing App-Server event", e)
                    _events.emit(CodexEvent.Error("Failed to process event", e))
                }
            }
        } catch (e: Exception) {
            logger.error("Error in event listener", e)
            _events.emit(CodexEvent.Error("Event listener failed", e))
        }
    }

    /**
     * 灏?App-Server 浜嬩欢鏄犲皠涓虹粺涓€鏍煎紡
     */
    private suspend fun mapAndEmitEvent(event: AppServerEvent) {
        when (event) {
            is AppServerEvent.ThreadStarted -> {
                _events.emit(CodexEvent.ThreadCreated(event.thread.id, event.thread))
            }

            is AppServerEvent.TurnStarted -> {
                threads[event.threadId]?.currentTurnId = event.turn.id
                _events.emit(CodexEvent.TurnStarted(event.threadId, event.turn.id, event.turn))
            }

            is AppServerEvent.TurnCompleted -> {
                threads[event.threadId]?.currentTurnId = null
                _events.emit(CodexEvent.TurnCompleted(event.threadId, event.turn.id, event.turn))
            }

            is AppServerEvent.ItemStarted -> {
                _events.emit(CodexEvent.ItemStarted(event.item))
            }

            is AppServerEvent.ItemCompleted -> {
                _events.emit(CodexEvent.ItemCompleted(event.item))
            }

            is AppServerEvent.AgentMessageDelta -> {
                _events.emit(
                    CodexEvent.StreamingContent(
                        threadId = event.threadId,
                        itemId = event.itemId,
                        contentType = "text",
                        content = event.delta
                    )
                )
            }

            is AppServerEvent.ReasoningDelta -> {
                _events.emit(
                    CodexEvent.StreamingContent(
                        threadId = event.threadId,
                        itemId = event.itemId,
                        contentType = "reasoning",
                        content = event.delta
                    )
                )
            }

            is AppServerEvent.CommandOutputDelta -> {
                _events.emit(
                    CodexEvent.StreamingContent(
                        threadId = event.threadId,
                        itemId = event.itemId,
                        contentType = "command_output",
                        content = event.delta
                    )
                )
            }

            is AppServerEvent.CommandApprovalRequired -> {
                _events.emit(
                    CodexEvent.CommandApprovalRequired(
                        requestId = event.requestId,
                        threadId = event.threadId,
                        turnId = event.turnId,
                        command = event.command,
                        cwd = event.cwd,
                        reason = event.reason,
                        proposedExecpolicyAmendment = event.proposedExecpolicyAmendment
                    )
                )

                // 鍚屾椂鍙戦€佸埌 approvalRequests channel (鍚戝悗鍏煎)
                _approvalRequests.send(
                    ApprovalRequest(
                        threadId = event.threadId,
                        turnId = event.turnId,
                        requestId = event.requestId,
                        type = ApprovalType.COMMAND,
                        command = event.command,
                        reason = event.reason
                    )
                )
            }

            is AppServerEvent.FileChangeApprovalRequired -> {
                _events.emit(
                    CodexEvent.FileChangeApprovalRequired(
                        requestId = event.requestId,
                        threadId = event.threadId,
                        turnId = event.turnId,
                        changes = event.changes,
                        reason = event.reason,
                        grantRoot = event.grantRoot
                    )
                )

                // 鍚屾椂鍙戦€佸埌 approvalRequests channel (鍚戝悗鍏煎)
                _approvalRequests.send(
                    ApprovalRequest(
                        threadId = event.threadId,
                        turnId = event.turnId,
                        requestId = event.requestId,
                        type = ApprovalType.FILE_CHANGE,
                        changes = event.changes,
                        reason = event.reason
                    )
                )
            }

            is AppServerEvent.TokenUsageUpdated -> {
                _events.emit(CodexEvent.TokenUsage(event.threadId, event.turnId, event.usage))
            }

            is AppServerEvent.Error -> {
                _events.emit(CodexEvent.Error(event.message))
            }
        }
    }

    /**
     * 纭繚鍚庣姝ｅ湪杩愯
     */
    private fun ensureRunning() {
        if (!isRunning.get()) {
            throw IllegalStateException("CodexBackendProvider is not running")
        }
    }

    /**
     * 妫€鏌ュ悗绔槸鍚︽鍦ㄨ繍琛?
     */
    val running: Boolean get() = isRunning.get()
}
