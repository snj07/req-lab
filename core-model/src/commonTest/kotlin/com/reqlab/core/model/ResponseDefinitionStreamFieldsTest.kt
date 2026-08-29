package com.reqlab.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseDefinitionStreamFieldsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun stream_fields_default_and_round_trip() {
        val original = ResponseDefinition(
            requestId = "r1",
            statusCode = 200,
            statusText = "OK",
            headers = emptyList(),
            cookies = emptyList(),
            bodyText = "Hello",
            contentType = "text/event-stream",
            executedAtEpochMillis = 1L,
            metrics = ResponseMetrics(
                statusCode = 200,
                responseTimeMs = 10,
                responseSizeBytes = 5,
                ttfbMs = 2,
                timeToFirstTokenMs = 3,
                timeToLastTokenMs = 9,
            ),
            streamEvents = listOf("""{"choices":[{"delta":{"content":"Hello"}}]}"""),
            assembledText = "Hello",
        )
        val encoded = json.encodeToString(ResponseDefinition.serializer(), original)
        val decoded = json.decodeFromString(ResponseDefinition.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun old_json_without_stream_fields_still_loads() {
        val oldJson = """
            {"requestId":"r1","statusCode":200,"statusText":"OK","headers":[],"cookies":[],
             "bodyText":"{}","contentType":"application/json","executedAtEpochMillis":1,
             "metrics":{"statusCode":200,"responseTimeMs":10,"responseSizeBytes":2}}
        """.trimIndent()
        val decoded = json.decodeFromString(ResponseDefinition.serializer(), oldJson)
        assertTrue(decoded.streamEvents.isEmpty())
        assertEquals(null, decoded.assembledText)
        assertEquals(-1, decoded.metrics.ttfbMs)
        assertEquals(-1, decoded.metrics.timeToFirstTokenMs)
        assertEquals(-1, decoded.metrics.timeToLastTokenMs)
    }
}
