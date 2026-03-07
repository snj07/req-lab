package com.reqlab.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseMetricsTimingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun defaultTimingFieldsAreMinusOne() {
        val metrics = ResponseMetrics(statusCode = 200, responseTimeMs = 100, responseSizeBytes = 512)
        assertEquals(-1, metrics.dnsMs)
        assertEquals(-1, metrics.connectMs)
        assertEquals(-1, metrics.tlsMs)
        assertEquals(-1, metrics.serverMs)
        assertEquals(-1, metrics.downloadMs)
    }

    @Test
    fun timingFieldsPreservedInSerialization() {
        val original = ResponseMetrics(
            statusCode = 200,
            responseTimeMs = 150,
            responseSizeBytes = 1024,
            dnsMs = 5,
            connectMs = 10,
            tlsMs = 20,
            serverMs = 80,
            downloadMs = 35,
        )
        val encoded = json.encodeToString(ResponseMetrics.serializer(), original)
        val decoded = json.decodeFromString(ResponseMetrics.serializer(), encoded)

        assertEquals(original, decoded)
        assertEquals(5, decoded.dnsMs)
        assertEquals(10, decoded.connectMs)
        assertEquals(20, decoded.tlsMs)
        assertEquals(80, decoded.serverMs)
        assertEquals(35, decoded.downloadMs)
    }

    @Test
    fun backwardCompatibleDeserialization() {
        // Simulates reading old persisted data without timing fields
        val oldJson = """{"statusCode":200,"responseTimeMs":100,"responseSizeBytes":512}"""
        val metrics = json.decodeFromString(ResponseMetrics.serializer(), oldJson)

        assertEquals(200, metrics.statusCode)
        assertEquals(100, metrics.responseTimeMs)
        assertEquals(512, metrics.responseSizeBytes)
        assertEquals(-1, metrics.dnsMs)
        assertEquals(-1, metrics.connectMs)
        assertEquals(-1, metrics.tlsMs)
        assertEquals(-1, metrics.serverMs)
        assertEquals(-1, metrics.downloadMs)
    }

    @Test
    fun timingPhasesSumApproximatesTotalTime() {
        val metrics = ResponseMetrics(
            statusCode = 200,
            responseTimeMs = 150,
            responseSizeBytes = 1024,
            serverMs = 100,
            downloadMs = 40,
        )
        // Server + Download should be <= total (DNS/connect/TLS are -1 so excluded)
        val measuredPhases = listOf(metrics.serverMs, metrics.downloadMs).filter { it >= 0 }
        assertTrue(measuredPhases.sum() <= metrics.responseTimeMs + 50) // Allow some slack
    }
}
