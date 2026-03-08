package com.reqlab.ui.web.components

import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.network.VariableResolver
import com.reqlab.ui.web.state.MutableKeyValue
import com.reqlab.ui.web.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestExecutorTest {

    @Test
    fun toRequestDefinition_maps_method_url_headers_body_and_auth() {
        val tab = RequestTabState(
            id = "req-1",
            name = "Create user",
            method = HttpMethodType.POST,
            url = "{{baseUrl}}/users",
        )
        tab.params.add(MutableKeyValue("page", "1", enabled = true))
        tab.headers.add(MutableKeyValue("X-Trace-Id", "abc-123", enabled = true))
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "{\"name\":\"Sam\"}"
        tab.authType = AuthType.BEARER
        tab.authToken = "{{token}}"

        val request = tab.toRequestDefinition(now = 42L)

        assertEquals("req-1", request.id)
        assertEquals(HttpMethodType.POST, request.method)
        assertEquals("{{baseUrl}}/users", request.url)
        assertTrue(request.queryParams.any { it.key == "page" && it.value == "1" })
        assertTrue(request.headers.any { it.key == "X-Trace-Id" && it.value == "abc-123" })
        assertEquals(BodyType.JSON, request.body.type)
        assertEquals("{\"name\":\"Sam\"}", request.body.content)
        assertEquals(AuthType.BEARER, request.auth.type)
        assertEquals("{{token}}", request.auth.params["token"])
        assertEquals(42L, request.createdAtEpochMillis)
        assertEquals(42L, request.updatedAtEpochMillis)
    }

    @Test
    fun variable_resolution_for_web_request_uses_shared_core_logic() {
        val rawUrl = "{{baseUrl}}/auth?token={{token}}"
        val rawHeader = "Bearer {{token}}"
        val layers = listOf(mapOf("baseUrl" to "https://api.example.com", "token" to "abc123"))

        val resolvedUrl = VariableResolver.resolve(rawUrl, layers)
        val resolvedHeader = VariableResolver.resolve(rawHeader, layers)

        assertEquals("https://api.example.com/auth?token=abc123", resolvedUrl)
        assertEquals("Bearer abc123", resolvedHeader)
    }
}
