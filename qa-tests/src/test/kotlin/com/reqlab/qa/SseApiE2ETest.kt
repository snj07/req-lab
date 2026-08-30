package com.reqlab.qa

import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.KtorApiClient
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.RetryPolicy
import com.reqlab.server.module
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

class SseApiE2ETest {

    private fun client() = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1), idleTimeoutMs = 10_000)

    private fun request(
        method: HttpMethodType,
        path: String,
        body: String? = null,
    ) = RequestDefinition(
        id = "sse-${path.hashCode()}",
        name = path,
        method = method,
        url = "$BASE_URL$path",
        headers = listOf(KeyValueEntry("Accept", "text/event-stream")),
        body = if (body != null) RequestBody(BodyType.JSON, content = body) else RequestBody(),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    @Test
    fun get_sse_emits_chunks_and_completes() = runBlocking {
        val events = client().execute(request(HttpMethodType.GET, "/sse")).toList()
        assertTrue(events.filterIsInstance<NetworkEvent.Chunk>().isNotEmpty())
        val success = events.last() as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.contentType.orEmpty().contains("text/event-stream"))
        assertTrue(success.response.streamEvents.isNotEmpty())
        assertTrue(success.response.streamEvents.any { it.contains("ping-0") })
    }

    @Test
    fun get_sse_count_emits_three_data_events() = runBlocking {
        val events = client().execute(request(HttpMethodType.GET, "/sse?count=3")).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertEquals(3, success.response.streamEvents.size)
        assertEquals(listOf("ping-0", "ping-1", "ping-2"), success.response.streamEvents)
    }

    @Test
    fun post_sse_echoes_body_snippet_and_completes() = runBlocking {
        val events = client().execute(
            request(HttpMethodType.POST, "/sse", """{"message":"hello-sse"}"""),
        ).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.streamEvents.isNotEmpty())
        assertTrue(success.response.streamEvents.any { it.contains("hello-sse") })
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
