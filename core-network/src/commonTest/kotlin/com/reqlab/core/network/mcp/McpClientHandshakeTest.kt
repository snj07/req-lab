package com.reqlab.core.network.mcp

import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.McpContent
import com.reqlab.core.model.McpCreateMessageResult
import com.reqlab.core.model.McpElicitAction
import com.reqlab.core.model.McpHttpMode
import com.reqlab.core.model.McpTransportType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpClientHandshakeTest {

    @Test
    fun initialize_and_list_tools() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Get || request.method == HttpMethod.Delete) {
                return@MockEngine respond("", HttpStatusCode.MethodNotAllowed)
            }
            val body = request.body.toByteArray().decodeToString()
            val envelope = mcpJson.decodeFromString(com.reqlab.core.model.JsonRpcEnvelope.serializer(), body)
            val result = when (envelope.method) {
                "initialize" -> """{"protocolVersion":"2025-06-18","capabilities":{"tools":{"listChanged":true},"resources":{"subscribe":true},"prompts":{}},"serverInfo":{"name":"mock","version":"1"}}"""
                "tools/list" -> """{"tools":[{"name":"echo","description":"echo","inputSchema":{"type":"object"}}]}"""
                "notifications/initialized" -> null
                else -> error(envelope.method.orEmpty())
            }
            if (result == null) {
                respond("", HttpStatusCode.Accepted)
            } else {
                respond(
                    """{"jsonrpc":"2.0","id":${envelope.id},"result":$result}""",
                    HttpStatusCode.OK,
                    headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "Mcp-Session-Id" to listOf("s1"),
                    ),
                )
            }
        }
        val client = McpClient(this, HttpClient(engine) { expectSuccess = false })
        val init = client.connect(McpConnectionConfig(url = "https://example/mcp", httpMode = McpHttpMode.STREAMABLE_2025_06_18))
        assertEquals("mock", init.serverInfo.name)
        assertEquals("s1", client.sessionId)
        val tools = client.listTools()
        assertEquals(listOf("echo"), tools.map { it.name })
        client.disconnect()
    }

    @Test
    fun inbound_sampling_and_elicitation() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val client = McpClient(
            scope = this,
            stdioFactory = { transport },
            callTimeoutMs = 5_000,
            handlers = McpClientHandlers(
                onSampling = {
                    McpCreateMessageResult(content = McpContent(type = "text", text = "hello"))
                },
                onElicit = { com.reqlab.core.model.McpElicitResult(action = McpElicitAction.ACCEPT, content = buildJsonObject { put("ok", true) }) },
            ),
        )
        val connect = async {
            client.connect(McpConnectionConfig(transport = McpTransportType.STDIO, command = "unused"))
        }
        val initLine = written.receive()
        assertTrue(initLine.contains("initialize"))
        val initId = mcpJson.decodeFromString(
            com.reqlab.core.model.JsonRpcEnvelope.serializer(),
            initLine,
        ).id
        inbound.send("""{"jsonrpc":"2.0","id":$initId,"result":{"protocolVersion":"2025-06-18","capabilities":{},"serverInfo":{"name":"t","version":"1"}}}""")
        written.receive() // notifications/initialized
        connect.await()
        inbound.send("""{"jsonrpc":"2.0","id":"srv-1","method":"sampling/createMessage","params":{"messages":[],"maxTokens":16}}""")
        inbound.send("""{"jsonrpc":"2.0","id":"srv-2","method":"elicitation/create","params":{"message":"hi","requestedSchema":{"type":"object"}}}""")
        val replies = listOf(written.receive(), written.receive())
        assertTrue(replies.any { it.contains("hello") && it.contains("srv-1") })
        assertTrue(replies.any { it.contains("accept") && it.contains("srv-2") })
        client.disconnect()
    }
}
