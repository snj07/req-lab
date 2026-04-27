package com.reqlab.editor.ui

import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.LanguageRegistry
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for two editor issues:
 *
 * ISSUE 1 — Stale diagnostics
 *   [Json.parseToJsonElement] is NOT coroutine-cancellable.  Without an editSequence
 *   guard, a slow validate() job from an OLD document can complete after the current
 *   document's job and overwrite the correct diagnostics with stale results.
 *   Fix: capture editSequence before the 500 ms delay and discard results if the
 *   sequence changed.
 *
 * ISSUE 2 — Collapse/expand arrow positions stale after editing
 *   [EditorViewModel.foldRegions] is only recomputed by [computeAndApplyFolds], which
 *   runs at construction and on [onExternalTextChanged] (i.e. after format).  Local
 *   keystrokes go through [applyReplace] which never updated [foldRegions], so the
 *   fold-arrow line numbers drift with every insert/delete that changes the line count.
 *   Fix (part A — immediate): shift fold-region line numbers in-place inside applyReplace,
 *   bump foldVersion so the renderer re-reads them.
 *   Fix (part B — structural): schedule a debounced background recompute that detects
 *   newly added or removed structural blocks (new {…} objects, etc.).
 */
class DiagnosticsAndFoldUpdateTest {

    @BeforeTest
    fun setup() {
        // Required so JsonMode (not PlainTextMode) handles validation in tests
        LanguageRegistry.registerBuiltins()
    }

    private fun vm(text: String, mode: LanguageMode = LanguageMode.JSON): EditorViewModel {
        val v = EditorViewModel(text, mode)
        // Wait for scheduleInitialFolds (Dispatchers.Default) to fully complete.
        // foldVersion is incremented by emitFoldUpdate() which runs AFTER computeAndApplyFolds(),
        // so foldVersion > 0 guarantees foldRegions is populated AND the StateFlow write
        // establishes a happens-before that makes foldRegions visible to this thread.
        awaitUntil(description = "initial fold pass") {
            v.state.value.foldVersion > 0 || v.document.length == 0
        }
        return v
    }

