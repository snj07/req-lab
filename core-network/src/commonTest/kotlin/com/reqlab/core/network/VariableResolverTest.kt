package com.reqlab.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VariableResolverTest {

    @Test
    fun resolves_variables_from_first_matching_layer() {
        val value = "{{baseUrl}}/v1/users/{{userId}}"
        val resolved = VariableResolver.resolve(
            value = value,
            variableLayers = listOf(
                mapOf("baseUrl" to "https://prod.api"),
                mapOf("baseUrl" to "https://staging.api", "userId" to "42")
            )
        )

        assertEquals("https://prod.api/v1/users/42", resolved)
    }

    @Test
    fun resolves_dynamic_timestamp_and_iso_timestamp_tokens() {
        val value = "ts={{\$timestamp}};iso={{\$isoTimestamp}}"
        val resolved = VariableResolver.resolve(value = value, variableLayers = emptyList())

        val parts = resolved.split(";")
        val ts = parts[0].substringAfter("ts=")
        val iso = parts[1].substringAfter("iso=")

        assertTrue(ts.toLongOrNull() != null)
        assertTrue(iso.contains("T"))
    }

    @Test
    fun resolves_dynamic_random_int_within_requested_range() {
        val value = "{{ \$randomInt(10, 20) }}"
        val resolved = VariableResolver.resolve(value = value, variableLayers = emptyList())
        val number = resolved.toInt()

        assertTrue(number in 10..20)
    }

    @Test
    fun keeps_unresolved_tokens_when_variable_not_found() {
        val value = "{{missingValue}}"
        val resolved = VariableResolver.resolve(value = value, variableLayers = emptyList())

        assertEquals("{{missingValue}}", resolved)
    }

    // ── Global variable layer tests (F2) ────────────────────────

    @Test
    fun environment_vars_override_global_vars() {
        // Layer 0 = environment, Layer 1 = global
        // First layer wins for same key
        val resolved = VariableResolver.resolve(
            value = "{{apiUrl}}",
            variableLayers = listOf(
                mapOf("apiUrl" to "https://env.api.com"),  // environment
                mapOf("apiUrl" to "https://global.api.com"),  // global
            )
        )
        assertEquals("https://env.api.com", resolved)
    }

    @Test
    fun global_vars_used_when_not_in_environment() {
        val resolved = VariableResolver.resolve(
            value = "{{appName}}/{{apiVersion}}",
            variableLayers = listOf(
                mapOf("otherKey" to "envValue"),  // environment has no appName/apiVersion
                mapOf("appName" to "ReqLab", "apiVersion" to "v1"),  // global
            )
        )
        assertEquals("ReqLab/v1", resolved)
    }

    @Test
    fun multiple_global_vars_in_single_url() {
        val resolved = VariableResolver.resolve(
            value = "{{baseUrl}}/{{apiVersion}}/{{resource}}",
            variableLayers = listOf(
                emptyMap(),  // empty environment
                mapOf("baseUrl" to "https://api.example.com", "apiVersion" to "v2", "resource" to "users"),
            )
        )
        assertEquals("https://api.example.com/v2/users", resolved)
    }
}
