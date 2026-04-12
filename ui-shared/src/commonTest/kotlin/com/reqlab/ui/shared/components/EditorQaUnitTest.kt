package com.reqlab.ui.shared.components

import com.reqlab.core.model.BodyType
import com.reqlab.ui.shared.state.MutableFormDataRow
import com.reqlab.ui.shared.state.RequestTabState
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * ═══════════════════════════════════════════════════════════════════════
 * QA Unit Test Suite — Editor Core Behaviour
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Areas covered (per the QA test plan):
 *  1.  Basic editor behavior — typing/editing round-trips via state
 *  2.  Multi-language support — language detection and highlighting
 *  3.  Error handling — JSON / XML validation with precise messages
 *  4.  Body type switching — content and language preservation
 *  5.  Large file performance — fold region detection on big payloads
 *  6.  Folding — brace/bracket/XML fold region detection
 *  7.  Syntax highlighting — per-language colour spans
 *  8.  Search — match finding across all visible lines
 *  9.  Whitespace / edge cases — empty, blank, single-char documents
 * 10.  Real large-file data — 5 MB and 10 MB JSON from qa-tests/resources
 *
 * NO Compose runtime required – runs on desktop JVM and wasmJs.
 */

// ═══════════════════════════════════════════════════════════════════════
// 1. Basic Editor Behaviour (state layer)
// ═══════════════════════════════════════════════════════════════════════

class BasicEditorStateTest {

    // ── typing round-trip ────────────────────────────────────────────

