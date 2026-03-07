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
}
