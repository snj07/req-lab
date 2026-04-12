package com.reqlab.ui.shared.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the CodeFolding module:
 * - Brace-based fold region detection (JSON / JS / GraphQL)
 * - XML/HTML tag-based fold region detection
 * - Multi-line comment fold region detection
 * - Language-aware dispatcher
 * - FoldState toggling / fold-all / unfold-all
 * - Visible-line computation with folds applied
 */
class CodeFoldingTest {

    // ── detectBraceFoldRegions ──────────────────────────────────

    @Test
    fun braceFold_simple_json_object() {
        val lines = listOf(
            "{",
            "  \"key\": \"value\"",
            "}",
        )
        val regions = detectBraceFoldRegions(lines)
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(2, regions[0].endLine)
    }

    @Test
    fun braceFold_nested_json() {
        val lines = listOf(
            "{",
            "  \"outer\": {",
            "    \"inner\": 1",
            "  }",
            "}",
        )
        val regions = detectBraceFoldRegions(lines)
        assertEquals(2, regions.size)
        // Inner object { line 1 → line 3 }
        val inner = regions.first { it.startLine == 1 }
        assertEquals(3, inner.endLine)
        // Outer object { line 0 → line 4 }
        val outer = regions.first { it.startLine == 0 }
        assertEquals(4, outer.endLine)
    }

    @Test
    fun braceFold_array_spanning_lines() {
        val lines = listOf(
            "[",
            "  1,",
            "  2,",
            "  3",
            "]",
        )
        val regions = detectBraceFoldRegions(lines)
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(4, regions[0].endLine)
    }

    @Test
    fun braceFold_single_line_no_fold() {
        val lines = listOf("""{"a":1}""")
        val regions = detectBraceFoldRegions(lines)
        assertEquals(0, regions.size, "Single-line braces should not create a fold region")
    }

    @Test
    fun braceFold_braces_in_string_ignored() {
        val lines = listOf(
            "{",
            "  \"code\": \"if (x) { y }\"",
            "}",
        )
        val regions = detectBraceFoldRegions(lines)
        // Only the outer { } should be detected; braces inside the string are ignored.
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(2, regions[0].endLine)
    }

    @Test
    fun braceFold_empty_lines() {
        val regions = detectBraceFoldRegions(emptyList())
        assertTrue(regions.isEmpty())
    }

    @Test
    fun braceFold_mixed_braces_and_brackets() {
        val lines = listOf(
            "{",
            "  \"items\": [",
            "    1,",
            "    2",
            "  ]",
            "}",
        )
        val regions = detectBraceFoldRegions(lines)
        assertEquals(2, regions.size)
        val arr = regions.first { it.startLine == 1 }
        assertEquals(4, arr.endLine)
        val obj = regions.first { it.startLine == 0 }
        assertEquals(5, obj.endLine)
    }

    // ── detectXmlFoldRegions ────────────────────────────────────

    @Test
    fun xmlFold_simple_element() {
        val lines = listOf(
            "<root>",
            "  <child>text</child>",
            "</root>",
        )
        val regions = detectXmlFoldRegions(lines)
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(2, regions[0].endLine)
    }

    @Test
    fun xmlFold_nested_elements() {
        val lines = listOf(
            "<root>",
            "  <parent>",
            "    <child>text</child>",
            "  </parent>",
            "</root>",
        )
        val regions = detectXmlFoldRegions(lines)
        assertEquals(2, regions.size)
    }

    @Test
    fun xmlFold_self_closing_no_fold() {
        val lines = listOf(
            "<root>",
            "  <br/>",
            "</root>",
        )
        val regions = detectXmlFoldRegions(lines)
        // Only root fold, <br/> should not create a fold
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(2, regions[0].endLine)
    }

    @Test
    fun xmlFold_void_element_no_fold() {
        val lines = listOf(
            "<div>",
            "  <img src=\"a.png\">",
            "  <input type=\"text\">",
            "</div>",
        )
        val regions = detectXmlFoldRegions(lines)
        // void elements (img, input) should not be pushed on the stack
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(3, regions[0].endLine)
    }

    @Test
    fun xmlFold_same_line_open_close_no_fold() {
        val lines = listOf("<root><child>text</child></root>")
        val regions = detectXmlFoldRegions(lines)
        assertEquals(0, regions.size, "Tags on the same line should not create folds")
    }

    @Test
    fun xmlFold_empty_lines() {
        val regions = detectXmlFoldRegions(emptyList())
        assertTrue(regions.isEmpty())
    }

