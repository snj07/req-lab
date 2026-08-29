package com.reqlab.core.network.mcp

import com.reqlab.core.model.JsonRpcEnvelope
import com.reqlab.core.model.McpHttpMode
import com.reqlab.core.model.McpOAuthConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Await [deferred] on the caller dispatcher while the timeout itself uses wall-clock
 * [Dispatchers.Default]. `withTimeout` on a test dispatcher is virtual and can fire
 * immediately; awaiting on Default deadlocks inbound jobs that stay on the test scheduler.
 */
internal suspend fun <T> awaitWithWallClockTimeout(
    deferred: CompletableDeferred<T>,
    timeoutMs: Long,
    timeoutException: () -> Exception,
): T {
    if (timeoutMs <= 0L) return deferred.await()
    val watcher = CoroutineScope(Dispatchers.Default).launch {
        delay(timeoutMs)
        deferred.completeExceptionally(timeoutException())
    }
    try {
        return deferred.await()
    } finally {
        watcher.cancel()
    }
}

interface McpTransport {
    val incoming: SharedFlow<JsonRpcEnvelope>
    val sessionId: String?
    var protocolVersion: String
    val lastEventId: String?
    val negotiatedHttpMode: McpHttpMode?

    /** Response headers from the most recent HTTP exchange, or `null` for non-HTTP transports (stdio). */
    val lastResponseHeaders: Map<String, List<String>>? get() = null

    suspend fun start()
    suspend fun send(message: JsonRpcEnvelope)
    suspend fun onHandshakeComplete() {}
    suspend fun close()
}

class McpSessionExpiredException(message: String = "MCP session expired") : Exception(message)
class McpUnauthorizedException(
    message: String = "Unauthorized",
    val wwwAuthenticate: String? = null,
    val oauth: McpOAuthConfig? = null,
) : Exception(message)
class McpLegacyHintException(message: String = "Server appears to use legacy HTTP+SSE transport") : Exception(message)
class McpTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
class McpTimeoutException(message: String) : Exception(message)
class McpProtocolException(message: String) : Exception(message)
