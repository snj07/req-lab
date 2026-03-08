package com.reqlab.qa

import com.reqlab.testsupport.DummyApiServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebSocketE2ETest {
    private lateinit var server: DummyApiServer
    private lateinit var client: HttpClient

    @BeforeTest
    fun setUp() {
        server = DummyApiServer()
        server.start()
        client = HttpClient(CIO) {
            install(WebSockets)
        }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.stop()
    }

    @Test
    fun connect_send_receive_and_disconnect() {
        runBlocking {
        val wsUrl = server.baseUrl().replace("http", "ws") + "/ws"

        withTimeout(5_000) {
            client.webSocket(urlString = wsUrl) {
                val connected = (incoming.receive() as Frame.Text).readText()
                assertEquals("connected", connected)

                send(Frame.Text("hello"))
                val echoed = (incoming.receive() as Frame.Text).readText()
                assertEquals("echo:hello", echoed)

                send(Frame.Text("close"))
                val goodbye = (incoming.receive() as Frame.Text).readText()
                assertEquals("bye", goodbye)
            }
        }
        }
    }
}
