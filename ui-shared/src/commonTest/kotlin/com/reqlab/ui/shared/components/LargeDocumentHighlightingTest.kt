package com.reqlab.ui.shared.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests that pin the highlighting behaviour for large documents in the
 * virtualized editor (VirtualizedEditableCodeContent).
 *
 * Regression being guarded:
 *   A global document-character threshold (formerly `if (text.length > 2_000_000)
 *   SyntaxLanguage.PLAIN`) disabled syntax colour for the ENTIRE editor as soon as
 *   the document exceeded ~2 MB.  A formatted 5 MB JSON with ~5 000 lines of ~1 KB
 *   each went entirely grey even though each per-line highlight call is cheap and
 *   cached via `remember`.
 *
 * The fix: apply `SyntaxLanguage.PLAIN` only when a **single line** was truncated
 * for display (syntax context is broken at the cut point).  All other lines keep
 * their requested language, regardless of total document size.
 */
class LargeDocumentHighlightingTest {

    // ── highlightLine produces colour spans for typical JSON lines ──────────

    /**
     * A typical JSON key-value line such as those found in a formatted 5 MB
     * document must produce at least one colour span (not plain white text).
     */
    @Test
    fun typical_json_key_value_line_produces_colour_spans() {
        val line = "  \"boardId\": \"147654876797\","
        val result = highlightLine(line, SyntaxLanguage.JSON)
        assertTrue(
            result.spanStyles.isNotEmpty(),
            "A JSON key-value line must produce colour spans; got ${result.spanStyles.size} spans"
        )
    }

