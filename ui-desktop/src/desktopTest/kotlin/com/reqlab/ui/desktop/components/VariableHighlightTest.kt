package com.reqlab.ui.shared.components

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the `{{variable}}` parsing and highlighting utilities in
 * VariableHighlight.kt.
 *
 * These are pure-function tests — no Compose runtime or compose rule needed.
 */
class VariableHighlightTest {

    // ── parseVariableNames ────────────────────────────────────────

    @Test
    fun `parseVariableNames – single token`() {
        val names = parseVariableNames("{{baseUrl}}/users")
        assertEquals(listOf("baseUrl"), names)
    }

    @Test
    fun `parseVariableNames – multiple tokens`() {
        val names = parseVariableNames("{{protocol}}://{{host}}:{{port}}/path")
        assertEquals(listOf("protocol", "host", "port"), names)
    }

    @Test
    fun `parseVariableNames – no tokens returns empty`() {
        val names = parseVariableNames("https://example.com/api/v1")
        assertEquals(emptyList(), names)
    }

    @Test
    fun `parseVariableNames – empty string returns empty`() {
        val names = parseVariableNames("")
        assertEquals(emptyList(), names)
    }

    @Test
    fun `parseVariableNames – surrounding spaces are trimmed`() {
        val names = parseVariableNames("{{ my var }}")
        assertEquals(listOf("my var"), names)
    }

    @Test
    fun `parseVariableNames – underscore is allowed`() {
        val names = parseVariableNames("{{my_var}}/path")
        assertEquals(listOf("my_var"), names)
    }

    @Test
    fun `parseVariableNames – digits are allowed`() {
        val names = parseVariableNames("{{var1}}/{{v2}}")
        assertEquals(listOf("var1", "v2"), names)
    }

    @Test
    fun `parseVariableNames – hyphens are matched`() {
        val names = parseVariableNames("{{my-var}}")
        assertEquals(listOf("my-var"), names)
    }

    @Test
    fun `parseVariableNames – dots are matched`() {
        val names = parseVariableNames("{{api.version}}")
        assertEquals(listOf("api.version"), names)
    }

    @Test
    fun `parseVariableNames – adjacent tokens`() {
        val names = parseVariableNames("{{a}}{{b}}")
        assertEquals(listOf("a", "b"), names)
    }

    @Test
    fun `parseVariableNames – token at string end`() {
        val names = parseVariableNames("prefix-{{suffix}}")
        assertEquals(listOf("suffix"), names)
    }

    @Test
    fun `parseVariableNames – strict regex finds valid token despite surrounding garbage`() {
        // The strict [a-zA-Z0-9_]+ regex skips over the invalid opening token
        // and finds the properly-formed {{valid}} later in the string.
        val names = parseVariableNames("{{notClosed and {{valid}}")
        assertEquals(listOf("valid"), names)
    }

    // ── highlightVariables ────────────────────────────────────────

    @Test
    fun `highlightVariables – plain text is preserved`() {
        val annotated = highlightVariables("https://example.com")
        assertEquals("https://example.com", annotated.text)
    }

    @Test
    fun `highlightVariables – variable token text is preserved`() {
        val annotated = highlightVariables("{{host}}/api")
        assertEquals("{{host}}/api", annotated.text)
    }

    @Test
    fun `highlightVariables – variable annotation is attached to token span`() {
        val annotated = highlightVariables("https://{{host}}/path")
        val ranges = annotated.getStringAnnotations("variable", 8, 16)
        assertEquals(1, ranges.size)
        assertEquals("host", ranges.first().item)
    }

    @Test
    fun `highlightVariables – multiple variables each have annotations`() {
        val text = "{{scheme}}://{{host}}"
        val annotated = highlightVariables(text)
        assertEquals(text, annotated.text)

        val schemeRange = annotated.getStringAnnotations("variable", 0, 10)
        val hostRange   = annotated.getStringAnnotations("variable", 13, 21)

        assertEquals(1, schemeRange.size)
        assertEquals("scheme", schemeRange.first().item)

        assertEquals(1, hostRange.size)
        assertEquals("host", hostRange.first().item)
    }

    @Test
    fun `highlightVariables – no tokens means no annotations`() {
        val annotated = highlightVariables("https://example.com/plain")
        val ranges = annotated.getStringAnnotations("variable", 0, annotated.text.length)
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `highlightVariables – empty string produces empty AnnotatedString`() {
        val annotated = highlightVariables("")
        assertEquals("", annotated.text)
    }

    @Test
    fun `variableNameAtOffset – finds token inside JSON body`() {
        val text = "{\"host\":\"{{base-url}}\",\"v\":\"{{api.version}}\"}"
        val baseOffset = text.indexOf("base-url") + 2
        val versionOffset = text.indexOf("api.version") + 1

        assertEquals("base-url", variableNameAtOffset(text, baseOffset))
        assertEquals("api.version", variableNameAtOffset(text, versionOffset))
    }

    @Test
    fun `variableNameAtOffset – returns null outside token`() {
        val text = "{\"name\":\"plain\"}"
        assertEquals(null, variableNameAtOffset(text, 3))
    }
}
