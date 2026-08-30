package com.reqlab.ui.shared.mcp

import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.McpConnectionState
import com.reqlab.core.model.McpSamplingMode
import com.reqlab.core.model.McpTransportType
import com.reqlab.core.network.mcp.McpClient
import com.reqlab.core.network.mcp.NdjsonStdioTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpSessionStateTest {
    @Test
    fun connect_lists_tools_from_injected_client() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val session = McpSessionState(this) { scope ->
            McpClient(scope, stdioFactory = { transport }, callTimeoutMs = 5_000)
        }
        session.confirmStdio = true
        val job = async {
            session.connect(McpConnectionConfig(transport = McpTransportType.STDIO, command = "x"))
        }
        written.receive() // initialize
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"s","version":"1"}}}""")
        written.receive() // notifications/initialized
        written.receive() // tools/list
        inbound.send("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","inputSchema":{"type":"object"}}]}}""")
        job.await()
        assertEquals(McpConnectionState.CONNECTED, session.connectionState.value)
        assertEquals(listOf("echo"), session.tools.value.map { it.name })
        session.disconnect()
    }

    @Test
    fun subscribe_and_resource_updated_triggers_reread() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val session = McpSessionState(this) { scope ->
            McpClient(scope, stdioFactory = { transport }, callTimeoutMs = 5_000)
        }
        session.confirmStdio = true
        val job = async {
            session.connect(McpConnectionConfig(transport = McpTransportType.STDIO, command = "x"))
        }
        written.receive() // initialize
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"resources":{"subscribe":true}},"serverInfo":{"name":"s","version":"1"}}}""")
        written.receive() // notifications/initialized
        written.receive() // resources/list
        inbound.send("""{"jsonrpc":"2.0","id":2,"result":{"resources":[{"uri":"reqlab://doc","name":"Doc"}]}}""")
        job.await()
        assertTrue(session.supportsSubscribe())

        // Subscribe to the resource.
        val sub = async { session.subscribeResource("reqlab://doc") }
        written.receive() // resources/subscribe
        inbound.send("""{"jsonrpc":"2.0","id":3,"result":{}}""")
        sub.await()
        assertTrue(session.subscribedUris.value.contains("reqlab://doc"))

        // Server pushes an update notification -> session re-reads the resource.
        inbound.send("""{"jsonrpc":"2.0","method":"notifications/resources/updated","params":{"uri":"reqlab://doc"}}""")
        written.receive() // resources/read triggered by the re-read
        inbound.send("""{"jsonrpc":"2.0","id":4,"result":{"contents":[{"uri":"reqlab://doc","text":"updated"}]}}""")

        withTimeout(5_000) {
            session.lastResourceResult.first { it?.contents?.firstOrNull()?.text == "updated" }
        }
        assertEquals("updated", session.lastResourceResult.value?.contents?.firstOrNull()?.text)
        assertEquals("resource", session.lastOperation.value?.kind)
        session.disconnect()
    }

    @Test
    fun unsubscribe_removes_uri_from_subscribed_set() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val session = McpSessionState(this) { scope ->
            McpClient(scope, stdioFactory = { transport }, callTimeoutMs = 5_000)
        }
        session.confirmStdio = true
        val job = async {
            session.connect(McpConnectionConfig(transport = McpTransportType.STDIO, command = "x"))
        }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"resources":{"subscribe":true}},"serverInfo":{"name":"s","version":"1"}}}""")
        written.receive()
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":2,"result":{"resources":[{"uri":"reqlab://doc","name":"Doc"}]}}""")
        job.await()

        val sub = async { session.subscribeResource("reqlab://doc") }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":3,"result":{}}""")
        sub.await()
        val unsub = async { session.unsubscribeResource("reqlab://doc") }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":4,"result":{}}""")
        unsub.await()
        assertTrue("reqlab://doc" !in session.subscribedUris.value)
        session.disconnect()
    }

    @Test
    fun failed_call_populates_error_operation_for_response_viewer() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val session = McpSessionState(this) { scope ->
            McpClient(scope, stdioFactory = { transport }, callTimeoutMs = 5_000)
        }
        session.confirmStdio = true
        val job = async {
            session.connect(McpConnectionConfig(transport = McpTransportType.STDIO, command = "x"))
        }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"s","version":"1"}}}""")
        written.receive()
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","inputSchema":{"type":"object"}}]}}""")
        job.await()

        val call = async { session.callSelectedTool("nope", null) }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":3,"error":{"code":-32602,"message":"Unknown tool"}}""")
        call.await()
        val op = session.lastOperation.value
        assertTrue(op != null && op.isError)
        assertTrue(op!!.bodyJson.contains("Unknown tool"))
        val response = op.toResponseDefinition("tab-1")
        assertEquals(500, response.statusCode)
        assertTrue(response.bodyText.contains("Unknown tool"))
        session.disconnect()
    }

    @Test
    fun console_bridge_forwards_received_as_success() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val captured = mutableListOf<Pair<String, com.reqlab.ui.shared.state.LogLevel>>()
        val session = McpSessionState(
            this,
            clientFactory = { scope -> McpClient(scope, stdioFactory = { transport }, callTimeoutMs = 5_000) },
            onConsole = { message, level -> captured.add(message to level) },
        )
        session.confirmStdio = true
        val job = async {
            session.connect(McpConnectionConfig(transport = McpTransportType.STDIO, command = "x"))
        }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"s","version":"1"}}}""")
        written.receive()
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}""")
        job.await()
        assertTrue(captured.any { it.first.startsWith("MCP") })
        assertTrue(captured.any { it.second == com.reqlab.ui.shared.state.LogLevel.SUCCESS })
        session.disconnect()
    }

    @Test
    fun operation_to_response_definition_includes_headers_and_timing() {
        val op = McpOperationResult(
            kind = "tool",
            label = "echo",
            bodyJson = """{"ok":true}""",
            isError = false,
            headers = listOf(com.reqlab.core.model.KeyValueEntry("Mcp-Session-Id", "sess-1")),
            elapsedMs = 42,
            sizeBytes = 11,
            timestampMs = 1_700_000_000_000L,
        )
        val response = op.toResponseDefinition("req-9", okStatusText = "Success")
        assertEquals(200, response.statusCode)
        assertEquals("Success", response.statusText)
        assertEquals("application/json", response.contentType)
        assertEquals("""{"ok":true}""", response.bodyText)
        assertEquals("sess-1", response.headers.single { it.key == "Mcp-Session-Id" }.value)
        assertEquals(42, response.metrics.responseTimeMs)
        assertEquals(11, response.metrics.responseSizeBytes)
    }

    @Test
    fun tool_response_uses_wire_jsonrpc_not_model_defaults() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val session = McpSessionState(this) { scope ->
            McpClient(scope, stdioFactory = { transport }, callTimeoutMs = 5_000)
        }
        session.confirmStdio = true
        val job = async {
            session.connect(McpConnectionConfig(transport = McpTransportType.STDIO, command = "x"))
        }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"s","version":"1"}}}""")
        written.receive()
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"add","inputSchema":{"type":"object"}}]}}""")
        job.await()

        val call = async { session.callSelectedTool("add", null) }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":6}],"isError":false}}""")
        call.await()
        val body = session.lastOperation.value?.bodyJson.orEmpty()
        assertTrue(body.contains("\"jsonrpc\""), body)
        assertTrue(body.contains("\"result\""), body)
        assertTrue(body.contains("\"text\": 6") || body.contains("\"text\":6"), body)
        assertTrue(!body.contains("structuredContent"), body)
        assertTrue(!body.contains("\"mimeType\""), body)
        session.disconnect()
    }

    @Test
    fun pretty_wire_json_preserves_numeric_text() {
        val pretty = mcpPrettyWireJson("""{"jsonrpc":"2.0","id":27,"result":{"content":[{"type":"text","text":6}],"isError":false}}""")
        assertTrue(pretty.contains("\"jsonrpc\""))
        assertTrue(pretty.contains("\"text\": 6") || pretty.contains("\"text\":6"), pretty)
        assertTrue(!pretty.contains("null"), pretty)
    }

    @Test
    fun pretty_wire_json_unwraps_callback_payload_string() {
        val raw = """{"jsonrpc":"2.0","id":6,"result":{"content":[{"type":"text","text":"{\"jsonrpc\":\"2.0\",\"id\":\"srv-sample\",\"result\":{\"role\":\"assistant\",\"content\":{\"type\":\"text\",\"text\":\"mock reply from ReqLab\"},\"model\":\"mock\",\"stopReason\":\"endTurn\"}}"}],"isError":false}}"""
        val pretty = mcpPrettyWireJson(raw)
        assertTrue(pretty.contains("\"text\": {") || pretty.contains("\"text\":{"), pretty)
        assertTrue(pretty.contains("\"srv-sample\""), pretty)
        assertTrue(pretty.contains("mock reply from ReqLab"), pretty)
        assertTrue(!pretty.contains("\\\"jsonrpc\\\""), pretty)
    }

    @Test
    fun pretty_wire_json_unwraps_roots_callback_string() {
        val raw = """{"jsonrpc":"2.0","id":5,"result":{"content":[{"type":"text","text":"{\"jsonrpc\":\"2.0\",\"id\":\"srv-roots\",\"result\":{\"roots\":[{\"uri\":\"file:///tmp/reqlab\",\"name\":\"tmp\"}]}}"}],"isError":false}}"""
        val pretty = mcpPrettyWireJson(raw)
        assertTrue(pretty.contains("\"srv-roots\""), pretty)
        assertTrue(pretty.contains("file:///tmp/reqlab"), pretty)
        assertTrue(pretty.contains("\"text\": {") || pretty.contains("\"text\":{"), pretty)
    }

    @Test
    fun default_args_json_from_schema() {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("text", buildJsonObject { put("type", "string") })
                put("count", buildJsonObject { put("type", "integer") })
            })
        }
        val json = mcpDefaultArgsJson(schema)
        assertTrue(json.contains("\"text\""))
        assertTrue(json.contains("\"count\""))
        assertTrue(json.contains("\"count\": 0") || json.contains("\"count\":0"), json)
        assertTrue(!json.contains("\"count\": \"\""), json)
    }

    @Test
    fun schema_form_fields_and_required_validation() {
        val schema = buildJsonObject {
            put("type", "object")
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("a")) })
            put("properties", buildJsonObject {
                put("a", buildJsonObject { put("type", "integer") })
                put("b", buildJsonObject { put("type", "string") })
            })
        }
        val fields = mcpSchemaFields(schema)
        assertEquals(listOf("a", "b"), fields.map { it.name })
        assertTrue(fields.first { it.name == "a" }.required)
        assertTrue(mcpSchemaFormSupported(schema))
        assertEquals(listOf("a"), mcpMissingRequiredArgs(schema, "{}"))
        assertEquals(emptyList(), mcpMissingRequiredArgs(schema, """{"a": 1}"""))
        val updated = mcpArgsPut("""{"a": 0}""", "a", kotlinx.serialization.json.JsonPrimitive(3))
        assertTrue(updated.contains("3"), updated)
    }

    @Test
    fun tool_annotation_chips_and_prompt_schema() {
        val chips = mcpToolHintChips(
            buildJsonObject {
                put("readOnlyHint", true)
                put("destructiveHint", false)
            },
        )
        assertEquals(listOf("readOnly"), chips)
        val prompt = com.reqlab.core.model.McpPrompt(
            name = "greet",
            arguments = listOf(
                com.reqlab.core.model.McpPromptArgument(name = "who", required = true),
            ),
        )
        val schema = mcpPromptSchema(prompt)
        assertEquals(listOf("who"), mcpMissingRequiredArgs(schema, "{}"))
    }

    @Test
    fun reconnect_needed_when_url_changes_after_connect() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val session = McpSessionState(this) { scope ->
            McpClient(scope, stdioFactory = { transport }, callTimeoutMs = 5_000)
        }
        session.confirmStdio = true
        val cfg = McpConnectionConfig(transport = McpTransportType.STDIO, command = "x")
        val job = async { session.connect(cfg) }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"s","version":"1"}}}""")
        written.receive()
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}""")
        job.await()
        assertTrue(!session.isReconnectNeeded(cfg))
        assertTrue(session.isReconnectNeeded(cfg.copy(command = "y")))
        session.disconnect()
    }

    @Test
    fun tools_call_timeout_clears_pending_sampling_overlay() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val session = McpSessionState(this) { scope ->
            McpClient(scope, stdioFactory = { transport }, callTimeoutMs = 5_000)
        }
        session.confirmStdio = true
        val connect = async {
            session.connect(
                McpConnectionConfig(
                    transport = McpTransportType.STDIO,
                    command = "x",
                    samplingMode = McpSamplingMode.MANUAL,
                ),
            )
        }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"s","version":"1"}}}""")
        written.receive()
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","inputSchema":{"type":"object"}}]}}""")
        connect.await()

        val call = async { session.callSelectedTool("echo", null) }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":"srv-1","method":"sampling/createMessage","params":{"messages":[],"maxTokens":8}}""")
        withTimeout(5_000) {
            session.pendingSampling.first { it is McpPendingSampling.ReviewRequest }
        }
        inbound.send(
            """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"Timed out waiting for client reply to srv-sample"}],"isError":true}}""",
        )
        call.await()
        assertEquals(null, session.pendingSampling.value)
        val op = session.lastOperation.value
        assertTrue(op != null && op.isError)
        assertTrue(op!!.bodyJson.contains("Timed out waiting for client reply"))
        session.disconnect()
    }

    @Test
    fun clear_logs_empties_activity_without_disconnect() = runTest {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val session = McpSessionState(this) { scope ->
            McpClient(scope, stdioFactory = { transport }, callTimeoutMs = 5_000)
        }
        session.confirmStdio = true
        val job = async {
            session.connect(McpConnectionConfig(transport = McpTransportType.STDIO, command = "x"))
        }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"s","version":"1"}}}""")
        written.receive()
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","inputSchema":{"type":"object"}}]}}""")
        job.await()
        withTimeout(5_000) {
            session.logs.first { it.isNotEmpty() }
        }
        assertTrue(session.logs.value.isNotEmpty())
        session.clearLogs()
        assertTrue(session.logs.value.isEmpty())
        assertEquals(McpConnectionState.CONNECTED, session.connectionState.value)
        session.disconnect()
    }

    @Test
    fun fingerprint_includes_sampling_roots_and_elicitation() {
        val base = McpConnectionConfig(url = "http://localhost/mcp")
        assertTrue(connectionFingerprint(base) != connectionFingerprint(base.copy(samplingMode = com.reqlab.core.model.McpSamplingMode.MANUAL)))
        assertTrue(connectionFingerprint(base) != connectionFingerprint(base.copy(samplingForwardUrl = "http://localhost:8080/v1/chat/completions")))
        assertTrue(connectionFingerprint(base) != connectionFingerprint(base.copy(samplingForwardToken = "secret")))
        assertTrue(connectionFingerprint(base) != connectionFingerprint(base.copy(autoRespondElicitation = false)))
        assertTrue(
            connectionFingerprint(base) != connectionFingerprint(
                base.copy(roots = listOf(com.reqlab.core.model.McpRoot("file:///tmp/reqlab", "tmp"))),
            ),
        )
    }
}