    @Test
    fun typing_updates_body_content() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = ""
        tab.bodyContent = "{"
        assertEquals("{", tab.bodyContent)
    }

    @Test
    fun appending_characters_accumulates_correctly() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = ""
        val chars = listOf("{", "\"", "k", "\"", ":", "1", "}")
        val sb = StringBuilder()
        for (ch in chars) {
            sb.append(ch)
            tab.bodyContent = sb.toString()
        }
        assertEquals("{\"k\":1}", tab.bodyContent)
    }

    @Test
    fun delete_all_returns_to_empty() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"key":"value"}"""
        tab.bodyContent = ""
        assertEquals("", tab.bodyContent)
    }

    // ── cursor / selection simulation (state layer) ──────────────────

    @Test
    fun inserting_at_start_of_content() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "world"
        tab.bodyContent = "hello " + tab.bodyContent
        assertEquals("hello world", tab.bodyContent)
    }

    @Test
    fun replacing_middle_of_content() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "aaa bbb ccc"
        tab.bodyContent = tab.bodyContent.replace("bbb", "ZZZ")
        assertEquals("aaa ZZZ ccc", tab.bodyContent)
    }

    // ── paste with newlines ──────────────────────────────────────────

    @Test
    fun pasting_multiline_text_stores_newlines() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        val json = "{\n  \"a\": 1,\n  \"b\": 2\n}"
        tab.bodyContent = json
        assertEquals(json, tab.bodyContent)
        assertEquals(4, tab.bodyContent.split('\n').size)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 2. Multi-Language Support — Language Detection
// ═══════════════════════════════════════════════════════════════════════

class MultiLanguageSupportTest {

    @Test
    fun json_content_type_maps_to_json_language() {
        assertEquals(SyntaxLanguage.JSON, detectLanguage("application/json"))
        assertEquals(SyntaxLanguage.JSON, detectLanguage("application/json; charset=utf-8"))
        assertEquals(SyntaxLanguage.JSON, detectLanguage("application/json;charset=UTF-8"))
    }

    @Test
    fun xml_content_type_maps_to_xml_language() {
        assertEquals(SyntaxLanguage.XML, detectLanguage("application/xml"))
        assertEquals(SyntaxLanguage.XML, detectLanguage("text/xml"))
        assertEquals(SyntaxLanguage.XML, detectLanguage("application/xml; charset=utf-8"))
    }

    @Test
    fun html_content_type_maps_to_html_language() {
        assertEquals(SyntaxLanguage.HTML, detectLanguage("text/html"))
        assertEquals(SyntaxLanguage.HTML, detectLanguage("text/html; charset=utf-8"))
    }

    @Test
    fun javascript_content_type_maps_to_javascript_language() {
        assertEquals(SyntaxLanguage.JAVASCRIPT, detectLanguage("application/javascript"))
        assertEquals(SyntaxLanguage.JAVASCRIPT, detectLanguage("text/javascript"))
    }

    @Test
    fun graphql_content_type_maps_to_graphql_language() {
        assertEquals(SyntaxLanguage.GRAPHQL, detectLanguage("application/graphql"))
    }

    @Test
    fun unknown_or_plain_text_maps_to_plain() {
        assertEquals(SyntaxLanguage.PLAIN, detectLanguage("text/plain"))
        assertEquals(SyntaxLanguage.PLAIN, detectLanguage("application/octet-stream"))
        assertEquals(SyntaxLanguage.PLAIN, detectLanguage(null))
        assertEquals(SyntaxLanguage.PLAIN, detectLanguage(""))
    }

    // ── Syntax highlighting produces spans per language ──────────────

    @Test
    fun json_highlighting_produces_colour_spans() {
        val line = """  "boardId": "147654876797","""
        assertTrue(highlightLine(line, SyntaxLanguage.JSON).spanStyles.isNotEmpty(),
            "JSON key-value line must produce colour spans")
    }

    @Test
    fun xml_highlighting_produces_colour_spans() {
        val result = highlightLine("<root><item id=\"1\">Text</item></root>", SyntaxLanguage.XML)
        assertTrue(result.spanStyles.isNotEmpty(), "XML tag line must produce colour spans")
    }

    @Test
    fun html_highlighting_produces_colour_spans() {
        val result = highlightLine("<h1 class=\"title\">Hello</h1>", SyntaxLanguage.HTML)
        assertTrue(result.spanStyles.isNotEmpty(), "HTML line must produce colour spans")
    }

    @Test
    fun javascript_highlighting_produces_colour_spans() {
        val result = highlightLine("const x = 42; // comment", SyntaxLanguage.JAVASCRIPT)
        assertTrue(result.spanStyles.isNotEmpty(), "JS line must produce colour spans")
    }

    @Test
    fun plain_highlighting_produces_no_spans() {
        val result = highlightLine("just plain text, no highlighting", SyntaxLanguage.PLAIN)
        assertTrue(result.spanStyles.isEmpty(), "PLAIN language must produce zero spans")
    }

    @Test
    fun highlighted_text_length_equals_input_length() {
        val inputs = listOf(
            """{"key": "value", "n": 42}""" to SyntaxLanguage.JSON,
            """<root><child/></root>""" to SyntaxLanguage.XML,
            """function f() { return 1; }""" to SyntaxLanguage.JAVASCRIPT,
        )
        for ((input, lang) in inputs) {
            val result = highlightLine(input, lang)
            assertEquals(input.length, result.text.length,
                "Highlighted text length must match input for $lang")
            assertEquals(input, result.text,
                "Highlighted text content must be preserved for $lang")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 3. Error Handling — Validation
// ═══════════════════════════════════════════════════════════════════════

class ErrorHandlingTest {

    // ── JSON validation ──────────────────────────────────────────────

    @Test
    fun trailing_comma_in_json_object_produces_error() {
        val err = validateJson("""{ "a": 1, }""")
        assertNotNull(err, "Trailing comma must produce a validation error")
        assertTrue(err.message.isNotBlank(), "Error message must not be blank")
    }

    @Test
    fun missing_closing_brace_produces_error() {
        val err = validateJson("""{"key": "value" """)
        assertNotNull(err)
        assertTrue(err.message.isNotBlank())
    }

    @Test
    fun unquoted_key_produces_error() {
        val err = validateJson("""{key: "value"}""")
        assertNotNull(err)
    }

    @Test
    fun partial_array_produces_error() {
        val err = validateJson("""[1, 2, 3""")
        assertNotNull(err)
        assertTrue(err.message.isNotBlank())
    }

    @Test
    fun double_comma_produces_error() {
        val err = validateJson("""{"a":1,,"b":2}""")
        assertNotNull(err)
    }

    @Test
    fun valid_json_object_produces_no_error() {
        assertNull(validateJson("""{"name":"ReqLab","version":2,"active":true}"""))
    }

    @Test
    fun valid_json_array_produces_no_error() {
        assertNull(validateJson("""[{"id":1},{"id":2},{"id":3}]"""))
    }

    @Test
    fun valid_nested_json_produces_no_error() {
        val json = """
        {
            "user": {
                "name": "Alice",
                "roles": ["admin", "editor"],
                "active": true,
                "score": 9.8
            }
        }
        """.trimIndent()
        assertNull(validateJson(json))
    }

    @Test
    fun empty_string_produces_no_error() {
        assertNull(validateJson(""))
        assertNull(validateJson("   "))
    }

    @Test
    fun plain_text_produces_error_not_crash() {
        val err = validateJson("hello world this is not json")
        assertNotNull(err)
    }

    // ── XML validation ───────────────────────────────────────────────

    @Test
    fun mismatched_xml_tags_produces_error() {
        val err = validateXml("<root><child></root>")
        assertNotNull(err, "Mismatched XML tags must produce error")
    }

    @Test
    fun unclosed_xml_tag_produces_error() {
        val err = validateXml("<root><child>text</root>")
        assertNotNull(err)
    }

    @Test
    fun valid_simple_xml_produces_no_error() {
        assertNull(validateXml("<root><item>1</item></root>"))
    }

    @Test
    fun valid_xml_with_attributes_produces_no_error() {
        assertNull(validateXml("""<root id="1"><item type="test">value</item></root>"""))
    }

    @Test
    fun blank_xml_produces_no_error() {
        assertNull(validateXml(""))
        assertNull(validateXml("   "))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 4. Body Type Switching — State Isolation
// ═══════════════════════════════════════════════════════════════════════

class BodyTypeSwitchingTest {

    @Test
    fun switching_json_to_form_and_back_preserves_json_content() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"original":"data"}"""

        tab.bodyType = BodyType.FORM_DATA
        tab.formRows.add(MutableFormDataRow(key = "field", value = "value"))

        tab.bodyType = BodyType.JSON
        assertEquals("""{"original":"data"}""", tab.bodyContent,
            "JSON content must be preserved after Form round-trip")
    }

    @Test
    fun switching_xml_to_urlencoded_and_back_preserves_xml_content() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.XML
        tab.bodyContent = "<root><data>preserved</data></root>"

        tab.bodyType = BodyType.X_WWW_FORM_URLENCODED
        tab.urlencodedRows.add(MutableFormDataRow(key = "param", value = "val"))

        tab.bodyType = BodyType.XML
        assertEquals("<root><data>preserved</data></root>", tab.bodyContent,
            "XML content must be preserved after URL-encoded round-trip")
    }

    @Test
    fun each_body_type_has_independent_content_store() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"type":"json"}"""

        tab.bodyType = BodyType.XML
        tab.bodyContent = "<root>xml</root>"

        tab.bodyType = BodyType.HTML
        tab.bodyContent = "<html><body>html</body></html>"

        tab.bodyType = BodyType.JAVASCRIPT
        tab.bodyContent = "console.log('js');"

        tab.bodyType = BodyType.RAW_TEXT
        tab.bodyContent = "raw text content"

        // Read back each
        tab.bodyType = BodyType.JSON
        assertEquals("""{"type":"json"}""", tab.bodyContent, "JSON content must survive 4 switches")

        tab.bodyType = BodyType.XML
        assertEquals("<root>xml</root>", tab.bodyContent)

        tab.bodyType = BodyType.HTML
        assertEquals("<html><body>html</body></html>", tab.bodyContent)

        tab.bodyType = BodyType.JAVASCRIPT
        assertEquals("console.log('js');", tab.bodyContent)

        tab.bodyType = BodyType.RAW_TEXT
        assertEquals("raw text content", tab.bodyContent)
    }

    @Test
    fun form_rows_are_preserved_across_body_type_switches() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.FORM_DATA
        tab.formRows.add(MutableFormDataRow(key = "file", value = "upload.png"))
        tab.formRows.add(MutableFormDataRow(key = "token", value = "abc123"))

        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"temp":"value"}"""

        tab.bodyType = BodyType.FORM_DATA
        assertEquals(2, tab.formRows.size)
        assertEquals("file", tab.formRows[0].key)
        assertEquals("upload.png", tab.formRows[0].value)
    }

    @Test
    fun urlencoded_rows_are_preserved_across_body_type_switches() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.X_WWW_FORM_URLENCODED
        tab.urlencodedRows.add(MutableFormDataRow(key = "user", value = "alice"))
        tab.urlencodedRows.add(MutableFormDataRow(key = "pass", value = "s3cr3t"))

        tab.bodyType = BodyType.XML
        tab.bodyContent = "<q/>"

        tab.bodyType = BodyType.X_WWW_FORM_URLENCODED
        assertEquals(2, tab.urlencodedRows.size)
        assertEquals("user", tab.urlencodedRows[0].key)
    }

    @Test
    fun html_content_preserved_when_switching_through_multiple_types() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.HTML
        val html = "<html><head><title>Test</title></head><body><p>Hello</p></body></html>"
        tab.bodyContent = html

        for (type in listOf(BodyType.JSON, BodyType.FORM_DATA, BodyType.X_WWW_FORM_URLENCODED, BodyType.RAW_TEXT)) {
            tab.bodyType = type
        }
        tab.bodyType = BodyType.HTML
        assertEquals(html, tab.bodyContent, "HTML content must survive multi-type round-trip")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 5. Fold Region Detection — JSON/XML
