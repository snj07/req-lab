package com.reqlab.core.network

import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestDefinition
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorApiClientTest {

    @Test
    fun emits_success_event_for_200_response() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "{\"ok\":true}",
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            expectSuccess = false
        }

        val apiClient = KtorApiClient(
            httpClient = client,
            retryPolicy = RetryPolicy(maxAttempts = 1)
        )

        val request = RequestDefinition(
            id = "req-1",
            name = "Get Health",
            method = HttpMethodType.GET,
            url = "https://api.test/health",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )

        val events = apiClient.execute(request).toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is NetworkEvent.Started)
        assertTrue(events[1] is NetworkEvent.Success)

        val success = events[1] as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertEquals("{\"ok\":true}", success.response.bodyText)
    }

    @Test
    fun resolves_dynamic_variables_before_network_execution() = runTest {
        var capturedPath = ""
        var capturedQuery = ""
        var capturedTraceHeader = ""

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedQuery = request.url.parameters["seed"].orEmpty()
            capturedTraceHeader = request.headers["X-Trace"].orEmpty()
            respond(
                content = "ok",
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            expectSuccess = false
        }

        val apiClient = KtorApiClient(httpClient = client, retryPolicy = RetryPolicy(maxAttempts = 1))

        val request = RequestDefinition(
            id = "req-dyn",
            name = "Dynamic Vars",
            method = HttpMethodType.GET,
            url = "https://api.test/items/{{\$timestamp}}",
            queryParams = listOf(KeyValueEntry("seed", "{{\$randomInt(10, 20)}}")),
            headers = listOf(KeyValueEntry("X-Trace", "{{\$isoTimestamp}}")),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )

        val events = apiClient.execute(request).toList()

        assertTrue(capturedPath.removePrefix("/items/").toLongOrNull() != null)
        val queryNumber = capturedQuery.toIntOrNull()
        assertTrue(queryNumber != null && queryNumber in 10..20)
        assertTrue(capturedTraceHeader.contains("T"))
        assertTrue(events.last() is NetworkEvent.Success)
    }

    @Test
    fun success_response_includes_timing_metrics() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "{\"result\":42}",
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            expectSuccess = false
        }

        val apiClient = KtorApiClient(httpClient = client, retryPolicy = RetryPolicy(maxAttempts = 1))

        val request = RequestDefinition(
            id = "req-timing",
            name = "Timing Test",
            method = HttpMethodType.GET,
            url = "https://api.test/data",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )

        val events = apiClient.execute(request).toList()
        val success = events.filterIsInstance<NetworkEvent.Success>().first()
        val metrics = success.response.metrics

        assertTrue(metrics.responseTimeMs >= 0, "responseTimeMs should be non-negative")
        assertTrue(metrics.serverMs >= 0, "serverMs should be non-negative")
        assertTrue(metrics.downloadMs >= 0, "downloadMs should be non-negative")
        assertTrue(metrics.responseSizeBytes > 0, "responseSizeBytes should be positive")
    }
}
