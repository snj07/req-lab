package com.reqlab.core.network.mcp

import com.reqlab.core.model.JsonRpcEnvelope
import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.jsonRpcId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamableHttpTransportTest {

    @Test
    fun json_response_and_session_header() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                return@MockEngine respond("no", HttpStatusCode.MethodNotAllowed)
            }
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("initialize"))
            assertEquals("application/json, text/event-stream", request.headers[HttpHeaders.Accept])
            respond(
                content = """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"mock","version":"1"}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Mcp-Session-Id" to listOf("sess-1"),
                ),
            )
        }
        val transport = StreamableHttpTransport(HttpClient(engine) { expectSuccess = false }, McpConnectionConfig(url = "https://example/mcp"), this)
        transport.start()
        val received = async { transport.incoming.first() }
        transport.send(JsonRpcEnvelope(id = jsonRpcId(1), method = "initialize"))
        val msg = received.await()
        assertEquals("sess-1", transport.sessionId)
        assertTrue(msg.isResponse())
        transport.close()
    }

    @Test
    fun sse_response_captures_event_id() = runTest {
        val sse = "id: 7\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}\n\n"
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(sse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
        }
        val transport = StreamableHttpTransport(HttpClient(engine) { expectSuccess = false }, McpConnectionConfig(url = "https://example/mcp"), this)
        val received = async { transport.incoming.first() }
        transport.send(JsonRpcEnvelope(id = jsonRpcId(1), method = "ping"))
        received.await()
        assertEquals("7", transport.lastEventId)
        transport.close()
    }

    @Test
    fun captures_response_headers() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                return@MockEngine respond("no", HttpStatusCode.MethodNotAllowed)
            }
            respond(
                content = """{"jsonrpc":"2.0","id":1,"result":{"ok":true}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Mcp-Session-Id" to listOf("sess-9"),
                    "X-Custom" to listOf("hello"),
                ),
            )
        }
        val transport = StreamableHttpTransport(HttpClient(engine) { expectSuccess = false }, McpConnectionConfig(url = "https://example/mcp"), this)
        val received = async { transport.incoming.first() }
        transport.send(JsonRpcEnvelope(id = jsonRpcId(1), method = "tools/list"))
        received.await()
        val headers = transport.lastResponseHeaders
        assertTrue(headers != null, "lastResponseHeaders should be captured")
        assertEquals(listOf("hello"), headers!!["X-Custom"])
        assertEquals(listOf("sess-9"), headers["Mcp-Session-Id"])
        transport.close()
    }

    @Test
    fun sends_bearer_auth_and_custom_headers() = runTest {
        var auth: String? = null
        var apiKey: String? = null
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                return@MockEngine respond("no", HttpStatusCode.MethodNotAllowed)
            }
            auth = request.headers[HttpHeaders.Authorization]
            apiKey = request.headers["X-Api-Key"]
            respond(
                content = """{"jsonrpc":"2.0","id":1,"result":{"ok":true}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val config = McpConnectionConfig(
            url = "https://example/mcp",
            auth = com.reqlab.core.model.AuthConfig(
                type = com.reqlab.core.model.AuthType.BEARER,
                params = mapOf("token" to "abc"),
            ),
            headers = listOf(com.reqlab.core.model.KeyValueEntry("X-Api-Key", "k1")),
        )
        val transport = StreamableHttpTransport(HttpClient(engine) { expectSuccess = false }, config, this)
        val received = async { transport.incoming.first() }
        transport.send(JsonRpcEnvelope(id = jsonRpcId(1), method = "initialize"))
        received.await()
        assertEquals("Bearer abc", auth)
        assertEquals("k1", apiKey)
        transport.close()
    }

    @Test
    fun accepted_notification_does_not_emit() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Accepted) }
        val transport = StreamableHttpTransport(HttpClient(engine) { expectSuccess = false }, McpConnectionConfig(url = "https://example/mcp"), this)
        transport.send(JsonRpcEnvelope(method = "notifications/initialized"))
        assertNull(transport.incoming.replayCache.firstOrNull())
        transport.close()
    }

    @Test
    fun not_found_is_session_expired() = runTest {
        val engine = MockEngine { respond("gone", HttpStatusCode.NotFound) }
        val transport = StreamableHttpTransport(HttpClient(engine) { expectSuccess = false }, McpConnectionConfig(url = "https://example/mcp"), this)
        val thrown = runCatching { transport.send(JsonRpcEnvelope(id = jsonRpcId(1), method = "tools/list")) }.exceptionOrNull()
        assertTrue(thrown is McpSessionExpiredException)
        transport.close()
    }

    @Test
    fun method_not_allowed_hints_legacy() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.MethodNotAllowed) }
        val transport = StreamableHttpTransport(HttpClient(engine) { expectSuccess = false }, McpConnectionConfig(url = "https://example/mcp"), this)
        val thrown = runCatching { transport.send(JsonRpcEnvelope(id = jsonRpcId(1), method = "initialize")) }.exceptionOrNull()
        assertTrue(thrown is McpLegacyHintException)
        transport.close()
    }
}
