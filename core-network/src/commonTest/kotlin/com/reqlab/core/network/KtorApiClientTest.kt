package com.reqlab.core.network

import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.model.BodyType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
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

    @Test
    fun form_data_body_is_sent_as_multipart() = runTest {
        var capturedBody = ""

        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
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

        val apiClient = KtorApiClient(
            httpClient = client,
            retryPolicy = RetryPolicy(maxAttempts = 1)
        )

        val request = RequestDefinition(
            id = "req-form",
            name = "Multipart",
            method = HttpMethodType.POST,
            url = "https://api.test/form-data",
            body = RequestBody(
                type = BodyType.FORM_DATA,
                formEntries = listOf(
                    KeyValueEntry("name", "alice"),
                    KeyValueEntry("role", "tester"),
                ),
            ),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )

        val events = apiClient.execute(request).toList()
        assertTrue(events.last() is NetworkEvent.Success)
        assertTrue(
            capturedBody.contains("form-data", ignoreCase = true),
            "Expected multipart payload content, got '$capturedBody'",
        )
        assertTrue(
            capturedBody.contains("name") && capturedBody.contains("alice") && capturedBody.contains("role") && capturedBody.contains("tester"),
            "Multipart payload should contain form fields, got '$capturedBody'",
        )
    }

    @Test
    fun emits_retry_scheduled_and_failure_when_retries_exhausted() = runTest {
        val mockEngine = MockEngine {
            throw IllegalStateException("timeout-like failure")
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            expectSuccess = false
        }

        val apiClient = KtorApiClient(
            httpClient = client,
            retryPolicy = RetryPolicy(maxAttempts = 2, baseDelayMs = 0L, maxDelayMs = 0L),
        )

        val request = RequestDefinition(
            id = "req-failure",
            name = "Failure path",
            method = HttpMethodType.GET,
            url = "https://api.test/fail",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val events = apiClient.execute(request).toList()

        assertTrue(events.first() is NetworkEvent.Started)
        assertEquals(1, events.count { it is NetworkEvent.RetryScheduled })
        assertTrue(events.last() is NetworkEvent.Failure)
        val failure = events.last() as NetworkEvent.Failure
        assertTrue(failure.error.isRetryExhausted)
        assertTrue(failure.error.message.contains("failed", ignoreCase = true) || failure.error.message.contains("timeout", ignoreCase = true))
    }
}
