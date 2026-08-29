package com.reqlab.core.network.mcp

import com.reqlab.core.model.JsonRpcEnvelope
import com.reqlab.core.model.MCP_PROTOCOL_VERSION
import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.McpHttpMode
import com.reqlab.core.network.SseParser
import com.reqlab.core.network.isSseContentType
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StreamableHttpTransport(
    private val httpClient: HttpClient,
    private val config: McpConnectionConfig,
    private val scope: CoroutineScope,
) : McpTransport {
    private val _incoming = MutableSharedFlow<JsonRpcEnvelope>(extraBufferCapacity = 256)
    override val incoming: SharedFlow<JsonRpcEnvelope> = _incoming

    override var sessionId: String? = null
        private set
    override var protocolVersion: String = MCP_PROTOCOL_VERSION
    override var lastEventId: String? = null
        private set
    override var lastResponseHeaders: Map<String, List<String>>? = null
        private set
    override val negotiatedHttpMode: McpHttpMode = McpHttpMode.STREAMABLE_2025_06_18

    private val mutex = Mutex()
    private var getJob: Job? = null
    private var closed = false

    override suspend fun start() = Unit

    override suspend fun onHandshakeComplete() {
        openGetStream()
    }

    override suspend fun send(message: JsonRpcEnvelope) {
        val body = mcpJson.encodeToString(JsonRpcEnvelope.serializer(), message)
        val response = httpClient.post(config.url) {
            contentType(ContentType.Application.Json)
            applyMcpHeaders(config.headers, config.auth, config.oauth, sessionId, protocolVersion, lastEventId)
            setBody(body)
        }
        handleResponse(response, isInitialize = message.method == "initialize")
    }

    override suspend fun close() {
        closed = true
        getJob?.cancel()
        getJob = null
        val sid = sessionId
        if (!sid.isNullOrBlank()) {
            runCatching {
                httpClient.delete(config.url) {
                    applyMcpHeaders(config.headers, config.auth, config.oauth, sid, protocolVersion)
                }
            }
        }
        sessionId = null
    }

    private suspend fun handleResponse(response: HttpResponse, isInitialize: Boolean) {
        lastResponseHeaders = response.headers.entries().associate { it.key to it.value }
        val sid = response.headers["Mcp-Session-Id"] ?: response.headers["mcp-session-id"]
        if (!sid.isNullOrBlank()) sessionId = sid
        when (response.status) {
            HttpStatusCode.Unauthorized -> throw McpUnauthorizedException(
                wwwAuthenticate = response.headers[HttpHeaders.WWWAuthenticate],
                oauth = config.oauth,
            )
            HttpStatusCode.NotFound -> throw McpSessionExpiredException()
            HttpStatusCode.MethodNotAllowed -> throw McpLegacyHintException()
            HttpStatusCode.Accepted -> return
            else -> Unit
        }
        if (response.status.value >= 400 && response.status != HttpStatusCode.NotFound) {
            val text = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            if (response.status == HttpStatusCode.BadRequest && text.contains("endpoint", ignoreCase = true)) {
                throw McpLegacyHintException(text)
            }
            throw McpProtocolException("HTTP ${response.status.value}: $text")
        }
        val contentType = response.headers[HttpHeaders.ContentType]
        if (isSseContentType(contentType)) {
            drainSse(response)
        } else {
            val text = response.bodyAsText()
            parseJsonRpc(text)?.let { _incoming.emit(it) }
        }
        if (isInitialize && contentType != null && isSseContentType(contentType) && sessionId == null) {
            // still valid; stateless
        }
    }

    private suspend fun drainSse(response: HttpResponse) {
        val channel = response.bodyAsChannel()
        val parser = SseParser()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val event = parser.feedLine(line) ?: continue
            if (!event.id.isNullOrBlank()) lastEventId = event.id
            parseJsonRpc(event.data)?.let { _incoming.emit(it) }
        }
        parser.flush()?.let { event ->
            if (!event.id.isNullOrBlank()) lastEventId = event.id
            parseJsonRpc(event.data)?.let { _incoming.emit(it) }
        }
    }

    private fun openGetStream() {
        if (closed) return
        getJob?.cancel()
        getJob = scope.launch {
            try {
                val response = httpClient.get(config.url) {
                    accept(ContentType.Text.EventStream)
                    header(HttpHeaders.CacheControl, "no-cache")
                    applyMcpHeaders(config.headers, config.auth, config.oauth, sessionId, protocolVersion, lastEventId)
                }
                if (response.status == HttpStatusCode.MethodNotAllowed ||
                    response.status == HttpStatusCode.NotFound
                ) {
                    return@launch
                }
                if (response.status == HttpStatusCode.Unauthorized) return@launch
                if (isSseContentType(response.headers[HttpHeaders.ContentType])) {
                    drainSse(response)
                }
            } catch (_: CancellationException) {
            } catch (_: Exception) {
            }
        }
    }

    suspend fun reconnectGetStream() {
        mutex.withLock { openGetStream() }
    }
}
