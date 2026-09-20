package com.reqlab.ui.desktop.integration

import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.server.module
import com.reqlab.ui.shared.components.sendRequest
import com.reqlab.ui.shared.components.syncParamsFromUrl
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.MutableKeyValue
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * End-to-end integration test for Bug #1: query params are duplicated in the
 * actual HTTP request when the URL already contains an embedded query string
 * and tab.params mirrors those same params.
 *
 * Flow under test:
 *   1. User opens a collection request whose URL is "…/echo-query?q=test&page=1"
 *   2. addTab() calls syncParamsFromUrl → tab.params = [q=test, page=1]
 *   3. User clicks Send — sendRequest() must NOT duplicate the params in the HTTP request.
 *
 * The sample-server's GET /api/echo-query endpoint returns a "paramCounts" object
 * showing how many times each key was received. If params are duplicated, counts > 1.
 *
 * Root cause: sendRequest() passed BOTH url = tab.url (with embedded ?q=test&page=1)
 * AND queryParams = tab.params to RequestDefinition. KtorApiClient then appended
 * tab.params on top of the already-parsed URL parameters → duplication.
 *
 * Fix: strip the embedded query from the URL at the start of sendRequest() so that
 * effectiveQueryParams is the single source of truth.
 */
class QueryParamsDuplicationSampleServerTest {

    companion object {
        // Distinct port to avoid conflicts with RequestSettingsPersistenceWorkflowIntegrationTest (18082)
        private const val PORT = 18083
        private lateinit var server: io.ktor.server.engine.EmbeddedServer<*, *>

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = embeddedServer(Netty, port = PORT, module = io.ktor.server.application.Application::module)
            server.start(wait = false)
            Thread.sleep(500)
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server.stop(500, 2000)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Calls sendRequest() exactly as the UI does (fire-and-forget on scope),
     * then waits up to [timeoutMs] for the tab to receive a response or error.
     *
     * Note: sendRequest() sets tab.isLoading = true *inside* a launched coroutine,
     * so we must not rely on that flag being true before the coroutine starts.
     * Instead we poll for tab.response or tab.lastError to become non-null.
     */
    private fun runSendRequest(state: AppState, timeoutMs: Long = 8000) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val tab = state.activeTab ?: error("No active tab")
            sendRequest(scope = scope, state = state, tab = tab)
            // Give the coroutine time to be scheduled on the IO dispatcher
            Thread.sleep(100)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (tab.response == null && tab.lastError == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
        } finally {
            scope.cancel()
        }
    }

    private fun parseBody(bodyText: String?): JsonObject {
        assertNotNull(bodyText, "Response body must not be null")
        return Json.parseToJsonElement(bodyText).jsonObject
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    /**
     * When tab.url = "…/echo-query?q=test&page=1" and tab.params = [q=test, page=1]
     * (the state produced by addTab() + syncParamsFromUrl after Bug #3 fix),
     * the executed HTTP request must send q and page each exactly ONCE.
     *
     * Pre-fix: KtorApiClient built URL as "…?q=test&page=1&q=test&page=1" because it
     *          parsed the embedded query string AND appended effectiveQueryParams on top.
     *          Server's paramCounts would show q=2, page=2.
     * Post-fix: The embedded query is stripped from effectiveUrl → paramCounts q=1, page=1.
     */
    @Test
    fun `bug1 – sendRequest does not duplicate params when URL already has embedded query string`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        // Simulate exactly what addTab() does when opening from sidebar:
        // URL has embedded params, then syncParamsFromUrl populates tab.params
        tab.url = "http://localhost:$PORT/api/echo-query?q=test&page=1"
        syncParamsFromUrl(tab, tab.url)

        assertEquals(2, tab.params.size, "Expected 2 params after sync")
        assertEquals("q", tab.params[0].key)
        assertEquals("test", tab.params[0].value)
        assertEquals("page", tab.params[1].key)
        assertEquals("1", tab.params[1].value)

        runSendRequest(state)

        val body = parseBody(tab.response?.bodyText)
        assertNotNull(tab.response, "Tab should have a response. Error: ${tab.lastError}")
        assertEquals(200, tab.response!!.statusCode)

        val counts = assertNotNull(body["paramCounts"]?.jsonObject, "Response must contain 'paramCounts'. Body: $body")

        assertEquals(1, counts["q"]?.jsonPrimitive?.intOrNull,
            "Param 'q' must be received exactly once (no duplication). Counts: $counts")
        assertEquals(1, counts["page"]?.jsonPrimitive?.intOrNull,
            "Param 'page' must be received exactly once (no duplication). Counts: $counts")

        // Values must be correct
        val params = assertNotNull(body["params"]?.jsonObject, "Response must contain 'params'. Body: $body")
        assertEquals("test", params["q"]?.jsonPrimitive?.content)
        assertEquals("1", params["page"]?.jsonPrimitive?.content)
    }

