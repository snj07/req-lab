package com.reqlab.qa

import com.reqlab.core.model.AuthConfig
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.KtorApiClient
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.RetryPolicy
import com.reqlab.core.scripting.ReqLabScriptEngine
import com.reqlab.core.scripting.ScriptContext
import com.reqlab.server.LLM_DEMO_REPLY
import com.reqlab.server.module
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmApiE2ETest {

    private val llmKey = "llm-test-key"

    private fun client() = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1), idleTimeoutMs = 10_000)

    private fun request(
        method: HttpMethodType,
        path: String,
        body: String? = null,
        stream: Boolean = false,
        auth: Boolean = true,
    ) = RequestDefinition(
        id = "llm-${path.hashCode()}",
        name = path,
        method = method,
        url = "$BASE_URL$path",
        auth = if (auth) AuthConfig(AuthType.BEARER, mapOf("token" to llmKey)) else AuthConfig(),
        body = if (body != null) RequestBody(BodyType.JSON, content = body) else RequestBody(),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    ).let { req ->
        if (!stream) req else req.copy(
            body = RequestBody(BodyType.JSON, content = body),
        )
    }

    @Test
    fun lists_models() = runBlocking {
        val events = client().execute(request(HttpMethodType.GET, "/v1/models")).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.bodyText.contains("mock-gpt"))
    }

    @Test
    fun chat_completions_non_stream() = runBlocking {
        val body = """{"model":"mock-gpt","messages":[{"role":"user","content":"hi"}],"stream":false}"""
        val events = client().execute(request(HttpMethodType.POST, "/v1/chat/completions", body)).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertEquals("Hello from ReqLab", success.response.assembledText)
        assertTrue(success.response.bodyText.contains("chat.completion"))
    }

    @Test
    fun chat_completions_stream_emits_chunks() = runBlocking {
        val body = """{"model":"mock-gpt","messages":[{"role":"user","content":"hi"}],"stream":true}"""
        val events = client().execute(request(HttpMethodType.POST, "/v1/chat/completions", body, stream = true)).toList()
        assertTrue(events.filterIsInstance<NetworkEvent.Chunk>().size >= 3)
        val success = events.last() as NetworkEvent.Success
        assertEquals("Hello from ReqLab", success.response.assembledText)
        assertTrue(success.response.streamEvents.isNotEmpty())
        assertTrue(success.response.metrics.timeToFirstTokenMs >= 0)
    }

    @Test
    fun chat_completions_demo_non_stream_is_a_full_reply() = runBlocking {
        val body = """{"model":"mock-gpt","messages":[{"role":"user","content":"Explain streaming"}],"stream":false}"""
        val events = client().execute(
            request(HttpMethodType.POST, "/v1/chat/completions?demo=true", body)
        ).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertEquals(LLM_DEMO_REPLY, success.response.assembledText)
        assertTrue(success.response.bodyText.contains("chat.completion"))
        assertTrue(success.response.bodyText.contains("system_fingerprint"))
    }

    @Test
    fun chat_completions_demo_stream_emits_many_tokens() = runBlocking {
        val body = """{"model":"mock-gpt","messages":[{"role":"user","content":"Explain streaming"}],"stream":true}"""
        val events = client().execute(
            request(HttpMethodType.POST, "/v1/chat/completions?demo=true&chunkMs=1", body, stream = true)
        ).toList()
        assertTrue(events.filterIsInstance<NetworkEvent.Chunk>().size >= 10)
        val success = events.last() as NetworkEvent.Success
        assertEquals(LLM_DEMO_REPLY, success.response.assembledText)
        assertTrue(success.response.streamEvents.size >= 10)
    }

    @Test
    fun ndjson_stream_assembles_text() = runBlocking {
        val events = client().execute(request(HttpMethodType.POST, "/v1/chat/ndjson")).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals("Hello from ReqLab", success.response.assembledText)
    }

    @Test
    fun embeddings_have_fixed_length() = runBlocking {
        val body = """{"model":"mock-gpt","input":"hello"}"""
        val events = client().execute(request(HttpMethodType.POST, "/v1/embeddings", body)).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.bodyText.contains("0.1"))
    }

    @Test
    fun tool_calls_and_json_mode() = runBlocking {
        val tools = """{"model":"mock-gpt","messages":[{"role":"user","content":"x"}],"tools":[{"type":"function"}]}"""
        val toolSuccess = client().execute(request(HttpMethodType.POST, "/v1/chat/completions", tools)).toList()
            .last() as NetworkEvent.Success
        assertTrue(toolSuccess.response.bodyText.contains("tool_calls"))

        val jsonMode = """{"model":"mock-gpt","messages":[{"role":"user","content":"x"}],"response_format":{"type":"json_object"}}"""
        val jsonSuccess = client().execute(request(HttpMethodType.POST, "/v1/chat/completions", jsonMode)).toList()
            .last() as NetworkEvent.Success
        assertTrue(jsonSuccess.response.assembledText!!.contains("\"ok\":true"))
    }

    @Test
    fun unauthorized_and_error_statuses() = runBlocking {
        val missing = client().execute(
            request(HttpMethodType.POST, "/v1/chat/completions", """{"model":"mock-gpt"}""", auth = false)
        ).toList().last() as NetworkEvent.Success
        assertEquals(401, missing.response.statusCode)

        val rate = client().execute(
            request(HttpMethodType.POST, "/v1/chat/completions?fail=429", """{"model":"mock-gpt"}""")
        ).toList().last() as NetworkEvent.Success
        assertEquals(429, rate.response.statusCode)

        val boom = client().execute(
            request(HttpMethodType.POST, "/v1/chat/completions?fail=500", """{"model":"mock-gpt"}""")
        ).toList().last() as NetworkEvent.Success
        assertEquals(500, boom.response.statusCode)
    }

    @Test
    fun early_close_stream_returns_partial_text() = runBlocking {
        val body = """{"model":"mock-gpt","messages":[{"role":"user","content":"hi"}],"stream":true}"""
        val events = client().execute(
            request(HttpMethodType.POST, "/v1/chat/completions?earlyClose=true", body, stream = true)
        ).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.assembledText!!.contains("Hello"))
    }

    @Test
    fun stream_outlives_short_request_timeout() = runBlocking {
        val http = HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 200 }
            expectSuccess = false
        }
        val api = KtorApiClient(httpClient = http, retryPolicy = RetryPolicy(maxAttempts = 1), idleTimeoutMs = 10_000)
        val body = """{"model":"mock-gpt","messages":[{"role":"user","content":"hi"}],"stream":true}"""
        val events = api.execute(
            request(HttpMethodType.POST, "/v1/chat/completions?chunkMs=120", body, stream = true)
        ).toList()
        val success = events.lastOrNull() as? NetworkEvent.Success
        assertTrue(success != null, "expected Success after a stream longer than 200ms, got ${events.lastOrNull()}")
        assertEquals("Hello from ReqLab", success!!.response.assembledText)
        http.close()
    }

    @Test
    fun post_request_script_sees_llm_helpers() = runBlocking {
        val body = """{"model":"mock-gpt","messages":[{"role":"user","content":"hi"}],"stream":true}"""
        val success = client().execute(
            request(HttpMethodType.POST, "/v1/chat/completions", body, stream = true)
        ).toList().last() as NetworkEvent.Success
        val engine = ReqLabScriptEngine()
        val result = engine.executeTestScript(
            """
            reqlab.test("assembled", function() {
                reqlab.expect(reqlab.response.llm.assembledText).to.include("Hello from ReqLab")
            })
            reqlab.test("finish", function() {
                reqlab.expect(reqlab.response.llm.finishReason).to.equal("stop")
            })
            """.trimIndent(),
            ScriptContext(
                url = success.response.requestId,
                method = "POST",
                statusCode = success.response.statusCode,
                responseBody = success.response.bodyText,
                streamEvents = success.response.streamEvents,
                assembledText = success.response.assembledText,
            ),
        )
        assertTrue(result.success, result.error ?: "")
        assertTrue(result.assertions.all { it.passed }, result.assertions.joinToString { it.message ?: it.name })
    }

    companion object {
        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private var port: Int = 0
        var BASE_URL: String = ""

        @BeforeClass
        @JvmStatic
        fun startServer() {
            port = ServerSocket(0).use { it.localPort }
            BASE_URL = "http://localhost:$port"
            server = embeddedServer(Netty, port = port, module = { module() })
            server!!.start(wait = false)
            repeat(50) {
                runCatching { java.net.Socket("localhost", port).close(); return }
                Thread.sleep(100)
            }
        }

        @AfterClass
        @JvmStatic
        fun stopServer() {
            server?.stop(100, 500)
        }
    }
}
