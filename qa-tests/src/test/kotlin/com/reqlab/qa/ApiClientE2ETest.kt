package com.reqlab.qa

import com.reqlab.core.model.AuthConfig
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.RequestBody
import com.reqlab.core.model.RequestDefinition
import com.reqlab.core.network.KtorApiClient
import com.reqlab.core.network.NetworkEvent
import com.reqlab.core.network.RetryPolicy
import com.reqlab.feature.requests.RequestExecutionService
import com.reqlab.testsupport.DummyApiServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiClientE2ETest {
    private lateinit var server: DummyApiServer
    private lateinit var baseUrl: String

    @BeforeTest
    fun setUp() {
        server = DummyApiServer()
        server.start()
        baseUrl = server.baseUrl()
    }

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    @Test
    fun supports_all_http_methods_and_response_parsing() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val service = RequestExecutionService(client)

        val getEvents = service.execute(requestOf(HttpMethodType.GET, "$baseUrl/users")).toList()
        val postEvents = service.execute(
            requestOf(
                method = HttpMethodType.POST,
                url = "$baseUrl/users",
                body = RequestBody(BodyType.JSON, """{"name":"Eva","email":"eva@example.com"}""")
            )
        ).toList()
        val putEvents = service.execute(
            requestOf(
                method = HttpMethodType.PUT,
                url = "$baseUrl/users/1",
                body = RequestBody(BodyType.JSON, """{"name":"Alice Updated","email":"alice+u@example.com"}""")
            )
        ).toList()
        val patchEvents = service.execute(
            requestOf(
                method = HttpMethodType.PATCH,
                url = "$baseUrl/users/2",
                body = RequestBody(BodyType.JSON, """{"name":"Bob Patched"}""")
            )
        ).toList()
        val deleteEvents = service.execute(requestOf(HttpMethodType.DELETE, "$baseUrl/users/3")).toList()
        val optionsEvents = service.execute(requestOf(HttpMethodType.OPTIONS, "$baseUrl/users")).toList()
        val headEvents = service.execute(requestOf(HttpMethodType.HEAD, "$baseUrl/users")).toList()

        assertSuccessStatus(getEvents, 200)
        assertSuccessStatus(postEvents, 201)
        assertSuccessStatus(putEvents, 200)
        assertSuccessStatus(patchEvents, 200)
        assertSuccessStatus(deleteEvents, 204)
        assertSuccessStatus(optionsEvents, 200)
        assertSuccessStatus(headEvents, 200)

        assertTrue((getEvents.last() as NetworkEvent.Success).response.bodyText.contains("Alice"))
    }

    @Test
    fun resolves_variables_in_url_headers_body_and_authorization() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))
        val request = requestOf(
            method = HttpMethodType.GET,
            url = "{{baseUrl}}/auth/protected",
            headers = listOf(KeyValueEntry("X-Trace", "{{traceId}}")),
            auth = AuthConfig(
                type = AuthType.BEARER,
                params = mapOf("token" to "{{authToken}}")
            )
        )

        val events = client.execute(
            request = request,
            variableLayers = listOf(
                mapOf(
                    "baseUrl" to baseUrl,
                    "traceId" to "trace-123",
                    "authToken" to "dummy-token"
                )
            )
        ).toList()

        assertSuccessStatus(events, 200)
        val lastLogged = server.requestsLog.value.last()
        assertEquals("trace-123", lastLogged.headers["X-Trace"])
        assertEquals("Bearer dummy-token", lastLogged.headers["Authorization"])
    }

    @Test
    fun supports_json_form_urlencoded_raw_text_and_graphql_bodies() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))

        val jsonEvents = client.execute(
            requestOf(
                method = HttpMethodType.POST,
                url = "$baseUrl/users",
                body = RequestBody(BodyType.JSON, """{"name":"JsonUser","email":"json@example.com"}""")
            )
        ).toList()

        val formEvents = client.execute(
            requestOf(
                method = HttpMethodType.POST,
                url = "$baseUrl/users",
                body = RequestBody(
                    type = BodyType.X_WWW_FORM_URLENCODED,
                    formEntries = listOf(
                        KeyValueEntry("name", "FormUser"),
                        KeyValueEntry("email", "form@example.com")
                    )
                )
            )
        ).toList()

        val rawEvents = client.execute(
            requestOf(
                method = HttpMethodType.POST,
                url = "$baseUrl/graphql",
                body = RequestBody(BodyType.RAW_TEXT, "{\"query\":\"{ users { id } }\"}")
            )
        ).toList()

        val graphQlEvents = client.execute(
            requestOf(
                method = HttpMethodType.POST,
                url = "$baseUrl/graphql",
                body = RequestBody(
                    type = BodyType.GRAPHQL,
                    graphQl = com.reqlab.core.model.GraphQlBody(query = "{ users { id name } }")
                )
            )
        ).toList()

        assertSuccessStatus(jsonEvents, 201)
        assertSuccessStatus(formEvents, 201)
        assertSuccessStatus(rawEvents, 200)
        assertSuccessStatus(graphQlEvents, 200)
    }

    @Test
    fun supports_xml_html_and_javascript_body_types() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))

        val xmlEvents = client.execute(
            requestOf(
                method = HttpMethodType.POST,
                url = "$baseUrl/echo-xml",
                body = RequestBody(BodyType.XML, "<root><msg>hello</msg></root>")
            )
        ).toList()

        val htmlEvents = client.execute(
            requestOf(
                method = HttpMethodType.POST,
                url = "$baseUrl/echo-html",
                body = RequestBody(BodyType.HTML, "<html><body><h1>Test</h1></body></html>")
            )
        ).toList()

        val jsEvents = client.execute(
            requestOf(
                method = HttpMethodType.POST,
                url = "$baseUrl/echo-js",
                body = RequestBody(BodyType.JAVASCRIPT, "console.log('hello');")
            )
        ).toList()

        assertSuccessStatus(xmlEvents, 200)
        assertSuccessStatus(htmlEvents, 200)
        assertSuccessStatus(jsEvents, 200)

        // Verify the body was echoed back
        val xmlSuccess = xmlEvents.filterIsInstance<NetworkEvent.Success>().first()
        assertTrue(xmlSuccess.response.bodyText.contains("hello"), "XML body should be echoed back")

        val htmlSuccess = htmlEvents.filterIsInstance<NetworkEvent.Success>().first()
        assertTrue(htmlSuccess.response.bodyText.contains("Test"), "HTML body should be echoed back")
    }

    @Test
    fun supports_basic_bearer_and_api_key_authentication() = runTest {
        val client = KtorApiClient(retryPolicy = RetryPolicy(maxAttempts = 1))

        val basic = client.execute(
            requestOf(
                method = HttpMethodType.GET,
                url = "$baseUrl/auth/protected",
                auth = AuthConfig(
                    type = AuthType.BASIC,
                    params = mapOf("username" to "user", "password" to "pass")
                )
            )
        ).toList()

        val bearer = client.execute(
            requestOf(
                method = HttpMethodType.GET,
                url = "$baseUrl/auth/protected",
                auth = AuthConfig(
                    type = AuthType.BEARER,
                    params = mapOf("token" to "dummy-token")
                )
            )
        ).toList()

        val apiKeyHeader = client.execute(
            requestOf(
                method = HttpMethodType.GET,
                url = "$baseUrl/auth/protected",
                auth = AuthConfig(
                    type = AuthType.API_KEY,
                    params = mapOf("key" to "X-API-Key", "value" to "test-api-key", "placement" to "header")
                )
            )
        ).toList()

        val unauthorized = client.execute(
            requestOf(
                method = HttpMethodType.GET,
                url = "$baseUrl/auth/protected",
                auth = AuthConfig(type = AuthType.BEARER, params = mapOf("token" to "bad-token"))
            )
        ).toList()

        assertSuccessStatus(basic, 200)
        assertSuccessStatus(bearer, 200)
        assertSuccessStatus(apiKeyHeader, 200)
        assertSuccessStatus(unauthorized, 401)
    }

    @Test
    fun retries_on_server_error_then_fails_with_exhausted_retries() = runTest {
        val client = KtorApiClient(
            retryPolicy = RetryPolicy(
                maxAttempts = 3,
                baseDelayMs = 1,
                maxDelayMs = 2,
                retryOnStatusCodes = setOf(500)
            )
        )
        val events = client.execute(requestOf(HttpMethodType.GET, "$baseUrl/users?mode=error")).toList()

        assertTrue(events.any { it is NetworkEvent.RetryScheduled })
        val terminal = events.last()
        assertIs<NetworkEvent.Success>(terminal)
        assertEquals(500, terminal.response.statusCode)
    }

    private fun assertSuccessStatus(events: List<NetworkEvent>, expectedStatus: Int) {
        assertTrue(events.isNotEmpty())
        val success = events.last() as NetworkEvent.Success
        assertEquals(expectedStatus, success.response.statusCode)
    }

    private fun requestOf(
        method: HttpMethodType,
        url: String,
        headers: List<KeyValueEntry> = emptyList(),
        body: RequestBody = RequestBody(),
        auth: AuthConfig = AuthConfig()
    ): RequestDefinition = RequestDefinition(
        id = "req-${method.name}-${url.hashCode()}",
        name = "${method.name} $url",
        method = method,
        url = url,
        headers = headers,
        auth = auth,
        body = body,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )
}
