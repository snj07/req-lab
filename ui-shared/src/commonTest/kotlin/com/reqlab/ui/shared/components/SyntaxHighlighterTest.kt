package com.reqlab.ui.shared.components

import com.reqlab.ui.shared.components.SyntaxLanguage
import com.reqlab.ui.shared.components.detectLanguage
import com.reqlab.ui.shared.components.findSearchMatches
import com.reqlab.ui.shared.components.formatXml
import com.reqlab.ui.shared.components.fileExtensionForContentType
import com.reqlab.ui.shared.components.highlightJson
import com.reqlab.ui.shared.components.highlightXml
import com.reqlab.ui.shared.components.highlightGraphql
import com.reqlab.ui.shared.components.highlightLine
import com.reqlab.ui.shared.components.highlightText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the SyntaxHighlighter module:
 * - Language detection from content-type
 * - JSON / XML / GraphQL tokenisation produces correct text
 * - Search match finder
 * - XML formatter
 * - Content-type → file-extension mapping
 */
class SyntaxHighlighterTest {

    // ── detectLanguage ──────────────────────────────────────────

    @Test
    fun detectLanguage_json_content_type() {
        assertEquals(SyntaxLanguage.JSON, detectLanguage("application/json"))
        assertEquals(SyntaxLanguage.JSON, detectLanguage("application/json; charset=utf-8"))
    }

    @Test
    fun detectLanguage_xml_content_type() {
        assertEquals(SyntaxLanguage.XML, detectLanguage("application/xml"))
        assertEquals(SyntaxLanguage.XML, detectLanguage("text/xml"))
    }

    @Test
    fun detectLanguage_html_content_type() {
        assertEquals(SyntaxLanguage.HTML, detectLanguage("text/html"))
        assertEquals(SyntaxLanguage.HTML, detectLanguage("text/html; charset=utf-8"))
    }

    @Test
    fun detectLanguage_graphql_content_type() {
        assertEquals(SyntaxLanguage.GRAPHQL, detectLanguage("application/graphql"))
    }

    @Test
    fun detectLanguage_unknown_returns_plain() {
        assertEquals(SyntaxLanguage.PLAIN, detectLanguage("text/plain"))
        assertEquals(SyntaxLanguage.PLAIN, detectLanguage(null))
    }

    // ── highlightJson ───────────────────────────────────────────

    @Test
    fun highlightJson_preserves_text() {
        val input = """{"name":"ReqLab","version":1,"active":true,"data":null}"""
        val result = highlightJson(input)
        assertEquals(input, result.text)
    }

    @Test
    fun highlightJson_multiline_preserves_text() {
        val input = """
{
  "items": [
    { "id": 1, "label": "first" },
    { "id": 2, "label": "second" }
  ]
}
        """.trimIndent()
        val result = highlightJson(input)
        assertEquals(input, result.text)
    }

    @Test
    fun highlightJson_empty_string() {
        val result = highlightJson("")
        assertEquals("", result.text)
    }

    @Test
    fun highlightJson_has_span_styles() {
        val input = """{"key":"value"}"""
        val result = highlightJson(input)
        // Should have at least some span styles for coloring
        assertTrue(result.spanStyles.isNotEmpty(), "Expected spans for coloured tokens")
    }

    // ── highlightXml ────────────────────────────────────────────

    @Test
    fun highlightXml_preserves_text() {
        val input = """<root><item id="1">Hello</item></root>"""
        val result = highlightXml(input)
        assertEquals(input, result.text)
    }

    @Test
    fun highlightXml_with_comment() {
        val input = """<!-- comment --><tag/>"""
        val result = highlightXml(input)
        assertEquals(input, result.text)
    }