    // ── detectCommentFoldRegions ────────────────────────────────

    @Test
    fun commentFold_c_style_block() {
        val lines = listOf(
            "/* comment start",
            " * middle",
            " */",
        )
        val regions = detectCommentFoldRegions(lines)
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(2, regions[0].endLine)
    }

    @Test
    fun commentFold_html_comment() {
        val lines = listOf(
            "<!-- start",
            "  middle",
            "-->",
        )
        val regions = detectCommentFoldRegions(lines)
        assertEquals(1, regions.size)
        assertEquals(0, regions[0].startLine)
        assertEquals(2, regions[0].endLine)
    }

    @Test
    fun commentFold_single_line_no_fold() {
        val lines = listOf("/* single line comment */")
        val regions = detectCommentFoldRegions(lines)
        assertEquals(0, regions.size, "Single-line block comment should not fold")
    }

    @Test
    fun commentFold_empty() {
        val regions = detectCommentFoldRegions(emptyList())
        assertTrue(regions.isEmpty())
    }

    // ── detectFoldRegions (language dispatcher) ─────────────────

    @Test
    fun detectFoldRegions_json_uses_braces() {
        val lines = listOf("{", "  \"a\": 1", "}")
        val regions = detectFoldRegions(lines, SyntaxLanguage.JSON)
        assertEquals(1, regions.size)
    }

    @Test
    fun detectFoldRegions_xml_uses_tags_and_comments() {
        val lines = listOf(
            "<root>",
            "  <!-- a",
            "    comment -->",
            "  <child>text</child>",
            "</root>",
        )
        val regions = detectFoldRegions(lines, SyntaxLanguage.XML)
        // root tag fold + comment fold = 2
        assertEquals(2, regions.size)
    }

    @Test
    fun detectFoldRegions_javascript_uses_braces_and_comments() {
        val lines = listOf(
            "function foo() {",
            "  /* multi-",
            "     line */",
            "  return 1;",
            "}",
        )
        val regions = detectFoldRegions(lines, SyntaxLanguage.JAVASCRIPT)
        // brace { } fold + comment fold = 2
        assertEquals(2, regions.size)
    }

    @Test
    fun detectFoldRegions_json_partial_invalid_keeps_brace_regions() {
        val lines = listOf(
            "{",
            "  \"outer\": {",
            "    \"incomplete\": ",
            "  ",
            "}",
        )
        val regions = detectFoldRegions(lines, SyntaxLanguage.JSON)
        assertTrue(regions.isNotEmpty(), "Partial JSON while typing should still keep fold regions")
        assertTrue(regions.any { it.startLine == 1 && it.endLine == 4 }, "Inner brace fold must remain visible")
    }

    @Test
    fun detectFoldRegions_plain_returns_empty() {
        val lines = listOf("line 1", "line 2", "line 3")
        val regions = detectFoldRegions(lines, SyntaxLanguage.PLAIN)
        assertTrue(regions.isEmpty())
    }

    @Test
    fun detectFoldRegions_fewer_than_3_lines_returns_empty() {
        val lines = listOf("{", "}")
        val regions = detectFoldRegions(lines, SyntaxLanguage.JSON)
        assertTrue(regions.isEmpty(), "Fewer than 3 lines should not generate folds")
    }

    // ── FoldState ───────────────────────────────────────────────

    @Test
    fun foldState_toggle() {
        val state = FoldState()
        assertFalse(state.isFolded(0))
        state.toggle(0)
        assertTrue(state.isFolded(0))
        state.toggle(0)
        assertFalse(state.isFolded(0))
    }

    @Test
    fun foldState_fold_and_unfold() {
        val state = FoldState()
        state.fold(5)
        assertTrue(state.isFolded(5))
        state.unfold(5)
        assertFalse(state.isFolded(5))
    }

    @Test
    fun foldState_fold_duplicate_no_double_add() {
        val state = FoldState()
        state.fold(3)
        state.fold(3)
        assertTrue(state.isFolded(3))
        // After one unfold it should be gone
        state.unfold(3)
        assertFalse(state.isFolded(3))
    }

    @Test
    fun foldState_foldAll_and_unfoldAll() {
        val regions = listOf(
            FoldRegion(0, 5),
            FoldRegion(7, 10),
            FoldRegion(12, 15),
        )
        val state = FoldState()
        state.foldAll(regions)
        assertTrue(state.isFolded(0))
        assertTrue(state.isFolded(7))
        assertTrue(state.isFolded(12))

        state.unfoldAll()
        assertFalse(state.isFolded(0))
        assertFalse(state.isFolded(7))
        assertFalse(state.isFolded(12))
    }

