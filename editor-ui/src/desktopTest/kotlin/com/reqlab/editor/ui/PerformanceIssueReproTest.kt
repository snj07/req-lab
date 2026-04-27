package com.reqlab.editor.ui

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 *   CR-1  — EditorDisplayState must expose hasLineTruncation: no flag exists → compile fails
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
     * (scheduleInitialFolds → computeAndApplyFolds → emitFoldUpdate with hasLineTruncation)
     * to complete.
     */
    private fun vm(text: String, mode: LanguageMode = LanguageMode.JSON): EditorViewModel {
        val v = EditorViewModel(text, mode)
        // foldVersion increments when scheduleInitialFolds completes
        awaitUntil { v.state.value.foldVersion > 0 }
        return v
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CR-1 — hasLineTruncation flag in EditorDisplayState
    //
    // FAILS BEFORE FIX:
    //   EditorDisplayState data class does not contain 'hasLineTruncation'.
    //   Test will not compile until the field is added.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * CR-1-A: EditorDisplayState must compile with hasLineTruncation field.
     * BEFORE FIX → compile error (unresolved reference 'hasLineTruncation').
     * AFTER FIX  → compiles; default value is false.
     */
    @Test
    fun cr1_a_EditorDisplayState_has_hasLineTruncation_field_defaulting_to_false() {
        val state = EditorDisplayState()
        // If 'hasLineTruncation' doesn't exist this won't compile → test failure ✓
        assertFalse(state.hasLineTruncation,
            "Default EditorDisplayState must have hasLineTruncation = false")
    }

    /**
     * CR-1-B: Loading a single-line document whose length exceeds
     * EditorViewModel.DISPLAY_LINE_LENGTH_LIMIT (50 000 chars) must set
     * state.hasLineTruncation = true.
     *
     * BEFORE FIX → 'hasLineTruncation' field missing → compile error / test fails.
     * AFTER FIX  → flag is true.
     */
    @Test
    fun cr1_b_hasLineTruncation_is_true_when_single_line_exceeds_50K_chars() {
        // Build a single-line JSON-ish document that exceeds the render limit
        val longLine = "{\"data\":\"" + "x".repeat(50_100) + "\"}"
        val v = vm(longLine, LanguageMode.PLAIN_TEXT)

        assertTrue(v.state.value.hasLineTruncation,
            "A single line of ${longLine.length} chars must set hasLineTruncation = true " +
            "(limit is ${EditorViewModel.DISPLAY_LINE_LENGTH_LIMIT})")
    }

    /**
     * CR-1-C: A normal multi-line document where every line is short must NOT
     * set hasLineTruncation.
     *
     * BEFORE FIX → compile error.
     * AFTER FIX  → flag is false.
     */
    @Test
    fun cr1_c_hasLineTruncation_is_false_for_normal_multiline_document() {
        val text = (1..1_000).joinToString("\n") { "{ \"line\": $it }" }
        val v = vm(text)

        assertFalse(v.state.value.hasLineTruncation,
            "A multi-line document with lines < ${EditorViewModel.DISPLAY_LINE_LENGTH_LIMIT} " +
            "chars must have hasLineTruncation = false")
    }

    /**
     * CR-1-D: After onExternalTextChanged delivers a single-line large payload,
     * the state flag must update to true.
     *
     * BEFORE FIX → compile error on 'hasLineTruncation'.
     * AFTER FIX  → flag is updated correctly.
     */
    @Test
    fun cr1_d_hasLineTruncation_updates_after_onExternalTextChanged_with_long_line() {
        // Start with a short normal document
        val v = vm("{\"a\":1}", LanguageMode.JSON)
        assertFalse(v.state.value.hasLineTruncation, "Initial short doc must NOT be flagged")

        // Deliver a minified replacement that produces one very long line
        val minified = "{\"data\":\"" + "y".repeat(50_200) + "\"}"
        v.onExternalTextChanged(minified)
        awaitUntil { v.state.value.hasLineTruncation }

        assertTrue(v.state.value.hasLineTruncation,
            "After onExternalTextChanged with a long line, hasLineTruncation must be true")
    }

    /**
     * CR-1-E: After onExternalTextChanged replaces the long content with normal
     * multi-line content, the flag must revert to false.
     *
     * BEFORE FIX → compile error.
     * AFTER FIX  → flag reverts correctly.
     */
    @Test
    fun cr1_e_hasLineTruncation_reverts_to_false_when_long_line_replaced_by_normal_content() {
        val minified = "{\"data\":\"" + "z".repeat(50_500) + "\"}"
        val v = vm(minified, LanguageMode.PLAIN_TEXT)
        assertTrue(v.state.value.hasLineTruncation, "Long line must be flagged initially")

        // Replace with a normal multi-line document
        val normal = (1..20).joinToString("\n") { "line $it" }
        v.onExternalTextChanged(normal)
        awaitUntil { !v.state.value.hasLineTruncation }

        assertFalse(v.state.value.hasLineTruncation,
            "After replacing long content with normal multi-line, hasLineTruncation must be false")
    }

    /**
     * CR-1-F: EditorViewModel must expose DISPLAY_LINE_LENGTH_LIMIT as a
     * public constant so UI components can reference the same threshold.
     *
     * BEFORE FIX → unresolved reference → compile error.
     * AFTER FIX  → constant is accessible, equals 50_000.
     */
    @Test
    fun cr1_f_DISPLAY_LINE_LENGTH_LIMIT_constant_is_public_and_equals_50_000() {
        assertEquals(50_000, EditorViewModel.DISPLAY_LINE_LENGTH_LIMIT,
            "DISPLAY_LINE_LENGTH_LIMIT must be 50_000 to match LineView.MAX_RENDER_CHARS_PER_LINE")
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