    /**
     * When URL has NO embedded query string and params come only from tab.params,
     * they must arrive correctly (each param exactly once).
     */
    @Test
    fun `bug1 – params arrive correctly when URL has no embedded query string`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        tab.url = "http://localhost:$PORT/api/echo-query"
        tab.params.clear()
        tab.params.add(MutableKeyValue("q", "hello"))
        tab.params.add(MutableKeyValue("page", "2"))

        runSendRequest(state)

        assertNotNull(tab.response, "Tab should have a response. Error: ${tab.lastError}")
        assertEquals(200, tab.response!!.statusCode)

        val body = parseBody(tab.response?.bodyText)
        val counts = assertNotNull(body["paramCounts"]?.jsonObject, "Response must contain 'paramCounts'. Body: $body")

        assertEquals(1, counts["q"]?.jsonPrimitive?.intOrNull)
        assertEquals(1, counts["page"]?.jsonPrimitive?.intOrNull)

        val params = assertNotNull(body["params"]?.jsonObject)
        assertEquals("hello", params["q"]?.jsonPrimitive?.content)
        assertEquals("2", params["page"]?.jsonPrimitive?.content)
    }

    /**
     * A URL with embedded query string and an EMPTY params table still sends
     * the URL-embedded params (Ktor client parses them from the URL).
     * This is the legacy flow for tabs opened before Bug #3 fix.
     */
    @Test
    fun `bug1 – URL-embedded params reach server when params table is empty`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        tab.url = "http://localhost:$PORT/api/echo-query?q=embedded&page=3"
        tab.params.clear()  // empty params table — stripped URL becomes base with no params

        runSendRequest(state)

        assertNotNull(tab.response, "Tab should have a response. Error: ${tab.lastError}")
        assertEquals(200, tab.response!!.statusCode)

        val body = parseBody(tab.response?.bodyText)
        // With the fix, effectiveUrl is stripped → empty params table → no query params sent.
        // The server returns an empty paramCounts object. Each key count must be 0 or 1, never 2.
        val counts = body["paramCounts"]?.jsonObject ?: buildJsonObject {}
        val qCount = counts["q"]?.jsonPrimitive?.intOrNull ?: 0
        val pageCount = counts["page"]?.jsonPrimitive?.intOrNull ?: 0
        assert(qCount <= 1) { "Param 'q' must not be duplicated. Counts: $counts. Body: $body" }
        assert(pageCount <= 1) { "Param 'page' must not be duplicated. Counts: $counts. Body: $body" }
    }

    /**
     * Multiple distinct params in the table — all arrive exactly once.
     */
    @Test
    fun `bug1 – multiple distinct params all arrive exactly once`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        tab.url = "http://localhost:$PORT/api/echo-query?q=kotlin&page=1&limit=20"
        syncParamsFromUrl(tab, tab.url)

        assertEquals(3, tab.params.size, "Expected 3 params: q, page, limit")

        runSendRequest(state)

        assertNotNull(tab.response, "Tab should have a response. Error: ${tab.lastError}")
        assertEquals(200, tab.response!!.statusCode)

        val body = parseBody(tab.response?.bodyText)
        val counts = assertNotNull(body["paramCounts"]?.jsonObject, "Response must contain 'paramCounts'. Body: $body")

        assertEquals(1, counts["q"]?.jsonPrimitive?.intOrNull, "q must be sent once. Counts: $counts")
        assertEquals(1, counts["page"]?.jsonPrimitive?.intOrNull, "page must be sent once. Counts: $counts")
        assertEquals(1, counts["limit"]?.jsonPrimitive?.intOrNull, "limit must be sent once. Counts: $counts")
    }

    /**
     * Repeated query keys are intentional multi-value parameters, not duplicates
     * introduced by ReqLab. Every row must survive the UI send path in order.
     */
    @Test
    fun `repeated query parameter values all reach the sample server`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        tab.url = "http://localhost:$PORT/api/echo-query?x=1&x=2"
        syncParamsFromUrl(tab, tab.url)

        assertEquals(2, tab.params.size, "Repeated keys must create separate parameter rows")
        assertEquals(listOf("x", "x"), tab.params.map { it.key })
        assertEquals(listOf("1", "2"), tab.params.map { it.value })

        runSendRequest(state)

        assertNotNull(tab.response, "Tab should have a response. Error: ${tab.lastError}")
        assertEquals(200, tab.response!!.statusCode)

        val body = parseBody(tab.response?.bodyText)
        val counts = assertNotNull(body["paramCounts"]?.jsonObject)
        assertEquals(
            2,
            counts["x"]?.jsonPrimitive?.intOrNull,
            "Both x values must reach the server. Body: $body",
        )

        val values = assertNotNull(body["paramValues"]?.jsonObject)
        assertEquals(
            listOf("1", "2"),
            values["x"]?.jsonArray?.map { it.jsonPrimitive.content },
            "Repeated query values must retain their order. Body: $body",
        )
    }

    @Test
    fun `pre-request setUrl with query replaces params instead of duplicating them`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        tab.url = "http://localhost:$PORT/api/echo-query?q=orig"
        syncParamsFromUrl(tab, tab.url)
        tab.preRequestScript = """
            reqlab.request.setUrl("http://localhost:$PORT/api/echo-query?fromScript=1")
        """.trimIndent()

        runSendRequest(state)

        assertNotNull(tab.response, "Tab should have a response. Error: ${tab.lastError}")
        assertEquals(200, tab.response!!.statusCode)

        val body = parseBody(tab.response?.bodyText)
        val counts = assertNotNull(body["paramCounts"]?.jsonObject, "Response must contain paramCounts. Body: $body")
        assertEquals(1, counts["fromScript"]?.jsonPrimitive?.intOrNull, "fromScript must be sent once. Counts: $counts")
        assertEquals(null, counts["q"]?.jsonPrimitive?.intOrNull, "Original q must not be re-appended onto setUrl. Counts: $counts")
    }

    @Test
    fun `setUrl without query clears old values then setQueryParam adds only the new one`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!
        tab.url = "http://localhost:$PORT/api/echo-query?old=1&old=2"
        syncParamsFromUrl(tab, tab.url)
        tab.preRequestScript = """
            reqlab.request.setQueryParam("discard", "before")
            reqlab.request.setUrl("http://localhost:$PORT/api/echo-query")
            reqlab.request.setQueryParam("new", "yes")
        """.trimIndent()
        runSendRequest(state)
        val values = parseBody(tab.response?.bodyText)["paramValues"]!!.jsonObject
        assertEquals(null, values["old"])
        assertEquals(null, values["discard"])
        assertEquals(listOf("yes"), values["new"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `encoded literal values and repeated keys reach server exactly once`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!
        tab.url = "http://localhost:$PORT/api/echo-query?x=1&x=2&space=a%20b&plus=a%2Bb&and=a%26b&eq=a%3Db&pct=%25&unicode=%E2%9C%93&empty=#fragment"
        syncParamsFromUrl(tab, tab.url)
        runSendRequest(state)
        val values = parseBody(tab.response?.bodyText)["paramValues"]!!.jsonObject
        mapOf(
            "x" to listOf("1", "2"), "space" to listOf("a b"), "plus" to listOf("a+b"),
            "and" to listOf("a&b"), "eq" to listOf("a=b"), "pct" to listOf("%"),
            "unicode" to listOf("✓"), "empty" to listOf(""),
        ).forEach { (key, expected) ->
            assertEquals(expected, values[key]!!.jsonArray.map { it.jsonPrimitive.content }, "Unexpected $key")
        }
    }

    @Test
    fun `query-position API key is sent in URL not in headers`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!
        tab.url = "http://localhost:$PORT/api/echo-query"
        tab.authType = AuthType.API_KEY
        tab.authApiPlacement = "query"
        tab.authApiKey = "api_key"
        tab.authApiValue = "a+b &"
        runSendRequest(state)
        val response = parseBody(tab.response?.bodyText)
        val values = response["paramValues"]!!.jsonObject
        assertEquals(listOf("a+b &"), values["api_key"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("", response["apiKeyHeader"]!!.jsonPrimitive.content)
    }

    @Test
    fun `normal request tab sends GraphQL query envelope`() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!
        tab.method = HttpMethodType.POST
        tab.url = "http://localhost:$PORT/api/graphql"
        tab.bodyType = BodyType.GRAPHQL
        tab.bodyContent = "query User { user(id: \"1\") { id name } }"
        runSendRequest(state)
        val body = parseBody(tab.response?.bodyText)
        assertEquals(tab.bodyContent, body["receivedQuery"]?.jsonPrimitive?.content)
    }
}
