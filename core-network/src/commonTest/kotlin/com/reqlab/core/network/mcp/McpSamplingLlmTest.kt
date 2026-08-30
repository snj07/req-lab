package com.reqlab.core.network.mcp

import com.reqlab.core.model.McpContent
import com.reqlab.core.model.McpCreateMessageRequest
import com.reqlab.core.model.McpSamplingMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpSamplingLlmTest {

    @Test
    fun maps_openai_chat_completion_to_sampling_result() = runTest {
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("\"messages\""))
            assertTrue(body.contains("Say hi"))
            assertEquals("Bearer llm-test-key", request.headers[HttpHeaders.Authorization])
            respond(
                content = """{"id":"chatcmpl-1","model":"mock-gpt","choices":[{"index":0,"message":{"role":"assistant","content":"Hello from ReqLab"},"finish_reason":"stop"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) { expectSuccess = false }
        val result = forwardMcpSampling(
            httpClient = client,
            url = "https://llm.example/v1/chat/completions",
            bearerToken = "llm-test-key",
            request = McpCreateMessageRequest(
                messages = listOf(
                    McpSamplingMessage(role = "user", content = McpContent(type = "text", text = "Say hi")),
                ),
                maxTokens = 32,
            ),
        )
        assertEquals("Hello from ReqLab", result.content.text)
        assertEquals("assistant", result.role)
        assertEquals("mock-gpt", result.model)
        assertEquals("endTurn", result.stopReason)
    }

    @Test
    fun maps_finish_reason_length_to_max_tokens() {
        assertEquals("maxTokens", mapFinishReason("length"))
        assertEquals("endTurn", mapFinishReason("stop"))
        assertEquals("contentFilter", mapFinishReason("content_filter"))
    }
}
