package com.reqlab.ui.shared.components

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestExecutorLogResolutionTest {

    @Test
    fun resolveUrlForLog_resolves_from_variable_layers() {
        val url = "{{baseUrl}}/api/token"
        val layers = listOf(
            mapOf("baseUrl" to "http://localhost:8080"),
        )

        val resolved = resolveUrlForLog(url, layers)

        assertEquals("http://localhost:8080/api/token", resolved)
    }

    @Test
    fun resolveUrlForLog_prefers_request_scoped_variables() {
        val url = "{{baseUrl}}/api/token"
        val layers = listOf(
            mapOf("baseUrl" to "http://env.example.com"),
        )
        val requestScoped = mapOf("baseUrl" to "http://script.example.com")

        val resolved = resolveUrlForLog(
            url = url,
            variableLayers = layers,
            requestScopedVars = requestScoped,
        )

        assertEquals("http://script.example.com/api/token", resolved)
    }

    @Test
    fun resolveUrlForLog_keeps_unresolved_placeholder_when_missing() {
        val url = "{{baseUrl}}/api/token"

        val resolved = resolveUrlForLog(url, emptyList())

        assertEquals("{{baseUrl}}/api/token", resolved)
    }
}
