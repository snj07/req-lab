package com.reqlab.core.network

import com.reqlab.core.model.MessageDirection
import com.reqlab.core.model.RealtimeMessage
import com.reqlab.core.model.RealtimeProtocol
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Connection state for realtime protocols.
 */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Manages realtime protocol connections (WebSocket, SSE).
 *
 * Each instance manages a single connection.
 * Messages are emitted via [messages] SharedFlow.
 * Connection state is emitted via [connectionState].
 */
class RealtimeClient(
    private val scope: CoroutineScope,
) {
    private val _messages = MutableSharedFlow<RealtimeMessage>(extraBufferCapacity = 100)
    val messages: SharedFlow<RealtimeMessage> = _messages

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1, extraBufferCapacity = 10)
    val connectionState: SharedFlow<ConnectionState> = _connectionState

    private var connectionJob: Job? = null
    private var webSocketSession: io.ktor.websocket.WebSocketSession? = null
    private val sendChannel = Channel<String>(Channel.BUFFERED)

    private fun generateId(): String = Random.nextLong().toString(36)

    /**
     * Connect to a WebSocket endpoint.
     */
    fun connectWebSocket(url: String, headers: Map<String, String> = emptyMap()) {
        disconnect()
        _connectionState.tryEmit(ConnectionState.CONNECTING)

        connectionJob = scope.launch {
            try {
                val client = HttpClient {
                    install(WebSockets)
                }

                client.webSocket(url) {
                    webSocketSession = this
                    _connectionState.tryEmit(ConnectionState.CONNECTED)

                    // Launch sender coroutine
                    val senderJob = launch {
                        for (message in sendChannel) {
                            send(Frame.Text(message))
                            _messages.emit(
                                RealtimeMessage(
                                    id = generateId(),
                                    direction = MessageDirection.SENT,
                                    content = message,
                                    timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                    protocol = RealtimeProtocol.WEBSOCKET,
                                )
                            )
                        }
                    }

                    // Receive loop
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                _messages.emit(
                                    RealtimeMessage(
                                        id = generateId(),
                                        direction = MessageDirection.RECEIVED,
                                        content = frame.readText(),
                                        timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                        protocol = RealtimeProtocol.WEBSOCKET,
                                    )
                                )
                            }
                        }
                    } finally {
                        senderJob.cancel()
                    }
                }

                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            } catch (e: CancellationException) {
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            } catch (e: Exception) {
                _connectionState.tryEmit(ConnectionState.ERROR)
                _messages.emit(
                    RealtimeMessage(
                        id = generateId(),
                        direction = MessageDirection.RECEIVED,
                        content = "Connection error: ${e.message}",
                        timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                        protocol = RealtimeProtocol.WEBSOCKET,
                        eventType = "error",
                    )
                )
            }
        }
    }

    /**
     * Connect to an SSE endpoint.
     */
    fun connectSSE(url: String, headers: Map<String, String> = emptyMap()) {
        disconnect()
        _connectionState.tryEmit(ConnectionState.CONNECTING)

        connectionJob = scope.launch {
            try {
                val client = HttpClient()
                val response = client.get(url) {
                    headers.forEach { (key, value) ->
                        this.headers.append(key, value)
                    }
                    this.headers.append("Accept", "text/event-stream")
                    this.headers.append("Cache-Control", "no-cache")
                }

                _connectionState.tryEmit(ConnectionState.CONNECTED)

                val channel = response.bodyAsChannel()
                var eventType = ""
                var data = StringBuilder()

                while (!channel.isClosedForRead && isActive) {
                    val line = channel.readUTF8Line() ?: break

                    when {
                        line.startsWith("event:") -> {
                            eventType = line.removePrefix("event:").trim()
                        }
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trim())
                        }
                        line.isBlank() && data.isNotEmpty() -> {
                            // End of event
                            _messages.emit(
                                RealtimeMessage(
                                    id = generateId(),
                                    direction = MessageDirection.RECEIVED,
                                    content = data.toString(),
                                    timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                                    protocol = RealtimeProtocol.SSE,
                                    eventType = eventType.ifEmpty { "message" },
                                )
                            )
                            data = StringBuilder()
                            eventType = ""
                        }
                    }
                }

                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            } catch (e: CancellationException) {
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            } catch (e: Exception) {
                _connectionState.tryEmit(ConnectionState.ERROR)
                _messages.emit(
                    RealtimeMessage(
                        id = generateId(),
                        direction = MessageDirection.RECEIVED,
                        content = "SSE connection error: ${e.message}",
                        timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                        protocol = RealtimeProtocol.SSE,
                        eventType = "error",
                    )
                )
            }
        }
    }

    /**
     * Send a text message (only applicable for WebSocket and Socket.IO).
     */
    fun sendMessage(text: String) {
        sendChannel.trySend(text)
    }

    /**
     * Disconnect the current session.
     */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        webSocketSession = null
        _connectionState.tryEmit(ConnectionState.DISCONNECTED)
    }
}
