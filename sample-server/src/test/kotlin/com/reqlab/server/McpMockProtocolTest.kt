package com.reqlab.server

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpMockProtocolTest {

    @Test
    fun tools_list_includes_trigger_tools() {
        val extra = mutableListOf<McpOutbound>()
        val result = McpMockProtocol.handle(
            """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""",
            McpMockSession(),
            extra,
        )
        val text = result.toString()
        assertTrue(text.contains("echo"))
        assertTrue(text.contains("trigger_sampling"))
        assertTrue(text.contains("trigger_elicitation"))
        assertTrue(text.contains("trigger_roots"))
        assertTrue(text.contains("trigger_ping"))
        assertTrue(extra.isEmpty())
    }

    @Test
    fun trigger_sampling_emits_create_message() {
        val extra = mutableListOf<McpOutbound>()
        val result = McpMockProtocol.handle(
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"trigger_sampling"}}""",
            McpMockSession(),
            extra,
        )
        assertEquals(MCP_CALLBACK_SAMPLE, McpMockProtocol.waitForCallbackId(result))
        assertEquals(1, extra.size)
        assertTrue(extra.single().envelope.toString().contains("sampling/createMessage"))
    }

    @Test
    fun trigger_elicitation_emits_elicit_create() {
        val extra = mutableListOf<McpOutbound>()
        val result = McpMockProtocol.handle(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"trigger_elicitation"}}""",
            McpMockSession(),
            extra,
        )
        assertEquals(MCP_CALLBACK_ELICIT, McpMockProtocol.waitForCallbackId(result))
        assertEquals(1, extra.size)
        assertTrue(extra.single().envelope.toString().contains("elicitation/create"))
    }

    @Test
    fun trigger_roots_emits_roots_list() {
        val extra = mutableListOf<McpOutbound>()
        val result = McpMockProtocol.handle(
            """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"trigger_roots"}}""",
            McpMockSession(),
            extra,
        )
        assertEquals(MCP_CALLBACK_ROOTS, McpMockProtocol.waitForCallbackId(result))
        assertEquals(1, extra.size)
        assertTrue(extra.single().envelope.toString().contains("roots/list"))
    }

    @Test
    fun trigger_ping_emits_ping() {
        val extra = mutableListOf<McpOutbound>()
        val result = McpMockProtocol.handle(
            """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"trigger_ping"}}""",
            McpMockSession(),
            extra,
        )
        assertEquals(MCP_CALLBACK_PING, McpMockProtocol.waitForCallbackId(result))
        assertTrue(extra.single().envelope.toString().contains("\"ping\""))
    }

    @Test
    fun json_rpc_response_is_accepted_and_stored() {
        val session = McpMockSession()
        val result = McpMockProtocol.handle(
            """{"jsonrpc":"2.0","id":"srv-roots","result":{"roots":[{"uri":"file:///tmp/reqlab","name":"tmp"}]}}""",
            session,
        )
        assertNull(result)
        val stored = session.lastReplies[MCP_CALLBACK_ROOTS]
        assertTrue(stored.toString().contains("file:///tmp/reqlab"))
    }

    @Test
    fun json_rpc_notification_returns_null() {
        val result = McpMockProtocol.handle(
            """{"jsonrpc":"2.0","method":"notifications/initialized"}""",
            McpMockSession(),
        )
        assertNull(result)
    }

    @Test
    fun await_callback_times_out_when_client_does_not_reply() = runBlocking {
        val echoed = McpMockProtocol.awaitCallback(McpMockSession(), "missing", timeoutMs = 50)
        assertNull(echoed)
    }

    @Test
    fun resolve_wait_result_echoes_client_reply() = runBlocking {
        val session = McpMockSession()
        val marker = McpMockProtocol.waitMarker(kotlinx.serialization.json.JsonPrimitive(9), MCP_CALLBACK_ROOTS)
        McpMockProtocol.handle(
            """{"jsonrpc":"2.0","id":"srv-roots","result":{"roots":[]}}""",
            session,
        )
        val resolved = McpMockProtocol.resolveWaitResult(session, marker)
        val text = resolved.toString()
        assertTrue(session.lastReplies[MCP_CALLBACK_ROOTS].toString().contains("roots"))
        assertTrue(text.contains("roots"))
        assertTrue(!text.contains(MCP_WAIT_FOR_KEY))
    }

    @Test
    fun sse_endpoint_frame_is_legacy_event() {
        val frame = McpMockProtocol.sseEndpointFrame("/mcp/messages?sessionId=abc")
        assertEquals("event: endpoint\ndata: /mcp/messages?sessionId=abc\n\n", frame)
    }
}
