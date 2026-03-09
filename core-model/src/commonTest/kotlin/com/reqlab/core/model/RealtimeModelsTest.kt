package com.reqlab.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the Realtime protocol models (F4).
 */
class RealtimeModelsTest {

    @Test
    fun protocols_have_non_empty_labels() {
        for (protocol in RealtimeProtocol.entries) {
            assertTrue(protocol.label.isNotBlank(), "${protocol.name} should have a label")
        }
    }

    @Test
    fun protocols_have_valid_default_urls() {
        for (protocol in RealtimeProtocol.entries) {
            assertTrue(
                protocol.defaultUrl.startsWith("ws") || protocol.defaultUrl.startsWith("http") || protocol.defaultUrl.startsWith("mqtt"),
                "${protocol.name} default URL should use a recognized scheme: ${protocol.defaultUrl}"
            )
        }
    }

    @Test
    fun websocket_default_url_uses_ws_scheme() {
        assertTrue(RealtimeProtocol.WEBSOCKET.defaultUrl.startsWith("ws"))
    }

    @Test
    fun sse_default_url_uses_http_scheme() {
        assertTrue(
            RealtimeProtocol.SSE.defaultUrl.startsWith("http"),
            "SSE should use HTTP(S) scheme"
        )
    }

    @Test
    fun message_directions_exist() {
        assertEquals(2, MessageDirection.entries.size)
        assertTrue(MessageDirection.entries.contains(MessageDirection.SENT))
        assertTrue(MessageDirection.entries.contains(MessageDirection.RECEIVED))
    }

    @Test
    fun realtime_message_construction() {
        val msg = RealtimeMessage(
            id = "msg-1",
            direction = MessageDirection.SENT,
            content = "Hello WebSocket",
            timestamp = 1700000000000L,
            protocol = RealtimeProtocol.WEBSOCKET,
        )
        assertEquals("msg-1", msg.id)
        assertEquals(MessageDirection.SENT, msg.direction)
        assertEquals("Hello WebSocket", msg.content)
        assertEquals(RealtimeProtocol.WEBSOCKET, msg.protocol)
        assertEquals(null, msg.eventType)
    }

    @Test
    fun realtime_message_with_event_type() {
        val msg = RealtimeMessage(
            id = "msg-2",
            direction = MessageDirection.RECEIVED,
            content = "{\"data\":\"test\"}",
            timestamp = 1700000000000L,
            protocol = RealtimeProtocol.SSE,
            eventType = "message",
        )
        assertEquals("message", msg.eventType)
        assertEquals(RealtimeProtocol.SSE, msg.protocol)
    }
}
