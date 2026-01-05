package com.asakii.codex.agent.sdk.appserver

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Codex App-Server JSON-RPC 2.0 客户端
 *
 * 处理双向 JSON-RPC 通信:
 * - 客户端请求 → 服务器响应
 * - 服务器通知 → 客户端处理
 * - 服务器请求 (审批) → 客户端响应
 */
class CodexJsonRpcClient(
    private val stdin: OutputStream,
    private val stdout: InputStream,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : Closeable {
    @PublishedApi
    internal val logger = Logger.getLogger(CodexJsonRpcClient::class.java.name)

    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true  // Allow non-quoted primitives (Codex sends id as integer)
    }

    private val writer = BufferedWriter(OutputStreamWriter(stdin, Charsets.UTF_8))
    private val reader = BufferedReader(InputStreamReader(stdout, Charsets.UTF_8))

    // 等待响应的请求
    @PublishedApi
    internal val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonRpcResponse>>()

    // 通知事件流
    private val _notifications = MutableSharedFlow<JsonRpcNotification>(extraBufferCapacity = 100)
    val notifications: SharedFlow<JsonRpcNotification> = _notifications.asSharedFlow()

    // 服务器请求 (审批) 流
    private val _serverRequests = MutableSharedFlow<ServerRequest>(extraBufferCapacity = 10)
    val serverRequests: SharedFlow<ServerRequest> = _serverRequests.asSharedFlow()

    private val isRunning = AtomicBoolean(false)
    private var readerJob: Job? = null

    /**
     * 启动消息读取循环
     */
    fun start() {
        if (isRunning.getAndSet(true)) return

        readerJob = scope.launch {
            try {
                while (isActive && isRunning.get()) {
                    val line = withContext(Dispatchers.IO) {
                        reader.readLine()
                    } ?: break

                    if (line.isBlank()) continue

                    try {
                        processMessage(line)
                    } catch (e: Exception) {
                        logger.warning("Error processing message: ${e.message}")
                        if (logger.isLoggable(Level.FINE)) {
                            logger.fine("Failed message payload: ${line.take(500)}")
                        }
                    }
                }
            } catch (e: IOException) {
                if (isRunning.get()) {
                    logger.warning("Reader error: ${e.message}")
                }
            } finally {
                isRunning.set(false)
            }
        }
    }

    private suspend fun processMessage(line: String) {
        val jsonElement = json.parseToJsonElement(line)
        val obj = jsonElement.jsonObject

        when {
            // 响应: 有 id 且有 result 或 error
            obj.containsKey("id") && (obj.containsKey("result") || obj.containsKey("error")) -> {
                val response = json.decodeFromJsonElement<JsonRpcResponse>(jsonElement)
                if (response.error != null) {
                    logger.warning("RPC response error: id=${response.id} code=${response.error.code} msg=${response.error.message}")
                } else if (logger.isLoggable(Level.FINE)) {
                    logger.fine("RPC response: id=${response.id}")
                }
                pendingRequests.remove(response.id)?.complete(response)
            }
            // 请求: 有 id 和 method (服务器请求，如审批)
            obj.containsKey("id") && obj.containsKey("method") -> {
                val request = json.decodeFromJsonElement<JsonRpcRequest>(jsonElement)
                logger.info("RPC server request: method=${request.method} id=${request.id}")
                handleServerRequest(request)
            }
            // 通知: 只有 method，没有 id
            obj.containsKey("method") && !obj.containsKey("id") -> {
                val notification = json.decodeFromJsonElement<JsonRpcNotification>(jsonElement)
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("RPC notification: method=${notification.method}")
                }
                _notifications.emit(notification)
            }
        }
    }

    private suspend fun handleServerRequest(request: JsonRpcRequest) {
        val serverRequest = when (request.method) {
            "item/commandExecution/requestApproval" -> {
                val params = request.params?.let {
                    json.decodeFromJsonElement<CommandExecutionRequestApprovalParams>(it)
                }
                params?.let { ServerRequest.CommandApproval(request.id, it) }
            }
            "item/fileChange/requestApproval" -> {
                val params = request.params?.let {
                    json.decodeFromJsonElement<FileChangeRequestApprovalParams>(it)
                }
                params?.let { ServerRequest.FileChangeApproval(request.id, it) }
            }
            else -> null
        }

        serverRequest?.let { _serverRequests.emit(it) }
    }

    /**
     * 发送请求并等待响应
     */
    suspend inline fun <reified T> request(method: String): T {
        return request<T, JsonElement>(method, null)
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified T, reified P> request(method: String, params: P? = null): T {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JsonRpcResponse>()
        pendingRequests[requestId] = deferred
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("RPC request: method=$method id=$requestId")
        }

        val request = buildJsonObject {
            put("method", method)
            put("id", requestId)
            params?.let {
                put("params", encodeToJsonElementSafely(it))
            }
        }

        sendLine(json.encodeToString(request))

        val response = deferred.await()

        if (response.error != null) {
            throw CodexRpcException(response.error.code, response.error.message)
        }

        @Suppress("UNCHECKED_CAST")
        return when {
            T::class == Unit::class -> Unit as T
            response.result != null -> json.decodeFromJsonElement<T>(response.result)
            else -> throw CodexRpcException(-1, "Empty result")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified P> requestUnit(method: String, params: P? = null) {
        request<Unit, P>(method, params)
    }

    /**
     * 发送通知 (无参数)
     */
    suspend fun notify(method: String) {
        val notification = buildJsonObject {
            put("method", method)
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("RPC notify: method=$method")
        }
        sendLine(json.encodeToString(notification))
    }

    /**
     * 发送通知 (无需响应)
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified P> notify(method: String, params: P? = null) {
        val notification = buildJsonObject {
            put("method", method)
            params?.let {
                put("params", encodeToJsonElementSafely(it))
            }
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("RPC notify: method=$method")
        }
        sendLine(json.encodeToString(notification))
    }

    /**
     * 响应服务器请求 (审批)
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified R> respondToServerRequest(requestId: String, result: R) {
        val response = buildJsonObject {
            put("id", requestId)
            put("result", encodeToJsonElementSafely(result))
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("RPC response to server request: id=$requestId")
        }
        sendLine(json.encodeToString(response))
    }

    @PublishedApi
    internal suspend fun sendLine(line: String) {
        withContext(Dispatchers.IO) {
            synchronized(writer) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        }
    }

    @PublishedApi
    internal inline fun <reified T> encodeToJsonElementSafely(value: T): JsonElement {
        return try {
            json.encodeToJsonElement(value)
        } catch (_: SerializationException) {
            anyToJsonElement(value)
        }
    }

    @PublishedApi
    internal fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> buildJsonObject {
                value.forEach { (k, v) ->
                    put(k.toString(), anyToJsonElement(v))
                }
            }
            is Iterable<*> -> buildJsonArray {
                value.forEach { add(anyToJsonElement(it)) }
            }
            is Array<*> -> buildJsonArray {
                value.forEach { add(anyToJsonElement(it)) }
            }
            else -> JsonPrimitive(value.toString())
        }
    }


    override fun close() {
        isRunning.set(false)
        readerJob?.cancel()
        pendingRequests.values.forEach {
            it.completeExceptionally(IOException("Client closed"))
        }
        pendingRequests.clear()
        runCatching { writer.close() }
        runCatching { reader.close() }
    }
}

/**
 * 服务器请求类型
 */
sealed class ServerRequest {
    abstract val requestId: String

    data class CommandApproval(
        override val requestId: String,
        val params: CommandExecutionRequestApprovalParams
    ) : ServerRequest()

    data class FileChangeApproval(
        override val requestId: String,
        val params: FileChangeRequestApprovalParams
    ) : ServerRequest()
}

/**
 * RPC 异常
 */
class CodexRpcException(
    val code: Int,
    override val message: String
) : RuntimeException(message)
