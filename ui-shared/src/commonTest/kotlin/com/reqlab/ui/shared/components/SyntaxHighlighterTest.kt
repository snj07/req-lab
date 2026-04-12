package com.reqlab.ui.shared.components

import com.reqlab.ui.shared.components.SyntaxLanguage
import com.reqlab.ui.shared.components.autoFormat
import com.reqlab.ui.shared.components.detectLanguage
import com.reqlab.ui.shared.components.findSearchMatches
import com.reqlab.ui.shared.components.formatXml
import com.reqlab.ui.shared.components.fileExtensionForContentType
import com.reqlab.ui.shared.components.highlightJavaScript
import com.reqlab.ui.shared.components.highlightJson
import com.reqlab.ui.shared.components.highlightXml
import com.reqlab.ui.shared.components.highlightGraphql
import com.reqlab.ui.shared.components.highlightLine
import com.reqlab.ui.shared.components.highlightText
import com.reqlab.ui.shared.components.tryPrettyPrint
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for the SyntaxHighlighter module:
 * - Language detection from content-type
 * - JSON / XML / GraphQL tokenisation produces correct text
 * - Search match finder
 * - XML formatter
 * - Content-type → file-extension mapping
 */
@OptIn(ExperimentalSerializationApi::class)
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

    // ── detectLanguage – JavaScript ─────────────────────────────

    @Test
    fun detectLanguage_javascript_content_type() {
        assertEquals(SyntaxLanguage.JAVASCRIPT, detectLanguage("application/javascript"))
        assertEquals(SyntaxLanguage.JAVASCRIPT, detectLanguage("text/javascript"))
        assertEquals(SyntaxLanguage.JAVASCRIPT, detectLanguage("application/javascript; charset=utf-8"))
    }

    @Test
    fun detectLanguage_ecmascript_content_type() {
        assertEquals(SyntaxLanguage.JAVASCRIPT, detectLanguage("application/ecmascript"))
    }

    // ── highlightJavaScript ─────────────────────────────────────

    @Test
    fun highlightJavaScript_preserves_text() {
        val input = "const x = 42;"
        val result = highlightJavaScript(input)
        assertEquals(input, result.text)
    }

    @Test
    fun highlightJavaScript_keywords_get_spans() {
        val input = "function test() { return true; }"
        val result = highlightJavaScript(input)
        assertTrue(result.spanStyles.isNotEmpty(), "Expected spans for JS keywords")
    }

    @Test
    fun highlightJavaScript_multiline() {
        val input = """
async function fetchData() {
    const response = await fetch('/api');
    return response.json();
}
        """.trimIndent()
        val result = highlightJavaScript(input)
        assertEquals(input, result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun highlightJavaScript_comments() {
        val input = """
// single line
/* multi
   line */
const a = 1;
        """.trimIndent()
        val result = highlightJavaScript(input)
        assertEquals(input, result.text)
    }

    @Test
    fun highlightJavaScript_strings() {
        val input = """const s = "hello"; const c = 'world'; const t = `tmpl`;"""
        val result = highlightJavaScript(input)
        assertEquals(input, result.text)
    }

    @Test
    fun highlightJavaScript_builtins() {
        val input = "console.log(JSON.stringify(Math.PI));"
        val result = highlightJavaScript(input)
        assertEquals(input, result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun highlightJavaScript_empty() {
        val result = highlightJavaScript("")
        assertEquals("", result.text)
    }

    @Test
    fun highlightJavaScript_numbers() {
        val input = "let x = 42; let y = 3.14; let z = -1;"
        val result = highlightJavaScript(input)
        assertEquals(input, result.text)
    }

    // ── highlightLine / highlightText – JavaScript ──────────────

    @Test
    fun highlightLine_javascript_delegates_correctly() {
        val input = "const x = 1;"
        val result = highlightLine(input, SyntaxLanguage.JAVASCRIPT)
        assertEquals(input, result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun highlightText_javascript_delegates_correctly() {
        val input = "function foo() {}"
        val viaText = highlightText(input, SyntaxLanguage.JAVASCRIPT)
        val viaDirect = highlightJavaScript(input)
        assertEquals(viaDirect.text, viaText.text)
    }

    // ── autoFormat ──────────────────────────────────────────────

    @Test
    fun autoFormat_json_pretty_prints() {
        val input = """{"a":1,"b":"hello"}"""
        val result = autoFormat(input, SyntaxLanguage.JSON)
        // Should be multi-line after formatting
        assertTrue(result.lines().size > 1, "Expected pretty-printed JSON")
        assertTrue(result.contains("\"a\""))
        assertTrue(result.contains("\"hello\""))
    }

    @Test
    fun autoFormat_json_invalid_returns_raw() {
        val input = "not valid json {"
        val result = autoFormat(input, SyntaxLanguage.JSON)
        assertEquals(input, result, "Invalid JSON should return raw text")
    }

    @Test
    fun autoFormat_xml_indents() {
        val input = "<root><child>text</child></root>"
        val result = autoFormat(input, SyntaxLanguage.XML)
        assertTrue(result.lines().size > 1, "Expected indented XML")
    }

    @Test
    fun autoFormat_html_indents() {
        val input = "<div><span>text</span></div>"
        val result = autoFormat(input, SyntaxLanguage.HTML)
        assertTrue(result.lines().size > 1, "Expected indented HTML")
    }

    @Test
    fun autoFormat_javascript_returns_raw() {
        val input = "function foo() { return 1; }"
        val result = autoFormat(input, SyntaxLanguage.JAVASCRIPT)
        assertEquals(input, result, "JS autoformat should return raw text (no formatter)")
    }

    @Test
    fun autoFormat_plain_returns_raw() {
        val input = "just some text"
        val result = autoFormat(input, SyntaxLanguage.PLAIN)
        assertEquals(input, result)
    }

    // ── tryPrettyPrint ──────────────────────────────────────────

    @Test
    fun tryPrettyPrint_valid_json() {
        val input = """{"key":"value","num":42}"""
        val result = tryPrettyPrint(input)
        assertNotEquals(input, result, "Pretty-printed JSON should differ from compact form")
        assertTrue(result.contains("\"key\""))
        assertTrue(result.contains("42"))
    }

    @Test
    fun tryPrettyPrint_invalid_json_falls_back() {
        val input = "not json"
        val result = tryPrettyPrint(input)
        assertEquals(input, result)
    }

    // ── fileExtensionForContentType – JavaScript ────────────────

    @Test
    fun fileExtension_javascript() {
        assertEquals("js", fileExtensionForContentType("application/javascript"))
        assertEquals("js", fileExtensionForContentType("text/javascript"))
    }
}
