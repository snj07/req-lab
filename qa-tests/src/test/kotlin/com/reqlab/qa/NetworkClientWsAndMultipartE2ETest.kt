package com.reqlab.qa

import com.reqlab.server.module
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.KtorApiClient
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.RetryPolicy
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

class NetworkClientWsAndMultipartE2ETest {

    companion object {
        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private var port: Int = 0
        private var baseUrl: String = ""

        @BeforeClass
        @JvmStatic
        fun startServer() {
            port = ServerSocket(0).use { it.localPort }
            baseUrl = "http://localhost:$port"
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
            server?.stop(100L, 100L)
        }
    }

    @Test
    fun multipart_form_data_request_succeeds() = runBlocking {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val request = RequestDefinition(
            id = "multipart-1",
            name = "multipart",
            method = HttpMethodType.POST,
            url = "$baseUrl/api/form-data",
            body = RequestBody(
                type = BodyType.FORM_DATA,
                formEntries = listOf(
                    KeyValueEntry("name", "Alice"),
                    KeyValueEntry("role", "tester"),
                ),
            ),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val events = client.execute(request).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals(200, success.response.statusCode)
        assertTrue(success.response.bodyText.contains("Multipart form-data received"))
        assertTrue(success.response.bodyText.contains("Alice"))
    }

    @Test
    fun websocket_request_over_ws_url_succeeds() = runBlocking {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val request = RequestDefinition(
            id = "ws-1",
            name = "ws",
            method = HttpMethodType.GET,
            url = "ws://localhost:$port/ws",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        val events = client.execute(request).toList()
        val success = events.last() as NetworkEvent.Success
        assertEquals(101, success.response.statusCode)
        assertTrue(success.response.bodyText.contains("Connected") || success.response.bodyText.contains("Echo:"))
    }
}
