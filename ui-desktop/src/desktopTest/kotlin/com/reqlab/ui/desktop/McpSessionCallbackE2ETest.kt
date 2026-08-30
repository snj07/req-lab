package com.reqlab.ui.desktop

import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.McpContent
import com.reqlab.core.model.McpCreateMessageResult
import com.reqlab.core.model.McpHttpMode
import com.reqlab.core.model.McpSamplingMode
import com.reqlab.core.network.mcp.McpClient
import com.reqlab.server.module
import com.reqlab.ui.shared.mcp.McpPendingSampling
import com.reqlab.ui.shared.mcp.McpSessionState
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpSessionCallbackE2ETest {

    @Test
    fun sampling_pane_typed_result_is_echoed_by_live_server() = withLiveSession { session ->
        session.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                samplingMode = McpSamplingMode.MANUAL,
            ),
        )
        val call = async { session.callSelectedTool("trigger_sampling", null) }
        withTimeout(10_000) {
            session.pendingSampling.first { it is McpPendingSampling.ReviewRequest }
        }
        session.approveSamplingGenerate()
        withTimeout(10_000) {
            session.pendingSampling.first { it is McpPendingSampling.ReviewResult && !it.generating }
        }
        session.submitSamplingResult(
            McpCreateMessageResult(
                role = "assistant",
                content = McpContent(type = "text", text = "typed-from-test"),
                model = "test-model",
                stopReason = "endTurn",
            ),
        )
        call.await()
        val text = session.lastToolResult.value?.content?.single()?.text.orEmpty()
        val payload = session.client?.lastReceivedPayload.orEmpty()
        assertEquals(false, session.lastToolResult.value?.isError)
        assertTrue(text.contains("typed-from-test"), "tool text: $text")
        assertTrue(!text.contains("mock reply from ReqLab"), "tool text: $text")
        assertTrue(payload.contains("\"result\""), payload)
        assertTrue(!payload.contains("-32600"), payload)
    }

    @Test
    fun sampling_pane_cancel_echoes_cancelled() = withLiveSession { session ->
        session.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                samplingMode = McpSamplingMode.MANUAL,
            ),
        )
        val call = async { session.callSelectedTool("trigger_sampling", null) }
        withTimeout(10_000) {
            session.pendingSampling.first { it is McpPendingSampling.ReviewRequest }
        }
        session.cancelSampling()
        call.await()
        val text = session.lastToolResult.value?.content?.single()?.text.orEmpty()
        assertEquals(false, session.lastToolResult.value?.isError)
        assertTrue(text.contains("cancelled"), "tool text: $text")
    }

    @Test
    fun elicitation_pane_accept_echoes_field() = withLiveSession { session ->
        session.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                autoRespondElicitation = false,
            ),
        )
        val call = async { session.callSelectedTool("trigger_elicitation", null) }
        withTimeout(10_000) {
            session.pendingElicitation.first { it != null }
        }
        session.updatePendingElicitArgs("""{"name":"typed-from-test"}""")
        session.submitElicitation()
        call.await()
        val text = session.lastToolResult.value?.content?.single()?.text.orEmpty()
        val payload = session.client?.lastReceivedPayload.orEmpty()
        assertEquals(false, session.lastToolResult.value?.isError)
        assertTrue(text.contains("accept"), "tool text: $text")
        assertTrue(text.contains("typed-from-test"), "tool text: $text")
        assertTrue(payload.contains("\"result\""), payload)
        assertTrue(!payload.contains("-32600"), payload)
    }

    @Test
    fun elicitation_pane_decline_echoes_decline() = withLiveSession { session ->
        session.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                autoRespondElicitation = false,
            ),
        )
        val call = async { session.callSelectedTool("trigger_elicitation", null) }
        withTimeout(10_000) {
            session.pendingElicitation.first { it != null }
        }
        session.declineElicitation()
        call.await()
        val text = session.lastToolResult.value?.content?.single()?.text.orEmpty()
        assertEquals(false, session.lastToolResult.value?.isError)
        assertTrue(text.contains("decline"), "tool text: $text")
    }

    private fun withLiveSession(block: suspend kotlinx.coroutines.CoroutineScope.(McpSessionState) -> Unit) =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val session = McpSessionState(scope) { clientScope ->
                McpClient(clientScope, callTimeoutMs = 15_000)
            }
            try {
                block(session)
            } finally {
                session.disconnect()
                scope.cancel()
            }
        }

    companion object {
        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private var port: Int = 0
        var BASE_URL: String = ""

        @JvmStatic
        @BeforeClass
        fun startServer() {
            port = ServerSocket(0).use { it.localPort }
            BASE_URL = "http://127.0.0.1:$port"
            server = embeddedServer(Netty, port = port, module = { module() })
            server!!.start(wait = false)
            repeat(50) {
                runCatching { java.net.Socket("127.0.0.1", port).close(); return }
                Thread.sleep(100)
            }
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server?.stop(1000, 2000)
        }
    }
}