    // ── computeVisibleLines ─────────────────────────────────────

    @Test
    fun visibleLines_no_folds() {
        val lines = listOf("a", "b", "c")
        val state = FoldState()
        val visible = computeVisibleLines(lines, emptyList(), state)
        assertEquals(3, visible.size)
        assertEquals("a", visible[0].text)
        assertEquals("b", visible[1].text)
        assertEquals("c", visible[2].text)
        assertTrue(visible.all { !it.isFolded })
    }

    @Test
    fun visibleLines_single_fold() {
        val lines = listOf("{", "  \"a\": 1", "  \"b\": 2", "}")
        val regions = listOf(FoldRegion(0, 3))
        val state = FoldState()
        state.fold(0)

        val visible = computeVisibleLines(lines, regions, state)
        assertEquals(1, visible.size)   // only the opening line is visible
        assertEquals("{", visible[0].text)
        assertTrue(visible[0].isFolded)
        assertEquals(3, visible[0].foldedLineCount)
    }

    @Test
    fun visibleLines_unfolded_shows_all() {
        val lines = listOf("{", "  \"a\": 1", "}")
        val regions = listOf(FoldRegion(0, 2))
        val state = FoldState()  // nothing folded

        val visible = computeVisibleLines(lines, regions, state)
        assertEquals(3, visible.size)
        assertTrue(visible[0].isFoldStart)
        assertFalse(visible[0].isFolded)
    }

    @Test
    fun visibleLines_multiple_folds() {
        val lines = listOf(
            "{",     // 0  – fold 1 start
            "  a",   // 1
            "}",     // 2  – fold 1 end
            "[",     // 3  – fold 2 start
            "  b",   // 4
            "]",     // 5  – fold 2 end
        )
        val regions = listOf(FoldRegion(0, 2), FoldRegion(3, 5))
        val state = FoldState()
        state.fold(0)
        state.fold(3)

        val visible = computeVisibleLines(lines, regions, state)
        assertEquals(2, visible.size)
        assertEquals("{", visible[0].text)
        assertTrue(visible[0].isFolded)
        assertEquals("[", visible[1].text)
        assertTrue(visible[1].isFolded)
    }

    @Test
    fun visibleLines_fold_preserves_original_index() {
        val lines = listOf("line0", "{", "  inner", "}", "line4")
        val regions = listOf(FoldRegion(1, 3))
        val state = FoldState()
        state.fold(1)

        val visible = computeVisibleLines(lines, regions, state)
        assertEquals(3, visible.size)
        assertEquals(0, visible[0].originalIndex)   // line0
        assertEquals(1, visible[1].originalIndex)   // { (folded)
        assertEquals(4, visible[2].originalIndex)   // line4
    }

    @Test
    fun visibleLines_empty_input() {
        val visible = computeVisibleLines(emptyList(), emptyList(), FoldState())
        assertTrue(visible.isEmpty())
    }

    // ── Performance: large input ────────────────────────────────

    @Test
    fun braceFold_5000_line_json_does_not_crash() {
        val lines = mutableListOf<String>()
        lines.add("{")
        for (i in 1..5000) {
            lines.add("  \"key$i\": \"value$i\"${if (i < 5000) "," else ""}")
        }
        lines.add("}")
        val regions = detectFoldRegions(lines, SyntaxLanguage.JSON)
        assertTrue(regions.isNotEmpty(), "Large JSON must produce at least one fold region")
    }

    @Test
    fun xmlFold_2000_element_xml_does_not_crash() {
        val lines = mutableListOf<String>()
        lines.add("<root>")
        for (i in 1..2000) {
            lines.add("  <item>value$i</item>")
        }
        lines.add("</root>")
        val regions = detectFoldRegions(lines, SyntaxLanguage.XML)
        assertTrue(regions.isNotEmpty(), "Large XML must produce at least one fold region")
    }

    @Test
    fun computeVisibleLines_large_fold_does_not_crash() {
        val lines = mutableListOf<String>()
        lines.add("{")
        for (i in 1..5000) {
            lines.add("  \"key$i\": $i,")
        }
        lines.add("}")
        val regions = detectFoldRegions(lines, SyntaxLanguage.JSON)
        val state = FoldState()
        state.foldAll(regions)
        val visible = computeVisibleLines(lines, regions, state)
        // After folding all, only fold-start lines remain visible
        assertTrue(visible.size < lines.size, "Folded view must have fewer lines than source")
    }
}