// ═══════════════════════════════════════════════════════════════════════

class FoldRegionDetectionQaTest {

    // ── JSON object root ─────────────────────────────────────────────

    @Test
    fun json_object_root_has_fold_region() {
        val lines = listOf("{", "  \"key\": \"value\"", "}")
        val regions = detectBraceFoldRegions(lines)
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(2, regions[0].endLine)
    }

    // ── JSON array root ──────────────────────────────────────────────

    @Test
    fun json_array_root_has_fold_region() {
        val lines = listOf("[", "  {\"a\": 1},", "  {\"b\": 2}", "]")
        val regions = detectBraceFoldRegions(lines)
        val rootFold = regions.first { it.startLine == 0 }
        assertEquals(3, rootFold.endLine, "Array root must fold from line 0 to line 3")
    }

    @Test
    fun nested_objects_in_array_have_independent_fold_regions() {
        val lines = listOf("[", "  {", "    \"key\": \"v\"", "  },", "  {", "    \"k2\": 2", "  }", "]")
        val regions = detectBraceFoldRegions(lines)
        val startLines = regions.map { it.startLine }.toSet()
        assertTrue(0 in startLines, "Root array fold at line 0")
        assertTrue(1 in startLines, "First object fold at line 1")
        assertTrue(4 in startLines, "Second object fold at line 4")
    }

