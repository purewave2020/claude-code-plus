package com.asakii.ai.agent.sdk.client

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.capabilities.AiPermissionMode
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.CodexOverrides
import com.asakii.ai.agent.sdk.model.ImageContent
import com.asakii.ai.agent.sdk.model.TextContent
import com.asakii.ai.agent.sdk.model.UiResultMessage
import com.asakii.ai.agent.sdk.model.UiStreamEvent
import com.asakii.ai.agent.sdk.model.UiTextDelta
import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.ModelReasoningEffort
import com.asakii.codex.agent.sdk.ThreadOptions
import com.asakii.codex.agent.sdk.appserver.AppServerEvent
import com.asakii.codex.agent.sdk.appserver.ListMcpServerStatusResponse
import com.asakii.codex.agent.sdk.appserver.McpServerStatus
import com.asakii.codex.agent.sdk.appserver.McpTool
import com.asakii.codex.agent.sdk.appserver.McpServerOauthLoginResponse
import com.asakii.codex.agent.sdk.appserver.ModelListResponse
import com.asakii.codex.agent.sdk.appserver.FileUpdateChange
import com.asakii.codex.agent.sdk.appserver.PatchChangeKind
import com.asakii.codex.agent.sdk.appserver.ReasoningSummary
import com.asakii.codex.agent.sdk.appserver.ThreadInfo
import com.asakii.codex.agent.sdk.appserver.ThreadItem
import com.asakii.codex.agent.sdk.appserver.TurnInfo
import com.asakii.codex.agent.sdk.appserver.TurnStatus
import com.asakii.codex.agent.sdk.appserver.SandboxPolicy
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CodexAgentClientImplTest {
    @Test
    fun `connect uses app server and starts new thread`() = runTest {
        val fake = FakeCodexAppServerApi()
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(
            AiAgentConnectOptions(
                provider = AiAgentProvider.CODEX,
                model = "gpt-5.1-codex-max"
            )
        )

        assertTrue(fake.initialized)
        assertEquals(1, fake.startThreadCalls)
        assertEquals(0, fake.resumeThreadCalls)
        assertNotNull(fake.lastStartThreadArgs)
        closeClient(client)
    }

    @Test
    fun `connect passes developer instructions to app server`() = runTest {
        val fake = FakeCodexAppServerApi()
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(
            AiAgentConnectOptions(
                provider = AiAgentProvider.CODEX,
                codex = CodexOverrides(
                    threadOptions = ThreadOptions(developerInstructions = "mcp instructions")
                )
            )
        )

        assertEquals("mcp instructions", fake.lastStartThreadArgs?.developerInstructions)
        closeClient(client)
    }

    @Test
    fun `connect passes codex client options to app server factory`() = runTest {
        val fake = FakeCodexAppServerApi()
        var capturedOptions: CodexClientOptions? = null
        var capturedThreadOptions: ThreadOptions? = null
        val factory = CodexAppServerApiFactory { options, threadOptions, _ ->
            capturedOptions = options
            capturedThreadOptions = threadOptions
            fake
        }

        val overrides = mapOf(
            "mcp_servers.user_interaction.url" to "\"http://127.0.0.1:1234/mcp\"",
            "features.web_search_request" to "true"
        )
        val clientOptions = CodexClientOptions(
            codexPathOverride = Paths.get("C:/tmp/codex.cmd"),
            configOverrides = overrides
        )
        val threadOptions = ThreadOptions(model = "gpt-5.2-codex")

        val client = CodexAgentClientImpl(appServerFactory = factory, scope = this)
        client.connect(
            AiAgentConnectOptions(
                provider = AiAgentProvider.CODEX,
                codex = CodexOverrides(
                    clientOptions = clientOptions,
                    threadOptions = threadOptions
                )
            )
        )

        assertEquals(overrides, capturedOptions?.configOverrides)
        assertEquals(clientOptions.codexPathOverride, capturedOptions?.codexPathOverride)
        assertEquals("gpt-5.2-codex", capturedThreadOptions?.model)
        closeClient(client)
    }

    @Test
    fun `connect resumes thread when resumeSessionId provided`() = runTest {
        val fake = FakeCodexAppServerApi()
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(
            AiAgentConnectOptions(
                provider = AiAgentProvider.CODEX,
                resumeSessionId = "thread-resume"
            )
        )

        assertEquals(0, fake.startThreadCalls)
        assertEquals(1, fake.resumeThreadCalls)
        assertEquals("thread-resume", fake.lastResumeThreadId)
        closeClient(client)
    }

    @Test
    fun `sendMessage streams app server events`() = runTest {
        val fake = FakeCodexAppServerApi()
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(
            AiAgentConnectOptions(
                provider = AiAgentProvider.CODEX,
                model = "gpt-5.1-codex-max"
            )
        )
        advanceUntilIdle()

        val events = mutableListOf<UiStreamEvent>()
        val collectJob = launch {
            client.streamEvents().collect { event ->
                events.add(event)
                if (event is UiResultMessage) {
                    cancel()
                }
            }
        }

        val sendJob = launch {
            client.sendMessage(AgentMessageInput(text = "hello"))
        }

        yield()

        val turnId = fake.lastStartTurnId ?: "turn-1"
        val threadId = "thread-1"
        fake.emit(AppServerEvent.TurnStarted(threadId, TurnInfo(id = turnId, status = TurnStatus.InProgress)))
        fake.emit(AppServerEvent.ItemStarted(threadId, turnId, ThreadItem.AgentMessage(id = "item-1", text = "")))
        fake.emit(AppServerEvent.AgentMessageDelta(threadId, turnId, itemId = "item-1", delta = "OK"))
        fake.emit(
            AppServerEvent.ItemCompleted(
                threadId,
                turnId,
                ThreadItem.AgentMessage(id = "item-1", text = "OK")
            )
        )
        fake.emit(AppServerEvent.TurnCompleted(threadId, TurnInfo(id = turnId, status = TurnStatus.Completed)))

        sendJob.join()
        collectJob.join()

        assertEquals(1, fake.startTurnCalls.size)
        assertTrue(events.any { it is UiTextDelta && it.text.contains("OK") })
        assertTrue(events.any { it is UiResultMessage && !it.isError && it.subtype == "completed" })
        closeClient(client)
    }

    @Test
    fun `sendMessage attaches images as local files`() = runTest {
        val fake = FakeCodexAppServerApi()
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(
            AiAgentConnectOptions(
                provider = AiAgentProvider.CODEX,
                model = "gpt-5.1-codex-max"
            )
        )
        advanceUntilIdle()

        val sendJob = launch {
            client.sendMessage(
                AgentMessageInput(
                    content = listOf(
                        TextContent("hello"),
                        ImageContent(data = "aGVsbG8=", mediaType = "image/png")
                    )
                )
            )
        }

        yield()

        val turnId = fake.lastStartTurnId ?: "turn-1"
        val threadId = "thread-1"
        fake.emit(AppServerEvent.TurnStarted(threadId, TurnInfo(id = turnId, status = TurnStatus.InProgress)))
        fake.emit(AppServerEvent.TurnCompleted(threadId, TurnInfo(id = turnId, status = TurnStatus.Completed)))

        sendJob.join()

        val images = fake.startTurnCalls.last().images
        assertEquals(1, images.size)
        val imagePath = Path.of(images.first())
        assertTrue(Files.exists(imagePath))
        closeClient(client)
    }

    @Test
    fun `command approval uses permission requester and accepts`() = runTest {
        val fake = FakeCodexAppServerApi()
        val requests = mutableListOf<PermissionRequest>()
        val requester = PermissionRequester { request ->
            requests.add(request)
            PermissionDecision(approved = true)
        }
        val client = CodexAgentClientImpl(
            permissionRequester = requester,
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(AiAgentConnectOptions(provider = AiAgentProvider.CODEX))
        advanceUntilIdle()
        advanceUntilIdle()
        advanceUntilIdle()
        yield()

        fake.emit(
            AppServerEvent.CommandApprovalRequired(
                requestId = "req-1",
                itemId = "item-1",
                threadId = "thread-1",
                turnId = "turn-1",
                command = "ls",
                cwd = "/repo",
                reason = "test",
                proposedExecpolicyAmendment = null
            )
        )

        advanceUntilIdle()

        assertEquals(listOf("req-1"), fake.acceptedCommands)
        assertTrue(fake.declinedCommands.isEmpty())
        assertEquals(1, requests.size)
        val payload = requests.first().inputJson as JsonObject
        assertEquals("Bash", requests.first().toolName)
        assertEquals("req-1", requests.first().toolUseId)
        assertEquals("ls", payload["command"]?.jsonPrimitive?.content)
        assertEquals("/repo", payload["cwd"]?.jsonPrimitive?.content)
        closeClient(client)
    }

    @Test
    fun `file change approval uses permission requester and declines`() = runTest {
        val fake = FakeCodexAppServerApi()
        val requests = mutableListOf<PermissionRequest>()
        val requester = PermissionRequester { request ->
            requests.add(request)
            PermissionDecision(approved = false, denyReason = "no")
        }
        val client = CodexAgentClientImpl(
            permissionRequester = requester,
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(AiAgentConnectOptions(provider = AiAgentProvider.CODEX))

        fake.emit(
            AppServerEvent.FileChangeApprovalRequired(
                requestId = "req-2",
                itemId = "item-2",
                threadId = "thread-1",
                turnId = "turn-2",
                changes = listOf(
                    FileUpdateChange(
                        path = "a.txt",
                        kind = PatchChangeKind.Update(),
                        diff = "+1"
                    )
                ),
                reason = "test",
                grantRoot = null
            )
        )

        advanceUntilIdle()

        assertEquals(listOf("req-2"), fake.declinedFileChanges)
        assertTrue(fake.acceptedFileChanges.isEmpty())
        assertEquals(1, requests.size)
        val payload = requests.first().inputJson as JsonObject
        val changes = payload["changes"] as? JsonArray
        assertEquals("Edit", requests.first().toolName)
        assertEquals("req-2", requests.first().toolUseId)
        assertNotNull(changes)
        val changeObj = changes[0] as JsonObject
        assertEquals("a.txt", changeObj["path"]?.jsonPrimitive?.content)
        closeClient(client)
    }

    @Test
    fun `setModel updates model used for turns`() = runTest {
        val fake = FakeCodexAppServerApi()
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(
            AiAgentConnectOptions(
                provider = AiAgentProvider.CODEX,
                model = "model-a"
            )
        )
        advanceUntilIdle()

        client.setModel("model-b")

        val sendJob = launch {
            client.sendMessage(AgentMessageInput(text = "hello"))
        }

        yield()

        val turnId = fake.lastStartTurnId ?: "turn-1"
        val threadId = "thread-1"
        fake.emit(AppServerEvent.TurnStarted(threadId, TurnInfo(id = turnId, status = TurnStatus.InProgress)))
        fake.emit(AppServerEvent.TurnCompleted(threadId, TurnInfo(id = turnId, status = TurnStatus.Completed)))

        sendJob.join()

        assertEquals("model-b", fake.startTurnCalls.last().model)
        closeClient(client)
    }

    @Test
    fun `bypass permission mode auto-approves command`() = runTest {
        val fake = FakeCodexAppServerApi()
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(AiAgentConnectOptions(provider = AiAgentProvider.CODEX))
        client.setPermissionMode(AiPermissionMode.BYPASS_PERMISSIONS)
        advanceUntilIdle()

        fake.emit(
            AppServerEvent.CommandApprovalRequired(
                requestId = "req-3",
                itemId = "item-3",
                threadId = "thread-1",
                turnId = "turn-3",
                command = "dir",
                cwd = null,
                reason = null,
                proposedExecpolicyAmendment = null
            )
        )

        advanceUntilIdle()

        assertEquals(listOf("req-3"), fake.acceptedCommands)
        assertTrue(fake.declinedCommands.isEmpty())
        closeClient(client)
    }

    @Test
    fun `accept edits mode auto-approves file changes`() = runTest {
        val fake = FakeCodexAppServerApi()
        val requests = mutableListOf<PermissionRequest>()
        val requester = PermissionRequester { request ->
            requests.add(request)
            PermissionDecision(approved = false)
        }
        val client = CodexAgentClientImpl(
            permissionRequester = requester,
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(AiAgentConnectOptions(provider = AiAgentProvider.CODEX))
        client.setPermissionMode(AiPermissionMode.ACCEPT_EDITS)
        advanceUntilIdle()

        fake.emit(
            AppServerEvent.FileChangeApprovalRequired(
                requestId = "req-4",
                itemId = "item-4",
                threadId = "thread-1",
                turnId = "turn-4",
                changes = listOf(
                    FileUpdateChange(
                        path = "b.txt",
                        kind = PatchChangeKind.Update(),
                        diff = "+1"
                    )
                ),
                reason = null,
                grantRoot = null
            )
        )

        advanceUntilIdle()

        assertEquals(listOf("req-4"), fake.acceptedFileChanges)
        assertTrue(requests.isEmpty())
        closeClient(client)
    }

    @Test
    fun `getMcpStatus and getMcpTools map app server response`() = runTest {
        val fake = FakeCodexAppServerApi()
        fake.mcpStatuses = listOf(
            McpServerStatus(
                name = "jetbrains",
                tools = mapOf(
                    "FileIndex" to McpTool(
                        name = "FileIndex",
                        description = "Index files",
                        inputSchema = buildJsonObject { put("type", "object") }
                    )
                ),
                authStatus = "bearerToken"
            )
        )
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(AiAgentConnectOptions(provider = AiAgentProvider.CODEX))

        val status = client.getMcpStatus()
        assertEquals(1, status.size)
        assertEquals("jetbrains", status.first().name)
        assertEquals("connected", status.first().status)

        val tools = client.getMcpTools("jetbrains")
        assertEquals(1, tools.count)
        assertEquals("FileIndex", tools.tools.first().name)
        assertEquals("Index files", tools.tools.first().description)
        closeClient(client)
    }

    @Test
    fun `sendMessage includes reasoning effort and summary`() = runTest {
        val fake = FakeCodexAppServerApi()
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(
            AiAgentConnectOptions(
                provider = AiAgentProvider.CODEX,
                codex = CodexOverrides(
                    threadOptions = ThreadOptions(
                        modelReasoningEffort = ModelReasoningEffort.HIGH,
                        modelReasoningSummary = "concise"
                    )
                )
            )
        )
        advanceUntilIdle()

        val sendJob = launch {
            client.sendMessage(AgentMessageInput(text = "hello"))
        }

        yield()

        val turnId = fake.lastStartTurnId ?: "turn-1"
        val threadId = "thread-1"
        fake.emit(AppServerEvent.TurnStarted(threadId, TurnInfo(id = turnId, status = TurnStatus.InProgress)))
        fake.emit(AppServerEvent.TurnCompleted(threadId, TurnInfo(id = turnId, status = TurnStatus.Completed)))

        sendJob.join()

        val last = fake.startTurnCalls.last()
        assertEquals("high", last.effort)
        assertEquals(ReasoningSummary.Concise, last.summary)
        closeClient(client)
    }

    @Test
    fun `startMcpOauthLogin returns url when auth required`() = runTest {
        val fake = FakeCodexAppServerApi()
        fake.mcpStatuses = listOf(
            McpServerStatus(
                name = "jetbrains",
                authStatus = "notLoggedIn"
            )
        )
        fake.oauthLoginUrl = "https://example.com/auth"
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ -> fake },
            scope = this
        )

        client.connect(AiAgentConnectOptions(provider = AiAgentProvider.CODEX))

        val url = client.startMcpOauthLogin("jetbrains")
        assertEquals("https://example.com/auth", url)
        assertEquals(listOf("jetbrains"), fake.oauthLoginCalls)
        closeClient(client)
    }

    @Test
    fun `reconnectMcp restarts app server and resumes thread`() = runTest {
        val first = FakeCodexAppServerApi()
        val second = FakeCodexAppServerApi()
        first.mcpStatuses = listOf(
            McpServerStatus(
                name = "jetbrains",
                authStatus = "bearerToken"
            )
        )
        second.mcpStatuses = listOf(
            McpServerStatus(
                name = "jetbrains",
                authStatus = "bearerToken",
                tools = mapOf(
                    "FileIndex" to McpTool(
                        name = "FileIndex",
                        description = "Index files",
                        inputSchema = buildJsonObject { put("type", "object") }
                    )
                )
            )
        )
        var createCalls = 0
        val client = CodexAgentClientImpl(
            appServerFactory = CodexAppServerApiFactory { _, _, _ ->
                createCalls += 1
                if (createCalls == 1) first else second
            },
            scope = this
        )

        client.connect(AiAgentConnectOptions(provider = AiAgentProvider.CODEX))

        val result = client.reconnectMcp("jetbrains")
        assertTrue(result.success)
        assertEquals("connected", result.status)
        assertEquals(1, result.toolsCount)
        assertTrue(second.initialized)
        assertEquals(1, second.resumeThreadCalls)
        assertEquals("thread-1", second.lastResumeThreadId)
        closeClient(client)
    }

    private suspend fun TestScope.closeClient(client: CodexAgentClientImpl) {
        client.disconnect()
        advanceUntilIdle()
    }

    private class FakeCodexAppServerApi : CodexAppServerApi {
        private val eventFlow = MutableSharedFlow<AppServerEvent>(replay = 1, extraBufferCapacity = 32)

        var initialized = false
            private set
        var startThreadCalls = 0
            private set
        var resumeThreadCalls = 0
            private set
        var lastResumeThreadId: String? = null
            private set
        var lastStartThreadArgs: StartThreadArgs? = null
            private set
        var startTurnCalls = mutableListOf<StartTurnArgs>()
            private set
        var lastStartTurnId: String? = null
            private set
        val acceptedCommands = mutableListOf<String>()
        val declinedCommands = mutableListOf<String>()
        val acceptedFileChanges = mutableListOf<String>()
        val declinedFileChanges = mutableListOf<String>()
        var mcpStatuses: List<McpServerStatus> = emptyList()
        var modelListResponse: ModelListResponse = ModelListResponse(data = emptyList())
        var oauthLoginUrl: String = "https://example.com"
        val oauthLoginCalls = mutableListOf<String>()

        override val events: Flow<AppServerEvent> = eventFlow

        suspend fun emit(event: AppServerEvent) {
            eventFlow.emit(event)
        }

        override suspend fun initialize(clientName: String, clientTitle: String, clientVersion: String) {
            initialized = true
        }

        override suspend fun startThread(
            model: String?,
            cwd: String?,
            approvalPolicy: String?,
            sandbox: String?,
            developerInstructions: String?
        ): ThreadInfo {
            startThreadCalls += 1
            lastStartThreadArgs = StartThreadArgs(model, cwd, approvalPolicy, sandbox, developerInstructions)
            return ThreadInfo(
                id = "thread-1",
                preview = "test",
                modelProvider = "openai",
                createdAt = 0,
                path = "/tmp/thread-1",
                cwd = "/tmp",
                cliVersion = "0.0.0"
            )
        }

        override suspend fun resumeThread(threadId: String): ThreadInfo {
            resumeThreadCalls += 1
            lastResumeThreadId = threadId
            return ThreadInfo(
                id = threadId,
                preview = "resume",
                modelProvider = "openai",
                createdAt = 0,
                path = "/tmp/$threadId",
                cwd = "/tmp",
                cliVersion = "0.0.0"
            )
        }

        override suspend fun startTurn(
            threadId: String,
            message: String,
            images: List<String>,
            cwd: String?,
            model: String?,
            approvalPolicy: String?,
            sandboxPolicy: SandboxPolicy?,
            effort: String?,
            summary: ReasoningSummary?
        ): TurnInfo {
            val turnId = "turn-${startTurnCalls.size + 1}"
            lastStartTurnId = turnId
            startTurnCalls.add(
                StartTurnArgs(
                    threadId,
                    message,
                    images,
                    cwd,
                    model,
                    approvalPolicy,
                    sandboxPolicy,
                    effort,
                    summary
                )
            )
            return TurnInfo(id = turnId, status = TurnStatus.InProgress)
        }

        override suspend fun interruptTurn(threadId: String, turnId: String) {
        }

        override suspend fun acceptCommand(rawId: JsonElement, forSession: Boolean) {
            acceptedCommands.add(rawId.toString())
        }

        override suspend fun declineCommand(rawId: JsonElement) {
            declinedCommands.add(rawId.toString())
        }

        override suspend fun acceptFileChange(rawId: JsonElement) {
            acceptedFileChanges.add(rawId.toString())
        }

        override suspend fun declineFileChange(rawId: JsonElement) {
            declinedFileChanges.add(rawId.toString())
        }

        override suspend fun listModels(cursor: String?, limit: Int?): ModelListResponse {
            return modelListResponse
        }

        override suspend fun listMcpServerStatus(
            cursor: String?,
            limit: Int?
        ): ListMcpServerStatusResponse {
            return ListMcpServerStatusResponse(data = mcpStatuses, nextCursor = null)
        }

        override suspend fun startMcpOauthLogin(
            name: String,
            scopes: List<String>?,
            timeoutSecs: Long?
        ): McpServerOauthLoginResponse {
            oauthLoginCalls.add(name)
            return McpServerOauthLoginResponse(authorizationUrl = oauthLoginUrl)
        }

        override fun close() {
        }

        data class StartThreadArgs(
            val model: String?,
            val cwd: String?,
            val approvalPolicy: String?,
            val sandbox: String?,
            val developerInstructions: String?
        )

        data class StartTurnArgs(
            val threadId: String,
            val message: String,
            val images: List<String>,
            val cwd: String?,
            val model: String?,
            val approvalPolicy: String?,
            val sandboxPolicy: SandboxPolicy?,
            val effort: String?,
            val summary: ReasoningSummary?
        )
    }
}
