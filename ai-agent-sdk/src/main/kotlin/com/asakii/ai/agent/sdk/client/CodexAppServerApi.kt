package com.asakii.ai.agent.sdk.client

import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.ThreadOptions
import com.asakii.codex.agent.sdk.appserver.AppServerEvent
import com.asakii.codex.agent.sdk.appserver.CodexAppServerClient
import com.asakii.codex.agent.sdk.appserver.ListMcpServerStatusResponse
import com.asakii.codex.agent.sdk.appserver.McpServerOauthLoginResponse
import com.asakii.codex.agent.sdk.appserver.ModelListResponse
import com.asakii.codex.agent.sdk.appserver.ReasoningSummary
import com.asakii.codex.agent.sdk.appserver.SandboxPolicy
import com.asakii.codex.agent.sdk.appserver.ThreadInfo
import com.asakii.codex.agent.sdk.appserver.TurnInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.Closeable
import java.nio.file.Path

internal interface CodexAppServerApi : Closeable {
    val events: Flow<AppServerEvent>

    suspend fun initialize(clientName: String, clientTitle: String, clientVersion: String)
    suspend fun startThread(
        model: String? = null,
        cwd: String? = null,
        approvalPolicy: String? = null,
        sandbox: String? = null,
        developerInstructions: String? = null
    ): ThreadInfo
    suspend fun resumeThread(threadId: String): ThreadInfo
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
    ): TurnInfo
    suspend fun interruptTurn(threadId: String, turnId: String)
    suspend fun acceptCommand(requestId: String, forSession: Boolean = false)
    suspend fun declineCommand(requestId: String)
    suspend fun acceptFileChange(requestId: String)
    suspend fun declineFileChange(requestId: String)
    suspend fun listModels(cursor: String? = null, limit: Int? = null): ModelListResponse
    suspend fun listMcpServerStatus(cursor: String? = null, limit: Int? = null): ListMcpServerStatusResponse
    suspend fun startMcpOauthLogin(
        name: String,
        scopes: List<String>? = null,
        timeoutSecs: Long? = null
    ): McpServerOauthLoginResponse
}

internal class DefaultCodexAppServerApi(private val client: CodexAppServerClient) : CodexAppServerApi {
    override val events: Flow<AppServerEvent> = client.events

    override suspend fun initialize(clientName: String, clientTitle: String, clientVersion: String) {
        client.initialize(clientName, clientTitle, clientVersion)
    }

    override suspend fun startThread(
        model: String?,
        cwd: String?,
        approvalPolicy: String?,
        sandbox: String?,
        developerInstructions: String?
    ): ThreadInfo = client.startThread(model, cwd, approvalPolicy, sandbox, developerInstructions)

    override suspend fun resumeThread(threadId: String): ThreadInfo = client.resumeThread(threadId)

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
    ): TurnInfo = client.startTurn(
        threadId,
        message,
        images = images,
        cwd = cwd,
        model = model,
        approvalPolicy = approvalPolicy,
        sandboxPolicy = sandboxPolicy,
        effort = effort,
        summary = summary
    )

    override suspend fun interruptTurn(threadId: String, turnId: String) {
        client.interruptTurn(threadId, turnId)
    }

    override suspend fun acceptCommand(requestId: String, forSession: Boolean) {
        client.acceptCommand(requestId, forSession)
    }

    override suspend fun declineCommand(requestId: String) {
        client.declineCommand(requestId)
    }

    override suspend fun acceptFileChange(requestId: String) {
        client.acceptFileChange(requestId)
    }

    override suspend fun declineFileChange(requestId: String) {
        client.declineFileChange(requestId)
    }

    override suspend fun listModels(cursor: String?, limit: Int?): ModelListResponse {
        return client.listModels(cursor = cursor, limit = limit)
    }

    override suspend fun listMcpServerStatus(cursor: String?, limit: Int?): ListMcpServerStatusResponse {
        return client.listMcpServerStatus(cursor = cursor, limit = limit)
    }

    override suspend fun startMcpOauthLogin(
        name: String,
        scopes: List<String>?,
        timeoutSecs: Long?
    ): McpServerOauthLoginResponse {
        return client.startMcpOauthLogin(name = name, scopes = scopes, timeoutSecs = timeoutSecs)
    }

    override fun close() = client.close()
}

internal fun interface CodexAppServerApiFactory {
    fun create(
        options: CodexClientOptions,
        threadOptions: ThreadOptions,
        scope: CoroutineScope
    ): CodexAppServerApi
}

internal object DefaultCodexAppServerApiFactory : CodexAppServerApiFactory {
    override fun create(
        options: CodexClientOptions,
        threadOptions: ThreadOptions,
        scope: CoroutineScope
    ): CodexAppServerApi {
        val workingDirectory = threadOptions.workingDirectory?.let { Path.of(it) }
        val env = options.env ?: emptyMap()
        val configOverrides = options.configOverrides ?: emptyMap()
        val client = CodexAppServerClient.create(
            codexPath = options.codexPathOverride,
            workingDirectory = workingDirectory,
            env = env,
            configOverrides = configOverrides,
            scope = scope
        )
        return DefaultCodexAppServerApi(client)
    }
}
