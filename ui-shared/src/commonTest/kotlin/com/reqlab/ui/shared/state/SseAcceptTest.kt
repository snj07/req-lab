package com.reqlab.ui.shared.state

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SseAcceptTest {

    @Test
    fun enabled_text_event_stream_is_sse() {
        assertTrue(isSseAccept("Accept", "text/event-stream"))
    }

    @Test
    fun combined_accept_with_event_stream_is_sse() {
        assertTrue(isSseAccept("Accept", "application/json, text/event-stream"))
    }

    @Test
    fun key_and_value_are_case_insensitive() {
        assertTrue(isSseAccept("accept", "Text/Event-Stream"))
    }

    @Test
    fun json_accept_is_not_sse() {
        assertFalse(isSseAccept("Accept", "application/json"))
    }

    @Test
    fun disabled_event_stream_is_not_sse() {
        assertFalse(isSseAccept("Accept", "text/event-stream", enabled = false))
    }
}
