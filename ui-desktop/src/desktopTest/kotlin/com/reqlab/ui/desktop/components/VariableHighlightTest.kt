package com.reqlab.ui.desktop.components

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
    fun `parseVariableNames – token with spaces is trimmed`() {
        val names = parseVariableNames("{{ my var }}")
        assertEquals(listOf("my var"), names)
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
    fun `parseVariableNames – outer braces absorb nested-looking incomplete tokens`() {
        // The regex is greedy: the first {{ matches everything (including inner {{)
        // up to the first }}, so "valid" is not a separate variable name.
        val names = parseVariableNames("{{notClosed and {{valid}}")
        // Only one match: the outer {{ consumes up to the first }}
        assertEquals(1, names.size)
        // The captured group contains "valid" as part of the longer variable name
        assertTrue(names.first().contains("valid"))
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
}
