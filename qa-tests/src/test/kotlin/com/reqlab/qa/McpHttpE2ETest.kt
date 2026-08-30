package com.reqlab.qa

import com.reqlab.core.model.AuthConfig
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.McpConnectionConfig
import com.reqlab.core.model.McpHttpMode
import com.reqlab.core.model.McpOAuthConfig
import com.reqlab.core.model.McpOAuthGrantType
import com.reqlab.core.model.McpRoot
import com.reqlab.core.model.McpSamplingMode
import com.reqlab.core.model.McpTransportType
import com.reqlab.core.network.mcp.McpClient
import com.reqlab.core.network.mcp.McpOAuthClient
import com.reqlab.core.network.mcp.McpUnauthorizedException
import com.reqlab.core.network.mcp.NdjsonStdioTransport
import com.reqlab.core.network.mcp.createStdioTransport
import com.reqlab.core.network.mcp.mcpStdioSupported
import com.reqlab.server.module
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpHttpE2ETest {

    private fun http() = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }

    @Test
    fun initialize_list_call_and_resource() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(url = "$BASE_URL/mcp", httpMode = McpHttpMode.STREAMABLE_2025_06_18),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        assertTrue(client.sessionId != null)
        val tools = client.listTools().map { it.name }
        assertTrue(tools.containsAll(listOf("echo", "add", "fail")))
        val echo = client.callTool("echo", buildJsonObject { put("text", "hi") })
        assertEquals("hi", echo.content.single().text)
        assertEquals(false, echo.isError)
        val fail = client.callTool("fail")
        assertTrue(fail.isError)
        val resource = client.readResource("reqlab://docs/welcome")
        assertTrue(resource.contents.single().text!!.contains("Welcome"))
        client.subscribeResource("reqlab://docs/welcome")
        val prompts = client.listPrompts()
        assertEquals("greet", prompts.single().name)
        val completion = client.complete(
            buildJsonObject { put("type", "ref/resource") },
            "name",
            "wel",
        )
        assertTrue(completion.completion.values.contains("welcome"))
        client.disconnect()
    }

    @Test
    fun subscribe_and_response_headers_captured() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        client.connect(
            McpConnectionConfig(url = "$BASE_URL/mcp", httpMode = McpHttpMode.STREAMABLE_2025_06_18),
        )
        client.listTools()
        val headers = client.lastResponseHeaders
        assertTrue(headers != null && headers.isNotEmpty(), "HTTP response headers should be captured")
        // Subscribing against a subscribe-capable mock should not raise a JSON-RPC error.
        client.subscribeResource("reqlab://docs/welcome")
        client.disconnect()
    }

    @Test
    fun subscribe_emits_resource_updated_notification() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        client.connect(
            McpConnectionConfig(url = "$BASE_URL/mcp", httpMode = McpHttpMode.STREAMABLE_2025_06_18),
        )
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
        val updated = async {
            withTimeout(10_000) {
                client.notifications
                    .onStart { ready.complete(Unit) }
                    .first { it.method == "notifications/resources/updated" }
            }
        }
        ready.await()
        client.subscribeResource("reqlab://docs/welcome")
        val notification = updated.await()
        val uri = (notification.params as? JsonObject)?.get("uri")?.jsonPrimitive?.contentOrNull
        assertEquals("reqlab://docs/welcome", uri)
        val headers = client.lastResponseHeaders
        assertTrue(headers != null && headers.isNotEmpty(), "subscribe response headers should be captured")
        client.unsubscribeResource("reqlab://docs/welcome")
        client.disconnect()
    }

    @Test
    fun bearer_and_api_key_auth_headers_are_accepted() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp/authed",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                auth = AuthConfig(AuthType.BEARER, mapOf("token" to "reqlab-mcp-token")),
                headers = listOf(KeyValueEntry("X-Api-Key", "reqlab-key")),
            ),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        assertTrue(client.sessionId != null)
        val headers = client.lastResponseHeaders
        assertTrue(headers != null && headers.isNotEmpty())
        val tools = client.listTools().map { it.name }
        assertTrue(tools.contains("echo"))
        client.disconnect()
    }

    @Test
    fun missing_auth_on_authed_endpoint_is_unauthorized() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val error = runCatching {
            client.connect(McpConnectionConfig(url = "$BASE_URL/mcp/authed"))
        }.exceptionOrNull()
        assertTrue(error is McpUnauthorizedException, "expected unauthorized, got $error")
        client.disconnect()
    }

    @Test
    fun bearer_only_auth_is_accepted() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp/auth/bearer",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                auth = AuthConfig(AuthType.BEARER, mapOf("token" to "reqlab-mcp-token")),
            ),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        client.disconnect()
    }

    @Test
    fun bearer_only_auth_rejects_missing_token() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val error = runCatching {
            client.connect(McpConnectionConfig(url = "$BASE_URL/mcp/auth/bearer"))
        }.exceptionOrNull()
        assertTrue(error is McpUnauthorizedException, "expected unauthorized, got $error")
        client.disconnect()
    }

    @Test
    fun basic_auth_is_accepted() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp/auth/basic",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                auth = AuthConfig(AuthType.BASIC, mapOf("username" to "admin", "password" to "password")),
            ),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        client.disconnect()
    }

    @Test
    fun basic_auth_rejects_wrong_password() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val error = runCatching {
            client.connect(
                McpConnectionConfig(
                    url = "$BASE_URL/mcp/auth/basic",
                    auth = AuthConfig(AuthType.BASIC, mapOf("username" to "admin", "password" to "nope")),
                ),
            )
        }.exceptionOrNull()
        assertTrue(error is McpUnauthorizedException, "expected unauthorized, got $error")
        client.disconnect()
    }

    @Test
    fun api_key_auth_is_accepted() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp/auth/apikey",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                auth = AuthConfig(AuthType.API_KEY, mapOf("key" to "X-Api-Key", "value" to "reqlab-key")),
            ),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        client.disconnect()
    }

    @Test
    fun api_key_auth_rejects_missing_header() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val error = runCatching {
            client.connect(McpConnectionConfig(url = "$BASE_URL/mcp/auth/apikey"))
        }.exceptionOrNull()
        assertTrue(error is McpUnauthorizedException, "expected unauthorized, got $error")
        client.disconnect()
    }

    @Test
    fun jwt_auth_is_accepted() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp/auth/jwt",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                auth = AuthConfig(AuthType.JWT, mapOf("token" to "reqlab-mcp-jwt")),
            ),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        client.disconnect()
    }

    @Test
    fun jwt_auth_rejects_wrong_token() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val error = runCatching {
            client.connect(
                McpConnectionConfig(
                    url = "$BASE_URL/mcp/auth/jwt",
                    auth = AuthConfig(AuthType.JWT, mapOf("token" to "reqlab-mcp-token")),
                ),
            )
        }.exceptionOrNull()
        assertTrue(error is McpUnauthorizedException, "expected unauthorized, got $error")
        client.disconnect()
    }

    @Test
    fun url_query_params_are_sent_and_required_tenant_is_accepted() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(
                url = "{{base}}/mcp?requireTenant=true&tenant={{tenant}}",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
            ),
            variableLayers = listOf(mapOf("base" to BASE_URL, "tenant" to "acme")),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        client.disconnect()
    }

    @Test
    fun url_query_params_reject_missing_required_tenant() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val error = runCatching {
            client.connect(
                McpConnectionConfig(
                    url = "$BASE_URL/mcp?requireTenant=true",
                    httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                ),
            )
        }.exceptionOrNull()
        assertTrue(error != null, "expected connect to fail without tenant")
        assertTrue(error!!.message!!.contains("missing_tenant") || error.message!!.contains("400"), error.message)
        client.disconnect()
    }

    @Test
    fun stateless_query_param_still_initializes() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp?stateless=true",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
            ),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        client.disconnect()
    }

    @Test
    fun url_variables_are_interpolated_on_connect() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(url = "{{base}}{{path}}", httpMode = McpHttpMode.STREAMABLE_2025_06_18),
            variableLayers = listOf(mapOf("base" to BASE_URL, "path" to "/mcp")),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        client.disconnect()
    }

    @Test
    fun unknown_method_is_jsonrpc_error() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        client.connect(McpConnectionConfig(url = "$BASE_URL/mcp"))
        val error = runCatching { client.callTool("nope") }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("-32602") || error.message!!.contains("Unknown"))
        client.disconnect()
    }

    @Test
    fun bidirectional_sampling_via_stdio_framing() = runBlocking {
        val inbound = Channel<String>(Channel.UNLIMITED)
        val written = Channel<String>(Channel.UNLIMITED)
        val transport = NdjsonStdioTransport(this, inbound, { written.send(it) })
        val client = McpClient(this, stdioFactory = { transport }, callTimeoutMs = 10_000)
        val job = async {
            client.connect(
                com.reqlab.core.model.McpConnectionConfig(
                    transport = com.reqlab.core.model.McpTransportType.STDIO,
                    command = "unused",
                ),
            )
        }
        written.receive()
        inbound.send("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"pipe","version":"1"}}}""")
        written.receive() // notifications/initialized
        job.await()
        inbound.send("""{"jsonrpc":"2.0","id":"srv-1","method":"sampling/createMessage","params":{"messages":[],"maxTokens":8}}""")
        val reply = written.receive()
        assertTrue(reply.contains("mock reply"))
        client.disconnect()
    }

    @Test
    fun oauth_client_credentials_against_sample_server() = runBlocking {
        val http = http()
        val oauth = McpOAuthClient(http)
        val tokens = oauth.authorize(
            "$BASE_URL/mcp/secure",
            McpOAuthConfig(
                authServerUrl = BASE_URL,
                grantType = McpOAuthGrantType.CLIENT_CREDENTIALS,
                useDcr = true,
            ),
        )
        assertEquals("mcp-oauth-token", tokens.accessToken)
        val client = McpClient(this, http, oauthClient = oauth, callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp/secure",
                oauth = tokens,
            ),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        client.disconnect()
    }

    @Test
    fun desktop_stdio_is_supported() {
        assertTrue(mcpStdioSupported)
    }

    @Test
    fun legacy_http_sse_initialize_and_tools_list() = runBlocking {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        val init = client.connect(
            McpConnectionConfig(
                url = "$BASE_URL/mcp/sse",
                httpMode = McpHttpMode.LEGACY_2024_11_05,
            ),
        )
        assertEquals("ReqLab MCP Mock", init.serverInfo.name)
        val tools = client.listTools().map { it.name }
        assertTrue(tools.contains("echo"))
        client.disconnect()
    }

    @Test
    fun stdio_sample_server_initialize_and_echo() = runBlocking {
        val java = ProcessHandle.current().info().command().orElse("java")
        val process = ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            "com.reqlab.server.ApplicationKt",
            "--stdio",
        ).start()
        try {
            val inbound = Channel<String>(Channel.UNLIMITED)
            val readerJob = launch(Dispatchers.IO) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { inbound.trySend(it) }
                }
            }
            val transport = NdjsonStdioTransport(
                scope = this,
                incomingLines = inbound,
                writeLine = {
                    process.outputStream.write((it + "\n").toByteArray(Charsets.UTF_8))
                    process.outputStream.flush()
                },
                onClose = { process.destroy() },
            )
            val client = McpClient(this, stdioFactory = { transport }, callTimeoutMs = 15_000)
            val init = client.connect(
                McpConnectionConfig(transport = McpTransportType.STDIO, command = "unused"),
            )
            assertEquals("ReqLab MCP Mock", init.serverInfo.name)
            val echo = client.callTool("echo", buildJsonObject { put("text", "hi") })
            assertEquals("hi", echo.content.single().text)
            client.disconnect()
            readerJob.cancel()
        } finally {
            process.destroyForcibly()
        }
    }

    @Test
    fun stdio_command_line_splits_and_launches_sample_server() = runBlocking {
        val transport = createStdioTransport(
            McpConnectionConfig(transport = McpTransportType.STDIO, command = repoMcpStdioLauncher()),
        )
        try {
            val client = McpClient(this, stdioFactory = { transport }, callTimeoutMs = 15_000)
            val init = client.connect(
                McpConnectionConfig(transport = McpTransportType.STDIO, command = "unused"),
            )
            assertEquals("ReqLab MCP Mock", init.serverInfo.name)
            client.disconnect()
        } finally {
            transport.close()
        }
    }

    @Test
    fun trigger_sampling_returns_mock_reply() = runBlocking {
        verifyCallback(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                samplingMode = McpSamplingMode.MOCK,
            ),
            tool = "trigger_sampling",
            mustContain = listOf("mock reply from ReqLab"),
            mustNotContain = listOf("cancelled", "Invalid request"),
        )
    }

    @Test
    fun trigger_sampling_manual_is_cancelled() = runBlocking {
        verifyCallback(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                samplingMode = McpSamplingMode.MANUAL,
            ),
            tool = "trigger_sampling",
            mustContain = listOf("cancelled"),
            mustNotContain = listOf("mock reply from ReqLab", "Invalid request"),
        )
    }

    @Test
    fun trigger_sampling_forward_llm_echoes_chat_completion() = runBlocking {
        verifyCallback(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                samplingMode = McpSamplingMode.FORWARD_LLM,
                samplingForwardUrl = "$BASE_URL/v1/chat/completions",
                samplingForwardToken = "llm-test-key",
            ),
            tool = "trigger_sampling",
            mustContain = listOf("Hello from ReqLab"),
            mustNotContain = listOf("mock reply from ReqLab", "Invalid request"),
        )
    }

    @Test
    fun trigger_roots_returns_configured_roots() = runBlocking {
        verifyCallback(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                roots = listOf(McpRoot("file:///tmp/reqlab", "tmp")),
            ),
            tool = "trigger_roots",
            mustContain = listOf("file:///tmp/reqlab", "tmp"),
            mustNotContain = listOf("Invalid request"),
        )
    }

    @Test
    fun trigger_roots_empty_list_is_not_tmp() = runBlocking {
        verifyCallback(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                roots = emptyList(),
            ),
            tool = "trigger_roots",
            mustContain = listOf("\"roots\":[]"),
            mustNotContain = listOf("file:///tmp/reqlab", "Invalid request"),
        )
    }

    @Test
    fun trigger_elicitation_accept_when_auto() = runBlocking {
        verifyCallback(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                autoRespondElicitation = true,
            ),
            tool = "trigger_elicitation",
            mustContain = listOf("accept"),
            mustNotContain = listOf("decline", "Invalid request"),
        )
    }

    @Test
    fun trigger_elicitation_decline_when_disabled() = runBlocking {
        verifyCallback(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
                autoRespondElicitation = false,
            ),
            tool = "trigger_elicitation",
            mustContain = listOf("decline"),
            mustNotContain = listOf("\"accept\"", "Invalid request"),
        )
    }

    @Test
    fun trigger_ping_echoes_empty_result() = runBlocking {
        verifyCallback(
            McpConnectionConfig(
                url = "$BASE_URL/mcp",
                httpMode = McpHttpMode.STREAMABLE_2025_06_18,
            ),
            tool = "trigger_ping",
            mustContain = listOf("srv-ping"),
            mustNotContain = listOf("Invalid request"),
        )
    }

    @Test
    fun trigger_roots_legacy_sse_echoes_configured_roots() = runBlocking {
        verifyCallback(
            McpConnectionConfig(
                url = "$BASE_URL/mcp/sse",
                httpMode = McpHttpMode.LEGACY_2024_11_05,
                roots = listOf(McpRoot("file:///tmp/reqlab", "tmp")),
            ),
            tool = "trigger_roots",
            mustContain = listOf("file:///tmp/reqlab", "tmp"),
            mustNotContain = listOf("Invalid request"),
        )
    }

    @Test
    fun stdio_trigger_roots_echoes_configured_roots() = runBlocking {
        val java = ProcessHandle.current().info().command().orElse("java")
        val process = ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            "com.reqlab.server.ApplicationKt",
            "--stdio",
        ).start()
        try {
            val inbound = Channel<String>(Channel.UNLIMITED)
            val readerJob = launch(Dispatchers.IO) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { inbound.trySend(it) }
                }
            }
            val transport = NdjsonStdioTransport(
                scope = this,
                incomingLines = inbound,
                writeLine = {
                    process.outputStream.write((it + "\n").toByteArray(Charsets.UTF_8))
                    process.outputStream.flush()
                },
                onClose = { process.destroy() },
            )
            val client = McpClient(this, stdioFactory = { transport }, callTimeoutMs = 15_000)
            client.connect(
                McpConnectionConfig(
                    transport = McpTransportType.STDIO,
                    command = "unused",
                    roots = listOf(McpRoot("file:///tmp/reqlab", "tmp")),
                ),
            )
            val result = client.callTool("trigger_roots")
            assertCallbackOracle(
                toolText = result.content.single().text.orEmpty(),
                isError = result.isError,
                lastPayload = client.lastReceivedPayload.orEmpty(),
                mustContain = listOf("file:///tmp/reqlab", "tmp"),
                mustNotContain = listOf("Invalid request"),
            )
            client.disconnect()
            readerJob.cancel()
        } finally {
            process.destroyForcibly()
        }
    }

    private suspend fun CoroutineScope.verifyCallback(
        config: McpConnectionConfig,
        tool: String,
        mustContain: List<String>,
        mustNotContain: List<String> = emptyList(),
    ) {
        val client = McpClient(this, http(), callTimeoutMs = 10_000)
        client.connect(config)
        val result = client.callTool(tool)
        assertCallbackOracle(
            toolText = result.content.single().text.orEmpty(),
            isError = result.isError,
            lastPayload = client.lastReceivedPayload.orEmpty(),
            mustContain = mustContain,
            mustNotContain = mustNotContain,
        )
        client.disconnect()
    }

    private fun assertCallbackOracle(
        toolText: String,
        isError: Boolean,
        lastPayload: String,
        mustContain: List<String>,
        mustNotContain: List<String>,
    ) {
        assertEquals(false, isError, "tool error: $toolText")
        mustContain.forEach { needle ->
            assertTrue(toolText.contains(needle), "tool text missing '$needle': $toolText")
        }
        mustNotContain.forEach { needle ->
            assertTrue(!toolText.contains(needle), "tool text should not contain '$needle': $toolText")
        }
        assertTrue(lastPayload.contains("\"result\""), "lastReceivedPayload is not a tools/call result: $lastPayload")
        assertTrue(!lastPayload.contains("Invalid request"), "lastReceivedPayload has Invalid request: $lastPayload")
        assertTrue(!lastPayload.contains("-32600"), "lastReceivedPayload has -32600: $lastPayload")
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

        /** Test-only: locate this checkout's stdio launcher without product walk-up. */
        private fun repoMcpStdioLauncher(): String {
            var dir = File(System.getProperty("user.dir"))
            repeat(8) {
                val candidate = File(dir, "sample-server/mcp-stdio")
                if (candidate.isFile) return candidate.absolutePath
                dir = dir.parentFile ?: return "sample-server/mcp-stdio"
            }
            return "sample-server/mcp-stdio"
        }
    }
}
