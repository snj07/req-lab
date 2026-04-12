package com.reqlab.ui.shared.components

import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.ui.shared.state.MutableFormDataRow
import com.reqlab.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for the editor architecture fixes:
 *
 * 1. Preview mode removal (no separate preview tab)
 * 2. Body type state isolation (per-type content map)
 * 3. Validation (JSON strict, XML basic)
 * 4. Code folding (fold region detection)
 * 5. Syntax highlighting (language mapping)
 * 6. Performance (large text handling threshold)
 */
class EditorArchitectureTest {

    // ═══════════════════════════════════════════════════════════
    // ── Body Type State Isolation ─────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun switching_raw_to_form_preserves_raw_content() {
        val tab = RequestTabState()

        // Set JSON content
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"key":"value"}"""

        // Switch to FORM_DATA
        tab.bodyType = BodyType.FORM_DATA
        tab.formRows.add(MutableFormDataRow(key = "field", value = "data"))

        // Switch back to JSON — content must be preserved
        tab.bodyType = BodyType.JSON
        assertEquals("""{"key":"value"}""", tab.bodyContent,
            "JSON content must be preserved after switching to FORM_DATA and back")
    }

    @Test
    fun switching_raw_to_urlencoded_preserves_raw_content() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.XML
        tab.bodyContent = "<root><item>1</item></root>"

        // Switch to URL_ENCODED
        tab.bodyType = BodyType.X_WWW_FORM_URLENCODED
        tab.urlencodedRows.add(MutableFormDataRow(key = "param", value = "val"))

        // Switch back — XML preserved
        tab.bodyType = BodyType.XML
        assertEquals("<root><item>1</item></root>", tab.bodyContent,
            "XML content must survive round-trip through URL_ENCODED")
    }

    @Test
    fun form_rows_independent_from_raw_content() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.FORM_DATA
        tab.formRows.add(MutableFormDataRow(key = "a", value = "1"))
        tab.formRows.add(MutableFormDataRow(key = "b", value = "2"))

        // Switch to JSON and back
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"x":1}"""

        tab.bodyType = BodyType.FORM_DATA
        assertEquals(2, tab.formRows.size, "Form rows must survive body type switch")
        assertEquals("a", tab.formRows[0].key)
        assertEquals("b", tab.formRows[1].key)
    }

    @Test
    fun urlencoded_rows_independent_from_raw_content() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.X_WWW_FORM_URLENCODED
        tab.urlencodedRows.add(MutableFormDataRow(key = "q", value = "test"))

        tab.bodyType = BodyType.HTML
        tab.bodyContent = "<html></html>"

        tab.bodyType = BodyType.X_WWW_FORM_URLENCODED
        assertEquals(1, tab.urlencodedRows.size)
        assertEquals("q", tab.urlencodedRows[0].key)
    }

    @Test
    fun all_raw_subtypes_have_independent_content() {
        val tab = RequestTabState()
        val subtypes = listOf(
            BodyType.JSON to """{"j":1}""",
            BodyType.XML to "<x/>",
            BodyType.HTML to "<h/>",
            BodyType.JAVASCRIPT to "var a=1;",
            BodyType.RAW_TEXT to "plain text",
        )

        // Set content for each subtype
        subtypes.forEach { (type, content) ->
            tab.bodyType = type
            tab.bodyContent = content
        }

        // Verify each subtype independently
        subtypes.forEach { (type, expected) ->
            tab.bodyType = type
            assertEquals(expected, tab.bodyContent,
                "${type.name} content should be independently stored")
        }
    }

    @Test
    fun graphql_content_independent_from_json() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"json":true}"""

        tab.bodyType = BodyType.GRAPHQL
        tab.bodyContent = "query { user { name } }"

        tab.bodyType = BodyType.JSON
        assertEquals("""{"json":true}""", tab.bodyContent)

        tab.bodyType = BodyType.GRAPHQL
        assertEquals("query { user { name } }", tab.bodyContent)
    }

    @Test
    fun binary_content_independent_from_other_types() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.BINARY
        tab.bodyContent = "reqlab-binary:test.bin\nbase64data"

        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"a":1}"""

        tab.bodyType = BodyType.BINARY
        assertEquals("reqlab-binary:test.bin\nbase64data", tab.bodyContent,
            "Binary content must be preserved")
    }

    // ═══════════════════════════════════════════════════════════
    // ── JSON Validation ───────────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun validateJson_deeply_nested_valid_json() {
        val json = """{"a":{"b":{"c":{"d":[1,2,{"e":"f"}]}}}}"""
        assertNull(validateJson(json), "Deeply nested valid JSON should pass")
    }

    @Test
    fun validateJson_empty_object_and_array() {
        assertNull(validateJson("{}"))
        assertNull(validateJson("[]"))
    }

    @Test
    fun validateJson_duplicate_keys_still_parses() {
        // kotlinx.serialization allows duplicate keys (last wins)
        assertNull(validateJson("""{"a":1,"a":2}"""))
    }

    @Test
    fun validateJson_single_values() {
        assertNull(validateJson("42"))
        assertNull(validateJson("\"hello\""))
        assertNull(validateJson("true"))
        assertNull(validateJson("false"))
        assertNull(validateJson("null"))
    }

    @Test
    fun validateJson_missing_colon_returns_error() {
        val err = validateJson("""{"key" "value"}""")
        assertNotNull(err, "Missing colon should be an error")
    }

    @Test
    fun validateJson_extra_bracket_returns_error() {
        val err = validateJson("""[1, 2, 3]]""")
        assertNotNull(err, "Extra bracket should be an error")
    }

    @Test
    fun validateJson_error_has_message() {
        val err = validateJson("{invalid")
        assertNotNull(err)
        assertTrue(err.message.isNotBlank(), "Error message should not be blank")
    }

    // ═══════════════════════════════════════════════════════════
    // ── XML Validation ────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun validateXml_well_formed_with_attributes() {
        assertNull(validateXml("""<root attr="val"><child id="1">text</child></root>"""))
    }

    @Test
    fun validateXml_void_html_elements_are_valid() {
        assertNull(validateXml("<html><head><meta charset=\"utf-8\"><link rel=\"icon\"></head><body></body></html>"))
    }

    @Test
    fun validateXml_not_starting_with_bracket() {
        val err = validateXml("hello <root></root>")
        assertNotNull(err)
        assertTrue(err.message.contains("<"), "Should mention XML must begin with '<'")
    }

    @Test
    fun validateXml_unclosed_tag_returns_error() {
        val err = validateXml("<root><child><nested>text</nested></root>")
        assertNotNull(err, "Unclosed child tag should be detected")
        assertTrue(err.message.contains("mismatched") || err.message.contains("tag"),
            "Should mention mismatched tags: ${err.message}")
    }

    // ═══════════════════════════════════════════════════════════
    // ── Code Folding Detection ────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun detectFoldRegions_json_object() {
        val json = """
        {
          "a": 1,
          "b": 2
        }
        """.trimIndent().lines()
        val regions = detectFoldRegions(json, SyntaxLanguage.JSON)
        assertTrue(regions.isNotEmpty(), "JSON object should have fold regions")
        assertEquals(0, regions[0].startLine)
        assertEquals(3, regions[0].endLine)
    }

    @Test
    fun detectFoldRegions_nested_json() {
        val json = """
        {
          "outer": {
            "inner": [
              1,
              2
            ]
          }
        }
        """.trimIndent().lines()
        val regions = detectFoldRegions(json, SyntaxLanguage.JSON)
        assertTrue(regions.size >= 3, "Nested JSON should have multiple fold regions")
    }

    @Test
    fun detectFoldRegions_xml_tags() {
        val xml = """
        <root>
          <child>
            <grandchild>text</grandchild>
          </child>
        </root>
        """.trimIndent().lines()
        val regions = detectFoldRegions(xml, SyntaxLanguage.XML)
        assertTrue(regions.isNotEmpty(), "XML elements spanning multiple lines should have fold regions")
    }

    @Test
    fun detectFoldRegions_plain_returns_empty() {
        val text = "line 1\nline 2\nline 3".lines()
        val regions = detectFoldRegions(text, SyntaxLanguage.PLAIN)
        assertTrue(regions.isEmpty(), "PLAIN text should have no fold regions")
    }

    @Test
    fun detectFoldRegions_javascript_functions() {
        val js = """
        function hello() {
          const x = 1;
          return x;
        }
        """.trimIndent().lines()
        val regions = detectFoldRegions(js, SyntaxLanguage.JAVASCRIPT)
        assertTrue(regions.isNotEmpty(), "JS function body should be foldable")
    }

    @Test
    fun detectFoldRegions_graphql_query() {
        val gql = """
        query {
          user {
            name
            email
          }
        }
        """.trimIndent().lines()
        val regions = detectFoldRegions(gql, SyntaxLanguage.GRAPHQL)
        assertTrue(regions.isNotEmpty(), "GraphQL query should have fold regions")
    }

    // ═══════════════════════════════════════════════════════════
    // ── Fold State ────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun foldState_toggle_folds_and_unfolds() {
        val state = FoldState()
        assertFalse(state.isFolded(0))

        state.toggle(0)
        assertTrue(state.isFolded(0))

        state.toggle(0)
        assertFalse(state.isFolded(0))
    }

    @Test
    fun foldState_foldAll_and_unfoldAll() {
        val regions = listOf(
            FoldRegion(0, 5),
            FoldRegion(7, 12),
            FoldRegion(14, 18),
        )
        val state = FoldState()

        state.foldAll(regions)
        assertTrue(state.isFolded(0))
        assertTrue(state.isFolded(7))
        assertTrue(state.isFolded(14))

        state.unfoldAll()
        assertFalse(state.isFolded(0))
        assertFalse(state.isFolded(7))
        assertFalse(state.isFolded(14))
    }

    @Test
    fun computeVisibleLines_hides_folded_content() {
        val lines = listOf("{", "  a", "  b", "}", "end")
        val regions = listOf(FoldRegion(0, 3))
        val state = FoldState()
        state.fold(0)

        val visible = computeVisibleLines(lines, regions, state)
        // Should have 2 visible lines: the folded start line and "end"
        assertEquals(2, visible.size)
        assertTrue(visible[0].isFolded)
        assertEquals("{", visible[0].text)
        assertEquals("end", visible[1].text)
    }

    @Test
    fun computeVisibleLines_shows_all_when_unfolded() {
        val lines = listOf("{", "  a", "  b", "}", "end")
        val regions = listOf(FoldRegion(0, 3))
        val state = FoldState()

        val visible = computeVisibleLines(lines, regions, state)
        assertEquals(5, visible.size)
    }

    // ═══════════════════════════════════════════════════════════
    // ── Syntax Highlighting ───────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun highlightJson_produces_annotated_string() {
        val result = highlightJson("""{"key":"value","num":42}""")
        assertTrue(result.text.contains("key"))
        assertTrue(result.text.contains("value"))
        assertTrue(result.text.contains("42"))
    }

    @Test
    fun highlightXml_produces_annotated_string() {
        val result = highlightXml("<root attr=\"val\">text</root>")
        assertTrue(result.text.contains("root"))
        assertTrue(result.text.contains("attr"))
        assertTrue(result.text.contains("text"))
    }

    @Test
    fun highlightGraphql_produces_annotated_string() {
        val result = highlightGraphql("query { user { name } }")
        assertTrue(result.text.contains("query"))
        assertTrue(result.text.contains("user"))
    }

    @Test
    fun highlightJavaScript_produces_annotated_string() {
        val result = highlightJavaScript("const x = 42;")
        assertTrue(result.text.contains("const"))
        assertTrue(result.text.contains("42"))
    }

    @Test
    fun highlightLine_dispatches_correctly() {
        val jsonResult = highlightLine("""{"a":1}""", SyntaxLanguage.JSON)
        assertTrue(jsonResult.text.isNotEmpty())

        val xmlResult = highlightLine("<tag/>", SyntaxLanguage.XML)
        assertTrue(xmlResult.text.isNotEmpty())

        val plainResult = highlightLine("plain text", SyntaxLanguage.PLAIN)
        assertEquals("plain text", plainResult.text)
    }

    @Test
    fun highlightText_dispatches_correctly() {
        val result = highlightText("""{"key":"value"}""", SyntaxLanguage.JSON)
        assertTrue(result.text.contains("key"))
    }

    // ═══════════════════════════════════════════════════════════
    // ── Search Matches ────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun findSearchMatches_finds_all_occurrences() {
        val lines = listOf("hello world", "hello again", "goodbye")
        val matches = findSearchMatches(lines, "hello")
        assertEquals(2, matches.size)
        assertEquals(0, matches[0].lineIndex)
        assertEquals(1, matches[1].lineIndex)
    }

    @Test
    fun findSearchMatches_case_insensitive() {
        val lines = listOf("Hello World", "HELLO again")
        val matches = findSearchMatches(lines, "hello")
        assertEquals(2, matches.size)
    }

    @Test
    fun findSearchMatches_empty_query_returns_empty() {
        val lines = listOf("hello")
        val matches = findSearchMatches(lines, "")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun findSearchMatches_multiple_on_same_line() {
        val lines = listOf("ab ab ab")
        val matches = findSearchMatches(lines, "ab")
        assertEquals(3, matches.size)
    }

    // ═══════════════════════════════════════════════════════════
    // ── Auto Formatting ───────────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @kotlinx.serialization.ExperimentalSerializationApi
    @Test
    fun tryPrettyPrint_formats_compact_json() {
        val compact = """{"a":1,"b":2}"""
        val pretty = tryPrettyPrint(compact)
        assertTrue(pretty.contains("\n"), "Pretty-printed JSON should contain newlines")
        assertTrue(pretty.contains("  "), "Pretty-printed JSON should be indented")
    }

    @kotlinx.serialization.ExperimentalSerializationApi
    @Test
    fun tryPrettyPrint_invalid_json_returns_raw() {
        val invalid = "not json"
        val result = tryPrettyPrint(invalid)
        assertEquals("not json", result, "Invalid JSON should return raw text")
    }

    @Test
    fun formatXml_indents_nested_elements() {
        val flat = "<root><child><grandchild>text</grandchild></child></root>"
        val formatted = formatXml(flat)
        assertTrue(formatted.contains("\n"), "Formatted XML should contain newlines")
    }

    // ═══════════════════════════════════════════════════════════
    // ── Language Detection ────────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun detectLanguage_json_content_types() {
        assertEquals(SyntaxLanguage.JSON, detectLanguage("application/json"))
        assertEquals(SyntaxLanguage.JSON, detectLanguage("application/json; charset=utf-8"))
    }

    @Test
    fun detectLanguage_xml_content_types() {
        assertEquals(SyntaxLanguage.XML, detectLanguage("application/xml"))
        assertEquals(SyntaxLanguage.XML, detectLanguage("text/xml"))
    }

    @Test
    fun detectLanguage_html_content_types() {
        assertEquals(SyntaxLanguage.HTML, detectLanguage("text/html"))
    }

    @Test
    fun detectLanguage_javascript_content_types() {
        assertEquals(SyntaxLanguage.JAVASCRIPT, detectLanguage("application/javascript"))
        assertEquals(SyntaxLanguage.JAVASCRIPT, detectLanguage("text/ecmascript"))
    }

    @Test
    fun detectLanguage_graphql() {
        assertEquals(SyntaxLanguage.GRAPHQL, detectLanguage("application/graphql"))
    }

    @Test
    fun detectLanguage_null_returns_plain() {
        assertEquals(SyntaxLanguage.PLAIN, detectLanguage(null))
    }

    @Test
    fun detectLanguage_unknown_returns_plain() {
        assertEquals(SyntaxLanguage.PLAIN, detectLanguage("application/octet-stream"))
    }

    // ═══════════════════════════════════════════════════════════
    // ── Performance: Large Text Threshold ─────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun large_json_validation_does_not_throw() {
        // Build a large but valid JSON array
        val sb = StringBuilder("[")
        repeat(10_000) { i ->
            if (i > 0) sb.append(",")
            sb.append("""{"id":$i,"value":"item-$i"}""")
        }
        sb.append("]")
        val largeJson = sb.toString()

        // Validation should still work (it uses kotlinx.serialization)
        val result = validateJson(largeJson)
        assertNull(result, "Large valid JSON should pass validation")
    }

    @Test
    fun large_xml_validation_does_not_throw() {
        val sb = StringBuilder("<root>")
        repeat(5_000) { i ->
            sb.append("<item id=\"$i\">value-$i</item>")
        }
        sb.append("</root>")
        val largeXml = sb.toString()

        val result = validateXml(largeXml)
        assertNull(result, "Large valid XML should pass validation")
    }

    @Test
    fun highlightJson_handles_large_input() {
        val sb = StringBuilder("[")
        repeat(1_000) { i ->
            if (i > 0) sb.append(",")
            sb.append("""{"k$i":"v$i"}""")
        }
        sb.append("]")
        val result = highlightJson(sb.toString())
        assertTrue(result.text.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════
    // ── File Extension Mapping ────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun fileExtension_covers_all_content_types() {
        assertEquals("json", fileExtensionForContentType("application/json"))
        assertEquals("xml", fileExtensionForContentType("application/xml"))
        assertEquals("html", fileExtensionForContentType("text/html"))
        assertEquals("js", fileExtensionForContentType("application/javascript"))
        assertEquals("txt", fileExtensionForContentType(null))
        assertEquals("txt", fileExtensionForContentType("application/octet-stream"))
    }

    // ═══════════════════════════════════════════════════════════
    // ── Edge Cases ────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════

    @Test
    fun bodyContent_for_none_type_is_empty() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.NONE
        assertEquals("", tab.bodyContent)
    }

    @Test
    fun bodyContent_setting_empty_string_is_stored() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "some content"
        tab.bodyContent = ""
        assertEquals("", tab.bodyContent, "Empty string should overwrite previous content")
    }

    @Test
    fun bodyContents_map_values_are_consistent() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "json"
        tab.bodyType = BodyType.XML
        tab.bodyContent = "xml"

        // Verify both values exist in the map
        assertEquals("json", tab.bodyContents[BodyType.JSON])
        assertEquals("xml", tab.bodyContents[BodyType.XML])

        // Verify accessor consistency
        tab.bodyType = BodyType.JSON
        assertEquals("json", tab.bodyContent)
        tab.bodyType = BodyType.XML
        assertEquals("xml", tab.bodyContent)
    }

    @Test
    fun switching_body_type_rapidly_preserves_all_content() {
        val tab = RequestTabState()

        // Rapidly switch between types setting and verifying content
        repeat(10) { round ->
            tab.bodyType = BodyType.JSON
            tab.bodyContent = "json-$round"

            tab.bodyType = BodyType.XML
            tab.bodyContent = "xml-$round"

            tab.bodyType = BodyType.HTML
            tab.bodyContent = "html-$round"
        }

        // Verify final content for each type
        tab.bodyType = BodyType.JSON
        assertEquals("json-9", tab.bodyContent)

        tab.bodyType = BodyType.XML
        assertEquals("xml-9", tab.bodyContent)

        tab.bodyType = BodyType.HTML
        assertEquals("html-9", tab.bodyContent)
    }
}