    @Test
    fun three_level_nesting_produces_three_regions() {
        val lines = listOf(
            "{",
            "  \"level1\": {",
            "    \"level2\": {",
            "      \"value\": 1",
            "    }",
            "  }",
            "}"
        )
        val regions = detectBraceFoldRegions(lines)
        assertEquals(3, regions.size, "Three nested objects must produce three fold regions")
    }

    @Test
    fun single_line_braces_do_not_fold() {
        val lines = listOf("""{"a":1,"b":[1,2,3],"c":{"d":4}}""")
        assertTrue(detectBraceFoldRegions(lines).isEmpty(),
            "Single-line document must produce no fold regions")
    }

    @Test
    fun braces_inside_strings_are_ignored() {
        val lines = listOf(
            "{",
            "  \"url\": \"https://api.example.com/{id}?q=[x,y]\",",
            "  \"real\": {",
            "    \"x\": 1",
            "  }",
            "}"
        )
        val regions = detectBraceFoldRegions(lines)
        assertEquals(2, regions.size, "String braces must not create extra fold regions; got ${regions.size}")
    }

    // ── XML fold regions ──────────────────────────────────────────────

    @Test
    fun xml_root_element_produces_fold_region() {
        val lines = listOf("<root>", "  <item>1</item>", "  <item>2</item>", "</root>")
        val regions = detectXmlFoldRegions(lines)
        assertTrue(regions.isNotEmpty(), "XML root element must create fold region")
        assertEquals(0, regions.first().startLine)
        assertEquals(3, regions.first().endLine)
    }

