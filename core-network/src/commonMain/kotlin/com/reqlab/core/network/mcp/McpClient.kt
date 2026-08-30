package com.reqlab.core.network.mcp

import com.reqlab.core.model.JsonRpcEnvelope
import com.reqlab.core.model.JsonRpcError
import com.reqlab.core.model.JsonRpcErrorCodes
import com.reqlab.core.model.MCP_PROTOCOL_VERSION
import com.reqlab.core.model.McpClientCapabilities
import com.reqlab.core.model.McpCompleteRequest
import com.reqlab.core.model.McpCompleteResult
import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.McpConnectionState
import com.reqlab.core.model.McpCreateMessageRequest
import com.reqlab.core.model.McpCreateMessageResult
import com.reqlab.core.model.McpElicitAction
import com.reqlab.core.model.McpElicitRequest
import com.reqlab.core.model.McpElicitResult
import com.reqlab.core.model.McpGetPromptResult
import com.reqlab.core.model.McpHttpMode
import com.reqlab.core.model.McpImplementation
import com.reqlab.core.model.McpInitializeParams
import com.reqlab.core.model.McpInitializeResult
import com.reqlab.core.model.McpListPromptsResult
import com.reqlab.core.model.McpListResourceTemplatesResult
import com.reqlab.core.model.McpListResourcesResult
import com.reqlab.core.model.McpListRootsResult
import com.reqlab.core.model.McpListToolsResult
import com.reqlab.core.model.McpLogEntry
import com.reqlab.core.model.McpLogEntryKind
import com.reqlab.core.model.McpLogLevel
import com.reqlab.core.model.McpOAuthDebugEntry
import com.reqlab.core.model.McpProgressNotification
import com.reqlab.core.model.McpPrompt
import com.reqlab.core.model.McpReadResourceResult
import com.reqlab.core.model.McpResource
import com.reqlab.core.model.McpResourceTemplate
import com.reqlab.core.model.McpRoot
import com.reqlab.core.model.McpSamplingMode
import com.reqlab.core.model.McpTool
import com.reqlab.core.model.McpToolResult
import com.reqlab.core.model.McpTransportType
import com.reqlab.core.model.jsonRpcId
import com.reqlab.core.network.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class McpClientHandlers(
    var onSampling: suspend (McpCreateMessageRequest) -> McpCreateMessageResult = {
        McpCreateMessageResult(
            content = com.reqlab.core.model.McpContent(type = "text", text = "mock reply from ReqLab"),
        )
    },
    var onRoots: suspend () -> List<McpRoot> = { emptyList() },
    var onElicit: suspend (McpElicitRequest) -> McpElicitResult = {
        McpElicitResult(action = McpElicitAction.ACCEPT, content = JsonObject(emptyMap()))
    },
)

