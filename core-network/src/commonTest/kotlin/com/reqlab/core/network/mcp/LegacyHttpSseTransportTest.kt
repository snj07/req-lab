package com.reqlab.core.network.mcp

import com.reqlab.core.model.JsonRpcEnvelope
import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.jsonRpcId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyHttpSseTransportTest {
    @Test
    fun endpoint_event_then_post() = runTest {
        var postedTo = ""
        val sse = "event: endpoint\ndata: /mcp/messages?sessionId=abc\n\n" +
            "id: 1\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}\n\n"
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                respond(
                    ByteReadChannel(sse),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            } else {
                postedTo = request.url.encodedPath
                respond("", HttpStatusCode.Accepted)
            }
        }
        val transport = LegacyHttpSseTransport(
            HttpClient(engine) { expectSuccess = false },
            McpConnectionConfig(url = "https://example.com/mcp/sse"),
            this,
        )
        val received = async { transport.incoming.first() }
        transport.start()
        transport.send(JsonRpcEnvelope(id = jsonRpcId(1), method = "ping"))
        received.await()
        assertEquals("/mcp/messages", postedTo.substringBefore("?").ifBlank { postedTo })
        assertTrue(postedTo.contains("/mcp/messages"))
        assertEquals("1", transport.lastEventId)
        transport.close()
    }

    @Test
    fun resolve_relative_endpoint() {
        assertEquals(
            "https://example.com/mcp/messages?sessionId=1",
            LegacyHttpSseTransport.resolveEndpoint("https://example.com/mcp/sse", "/mcp/messages?sessionId=1"),
        )
    }
}
