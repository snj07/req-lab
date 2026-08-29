package com.reqlab.core.network.mcp

import com.reqlab.core.model.JsonRpcEnvelope
import com.reqlab.core.model.MCP_PROTOCOL_VERSION_LEGACY
import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.McpHttpMode
import com.reqlab.core.network.SseParser
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * MCP 2024-11-05 HTTP+SSE transport: GET opens the SSE stream, first `endpoint`
 * event provides the POST URL, responses arrive on the SSE stream.
 */
class LegacyHttpSseTransport(
    private val httpClient: HttpClient,
    private val config: McpConnectionConfig,
    private val scope: CoroutineScope,
) : McpTransport {
    private val _incoming = MutableSharedFlow<JsonRpcEnvelope>(extraBufferCapacity = 256)
    override val incoming: SharedFlow<JsonRpcEnvelope> = _incoming

    override var sessionId: String? = null
        private set
    override var protocolVersion: String = MCP_PROTOCOL_VERSION_LEGACY
    override var lastEventId: String? = null
        private set
    override var lastResponseHeaders: Map<String, List<String>>? = null
        private set
    override val negotiatedHttpMode: McpHttpMode = McpHttpMode.LEGACY_2024_11_05

    private var postUrl: String? = null
    private var sseJob: Job? = null
    private val endpointReady = CompletableDeferred<String>()

    override suspend fun start() {
        sseJob = scope.launch { openSse() }
        postUrl = awaitWithWallClockTimeout(endpointReady, 15_000) {
            McpTransportException("Timed out waiting for legacy SSE endpoint event")
        }
    }

    override suspend fun send(message: JsonRpcEnvelope) {
        val target = postUrl ?: throw McpTransportException("Legacy SSE endpoint URL not ready")
        val body = mcpJson.encodeToString(JsonRpcEnvelope.serializer(), message)
        val response = httpClient.post(target) {
            contentType(ContentType.Application.Json)
            applyMcpHeaders(config.headers, config.auth, config.oauth, sessionId, protocolVersion, lastEventId)
            setBody(body)
        }
        lastResponseHeaders = response.headers.entries().associate { it.key to it.value }
    }

    override suspend fun close() {
        sseJob?.cancel()
        sseJob = null
        postUrl = null
    }

    private suspend fun openSse() {
        val response = httpClient.get(config.url) {
            accept(ContentType.Text.EventStream)
            header(HttpHeaders.CacheControl, "no-cache")
            applyMcpHeaders(config.headers, config.auth, config.oauth, sessionId, protocolVersion, lastEventId)
        }
        val channel = response.bodyAsChannel()
        val parser = SseParser()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val event = parser.feedLine(line) ?: continue
            if (!event.id.isNullOrBlank()) lastEventId = event.id
            if (event.eventType == "endpoint") {
                val relative = event.data.trim()
                val resolved = resolveEndpoint(config.url, relative)
                postUrl = resolved
                if (!endpointReady.isCompleted) endpointReady.complete(resolved)
            } else {
                parseJsonRpc(event.data)?.let { _incoming.emit(it) }
            }
        }
        parser.flush()?.let { event ->
            parseJsonRpc(event.data)?.let { _incoming.emit(it) }
        }
    }

    companion object {
        fun resolveEndpoint(baseUrl: String, endpoint: String): String {
            if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) return endpoint
            val slash = baseUrl.indexOf('/', baseUrl.indexOf("://") + 3)
            val origin = if (slash < 0) baseUrl.trimEnd('/') else baseUrl.substring(0, slash)
            return if (endpoint.startsWith("/")) origin + endpoint else "$origin/$endpoint"
        }
    }
}