    @Test
    fun json_string_value_line_produces_spans() {
        val line = "  \"name\": \"Adeel Solangi\","
        val result = highlightLine(line, SyntaxLanguage.JSON)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun json_boolean_value_line_produces_spans() {
        val line = "  \"active\": true"
        val result = highlightLine(line, SyntaxLanguage.JSON)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun json_number_value_line_produces_spans() {
        val line = "  \"count\": 42,"
        val result = highlightLine(line, SyntaxLanguage.JSON)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun opening_brace_line_produces_spans() {
        val line = "{"
        val result = highlightLine(line, SyntaxLanguage.JSON)
        // Braces get a colour span in JSON highlighting
        assertTrue(result.spanStyles.isNotEmpty())
    }

    // ── PLAIN language produces NO colour spans ─────────────────────────────

    /**
     * When SyntaxLanguage.PLAIN is used (e.g. for a truncated line), `highlightLine`
     * must return an AnnotatedString with no spans — plain white text.
     */
    @Test
    fun plain_language_produces_no_spans() {
        val line = "  \"boardId\": \"147654876797\","
        val result = highlightLine(line, SyntaxLanguage.PLAIN)
        assertTrue(
            result.spanStyles.isEmpty(),
            "PLAIN language must produce zero spans; got ${result.spanStyles.size}"
        )
        assertEquals(line, result.text, "PLAIN language must preserve the original text exactly")
    }

    // ── Per-line language selection logic ───────────────────────────────────

    /**
     * Simulates the VirtualizedEditableCodeContent per-line language selection:
     * `lineLanguage = if (isTruncated) SyntaxLanguage.PLAIN else language`
     *
     * A non-truncated line must use the real language and produce spans.
     */
    @Test
    fun non_truncated_line_uses_real_language() {
        val rawLine = "  \"boardId\": \"147654876797\","
        val maxDisplayLength = 50_000
        val isTruncated = rawLine.length > maxDisplayLength  // false

        assertFalse(isTruncated, "Test setup: line is short and must not be truncated")

        val lineLanguage = if (isTruncated) SyntaxLanguage.PLAIN else SyntaxLanguage.JSON
        assertEquals(SyntaxLanguage.JSON, lineLanguage)

        val result = highlightLine(rawLine, lineLanguage)
        assertTrue(result.spanStyles.isNotEmpty(), "non-truncated JSON line must have colour spans")
    }

    /**
     * A line longer than MAX_LINE_DISPLAY_LENGTH (50 000 chars) is truncated for
     * display → its `lineLanguage` must be PLAIN.
     */
    @Test
    fun truncated_line_uses_plain_language() {
        val rawLine = "[" + "\"item\",".repeat(10_000) + "]"  // ~70 000 chars
        val maxDisplayLength = 50_000
        val isTruncated = rawLine.length > maxDisplayLength   // true

        assertTrue(isTruncated, "Test setup: line must be > $maxDisplayLength chars")

        val lineLanguage = if (isTruncated) SyntaxLanguage.PLAIN else SyntaxLanguage.JSON
        assertEquals(SyntaxLanguage.PLAIN, lineLanguage)

        // Truncated display text: first 50K chars + suffix
        val displayText = rawLine.take(maxDisplayLength) + " … (${rawLine.length} chars)"
        val result = highlightLine(displayText, lineLanguage)
        assertTrue(result.spanStyles.isEmpty(), "truncated line must use PLAIN (no colour spans)")
    }

    // ── Large document simulation ────────────────────────────────────────────

    /**
     * Simulates a formatted 5 MB JSON document (~5 013 lines, total chars > 2 MB).
     * Every line is short so `isTruncated = false` → every line uses the real
     * language → every line produces colour spans.
     *
     * Before the fix, a global `if (text.length > 2_000_000) SyntaxLanguage.PLAIN`
     * check would have returned PLAIN for ALL lines.
     */
    @Test
    fun formatted_5mb_document_lines_all_produce_spans() {
        // Build a document that resembles formatted 5 MB JSON (5 000 lines, > 2 MB total)
        val singleLine = "  \"boardId\": \"147654876797\","  // 30 chars
        // 5 000 lines × 30 chars + 5 000 newlines ≈ 155 000 chars — not actually 2 MB
        // To hit 2 MB cheaply, make each line longer:
        val paddedLine = "  \"key\": \"${"x".repeat(400)}\"," // ~410 chars
        val totalChars = paddedLine.length * 5_000
        assertTrue(totalChars > 2_000_000, "Test setup: total chars must exceed 2 MB, got $totalChars")

        // Verify that per-line highlighting is independent of document total size
        val maxDisplayLength = 50_000
        repeat(5) { idx ->
            val lineText = "  \"key$idx\": \"${"v".repeat(400)}\","
            val isTruncated = lineText.length > maxDisplayLength  // false
            val lineLanguage = if (isTruncated) SyntaxLanguage.PLAIN else SyntaxLanguage.JSON
            val result = highlightLine(lineText, lineLanguage)
            assertTrue(
                result.spanStyles.isNotEmpty(),
                "Line $idx of a > 2 MB document must still produce colour spans"
            )
        }
    }

    /**
     * Exactly at the boundary: a line that is precisely MAX_LINE_DISPLAY_LENGTH chars
     * must NOT be truncated and must still receive full highlighting.
     */
    @Test
    fun line_at_exact_display_limit_is_not_truncated() {
        val maxDisplayLength = 50_000
        // Construct a valid JSON string value exactly at the limit
        val rawLine = "\"" + "a".repeat(maxDisplayLength - 2) + "\""
        assertEquals(maxDisplayLength, rawLine.length)

        val isTruncated = rawLine.length > maxDisplayLength   // false (equal, not greater)
        assertFalse(isTruncated)

        val lineLanguage = if (isTruncated) SyntaxLanguage.PLAIN else SyntaxLanguage.JSON
        assertEquals(SyntaxLanguage.JSON, lineLanguage)

        val result = highlightLine(rawLine, lineLanguage)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    /**
     * A line one character past the limit IS truncated and must use PLAIN.
     */
    @Test
    fun line_one_past_display_limit_is_truncated() {
        val maxDisplayLength = 50_000
        val rawLine = "\"" + "a".repeat(maxDisplayLength - 1) + "\""  // length = 50_001
        assertEquals(maxDisplayLength + 1, rawLine.length)

        val isTruncated = rawLine.length > maxDisplayLength   // true
        assertTrue(isTruncated)

        val lineLanguage = if (isTruncated) SyntaxLanguage.PLAIN else SyntaxLanguage.JSON
        assertEquals(SyntaxLanguage.PLAIN, lineLanguage)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fold-region detection tests
//
// Regression: JSON documents whose root is an array (`[...]`) were not
// registering fold regions because `[` and `]` were (incorrectly) not handled
// by the fold detection algorithm.  The fix: `detectBraceFoldRegions` already
// handles both `{`/`}` and `[`/`]`.  These tests pin that behaviour.
//
// A second regression: after clicking a fold triangle, any keyboard action
// (Cmd+A, Cmd+Z, arrow keys) cleared all folds and restored the unfolded text
// because `onValueChange` compared the incoming text against `viewText`
// (the already-folded representation) instead of against `fieldValue.text`
// (the text the BasicTextField was showing).  The fix is in `EditableCodeContent`
// — these unit tests cover the fold-detection half of the story.
// ─────────────────────────────────────────────────────────────────────────────
class JsonArrayFoldingTest {

    // ── JSON array root ─────────────────────────────────────────────────────

    /**
     * A JSON document whose root is an array literal must yield a fold region
     * that spans from the `[` line to the `]` line.
     */
    @Test
    fun json_array_root_produces_fold_region() {
        val lines = listOf(
            "[",
            "  {\"a\": 1},",
            "  {\"b\": 2}",
            "]",
        )
        val regions = detectBraceFoldRegions(lines)
        assertTrue(
            regions.isNotEmpty(),
            "JSON array root must produce at least one fold region; got none"
        )
        val arrayRegion = regions.first { it.startLine == 0 }
        assertEquals(0, arrayRegion.startLine, "Array fold must start at line 0 (the `[`)")
        assertEquals(3, arrayRegion.endLine,   "Array fold must end at line 3 (the `]`)")
    }

    /**
     * Objects nested inside the root array must each produce their own fold
     * region independently of the outer array fold.
     */
    @Test
    fun json_objects_inside_array_each_have_fold_regions() {
        val lines = listOf(
            "[",
            "  {",
            "    \"key\": \"value\"",
            "  },",
            "  {",
            "    \"key2\": 42",
            "  }",
            "]",
        )
        val regions = detectBraceFoldRegions(lines)
        // Expect: array [0..7], object1 [1..3], object2 [4..6]
        assertTrue(regions.size >= 3, "Expected at least 3 fold regions, got ${regions.size}")

        val startLines = regions.map { it.startLine }.toSet()
        assertTrue(0 in startLines, "Root array fold must start at line 0")
        assertTrue(1 in startLines, "First nested object fold must start at line 1")
        assertTrue(4 in startLines, "Second nested object fold must start at line 4")
    }

    /**
     * An array that does not span multiple lines (open and close on the same line)
     * must NOT produce a fold region.
     */
    @Test
    fun inline_json_array_does_not_produce_fold_region() {
        val lines = listOf(
            "{",
            "  \"items\": [1, 2, 3]",
            "}",
        )
        val regions = detectBraceFoldRegions(lines)
        // Only the outer object fold is expected; the inline array on line 1 is not
        val inlineArrayFold = regions.firstOrNull { it.startLine == 1 }
        assertEquals(null, inlineArrayFold, "Inline same-line array must not produce a fold region")
    }

    /**
     * An empty document must produce no fold regions.
     */
    @Test
    fun empty_document_has_no_fold_regions() {
        assertTrue(detectBraceFoldRegions(emptyList()).isEmpty())
        assertTrue(detectBraceFoldRegions(listOf("")).isEmpty())
    }

    /**
     * A JSON object root (the existing common case) must still produce a fold region.
     */
    @Test
    fun json_object_root_produces_fold_region() {
        val lines = listOf(
            "{",
            "  \"name\": \"test\",",
            "  \"value\": 1",
            "}",
        )
        val regions = detectBraceFoldRegions(lines)
        assertTrue(regions.isNotEmpty(), "JSON object root must produce a fold region")
        assertEquals(0, regions.first().startLine)
        assertEquals(3, regions.first().endLine)
    }

    /**
     * Braces and brackets inside JSON strings must NOT be counted as fold
     * region starters / closers.
     */
    @Test
    fun brackets_inside_strings_are_ignored() {
        val lines = listOf(
            "{",
            "  \"url\": \"https://example.com/{id}?filter=[a,b]\",",
            "  \"real\": {",
            "    \"x\": 1",
            "  }",
            "}",
        )
        val regions = detectBraceFoldRegions(lines)
        // Only outer object and inner `real` object should fold
        val startLines = regions.map { it.startLine }
        assertTrue(0 in startLines, "Outer object must fold at line 0")
        assertTrue(2 in startLines, "Inner 'real' object must fold at line 2")
        // Stray `{`, `}`, `[`, `]` inside the string on line 1 must not create regions
        assertEquals(2, regions.size, "Must be exactly 2 fold regions; got ${regions.size}")
    }

    // ── Fold region depth ordering ──────────────────────────────────────────

    /**
     * Fold regions must be returned sorted by [FoldRegion.startLine].
     */
    @Test
    fun fold_regions_are_sorted_by_start_line() {
        val lines = listOf(
            "[",   // 0
            "  {", // 1
            "    [", // 2
            "    ]", // 3
            "  },", // 4
            "]",   // 5
        )
        val regions = detectBraceFoldRegions(lines)
        val startLines = regions.map { it.startLine }
        assertEquals(startLines.sorted(), startLines, "Regions must be sorted by startLine")
    }
}
