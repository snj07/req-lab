package com.reqlab.qa

import com.reqlab.server.module
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.net.ServerSocket
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end test suite for the ReqLab sample collection.
 * Every @Test method uses block-body syntax so JUnit4 sees void return type.
 */
class SampleCollectionE2ETest {

    private val baseUrl get() = Companion.BASE_URL
    private val token = "test-token"
    private val apiKey = "test-api-key"
    private val basicUser = "admin"
    private val basicPassword = "password"
    private val basicCreds
        get() = Base64.getEncoder().encodeToString("$basicUser:$basicPassword".toByteArray())

    private fun parseJson(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject

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
            server?.stop(100L, 100L)
        }

        val client = HttpClient(CIO) {
            followRedirects = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    // =========================================================================
    // Health Check
    // =========================================================================

    @Test
    fun health_check() {
        runBlocking {
            val r = client.get(baseUrl)
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue("running" in r.bodyAsText())
        }
    }

    @Test
    fun current_server_time() {
        runBlocking {
            val r = client.get("$baseUrl/api/time")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue("epochMillis" in body)
            assertTrue("iso8601" in body)
        }
    }

    // =========================================================================
    // HTTP Methods
    // =========================================================================

    @Test
    fun http_get_all_users() {
        runBlocking {
            val r = client.get("$baseUrl/api/users")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertEquals("GET", body["method"]?.jsonPrimitive?.content)
            assertNotNull(body["users"])
        }
    }

    @Test
    fun http_post_create_user() {
        runBlocking {
            val r = client.post("$baseUrl/api/users") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Dave","email":"dave@example.com"}""")
            }
            assertEquals(HttpStatusCode.Created, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("created", ignoreCase = true) == true)
        }
    }

    @Test
    fun http_put_replace_user() {
        runBlocking {
            val r = client.put("$baseUrl/api/users/1") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Alice Replaced","email":"alice@example.com"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("replaced", ignoreCase = true) == true)
        }
    }

    @Test
    fun http_patch_update_user() {
        runBlocking {
            val r = client.patch("$baseUrl/api/users/2") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"bob-updated@example.com"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("updated", ignoreCase = true) == true)
        }
    }

    @Test
    fun http_delete_user() {
        runBlocking {
            val r = client.delete("$baseUrl/api/users/3")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("deleted", ignoreCase = true) == true)
        }
    }

    @Test
    fun http_options_users() {
        runBlocking {
            val r = client.options("$baseUrl/api/users")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertEquals("OPTIONS", body["method"]?.jsonPrimitive?.content)
            assertNotNull(body["allow"])
        }
    }

    @Test
    fun http_head_users() {
        runBlocking {
            val r = client.head("$baseUrl/api/users")
            assertEquals(HttpStatusCode.OK, r.status)
            assertEquals("3", r.headers["X-Total-Count"])
        }
    }

    // =========================================================================
    // Query Params
    // =========================================================================

    @Test
    fun query_params_basic_search() {
        runBlocking {
            val r = client.get("$baseUrl/api/search?q=test&page=1&limit=10")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertEquals("test", body["query"]?.jsonPrimitive?.content)
            assertEquals(1, body["page"]?.jsonPrimitive?.content?.toIntOrNull())
            assertEquals(10, body["limit"]?.jsonPrimitive?.content?.toIntOrNull())
            assertNotNull(body["results"])
        }
    }

    @Test
    fun query_params_page2() {
        runBlocking {
            val r = client.get("$baseUrl/api/search?q=hello&page=2&limit=25")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertEquals("hello", body["query"]?.jsonPrimitive?.content)
            assertEquals(2, body["page"]?.jsonPrimitive?.content?.toIntOrNull())
        }
    }

    @Test
    fun query_params_defaults() {
        runBlocking {
            val r = client.get("$baseUrl/api/search")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertEquals("", body["query"]?.jsonPrimitive?.content)
            assertEquals(1, body["page"]?.jsonPrimitive?.content?.toIntOrNull())
            assertEquals(10, body["limit"]?.jsonPrimitive?.content?.toIntOrNull())
        }
    }

    @Test
    fun query_params_empty_q() {
        runBlocking {
            val r = client.get("$baseUrl/api/search?q=&page=1&limit=5")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertEquals("", body["query"]?.jsonPrimitive?.content)
            assertEquals(5, body["limit"]?.jsonPrimitive?.content?.toIntOrNull())
        }
    }

    @Test
    fun query_params_multiple() {
        runBlocking {
            val r = client.get("$baseUrl/api/search?q=kotlin&page=1&limit=3&sort=name&order=asc")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertEquals("kotlin", body["query"]?.jsonPrimitive?.content)
            val allParams = body["allParams"]?.jsonObject
            assertNotNull(allParams)
            assertEquals("name", allParams["sort"]?.jsonPrimitive?.content)
            assertEquals("asc", allParams["order"]?.jsonPrimitive?.content)
        }
    }

    // =========================================================================
    // Headers
    // =========================================================================

    @Test
    fun headers_echo_basic() {
        runBlocking {
            val r = client.get("$baseUrl/api/echo-headers")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertNotNull(body["receivedHeaders"])
            assertEquals("ReqLab/1.0", r.headers["X-Echo-Server"])
        }
    }

    @Test
    fun headers_echo_custom_client_id() {
        runBlocking {
            val r = client.get("$baseUrl/api/echo-headers") {
                header("X-Client-Id", "reqlab-test")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val received = parseJson(r.bodyAsText())["receivedHeaders"]?.jsonObject
            assertNotNull(received)
            assertEquals("reqlab-test", received["X-Client-Id"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun headers_echo_accept_and_request_id() {
        runBlocking {
            val r = client.get("$baseUrl/api/echo-headers") {
                header("Accept", "application/json")
                header("X-Request-Id", "req-abc-123")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val received = parseJson(r.bodyAsText())["receivedHeaders"]?.jsonObject
            assertNotNull(received)
            assertEquals("req-abc-123", received["X-Request-Id"]?.jsonPrimitive?.content)
        }
    }

    // =========================================================================
    // Body Types
    // =========================================================================

    @Test
    fun body_json() {
        runBlocking {
            val r = client.post("$baseUrl/api/json") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Alice","age":30}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("JSON") == true)
            assertTrue(body["body"]?.jsonPrimitive?.content?.contains("Alice") == true)
        }
    }

    @Test
    fun body_json_nested() {
        runBlocking {
            val payload = """{"user":{"name":"Bob","roles":["admin","user"]},"meta":{"version":2}}"""
            val r = client.post("$baseUrl/api/json") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(parseJson(r.bodyAsText())["body"]?.jsonPrimitive?.content?.contains("Bob") == true)
        }
    }

    @Test
    fun body_raw_text() {
        runBlocking {
            val r = client.post("$baseUrl/api/raw") {
                contentType(ContentType.Text.Plain)
                setBody("Hello from ReqLab raw body test")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("Raw text") == true)
            assertTrue(body["body"]?.jsonPrimitive?.content?.contains("Hello from ReqLab") == true)
        }
    }

    @Test
    fun body_form_data_multipart() {
        runBlocking {
            val r = client.post("$baseUrl/api/form-data") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("name", "tester")
                            append("age", "42")
                            append("role", "qa")
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("form-data") == true)
            val fields = body["fields"]?.jsonObject
            assertNotNull(fields)
            assertEquals("tester", fields["name"]?.jsonPrimitive?.content)
            assertEquals("42", fields["age"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun body_urlencoded() {
        runBlocking {
            val r = client.post("$baseUrl/api/urlencoded") {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("username", "alice")
                            append("email", "alice@example.com")
                            append("role", "admin")
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("URL-encoded") == true)
            val fields = body["fields"]?.jsonObject
            assertNotNull(fields)
            assertEquals("alice", fields["username"]?.jsonPrimitive?.content)
            assertEquals("alice@example.com", fields["email"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun body_file_upload() {
        runBlocking {
            val content = "reqlab test file content".toByteArray()
            val r = client.post("$baseUrl/api/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file", content,
                                Headers.build {
                                    append(
                                        "Content-Disposition",
                                        "form-data; name=\"file\"; filename=\"test.txt\""
                                    )
                                    append("Content-Type", "text/plain")
                                }
                            )
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("uploaded") == true)
            assertNotNull(body["files"])
        }
    }

    // =========================================================================
    // Authentication
    // =========================================================================

    @Test
    fun auth_basic_valid() {
        runBlocking {
            val r = client.get("$baseUrl/api/auth/basic") {
                header("Authorization", "Basic $basicCreds")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("Basic auth OK") == true)
            assertEquals("admin", body["user"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun auth_basic_wrong_password() {
        runBlocking {
            val wrongCreds = Base64.getEncoder().encodeToString("admin:wrongpass".toByteArray())
            val r = client.get("$baseUrl/api/auth/basic") {
                header("Authorization", "Basic $wrongCreds")
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun auth_basic_no_header() {
        runBlocking {
            val r = client.get("$baseUrl/api/auth/basic")
            assertEquals(HttpStatusCode.Unauthorized, r.status)
            assertTrue(r.headers["WWW-Authenticate"]?.contains("Basic") == true)
        }
    }

    @Test
    fun auth_bearer_valid() {
        runBlocking {
            val r = client.get("$baseUrl/api/auth/bearer") {
                header("Authorization", "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("Bearer auth OK") == true)
            assertEquals(token, body["token"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun auth_bearer_wrong_token() {
        runBlocking {
            val r = client.get("$baseUrl/api/auth/bearer") {
                header("Authorization", "Bearer i-am-wrong")
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun auth_bearer_no_header() {
        runBlocking {
            val r = client.get("$baseUrl/api/auth/bearer")
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun auth_apikey_valid() {
        runBlocking {
            val r = client.get("$baseUrl/api/auth/apikey") {
                header("X-API-Key", apiKey)
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(
                parseJson(r.bodyAsText())["message"]?.jsonPrimitive?.content
                    ?.contains("API key auth OK") == true
            )
        }
    }

    @Test
    fun auth_apikey_wrong_key() {
        runBlocking {
            val r = client.get("$baseUrl/api/auth/apikey") {
                header("X-API-Key", "bad-key")
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    @Test
    fun auth_apikey_missing() {
        runBlocking {
            val r = client.get("$baseUrl/api/auth/apikey")
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
    }

    // =========================================================================
    // Cookies
    // =========================================================================

    @Test
    fun cookies_set_in_response() {
        runBlocking {
            val r = client.get("$baseUrl/api/cookies")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("session") == true)
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("user") == true)
            val setCookies = r.headers.getAll("Set-Cookie") ?: emptyList()
            assertTrue(setCookies.any { it.startsWith("session=") })
            assertTrue(setCookies.any { it.startsWith("user=") })
        }
    }

    @Test
    fun cookies_echoed_when_resent() {
        runBlocking {
            val r = client.get("$baseUrl/api/cookies") {
                header("Cookie", "session=abc123; user=reqlab-tester")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val received = parseJson(r.bodyAsText())["receivedCookies"]?.jsonObject
            assertNotNull(received)
            assertEquals("abc123", received["session"]?.jsonPrimitive?.content)
            assertEquals("reqlab-tester", received["user"]?.jsonPrimitive?.content)
        }
    }

    // =========================================================================
    // Redirects
    // =========================================================================

    @Test
    fun redirect_returns_302() {
        runBlocking {
            val r = client.get("$baseUrl/api/redirect")   // followRedirects = false
            assertEquals(HttpStatusCode.Found, r.status)
            assertTrue(r.headers["Location"]?.endsWith("/api/final") == true)
        }
    }

    @Test
    fun redirect_final_direct() {
        runBlocking {
            val r = client.get("$baseUrl/api/final")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("redirect destination") == true)
            assertEquals("/api/final", body["endpoint"]?.jsonPrimitive?.content)
        }
    }

    // =========================================================================
    // Error Responses
    // =========================================================================

    private fun assertErrorCode(code: Int) {
        runBlocking {
            val r = client.get("$baseUrl/api/error/$code")
            assertEquals(code, r.status.value)
            val body = parseJson(r.bodyAsText())
            assertEquals(code, body["code"]?.jsonPrimitive?.content?.toIntOrNull())
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("$code") == true)
        }
    }

    @Test fun error_400() { assertErrorCode(400) }
    @Test fun error_401() { assertErrorCode(401) }
    @Test fun error_403() { assertErrorCode(403) }
    @Test fun error_404() { assertErrorCode(404) }
    @Test fun error_405() { assertErrorCode(405) }
    @Test fun error_422() { assertErrorCode(422) }
    @Test fun error_429() { assertErrorCode(429) }
    @Test fun error_500() { assertErrorCode(500) }
    @Test fun error_502() { assertErrorCode(502) }
    @Test fun error_503() { assertErrorCode(503) }

    // =========================================================================
    // Slow Requests
    // =========================================================================

    @Test
    fun slow_request_100ms() {
        runBlocking {
            val start = System.currentTimeMillis()
            val r = client.get("$baseUrl/api/slow?ms=100")
            val elapsed = System.currentTimeMillis() - start
            assertEquals(HttpStatusCode.OK, r.status)
            assertEquals(100L, parseJson(r.bodyAsText())["delayMs"]?.jsonPrimitive?.content?.toLongOrNull())
            assertTrue(elapsed >= 100)
        }
    }

    @Test
    fun slow_request_1s() {
        runBlocking {
            val start = System.currentTimeMillis()
            val r = client.get("$baseUrl/api/slow?ms=1000")
            val elapsed = System.currentTimeMillis() - start
            assertEquals(HttpStatusCode.OK, r.status)
            assertTrue(elapsed >= 900)
        }
    }

    // =========================================================================
    // Scripts & Tests
    // =========================================================================

    @Test
    fun scripts_timestamp_structure() {
        runBlocking {
            val r = client.get("$baseUrl/api/timestamp")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            val unix = body["unix"]?.jsonPrimitive?.content?.toLongOrNull()
            assertNotNull(unix)
            assertTrue(unix > 0)
            assertNotNull(body["iso"])
            assertEquals("UTC", body["tz"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun scripts_token_response() {
        runBlocking {
            val r = client.post("$baseUrl/api/token") {
                contentType(ContentType.Application.Json)
                setBody("""{"user":"alice","role":"admin"}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            val tok = body["token"]?.jsonPrimitive?.content
            assertNotNull(tok)
            assertTrue(tok.startsWith("rl."))
            assertEquals("alice", body["user"]?.jsonPrimitive?.content)
            assertEquals("admin", body["role"]?.jsonPrimitive?.content)
            assertNotNull(body["expiresIn"])
        }
    }

    @Test
    fun scripts_protected_with_valid_token() {
        runBlocking {
            val tokenResp = client.post("$baseUrl/api/token") {
                contentType(ContentType.Application.Json)
                setBody("""{"user":"alice","role":"admin"}""")
            }
            assertEquals(HttpStatusCode.OK, tokenResp.status)
            val scriptToken = parseJson(tokenResp.bodyAsText())["token"]?.jsonPrimitive?.content
            assertNotNull(scriptToken)

            val r = client.get("$baseUrl/api/protected") {
                header("X-Token", scriptToken)
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertEquals(
                "Access granted",
                parseJson(r.bodyAsText())["message"]?.jsonPrimitive?.content
            )
        }
    }

    @Test
    fun scripts_protected_no_token_returns_401() {
        runBlocking {
            val r = client.get("$baseUrl/api/protected")
            assertEquals(HttpStatusCode.Unauthorized, r.status)
            assertNotNull(parseJson(r.bodyAsText())["error"])
        }
    }

    @Test
    fun scripts_validate_json_body_valid() {
        runBlocking {
            val r = client.post("$baseUrl/api/validate") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"ReqLab","version":"1.0","active":true}""")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertEquals("true", body["valid"]?.jsonPrimitive?.content)
            val fieldCount = body["fieldCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            assertTrue(fieldCount > 0)
        }
    }

    @Test
    fun scripts_validate_empty_body() {
        runBlocking {
            val r = client.post("$baseUrl/api/validate") {
                contentType(ContentType.Application.Json)
                setBody("")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            assertEquals("false", parseJson(r.bodyAsText())["valid"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun scripts_echo_full_structure() {
        runBlocking {
            val r = client.get("$baseUrl/api/echo-full")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertEquals("GET", body["method"]?.jsonPrimitive?.content)
            assertNotNull(body["headers"])
            assertNotNull(body["params"])
            assertNotNull(body["path"])
        }
    }

    @Test
    fun scripts_echo_full_injected_headers() {
        runBlocking {
            val r = client.get("$baseUrl/api/echo-full") {
                header("X-Request-Id", "req-scripted")
                header("X-Client", "ReqLab-ScriptEngine")
            }
            assertEquals(HttpStatusCode.OK, r.status)
            val headers = parseJson(r.bodyAsText())["headers"]?.jsonObject
            assertNotNull(headers)
            assertEquals("req-scripted", headers["X-Request-Id"]?.jsonPrimitive?.content)
            assertEquals("ReqLab-ScriptEngine", headers["X-Client"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun scripts_user_list_structure() {
        runBlocking {
            val r = client.get("$baseUrl/api/users")
            assertEquals(HttpStatusCode.OK, r.status)
            val body = parseJson(r.bodyAsText())
            assertNotNull(body["users"])
            assertEquals("GET", body["method"]?.jsonPrimitive?.content)
        }
    }
}
