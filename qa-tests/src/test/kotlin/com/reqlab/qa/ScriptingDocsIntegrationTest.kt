package com.reqlab.qa

import com.reqlab.core.scripting.ReqLabScriptEngine
import com.reqlab.core.scripting.ScriptContext
import com.reqlab.core.scripting.ScriptResult
import com.reqlab.server.module
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.net.ServerSocket
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptingDocsIntegrationTest {

    private val engine = ReqLabScriptEngine()
    private val baseUrl get() = BASE_URL

    data class MutableRequest(
        var method: String,
        var url: String,
        val headers: MutableMap<String, String> = linkedMapOf(),
        var body: String? = null,
    )

    data class ScriptRunArtifacts(
        val preResult: ScriptResult,
        val testResult: ScriptResult,
        val responseCode: Int,
        val responseBody: String,
        val responseHeaders: Map<String, String>,
        val finalRequest: MutableRequest,
    )

    companion object {
        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private var port: Int = 0
        private var BASE_URL: String = ""

        private val client = HttpClient(CIO) {
            followRedirects = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

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
            server?.stop(100L, 100L)
        }
    }

    @Test
    fun docs_pre_request_header_query_body_mutation_example_is_valid() = runTest {
        val preScript = """
            var runId = "run-" + Date.now()
            reqlab.environment.set("runId", runId)
            reqlab.request.headers.upsert("X-Run-Id", runId)
            reqlab.request.setQueryParam("source", "pre-script")
            reqlab.request.setBody('{"from":"pre-request"}')
        """.trimIndent()

        val artifacts = executeWithScripts(
            preScript = preScript,
            testScript = """
                reqlab.test("status 200", function() {
                    reqlab.expect(reqlab.response.code).to.equal(200)
                })
                reqlab.test("runId and source reflected", function() {
                    var body = reqlab.response.json()
                    reqlab.expect(body.headers["X-Run-Id"]).to.exist
                    reqlab.expect(body.params.source).to.equal("pre-script")
                })
            """.trimIndent(),
            initialRequest = MutableRequest(
                method = "GET",
                url = "$baseUrl/api/echo-full",
            ),
        )

        assertTrue(artifacts.preResult.success)
        assertTrue(artifacts.preResult.newVariables.containsKey("runId"))
        assertEquals("pre-script", queryParamsOf(artifacts.finalRequest.url)["source"])
        assertEquals("{\"from\":\"pre-request\"}", artifacts.finalRequest.body)
        assertTrue(artifacts.testResult.success)
    }

    @Test
    fun docs_method_and_url_override_example_is_valid() = runTest {
        val artifacts = executeWithScripts(
            preScript = """
                reqlab.request.setMethod("POST")
                reqlab.request.setUrl("$baseUrl/echo-body")
                reqlab.request.setBody('{"changed":true,"source":"pre"}')
            """.trimIndent(),
            testScript = """
                reqlab.test("request was rewritten", function() {
                    var body = reqlab.response.json()
                    reqlab.expect(body.method).to.equal("POST")
                    reqlab.expect(body.body).to.include("\"changed\":true")
                })
            """.trimIndent(),
            initialRequest = MutableRequest(
                method = "GET",
                url = "$baseUrl/status/200",
            ),
        )

        assertTrue(artifacts.preResult.success)
        assertEquals("POST", artifacts.finalRequest.method)
        assertEquals("$baseUrl/echo-body", artifacts.finalRequest.url)
        assertTrue(artifacts.testResult.success)
    }

    @Test
    fun docs_defensive_global_token_handling_example_is_valid() = runTest {
        val withToken = executeWithScripts(
            preScript = """
                var token = reqlab.globals.get("token")
                reqlab.request.headers.upsert("Authorization", token ? "Bearer " + token : "")
            """.trimIndent(),
            testScript = """
                reqlab.test("auth header exists", function() {
                    var body = reqlab.response.json()
                    reqlab.expect(body.headers.Authorization).to.equal("Bearer abc123")
                })
            """.trimIndent(),
            initialRequest = MutableRequest("GET", "$baseUrl/api/echo-full"),
            globals = mapOf("token" to "abc123"),
        )

        assertTrue(withToken.preResult.success)
        assertEquals("Bearer abc123", withToken.finalRequest.headers["Authorization"])
        assertTrue(withToken.testResult.success)

        val withoutToken = executeWithScripts(
            preScript = """
                var token = reqlab.globals.get("token")
                reqlab.request.headers.upsert("Authorization", token ? "Bearer " + token : "")
            """.trimIndent(),
            testScript = """
                reqlab.test("auth header can be empty safely", function() {
                    var body = reqlab.response.json()
                    reqlab.expect(body.headers.Authorization).to.equal("")
                })
            """.trimIndent(),
            initialRequest = MutableRequest("GET", "$baseUrl/api/echo-full"),
        )

        assertTrue(withoutToken.preResult.success)
        assertEquals("", withoutToken.finalRequest.headers["Authorization"])
        assertTrue(withoutToken.testResult.success)
    }

    @Test
    fun docs_status_body_timing_size_and_extract_examples_are_valid() = runTest {
        val artifacts = executeWithScripts(
            preScript = "",
            testScript = """
                reqlab.test("status is 200", function () {
                  reqlab.expect(reqlab.response.code).to.equal(200)
                })

                reqlab.test("response has id", function () {
                  var body = reqlab.response.json()
                  reqlab.expect(body).to.have.property("id")
                })

                reqlab.test("time under 2s", function () {
                  reqlab.expect(reqlab.response.responseTime).to.be.below(2000)
                })

                reqlab.test("payload not empty", function () {
                  reqlab.expect(reqlab.response.size).to.be.above(0)
                })

                reqlab.test("token exists and is extracted", function () {
                  var token = reqlab.response.json().id
                  reqlab.expect(token).to.exist
                  reqlab.environment.set("token", String(token))
                })
            """.trimIndent(),
            initialRequest = MutableRequest(
                method = "GET",
                url = "$baseUrl/json/user",
            ),
        )

        assertTrue(artifacts.testResult.success)
        assertEquals("1", artifacts.testResult.newVariables["token"])
    }

    @Test
    fun docs_json_parse_safe_error_handling_example_is_valid() = runTest {
        val artifacts = executeWithScripts(
            preScript = "",
            testScript = """
                reqlab.test("json parse safe", function () {
                  var threw = false
                  try { reqlab.response.json() } catch (e) { threw = true }
                  reqlab.expect(threw).to.be.false
                })
            """.trimIndent(),
            initialRequest = MutableRequest("GET", "$baseUrl/json/object"),
        )

        assertTrue(artifacts.testResult.success)
        assertFalse(artifacts.testResult.assertions.any { !it.passed })
    }

    @Test
    fun docs_variable_flow_set_in_one_request_use_in_next_request_is_valid() = runTest {
        val first = executeWithScripts(
            preScript = "",
            testScript = """
                reqlab.test("store run id", function () {
                  reqlab.environment.set("savedRun", "flow-123")
                  reqlab.expect(reqlab.environment.get("savedRun")).to.equal("flow-123")
                })
            """.trimIndent(),
            initialRequest = MutableRequest("GET", "$baseUrl/status/200"),
        )

        assertTrue(first.testResult.success)
        val nextEnv = mapOf("savedRun" to (first.testResult.newVariables["savedRun"] ?: ""))

        val second = executeWithScripts(
            preScript = """
                var run = reqlab.environment.get("savedRun")
                reqlab.request.headers.upsert("X-Run-Id", run)
            """.trimIndent(),
            testScript = """
                reqlab.test("reused variable is applied", function() {
                    var body = reqlab.response.json()
                    reqlab.expect(body.headers["X-Run-Id"]).to.equal("flow-123")
                })
            """.trimIndent(),
            initialRequest = MutableRequest("GET", "$baseUrl/api/echo-full"),
            environment = nextEnv,
        )

        assertTrue(second.preResult.success)
        assertTrue(second.testResult.success)
    }

    @Test
    fun docs_failure_behaviour_reports_failed_assertion_without_crashing() = runTest {
        val artifacts = executeWithScripts(
            preScript = "",
            testScript = """
                reqlab.test("intentional fail", function () {
                  reqlab.expect(reqlab.response.code).to.equal(201)
                })
            """.trimIndent(),
            initialRequest = MutableRequest("GET", "$baseUrl/status/200"),
        )

        assertFalse(artifacts.testResult.success)
        assertTrue(artifacts.testResult.assertions.any { !it.passed })
    }

    private suspend fun executeWithScripts(
        preScript: String,
        testScript: String,
        initialRequest: MutableRequest,
        environment: Map<String, String> = emptyMap(),
        globals: Map<String, String> = emptyMap(),
        collections: Map<String, String> = emptyMap(),
    ): ScriptRunArtifacts {
        val preContext = ScriptContext(
            url = initialRequest.url,
            method = initialRequest.method,
            variables = environment,
            globalVariables = globals,
            collectionVariables = collections,
            requestHeaders = initialRequest.headers,
            requestQueryParams = queryParamsOf(initialRequest.url),
            requestBody = initialRequest.body,
        )

        val preResult = engine.executePreRequestScript(preScript, preContext, prefix = "reqlab")
        applyMutations(initialRequest, preResult)

        val mergedEnv = environment + preResult.newVariables
        val mergedGlobals = globals + preResult.newGlobalVariables
        val mergedCollections = collections + preResult.newCollectionVariables

        val elapsedMs: Long
        val statusCode: Int
        val responseBody: String
        val responseHeaders: Map<String, String>

        var localStatusCode = 0
        var localBody = ""
        var localHeaders: Map<String, String> = emptyMap()

        val time = measureTimeMillis {
            val response = client.request(initialRequest.url) {
                method = HttpMethod.parse(initialRequest.method)
                initialRequest.headers.forEach { (k, v) ->
                    headers.append(k, v)
                }
                initialRequest.body?.let { setBody(it) }
            }
            localStatusCode = response.status.value
            localBody = response.bodyAsText()
            localHeaders = response.headers.entries().associate { it.key to (it.value.firstOrNull() ?: "") }
        }

        elapsedMs = time
        statusCode = localStatusCode
        responseBody = localBody
        responseHeaders = localHeaders

        val testContext = ScriptContext(
            url = initialRequest.url,
            method = initialRequest.method,
            statusCode = statusCode,
            responseBody = responseBody,
            responseSizeBytes = responseBody.toByteArray().size.toLong(),
            responseHeaders = responseHeaders,
            responseTimeMs = elapsedMs,
            variables = mergedEnv,
            globalVariables = mergedGlobals,
            collectionVariables = mergedCollections,
            requestHeaders = initialRequest.headers,
            requestQueryParams = queryParamsOf(initialRequest.url),
            requestBody = initialRequest.body,
        )

        val testResult = engine.executeTestScript(testScript, testContext, prefix = "reqlab")

        return ScriptRunArtifacts(
            preResult = preResult,
            testResult = testResult,
            responseCode = statusCode,
            responseBody = responseBody,
            responseHeaders = responseHeaders,
            finalRequest = initialRequest,
        )
    }

    private fun applyMutations(request: MutableRequest, preResult: ScriptResult) {
        preResult.requestMutations.url?.let { request.url = it }
        preResult.requestMutations.method?.let { request.method = it }
        preResult.requestMutations.body?.let { request.body = it }

        if (preResult.requestMutations.headers.isNotEmpty()) {
            request.headers.putAll(preResult.requestMutations.headers)
        }

        if (preResult.requestMutations.queryParams.isNotEmpty()) {
            val urlBuilder = URLBuilder(request.url)
            preResult.requestMutations.queryParams.forEach { (k, v) ->
                urlBuilder.parameters.remove(k)
                urlBuilder.parameters.append(k, v)
            }
            request.url = urlBuilder.buildString()
        }
    }

    private fun queryParamsOf(url: String): Map<String, String> {
        val parsed = Url(url)
        return parsed.parameters.entries().associate { (k, values) ->
            k to (values.firstOrNull() ?: "")
        }
    }
}
