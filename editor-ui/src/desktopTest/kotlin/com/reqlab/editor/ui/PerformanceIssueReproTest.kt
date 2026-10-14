package com.reqlab.editor.ui

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that REPRODUCE issues identified in performance-test-report.md.
 *
 * TDD contract:
 *   - Every test in the "FAILING BEFORE FIX" sections MUST FAIL on the unmodified code.
 *   - Every test in the "REGRESSION GUARD" sections documents correct behaviour and passes
 *     both before and after the fix.
 *
 * Issues covered:
 *   CR-1  — oversized lines must remain complete through horizontal virtualization
 *   MN-2  — getSelectedText() must use GapBuffer.subSequence; regression guard for correctness
 *   BONUS — getFullText() must equal document.toFullString() at all times (regression guard)
 */
class PerformanceIssueReproTest {

    // ── Test helper ──────────────────────────────────────────────────────────

    /**
     * Polls [condition] every 50 ms until it returns true or [timeoutMs] elapses.
     */
    private fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
    }

    /**
     * Construct an EditorViewModel and wait for the background initialisation coroutine
     * (scheduleInitialFolds → computeAndApplyFolds → emitFoldUpdate)
     * to complete.
     */
    private fun vm(text: String, mode: LanguageMode = LanguageMode.JSON): EditorViewModel {
        val v = EditorViewModel(text, mode)
        // foldVersion increments when scheduleInitialFolds completes
        awaitUntil { v.state.value.foldVersion > 0 }
        return v
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CR-1 — complete oversized-line rendering through bounded windows
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun cr1_a_first_window_starts_at_document_column_zero() {
        val window = horizontalLineWindow(
            lineLength = 100_000,
            scrollPx = 0f,
            viewportWidthPx = 800,
            charWidthPx = 8f,
        )

        assertEquals(0, window.startColumn)
        assertTrue(window.endColumn < 100_000, "Only the bounded visible window should be laid out")
    }

    @Test
    fun cr1_b_middle_window_preserves_absolute_columns() {
        val window = horizontalLineWindow(
            lineLength = 100_000,
            scrollPx = 400_000f,
            viewportWidthPx = 800,
            charWidthPx = 8f,
        )

        assertTrue(50_000 in window.startColumn until window.endColumn)
        assertTrue(window.startColumn > 0)
    }

    @Test
    fun cr1_c_final_window_reaches_the_real_line_end() {
        val lineLength = 100_004
        val viewportWidth = 800
        val charWidth = 8f
        val finalScroll = lineLength * charWidth - viewportWidth
        val window = horizontalLineWindow(
            lineLength = lineLength,
            scrollPx = finalScroll,
            viewportWidthPx = viewportWidth,
            charWidthPx = charWidth,
        )

        assertEquals(lineLength, window.endColumn)
        assertTrue(window.startColumn < lineLength - 4, "The TAIL marker must be in the rendered window")
    }

    @Test
    fun cr1_d_wrapped_window_follows_a_caret_at_the_real_line_end() {
        val lineLength = 5_000_004
        val window = wrappedLineWindow(lineLength, anchorColumn = lineLength)

        assertEquals(lineLength, window.endColumn)
        assertTrue(window.endColumn - window.startColumn <= 128_000)
        assertTrue(window.startColumn < lineLength - 4, "The TAIL marker must remain reachable")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MN-2 — getSelectedText() regression guards
    //
    // Current code: toFullString().substring(s, e) — correct but allocates entire doc.
    // Fix: document.buffer.subSequence(s, e)       — same result, O(selection size).
    //
    // These tests verify CORRECTNESS and pass both before and after the fix.
    // They serve as regression guards to prove the optimisation doesn't break behaviour.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun mn2_getSelectedText_returns_correct_text_for_small_selection_in_large_doc() {
        val prefix = "Hello "
        val target = "World"
        val suffix = " Goodbye"
        val padding = "x".repeat(10_000)
        val text = padding + prefix + target + suffix + padding
        val v = vm(text, LanguageMode.PLAIN_TEXT)

        val selStart = padding.length + prefix.length
        val selEnd   = selStart + target.length
        v.moveCursorTo(selStart)
        v.moveCursorTo(selEnd, extendSelection = true)

        assertEquals(target, v.getSelectedText(),
            "getSelectedText() must return exactly the selected range '${target}'")
    }

    @Test
    fun mn2_getSelectedText_returns_empty_when_no_selection() {
        val v = vm("{\"a\":1}")
        v.moveCursorTo(3)  // cursor without selection
        assertEquals("", v.getSelectedText(),
            "getSelectedText() must return empty string when no selection is active")
    }

    @Test
    fun mn2_getSelectedText_returns_full_doc_after_selectAll() {
        val text = "{\"key\": \"value\"}"
        val v = vm(text, LanguageMode.JSON)
        v.selectAll()
        assertEquals(text, v.getSelectedText(),
            "getSelectedText() must return the full document text after selectAll()")
    }

    @Test
    fun mn2_getSelectedText_returns_correct_multiline_selection() {
        val text = "line one\nline two\nline three"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        // Select "one\nline two"
        val selStart = "line ".length
        val selEnd   = selStart + "one\nline two".length
        v.moveCursorTo(selStart)
        v.moveCursorTo(selEnd, extendSelection = true)

        assertEquals("one\nline two", v.getSelectedText(),
            "getSelectedText() must handle multi-line selections correctly")
    }

    @Test
    fun mn2_getSelectedText_at_document_end_boundary() {
        val text = "abcde"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(2)
        v.moveCursorTo(5, extendSelection = true)
        assertEquals("cde", v.getSelectedText(), "Selection at end boundary must be correct")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BONUS — getFullText / document consistency regression guards
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun bonus_getFullText_matches_document_toFullString_after_inserts() {
        val v = vm("{}", LanguageMode.JSON)
        v.moveCursorTo(1)
        v.insertAtCursor("\"a\":1")
        assertEquals(v.document.toFullString(), v.getFullText(),
            "getFullText() must always equal document.toFullString() after edits")
    }

    @Test
    fun bonus_getFullText_matches_document_toFullString_after_deletes() {
        val v = vm("{\"a\":1}", LanguageMode.JSON)
        v.moveCursorTo(7)
        v.deleteBeforeCursor()
        assertEquals(v.document.toFullString(), v.getFullText(),
            "getFullText() must equal document.toFullString() after a delete")
    }
}