    @Test
    fun highlightXml_has_span_styles() {
        val input = """<root attr="val">text</root>"""
        val result = highlightXml(input)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    // ── highlightGraphql ────────────────────────────────────────

    @Test
    fun highlightGraphql_preserves_text() {
        val input = """
query GetUsers {
  users(limit: 10) {
    id
    name
  }
}
        """.trimIndent()
        val result = highlightGraphql(input)
        assertEquals(input, result.text)
    }

    @Test
    fun highlightGraphql_has_spans_for_keywords() {
        val input = "query { users { id } }"
        val result = highlightGraphql(input)
        assertTrue(result.spanStyles.isNotEmpty(), "Expected keyword spans")
    }

    @Test
    fun highlightGraphql_with_variables_and_types() {
        val input = """
mutation CreateUser(${'$'}input: UserInput!) {
  createUser(input: ${'$'}input) {
    id
    name
  }
}
        """.trimIndent()
        val result = highlightGraphql(input)
        assertEquals(input, result.text)
    }

    // ── highlightLine / highlightText ───────────────────────────

    @Test
    fun highlightLine_plain_returns_same_text() {
        val result = highlightLine("Hello, world!", SyntaxLanguage.PLAIN)
        assertEquals("Hello, world!", result.text)
    }

    @Test
    fun highlightText_json_delegates_correctly() {
        val input = """{"a":"b"}"""
        val viaText = highlightText(input, SyntaxLanguage.JSON)
        val viaJson = highlightJson(input)
        assertEquals(viaJson.text, viaText.text)
    }

    // ── findSearchMatches ───────────────────────────────────────

    @Test
    fun findSearchMatches_finds_all_occurrences() {
        val lines = listOf("Hello World", "hello there", "HELLO HELLO")
        val matches = findSearchMatches(lines, "hello")
        // "Hello" in line 0, "hello" in line 1, "HELLO"x2 in line 2 → 4 matches
        assertEquals(4, matches.size)
    }

    @Test
    fun findSearchMatches_case_sensitive() {
        val lines = listOf("Hello World", "hello there")
        val matches = findSearchMatches(lines, "Hello", ignoreCase = false)
        assertEquals(1, matches.size)
        assertEquals(0, matches[0].lineIndex)
    }

    @Test
    fun findSearchMatches_empty_query_returns_empty() {
        val lines = listOf("some text")
        val matches = findSearchMatches(lines, "")
        assertEquals(0, matches.size)
    }

    @Test
    fun findSearchMatches_no_match() {
        val lines = listOf("foo", "bar")
        val matches = findSearchMatches(lines, "baz")
        assertEquals(0, matches.size)
    }

    // ── formatXml ───────────────────────────────────────────────

    @Test
    fun formatXml_indents_nested_tags() {
        val input = "<root><child><item/></child></root>"
        val formatted = formatXml(input)
        val lines = formatted.lines()
        assertTrue(lines.size > 1, "Expected multi-line output, got: $formatted")
        assertTrue(lines.any { it.startsWith("  ") }, "Expected indented lines")
    }

    @Test
    fun formatXml_preserves_tag_content() {
        val input = "<root><child>text</child></root>"
        val formatted = formatXml(input)
        assertTrue(formatted.contains("<root>"))
        assertTrue(formatted.contains("<child>text</child>"))
        assertTrue(formatted.contains("</root>"))
    }

    @Test
    fun formatXml_handles_empty_string() {
        val result = formatXml("")
        assertEquals("", result.trim())
    }

    @Test
    fun formatXml_self_closing_tag() {
        val input = "<root><item/></root>"
        val formatted = formatXml(input)
        assertTrue(formatted.contains("<item/>"))
    }

    // ── fileExtensionForContentType ─────────────────────────────

    @Test
    fun fileExtension_json() {
        assertEquals("json", fileExtensionForContentType("application/json"))
        assertEquals("json", fileExtensionForContentType("application/json; charset=utf-8"))
    }

    @Test
    fun fileExtension_xml() {
        assertEquals("xml", fileExtensionForContentType("application/xml"))
        assertEquals("xml", fileExtensionForContentType("text/xml"))
    }

    @Test
    fun fileExtension_html() {
        assertEquals("html", fileExtensionForContentType("text/html"))
    }

    @Test
    fun fileExtension_null() {
        assertEquals("txt", fileExtensionForContentType(null))
    }
}
