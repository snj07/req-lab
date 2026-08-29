package com.reqlab.core.network

import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmTextAssemblerTest {

    @Test
    fun assembles_openai_delta_content() {
        val events = listOf(
            """{"choices":[{"delta":{"content":"Hello"}}]}""",
            """{"choices":[{"delta":{"content":" from"}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"stop"}]}""",
        )
        assertEquals("Hello from", LlmTextAssembler.assemble(events))
        assertEquals("stop", LlmTextAssembler.extractFinishReason(null, events))
    }

    @Test
    fun assembles_ollama_ndjson_message_content() {
        val events = listOf(
            """{"message":{"content":"Hel"},"done":false}""",
            """{"message":{"content":"lo"},"done":true}""",
        )
        assertEquals("Hello", LlmTextAssembler.assemble(events))
        assertTrue(LlmTextAssembler.isNdjsonDone(events.last()))
        assertFalse(LlmTextAssembler.isNdjsonDone(events.first()))
    }

    @Test
    fun extracts_full_message_from_non_stream_body() {
        val body = """{"choices":[{"message":{"content":"Hello from ReqLab"},"finish_reason":"stop"}]}"""
        assertEquals("Hello from ReqLab", LlmTextAssembler.assembleFromBody(body))
        assertEquals("stop", LlmTextAssembler.extractFinishReason(body, emptyList()))
    }

    @Test
    fun requestLooksLikeStreaming_detects_stream_true_and_accept_header() {
        val streamed = RequestDefinition(
            id = "1",
            name = "s",
            method = HttpMethodType.POST,
            url = "https://api.test/v1/chat/completions",
            body = RequestBody(BodyType.JSON, content = """{"stream": true}"""),
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        assertTrue(requestLooksLikeStreaming(streamed))
        assertTrue(isStreamingContentType("text/event-stream; charset=utf-8"))
        assertTrue(isNdjsonContentType("application/x-ndjson"))
        assertFalse(isStreamingContentType("application/json"))

        val accept = streamed.copy(
            body = RequestBody(BodyType.JSON, content = """{"stream":false}"""),
            headers = listOf(KeyValueEntry("Accept", "text/event-stream")),
        )
        assertTrue(requestLooksLikeStreaming(accept))
    }
}