    /**
     * Polls [condition] every 50 ms until it returns true or [timeoutMs] elapses.
     * More reliable than a fixed delay on slow CI machines.
     */
    private fun awaitUntil(
        timeoutMs: Long = 10_000,
        description: String = "condition",
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(condition(), "Timed out waiting for $description after ${timeoutMs}ms")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ISSUE 1 — Diagnostic correctness (regression guards)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Baseline: invalid JSON must produce at least one diagnostic after the 500 ms debounce.
     */
    @Test
    fun diag1_invalid_json_produces_diagnostics_after_debounce() {
        // Construct empty, then load content via onExternalTextChanged to trigger
        // the full pipeline (scheduleDiagnostics is only called from onExternalTextChanged
        // and applyReplace, not from the constructor).
        val v = vm("", LanguageMode.JSON)
        v.onExternalTextChanged("{invalid}")
        awaitUntil { v.state.value.diagnostics.isNotEmpty() }
        assertTrue(v.state.value.diagnostics.isNotEmpty(),
            "Invalid JSON must produce diagnostics after the debounce period")
    }

    /**
     * Baseline: valid JSON must produce no diagnostics.
     */
    @Test
    fun diag2_valid_json_produces_no_diagnostics() {
        val v = vm("", LanguageMode.JSON)
        v.onExternalTextChanged("{\"a\":1}")
        // Wait for the debounce to fire; diagnostics should stay empty for valid JSON.
        // We wait 1500ms (3x debounce) — no condition to poll since we expect emptiness.
        Thread.sleep(1500)
        assertTrue(v.state.value.diagnostics.isEmpty(),
            "Valid JSON must have no diagnostics")
    }

    /**
     * After replacing an invalid document with valid JSON, the final diagnostics must
     * be empty — stale error results from the OLD validate() job must not overwrite the
     * clean state of the new document.
     *
     * This guards against the stale-diagnostics race: Json.parseToJsonElement is not
     * coroutine-cancellable, so even after diagnosticsJob?.cancel() the old parse can
     * complete and call _state.update with wrong results.  The editSequence guard in
     * scheduleDiagnostics prevents this.
     */
    @Test
    fun diag3_stale_results_do_not_overwrite_after_replacement_with_valid_json() {
        val v = vm("", LanguageMode.JSON)
        v.onExternalTextChanged("{invalid}")
        Thread.sleep(100)   // < 500 ms: diagnostics NOT yet fired

        // Replace with valid JSON — this must bump editSequence so the old job is discarded
        v.onExternalTextChanged("{\"a\":1}")
        // Wait for both jobs to complete: the cancelled old job + the new valid-JSON job.
        Thread.sleep(2_000)

        assertTrue(v.state.value.diagnostics.isEmpty(),
            "After replacing invalid JSON with valid JSON, diagnostics must be empty. " +
            "Stale errors from the old validate() must not land.")
    }

    /**
     * After inserting invalid syntax into a valid document, diagnostics must eventually
     * reflect the error.
     */
    @Test
    fun diag4_diagnostics_update_after_inserting_invalid_syntax() {
        val v = vm("", LanguageMode.JSON)
        v.onExternalTextChanged("{\"a\":1}")
        // Wait for initial diagnostics to settle (expect empty for valid JSON).
        Thread.sleep(1500)
        assertTrue(v.state.value.diagnostics.isEmpty(), "precondition: start valid")

        // Corrupt the JSON by inserting invalid characters after the opening brace
        v.moveCursorTo(1)
        v.insertAtCursor("!!!")
        awaitUntil { v.state.value.diagnostics.isNotEmpty() }

        assertTrue(v.state.value.diagnostics.isNotEmpty(),
            "diagnostics must show an error after inserting invalid syntax")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ISSUE 2 — Fold arrow positions update after editing
    //
    // Tests fold1–fold5 FAIL before the fix because applyReplace() does not
    // update foldRegions.  Test fold6 FAILS before the fix because there is no
    // scheduleFoldRegionsUpdate() background recompute.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * FOLD-1: When one line is inserted BEFORE a fold-region's start line,
     * the region's startLine and endLine must both shift by +1 immediately (no sleep).
     *
     * BEFORE FIX → foldRegions not updated in applyReplace → test fails.
     * AFTER FIX  → immediate line-number shift in applyReplace → test passes.
     */
    @Test
    fun fold1_arrow_shifts_immediately_after_inserting_line_before_region() {
        val v = vm("{\n  \"a\": 1\n}")
        awaitUntil(description = "initial fold regions for fold1") { v.foldRegions.isNotEmpty() }
        assertTrue(v.foldRegions.isNotEmpty(), "precondition: initial fold region detected")
        val originalStart = v.foldRegions.first().startLine   // 1-based, expect 1
        val originalEnd   = v.foldRegions.first().endLine     // 1-based, expect 3

        // Insert one new line before the { (at offset 0 = start of document)
        v.moveCursorTo(0)
        v.insertAtCursor("// header\n")
        // NO Thread.sleep — the shift is synchronous inside applyReplace

        assertTrue(v.foldRegions.isNotEmpty(), "fold region must still exist after insert")
        assertEquals(originalStart + 1, v.foldRegions.first().startLine,
            "startLine must shift by +1 after inserting a line before the region; " +
            "expected ${originalStart + 1}, got ${v.foldRegions.first().startLine}")
        assertEquals(originalEnd + 1, v.foldRegions.first().endLine,
            "endLine must also shift by +1")
    }

    /**
     * FOLD-2: Inserting three lines before a fold region shifts it by exactly three.
     *
     * BEFORE FIX → fails.
     * AFTER FIX  → passes.
     */
    @Test
    fun fold2_arrow_shifts_by_correct_delta_for_multi_line_insert() {
        val v = vm("{\n  \"a\": 1\n}")
        awaitUntil(description = "initial fold regions for fold2") { v.foldRegions.isNotEmpty() }
        assertTrue(v.foldRegions.isNotEmpty(), "precondition")
        val originalStart = v.foldRegions.first().startLine

        v.moveCursorTo(0)
        v.insertAtCursor("line1\nline2\nline3\n")   // lineDelta = 3

        assertEquals(originalStart + 3, v.foldRegions.first().startLine,
            "startLine must shift by 3 after inserting 3 lines before the region")
    }

    /**
     * FOLD-3: Deleting a line before a fold region shifts it by -1.
     *
     * BEFORE FIX → fails.
     * AFTER FIX  → passes.
     */
    @Test
    fun fold3_arrow_shifts_after_deleting_line_before_region() {
        // "// comment\n" = 11 chars (indices 0..10)
        val v = vm("// comment\n{\n  \"a\": 1\n}")
        awaitUntil(description = "initial fold regions for fold3") { v.foldRegions.isNotEmpty() }
        assertTrue(v.foldRegions.isNotEmpty(), "precondition")
        val originalStart = v.foldRegions.first().startLine   // 2-based (1-indexed { is on line 2)

        // Select and delete the first line "// comment\n"
        v.moveCursorTo(0)
        v.moveCursorTo(11, extendSelection = true)
        v.deleteSelection()

        assertEquals(originalStart - 1, v.foldRegions.first().startLine,
            "startLine must shift by -1 after deleting the line before the region; " +
            "expected ${originalStart - 1}, got ${v.foldRegions.first().startLine}")
    }

    /**
     * FOLD-4: Inserting a line INSIDE a fold region (after startLine) shifts only endLine.
     *
     * BEFORE FIX → fails.
     * AFTER FIX  → only endLine changes, startLine stays.
     */
    @Test
    fun fold4_only_endLine_shifts_when_line_inserted_inside_region() {
        // Line 0: {   (fold header, startLine=1 in 1-based)
        // Line 1:   "a": 1
        // Line 2: }   (fold footer, endLine=3)
        val v = vm("{\n  \"a\": 1\n}")
        awaitUntil(description = "initial fold regions for fold4") { v.foldRegions.isNotEmpty() }
        assertTrue(v.foldRegions.isNotEmpty(), "precondition")
        val originalStart = v.foldRegions.first().startLine   // expect 1
        val originalEnd   = v.foldRegions.first().endLine     // expect 3

        // Insert a line at the start of line 1 (inside the region, after the { header)
        val lineOneStart = v.document.lineStart(1)   // offset of "  \"a\": 1"
        v.moveCursorTo(lineOneStart)
        v.insertAtCursor("  // inserted\n")

        assertEquals(originalStart, v.foldRegions.first().startLine,
            "startLine must NOT shift (insert is inside the region, after the header)")
        assertEquals(originalEnd + 1, v.foldRegions.first().endLine,
            "endLine must shift by +1 when inserting a line inside the region")
    }

    /**
     * FOLD-5: foldVersion in EditorDisplayState must increment when fold region
     * positions change, so the renderer knows to re-read foldRegions.
     *
     * BEFORE FIX → foldVersion does not change on insert → test fails.
     * AFTER FIX  → foldVersion increments → test passes.
     */
    @Test
    fun fold5_foldVersion_increments_when_line_inserted_before_region() {
        val v = vm("{\n  \"a\": 1\n}")
        awaitUntil(description = "initial fold regions for fold5") { v.foldRegions.isNotEmpty() }
        assertTrue(v.foldRegions.isNotEmpty(), "precondition")
        val versionBefore = v.state.value.foldVersion

        v.moveCursorTo(0)
        v.insertAtCursor("new line\n")   // lineDelta = 1

        assertTrue(v.state.value.foldVersion > versionBefore,
            "foldVersion must increment when fold region positions are adjusted; " +
            "was $versionBefore, still ${v.state.value.foldVersion}")
    }

    /**
     * FOLD-6: After typing a new structural block (new {…}), a background recompute
     * must detect the new foldable region within ~500 ms.
     *
     * BEFORE FIX → no scheduleFoldRegionsUpdate → new regions never detected → test fails.
     * AFTER FIX  → background recompute runs after 300 ms debounce → test passes.
     */
    @Test
    fun fold6_background_recompute_detects_newly_added_structural_block() {
        val v = vm("{\n  \"a\": 1\n}")
        awaitUntil(description = "initial fold regions for fold6") { v.foldRegions.isNotEmpty() }
        val initialCount = v.foldRegions.size   // expect 1

        // Append a second top-level foldable block ("[\n  ...\n]")
        v.moveCursorTo(v.document.length)
        v.insertAtCursor("\n[\n  1,\n  2\n]")

        awaitUntil { v.foldRegions.size > initialCount }

        assertTrue(v.foldRegions.size > initialCount,
            "after inserting a new foldable block, foldRegions.size must grow after " +
            "background recompute; was $initialCount, got ${v.foldRegions.size}")
    }
}
