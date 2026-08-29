package com.reqlab.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SseParserTest {

    @Test
    fun parses_openai_chat_chunks_and_stops_on_done() {
        val parser = SseParser()
        val events = mutableListOf<SseEvent>()
        val raw = """
            data: {"choices":[{"delta":{"content":"Hello"}}]}

            data: {"choices":[{"delta":{"content":" world"}}]}

            data: [DONE]

        """.trimIndent()
        raw.lineSequence().forEach { line ->
            parser.feedLine(line)?.let { events += it }
        }
        parser.flush()?.let { events += it }

        assertEquals(3, events.size)
        assertEquals("""{"choices":[{"delta":{"content":"Hello"}}]}""", events[0].data)
        assertEquals("""{"choices":[{"delta":{"content":" world"}}]}""", events[1].data)
        assertTrue(events[2].isDone)
        assertEquals("[DONE]", events[2].data)
    }

    @Test
    fun ignores_comment_lines_and_strips_leading_space() {
        val parser = SseParser()
        val events = mutableListOf<SseEvent>()
        listOf(": keep-alive", "data: hello", "").forEach { line ->
            parser.feedLine(line)?.let { events += it }
        }
        assertEquals(1, events.size)
        assertEquals("hello", events[0].data)
        assertFalse(events[0].isDone)
        assertNull(events[0].id)
    }

    @Test
    fun parses_event_id_for_resumability() {
        val parser = SseParser()
        var event: SseEvent? = null
        listOf("id: 42", "event: message", "data: {\"ok\":true}", "").forEach { line ->
            parser.feedLine(line)?.let { event = it }
        }
        assertEquals("42", event?.id)
        assertEquals("message", event?.eventType)
        assertEquals("""{"ok":true}""", event?.data)
    }

    @Test
    fun joins_multiple_data_lines_with_newline() {
        val parser = SseParser()
        var event: SseEvent? = null
        listOf("data: one", "data: two", "").forEach { line ->
            parser.feedLine(line)?.let { event = it }
        }
        assertEquals("one\ntwo", event?.data)
    }

    @Test
    fun handles_crlf_lines() {
        val parser = SseParser()
        val event = parser.feedLine("data: ping\r")
        assertNull(event)
        val dispatched = parser.feedLine("\r")
        assertEquals("ping", dispatched?.data)
    }

    @Test
    fun flush_emits_trailing_event_without_blank_line() {
        val parser = SseParser()
        parser.feedLine("data: trailing")
        val flushed = parser.flush()
        assertEquals("trailing", flushed?.data)
        assertNull(parser.flush())
    }
}