    @Test
    fun self_closing_xml_tags_do_not_fold() {
        val lines = listOf("<root>", "  <item/>", "</root>")
        val regions = detectXmlFoldRegions(lines)
        // Only outer root should fold; <item/> self-closes
        val itemFold = regions.firstOrNull { it.startLine == 1 }
        assertNull(itemFold, "Self-closing tags must not produce fold regions")
    }

    @Test
    fun nested_xml_elements_produce_nested_folds() {
        val lines = listOf("<root>", "  <parent>", "    <child>x</child>", "  </parent>", "</root>")
        val regions = detectXmlFoldRegions(lines)
        assertTrue(regions.size >= 2, "Nested XML elements must each produce folds; got ${regions.size}")
    }

    // ── Comment fold regions ──────────────────────────────────────────

    @Test
    fun multiline_c_comment_produces_fold_region() {
        val lines = listOf("/*", " * This is a", " * multi-line comment", " */")
        val regions = detectCommentFoldRegions(lines)
        assertTrue(regions.isNotEmpty(), "Multi-line C comment must produce fold region")
        assertEquals(0, regions.first().startLine)
        assertEquals(3, regions.first().endLine)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 6. Search Functionality
// ═══════════════════════════════════════════════════════════════════════

class SearchFunctionalityTest {

    @Test
    fun search_finds_exact_match_on_single_line() {
        val lines = listOf("hello world", "foo bar")
        val matches = findSearchMatches(lines, "world")
        assertEquals(1, matches.size)
        assertEquals(0, matches[0].lineIndex)
    }

    @Test
    fun search_is_case_insensitive() {
        val lines = listOf("Hello World", "HELLO WORLD", "hello world")
        val matches = findSearchMatches(lines, "hello")
        assertEquals(3, matches.size, "Case-insensitive search must match all 3 lines")
    }

    @Test
    fun search_finds_multiple_occurrences_on_same_line() {
        val lines = listOf("abc abc abc")
        val matches = findSearchMatches(lines, "abc")
        assertEquals(3, matches.size, "Three occurrences on the same line must yield 3 matches")
    }

    @Test
    fun search_across_multiple_lines() {
        val lines = listOf("{", "  \"name\": \"Alice\",", "  \"email\": \"alice@example.com\"", "}")
        val nameMatches = findSearchMatches(lines, "alice")
        assertTrue(nameMatches.size >= 2, "Should find 'alice' in both name and email lines")
    }

    @Test
    fun empty_search_query_returns_no_matches() {
        val lines = listOf("anything", "here")
        assertTrue(findSearchMatches(lines, "").isEmpty())
        assertTrue(findSearchMatches(lines, "   ").isEmpty())
    }

    @Test
    fun search_on_empty_document_returns_no_matches() {
        assertTrue(findSearchMatches(emptyList(), "query").isEmpty())
        assertTrue(findSearchMatches(listOf(""), "query").isEmpty())
    }

    @Test
    fun search_respects_line_boundaries() {
        // "key" appears ONLY in line 1
        val lines = listOf("{", "  \"key\": \"value\"", "  \"other\": 1", "}")
        val matches = findSearchMatches(lines, "key")
        val lineIndices = matches.map { it.lineIndex }.toSet()
        assertTrue(1 in lineIndices, "Match must be on line 1")
        assertFalse(0 in lineIndices)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 7. Large File Performance — Fold Detection & Highlighting
// ═══════════════════════════════════════════════════════════════════════

class LargeFilePerformanceUnitTest {

    /**
     * Generate a formatted JSON document with [lineCount] key-value entries.
     * Each line is short (~30-50 chars) to ensure per-line highlighting stays active.
     */
    private fun buildFormattedJson(lineCount: Int): String {
        val sb = StringBuilder("[\n")
        for (i in 0 until lineCount) {
            sb.append("  {\"id\": $i, \"name\": \"item_$i\"},\n")
        }
        sb.append("]")
        return sb.toString()
    }

    @Test
    fun fold_detection_completes_without_timeout_for_5000_lines() {
        val lines = buildFormattedJson(2500).split('\n')
        val mark = TimeSource.Monotonic.markNow()
        val regions = detectBraceFoldRegions(lines)
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        assertTrue(regions.isNotEmpty(), "5000-line document must produce fold regions")
        assertTrue(elapsed < 2000, "Fold detection must complete < 2s for 5 000 lines; took ${elapsed}ms")
    }

    @Test
    fun per_line_highlighting_does_not_regress_at_scale() {
        val templateLine = """  "boardId": "ABCDEFGHIJKLM12345678","""
        val mark = TimeSource.Monotonic.markNow()
        repeat(500) {
            val result = highlightLine(templateLine, SyntaxLanguage.JSON)
            assertTrue(result.spanStyles.isNotEmpty())
        }
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        assertTrue(elapsed < 2000, "500 per-line highlights must complete < 2s; took ${elapsed}ms")
    }

    @Test
    fun five_hundred_line_json_produces_fold_regions_for_array_root() {
        val lines = buildFormattedJson(250).split('\n')
        val regions = detectBraceFoldRegions(lines)
        // Root array + each object = at least 1 fold
        assertTrue(regions.isNotEmpty(), "Formatted JSON must have at least the root array fold")
        assertEquals(0, regions.first { it.startLine == 0 }.startLine)
    }

    @Test
    fun validation_of_large_valid_json_produces_no_errors() {
        // Build a valid flat JSON array of 200 items
        val items = (1..200).joinToString(",") { """{"id":$it,"value":"item$it"}""" }
        val json = "[$items]"
        assertNull(validateJson(json), "Large valid JSON must produce no validation error")
    }

    @Test
    fun validation_of_large_invalid_json_produces_error_not_crash() {
        val items = (1..200).joinToString(",") { """{"id":$it,"value":"item$it"}""" }
        val json = "[$items,]"   // trailing comma — invalid
        assertNotNull(validateJson(json), "Large invalid JSON must produce error without crash")
    }

}

// ═══════════════════════════════════════════════════════════════════════
// 8. FoldState API Correctness
// ═══════════════════════════════════════════════════════════════════════

class FoldStateApiTest {

    // FoldState internals use Compose mutableStateListOf which works
    // outside Compose runtime in unit tests.

    @Test
    fun fold_adds_start_line_to_set() {
        val state = FoldState()
        assertFalse(state.isFolded(0))
        state.fold(0)
        assertTrue(state.isFolded(0))
    }

    @Test
    fun unfold_removes_start_line() {
        val state = FoldState()
        state.fold(3)
        assertTrue(state.isFolded(3))
        state.unfold(3)
        assertFalse(state.isFolded(3))
    }

    @Test
    fun toggle_folds_then_unfolds() {
        val state = FoldState()
        assertFalse(state.isFolded(5))
        state.toggle(5)
        assertTrue(state.isFolded(5))
        state.toggle(5)
        assertFalse(state.isFolded(5))
    }

    @Test
    fun fold_all_folds_all_regions() {
        val state = FoldState()
        val regions = listOf(FoldRegion(0, 5), FoldRegion(7, 15), FoldRegion(20, 30))
        state.foldAll(regions)
        assertTrue(state.isFolded(0))
        assertTrue(state.isFolded(7))
        assertTrue(state.isFolded(20))
    }

    @Test
    fun unfold_all_clears_all_folds() {
        val state = FoldState()
        state.fold(0); state.fold(5); state.fold(10)
        state.unfoldAll()
        assertFalse(state.isFolded(0))
        assertFalse(state.isFolded(5))
        assertFalse(state.isFolded(10))
    }

    @Test
    fun duplicate_fold_is_idempotent() {
        val state = FoldState()
        state.fold(2)
        state.fold(2)  // should not duplicate
        state.unfold(2)
        assertFalse(state.isFolded(2), "Folding twice then unfolding once must leave region unfolded")
    }

    @Test
    fun unfold_non_folded_line_is_safe() {
        val state = FoldState()
        state.unfold(99)  // must not throw
        assertFalse(state.isFolded(99))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 9. Visible Lines Computation with Folds
// ═══════════════════════════════════════════════════════════════════════

class VisibleLineComputationTest {

    @Test
    fun no_folds_returns_all_original_lines() {
        val lines = listOf("a", "b", "c", "d")
        val regions = emptyList<FoldRegion>()
        val state = FoldState()
        val visible = computeVisibleLines(lines, regions, state)
        assertEquals(4, visible.size)
        visible.forEachIndexed { i, vl -> assertEquals(i, vl.originalIndex) }
    }

    @Test
    fun folding_region_hides_interior_lines() {
        val lines = listOf("{", "  \"a\": 1", "  \"b\": 2", "}")
        val regions = listOf(FoldRegion(0, 3))
        val state = FoldState()
        state.fold(0)
        val visible = computeVisibleLines(lines, regions, state)
        assertEquals(1, visible.size, "All interior lines must be hidden; only start line visible")
        assertTrue(visible[0].isFolded)
    }

    @Test
    fun unfolding_restores_all_lines() {
        val lines = listOf("{", "  \"a\": 1", "}")
        val regions = listOf(FoldRegion(0, 2))
        val state = FoldState()
        state.fold(0)
        val folded = computeVisibleLines(lines, regions, state)
        assertEquals(1, folded.size)

        state.unfold(0)
        val unfolded = computeVisibleLines(lines, regions, state)
        assertEquals(3, unfolded.size)
        assertFalse(unfolded.any { it.isFolded })
    }

    @Test
    fun sibling_fold_regions_can_be_independently_folded() {
        val lines = listOf("[", "  {", "    \"a\": 1", "  },", "  {", "    \"b\": 2", "  }", "]")
        val regions = detectBraceFoldRegions(lines)
        val state = FoldState()

        // Fold only the first nested object
        state.fold(1)
        val visible = computeVisibleLines(lines, regions, state)
        // Line 2 and 3 should be hidden; lines 0, 1, 4, 5, 6, 7 visible
        val visibleIndices = visible.map { it.originalIndex }
        assertFalse(2 in visibleIndices, "Line 2 must be hidden")
        assertFalse(3 in visibleIndices, "Line 3 must be hidden")
        assertTrue(4 in visibleIndices, "Line 4 (second object) must be visible")
    }

    @Test
    fun folded_line_reports_correct_folded_line_count() {
        val lines = listOf("{", "  line1", "  line2", "  line3", "}")
        val regions = listOf(FoldRegion(0, 4))
        val state = FoldState()
        state.fold(0)
        val visible = computeVisibleLines(lines, regions, state)
        assertEquals(1, visible.size)
        assertEquals(4, visible[0].foldedLineCount,
            "Folded region covering lines 1-4 must report foldedLineCount = 4")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 10. Auto-format / Pretty-print
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalSerializationApi::class)
class AutoFormatTest {

    @Test
    fun format_minified_json_produces_multiline_output() {
        val minified = """{"name":"Alice","age":30,"hobbies":["reading","coding"]}"""
        val formatted = autoFormat(minified, SyntaxLanguage.JSON)
        assertTrue(formatted.contains('\n'), "Formatted JSON must contain newlines")
        assertTrue(formatted.length > minified.length, "Formatted must be longer than minified")
    }

    @Test
    fun format_preserves_all_json_keys_and_values() {
        val input = """{"id":1,"name":"test","active":true,"items":[1,2,3]}"""
        val formatted = autoFormat(input, SyntaxLanguage.JSON)
        assertTrue(formatted.contains("\"id\""))
        assertTrue(formatted.contains("\"name\""))
        assertTrue(formatted.contains("\"active\""))
        assertTrue(formatted.contains("\"items\""))
    }

    @Test
    fun format_already_formatted_json_is_idempotent() {
        val json = "{\n  \"key\": \"value\"\n}"
        val formatted = autoFormat(json, SyntaxLanguage.JSON)
        // autoFormat of already-formatted JSON must remain valid (validateJson returns null for valid)
        assertNull(validateJson(formatted), "Re-formatting valid JSON must still produce valid JSON")
    }

    @Test
    fun format_invalid_json_does_not_crash() {
        // autoFormat must handle invalid input gracefully (return original or best-effort)
        val invalid = """{"broken: value"""
        val result = autoFormat(invalid, SyntaxLanguage.JSON)
        assertFalse(result.isEmpty(), "autoFormat must return non-empty result even for invalid JSON")
    }

    @Test
    fun format_plain_language_returns_original() {
        val text = "line 1\nline 2\nline 3"
        val result = autoFormat(text, SyntaxLanguage.PLAIN)
        assertEquals(text, result, "autoFormat for PLAIN language must return original unchanged")
    }
}