class McpClient(
    private val scope: CoroutineScope,
    private val httpClient: HttpClient = defaultMcpHttpClient(),
    val handlers: McpClientHandlers = McpClientHandlers(),
    private val callTimeoutMs: Long = 30_000L,
    private val oauthClient: McpOAuthClient? = null,
    private val stdioFactory: (McpConnectionConfig) -> McpTransport = { createStdioTransport(it) },
) {
    private val pending = linkedMapOf<String, CompletableDeferred<JsonRpcEnvelope>>()
    private val pendingMutex = Mutex()
    private var nextId = 1L
    private var transport: McpTransport? = null
    private var inboundJob: Job? = null
    // Isolate GET-SSE / callback jobs so CIO ClosedSelectorException on disconnect
    // cannot fail the caller's runBlocking (qa E2E). Recreated on each connect.
    private lateinit var workers: Job
    private lateinit var workerScope: CoroutineScope
    var config: McpConnectionConfig = McpConnectionConfig()
        private set
    var initializeResult: McpInitializeResult? = null
        private set
    val oauthDebug: List<McpOAuthDebugEntry>
        get() = oauthClient?.debugLog.orEmpty()

    private val _state = MutableStateFlow(McpConnectionState.DISCONNECTED)
    val state: StateFlow<McpConnectionState> = _state
    private val _logs = MutableSharedFlow<McpLogEntry>(replay = 64, extraBufferCapacity = 256)
    val logs: SharedFlow<McpLogEntry> = _logs
    private val _notifications = MutableSharedFlow<JsonRpcEnvelope>(extraBufferCapacity = 256)
    val notifications: SharedFlow<JsonRpcEnvelope> = _notifications
    private val _progress = MutableSharedFlow<McpProgressNotification>(extraBufferCapacity = 32)
    val progress: SharedFlow<McpProgressNotification> = _progress

    val negotiatedHttpMode: McpHttpMode? get() = transport?.negotiatedHttpMode
    val sessionId: String? get() = transport?.sessionId
    val protocolVersion: String get() = transport?.protocolVersion ?: MCP_PROTOCOL_VERSION
    val lastResponseHeaders: Map<String, List<String>>? get() = transport?.lastResponseHeaders

    /** Exact JSON-RPC frame from the most recent inbound response (used by the Response pane). */
    var lastReceivedPayload: String? = null
        private set

    init {
        workerScope = newWorkerScope()
    }

    private fun newWorkerScope(): CoroutineScope {
        workers = SupervisorJob(scope.coroutineContext[Job])
        return CoroutineScope(
            scope.coroutineContext + workers + CoroutineExceptionHandler { _, _ -> },
        )
    }

    suspend fun connect(
        connection: McpConnectionConfig,
        variableLayers: List<Map<String, String>> = emptyList(),
        oauthRetry: Boolean = true,
    ): McpInitializeResult {
        disconnect()
        workerScope = newWorkerScope()
        config = resolveMcpConfig(connection, variableLayers)
        applyConfigHandlers(config)
        _state.value = McpConnectionState.CONNECTING
        log(McpLogEntryKind.STATE, "Connecting via ${config.transport} ${config.httpMode}")
        try {
            val created = createTransport(config)
            transport = created
            inboundJob = workerScope.launch { created.incoming.collect { routeInbound(it) } }
            created.start()
            val result = handshake(created)
            created.onHandshakeComplete()
            initializeResult = result
            _state.value = McpConnectionState.CONNECTED
            log(McpLogEntryKind.STATE, "Connected ${result.serverInfo.name} ${result.protocolVersion}")
            return result
        } catch (e: McpUnauthorizedException) {
            val oauth = oauthClient
            val oauthConfig = config.oauth
            if (oauthRetry && oauth != null && oauthConfig != null) {
                val updated = oauth.authorize(config.url, oauthConfig, e.wwwAuthenticate)
                config = config.copy(oauth = updated)
                return connect(config, emptyList(), oauthRetry = false)
            }
            _state.value = McpConnectionState.ERROR
            log(McpLogEntryKind.ERROR, e.message ?: "Unauthorized")
            throw e
        } catch (e: Exception) {
            _state.value = McpConnectionState.ERROR
            log(McpLogEntryKind.ERROR, e.message ?: e.toString())
            throw e
        }
    }

    suspend fun disconnect() {
        failPending("Disconnected")
        inboundJob?.cancel()
        inboundJob = null
        workers.cancel()
        runCatching { transport?.close() }
        transport = null
        initializeResult = null
        lastReceivedPayload = null
        _state.value = McpConnectionState.DISCONNECTED
        log(McpLogEntryKind.STATE, "Disconnected")
    }

    suspend fun listTools(): List<McpTool> = paginate("tools/list") { cursor ->
        val result = request<McpListToolsResult>("tools/list", cursorParams(cursor))
        result.tools to result.nextCursor
    }

    suspend fun callTool(name: String, arguments: JsonElement? = null, progressToken: String? = null): McpToolResult {
        val params = buildJsonObject {
            put("name", name)
            if (arguments != null) put("arguments", arguments)
            if (progressToken != null) {
                put("_meta", buildJsonObject { put("progressToken", progressToken) })
            }
        }
        return request("tools/call", params)
    }

    suspend fun listResources(): List<McpResource> = paginate("resources/list") { cursor ->
        val result = request<McpListResourcesResult>("resources/list", cursorParams(cursor))
        result.resources to result.nextCursor
    }

    suspend fun listResourceTemplates(): List<McpResourceTemplate> = paginate("resources/templates/list") { cursor ->
        val result = request<McpListResourceTemplatesResult>("resources/templates/list", cursorParams(cursor))
        result.resourceTemplates to result.nextCursor
    }

    suspend fun readResource(uri: String): McpReadResourceResult =
        request("resources/read", buildJsonObject { put("uri", uri) })

    suspend fun subscribeResource(uri: String) {
        val env = rpcCall("resources/subscribe", buildJsonObject { put("uri", uri) })
        env.error?.let { throw McpProtocolException("${it.code} ${it.message}") }
    }

    suspend fun unsubscribeResource(uri: String) {
        val env = rpcCall("resources/unsubscribe", buildJsonObject { put("uri", uri) })
        env.error?.let { throw McpProtocolException("${it.code} ${it.message}") }
    }

    suspend fun listPrompts(): List<McpPrompt> = paginate("prompts/list") { cursor ->
        val result = request<McpListPromptsResult>("prompts/list", cursorParams(cursor))
        result.prompts to result.nextCursor
    }

    suspend fun getPrompt(name: String, arguments: Map<String, String> = emptyMap()): McpGetPromptResult {
        val params = buildJsonObject {
            put("name", name)
            if (arguments.isNotEmpty()) {
                put("arguments", buildJsonObject { arguments.forEach { (k, v) -> put(k, v) } })
            }
        }
        return request("prompts/get", params)
    }

    suspend fun complete(ref: JsonObject, argumentName: String, argumentValue: String): McpCompleteResult {
        val req = McpCompleteRequest(ref, com.reqlab.core.model.McpCompleteArgument(argumentName, argumentValue))
        return request("completion/complete", encodeParams(req))
    }

    suspend fun setLogLevel(level: McpLogLevel) {
        rpcCall("logging/setLevel", buildJsonObject { put("level", level.name.lowercase()) })
    }

    suspend fun cancel(id: String, reason: String? = null) {
        val params = buildJsonObject {
            put("requestId", id)
            if (reason != null) put("reason", reason)
        }
        notify("notifications/cancelled", params)
        pendingMutex.withLock {
            pending.remove(id)?.completeExceptionally(kotlinx.coroutines.CancellationException(reason ?: "cancelled"))
        }
    }

    suspend fun notifyRootsChanged() {
        notify("notifications/roots/list_changed", null)
    }

    suspend fun generateSampling(request: McpCreateMessageRequest): McpCreateMessageResult {
        val url = config.samplingForwardUrl?.takeIf { it.isNotBlank() }
            ?: throw McpProtocolException("No sampling LLM URL")
        return forwardMcpSampling(
            httpClient = httpClient,
            url = url,
            bearerToken = config.samplingForwardToken,
            request = request,
            maxTokensCap = config.samplingMaxTokens,
        )
    }

    private fun applyConfigHandlers(cfg: McpConnectionConfig) {
        handlers.onRoots = { cfg.roots }
        handlers.onSampling = {
            when (cfg.samplingMode) {
                McpSamplingMode.MANUAL -> cancelledMcpSamplingResult()
                McpSamplingMode.MOCK -> McpCreateMessageResult(
                    content = com.reqlab.core.model.McpContent(type = "text", text = "mock reply from ReqLab"),
                )
                McpSamplingMode.FORWARD_LLM -> generateSampling(it)
            }
        }
        handlers.onElicit = {
            if (cfg.autoRespondElicitation) {
                McpElicitResult(action = McpElicitAction.ACCEPT, content = JsonObject(emptyMap()))
            } else {
                McpElicitResult(action = McpElicitAction.DECLINE)
            }
        }
    }

    private suspend fun handshake(active: McpTransport): McpInitializeResult {
        val params = McpInitializeParams(
            protocolVersion = MCP_PROTOCOL_VERSION,
            capabilities = McpClientCapabilities(),
            clientInfo = McpImplementation(name = "ReqLab", version = "1.18.0"),
        )
        val result = try {
            request<McpInitializeResult>("initialize", encodeParams(params))
        } catch (e: McpLegacyHintException) {
            if (config.httpMode != McpHttpMode.AUTO) throw e
            log(McpLogEntryKind.STATE, "Auto-detect falling back to legacy HTTP+SSE")
            inboundJob?.cancel()
            runCatching { active.close() }
            val legacy = LegacyHttpSseTransport(httpClient, config.copy(url = config.url), workerScope)
            transport = legacy
            legacy.start()
            inboundJob = workerScope.launch { legacy.incoming.collect { routeInbound(it) } }
            request("initialize", encodeParams(params))
        }
        transport?.protocolVersion = result.protocolVersion.ifBlank { MCP_PROTOCOL_VERSION }
        notify("notifications/initialized", null)
        return result
    }

    private suspend fun createTransport(cfg: McpConnectionConfig): McpTransport {
        return when (cfg.transport) {
            McpTransportType.STDIO -> stdioFactory(cfg)
            McpTransportType.STREAMABLE_HTTP -> when (cfg.httpMode) {
                McpHttpMode.LEGACY_2024_11_05 -> LegacyHttpSseTransport(httpClient, cfg, workerScope)
                else -> StreamableHttpTransport(
                    httpClient,
                    cfg,
                    workerScope,
                    replyClient = mcpAuxHttpClient(),
                    streamClient = mcpAuxHttpClient(),
                )
            }
        }
    }

    private suspend inline fun <reified T> request(method: String, params: JsonElement?): T {
        val envelope = rpcCall(method, params)
        val error = envelope.error
        if (error != null) {
            throw McpProtocolException("${error.code} ${error.message}")
        }
        return decodeResult(envelope.result)
    }

    private suspend fun rpcCall(method: String, params: JsonElement?): JsonRpcEnvelope {
        val active = transport ?: throw McpProtocolException("Not connected")
        val deferred = CompletableDeferred<JsonRpcEnvelope>()
        val idValue = pendingMutex.withLock {
            val id = nextId++
            pending[id.toString()] = deferred
            id
        }
        val key = idValue.toString()
        val message = JsonRpcEnvelope(id = jsonRpcId(idValue), method = method, params = params)
        log(McpLogEntryKind.SENT, method, mcpJson.encodeToString(JsonRpcEnvelope.serializer(), message), method, key)
        try {
            active.send(message)
        } catch (e: McpSessionExpiredException) {
            log(McpLogEntryKind.STATE, "Session expired; re-initialize")
            handshake(active)
            active.send(message)
        }
        // Keep the local deferred: legacy HTTP+SSE (and any transport that delivers on
        // another coroutine) can complete and remove the map entry during send().
        return try {
            awaitWithWallClockTimeout(deferred, callTimeoutMs) {
                McpTimeoutException("Timed out waiting for $method")
            }
        } catch (e: McpTimeoutException) {
            pendingMutex.withLock { pending.remove(key) }
            throw e
        }
    }

    private suspend fun notify(method: String, params: JsonElement?) {
        val active = transport ?: throw McpProtocolException("Not connected")
        val message = JsonRpcEnvelope(method = method, params = params)
        log(McpLogEntryKind.SENT, method, mcpJson.encodeToString(JsonRpcEnvelope.serializer(), message), method, null)
        active.send(message)
    }

    private suspend fun routeInbound(message: JsonRpcEnvelope) {
        val encoded = mcpJson.encodeToString(JsonRpcEnvelope.serializer(), message)
        when {
            message.isResponse() -> {
                val key = message.idKey() ?: return
                lastReceivedPayload = encoded
                log(McpLogEntryKind.RECEIVED, "response $key", encoded, null, key)
                val deferred = pendingMutex.withLock { pending.remove(key) }
                deferred?.complete(message)
            }
            message.isRequest() -> {
                log(McpLogEntryKind.RECEIVED, message.method.orEmpty(), encoded, message.method, message.idKey())
                workerScope.launch { handleServerRequest(message) }
            }
            message.isNotification() -> {
                log(McpLogEntryKind.NOTIFICATION, message.method.orEmpty(), encoded, message.method, null)
                _notifications.emit(message)
                if (message.method == "notifications/progress") {
                    runCatching {
                        decodeResult<McpProgressNotification>(message.params)
                    }.getOrNull()?.let { _progress.emit(it) }
                }
            }
        }
    }

    private suspend fun handleServerRequest(message: JsonRpcEnvelope) {
        val id = message.id ?: return
        try {
            val result: JsonElement = when (message.method) {
                "sampling/createMessage" -> {
                    val req = decodeResult<McpCreateMessageRequest>(message.params)
                    encodeParams(handlers.onSampling(req))
                }
                "roots/list" -> encodeParams(McpListRootsResult(handlers.onRoots()))
                "elicitation/create" -> {
                    val req = decodeResult<McpElicitRequest>(message.params)
                    encodeParams(handlers.onElicit(req))
                }
                "ping" -> JsonObject(emptyMap())
                else -> {
                    sendError(id, JsonRpcError(JsonRpcErrorCodes.METHOD_NOT_FOUND, "Method not found"))
                    return
                }
            }
            val response = JsonRpcEnvelope(id = id, result = result)
            transport?.send(response)
            log(McpLogEntryKind.SENT, "result ${message.method}", mcpJson.encodeToString(JsonRpcEnvelope.serializer(), response), message.method, message.idKey())
        } catch (e: Exception) {
            sendError(id, JsonRpcError(JsonRpcErrorCodes.INTERNAL_ERROR, e.message ?: "internal error"))
        }
    }

    private suspend fun sendError(id: JsonElement, error: JsonRpcError) {
        val response = JsonRpcEnvelope(id = id, error = error)
        transport?.send(response)
    }

    private suspend fun failPending(reason: String) {
        pendingMutex.withLock {
            pending.values.forEach { it.completeExceptionally(McpProtocolException(reason)) }
            pending.clear()
        }
    }

    private suspend fun <T> paginate(label: String, page: suspend (String?) -> Pair<List<T>, String?>): List<T> {
        val all = mutableListOf<T>()
        var cursor: String? = null
        do {
            val (items, next) = page(cursor)
            all += items
            cursor = next
        } while (!cursor.isNullOrBlank())
        log(McpLogEntryKind.STATE, "$label returned ${all.size}")
        return all
    }

    private fun cursorParams(cursor: String?): JsonElement? =
        if (cursor.isNullOrBlank()) null else buildJsonObject { put("cursor", cursor) }

    private fun log(kind: McpLogEntryKind, summary: String, payload: String? = null, method: String? = null, id: String? = null) {
        _logs.tryEmit(
            McpLogEntry(
                timestampEpochMillis = Clock.System.now().toEpochMilliseconds(),
                kind = kind,
                summary = summary,
                payload = payload,
                method = method,
                id = id,
            )
        )
    }
}

internal fun defaultMcpHttpClient(): HttpClient = mcpAuxHttpClient()

internal fun mcpAuxHttpClient(): HttpClient = createPlatformHttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
    }
    expectSuccess = false
}
