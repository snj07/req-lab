package com.reqlab.editor.ui

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for new [EditorViewModel] methods added to fix issues in qa-pre-release-report.md.
 *
 * Tests in this file FAIL before the fixes are applied.
 *
 * Issues covered:
 *  C-2 — Fold state preserved through edits
 *  M-1 — moveCursorPageUp / moveCursorPageDown
 *  M-2 — dedentAtCursor (Shift+Tab)
 *  M-3 — deleteWordBeforeCursor (Cmd/Ctrl+Backspace)
 *  M-4 — insertNewlineWithAutoIndent (Enter keeping indentation)
 */
class EditorViewModelFixTest {

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

    private fun vm(text: String, mode: LanguageMode = LanguageMode.JSON): EditorViewModel {
        val v = EditorViewModel(text, mode)
        // Wait for scheduleInitialFolds (Dispatchers.Default) to populate foldRegions.
        awaitUntil { v.state.value.foldVersion > 0 }
        return v
    }

    // ═══════════════════════════════════════════════════════════════════════
    // C-2 — Fold state preserved through edits
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * BEFORE FIX: calling insertAtCursor after toggleFold loses the fold.
     * AFTER FIX:  fold state is preserved across same-line edits.
     */
    @Test
    fun c2_fold_state_preserved_after_same_line_edit() {
        val text = "{\n  \"a\": 1,\n  \"b\": 2\n}"
        val v = vm(text)
        // Directly fold lines 1..3 under line 0, bypassing the background
        // foldRegions coroutine that does not run in unit tests.
        v.displayLineMap.setFolded(0, v.document.lineCount - 1)
        // Verify the fold is now active (visible lines should be reduced)
        val visibleAfterFold = v.displayLineMap.totalDisplayLines
        assertTrue(visibleAfterFold < v.document.lineCount,
            "After folding, totalDisplayLines ($visibleAfterFold) must be < lineCount (${v.document.lineCount})")

        // Move cursor to line 1 col 0 and add a space — same-line edit
        v.moveCursorTo(v.document.lineStart(1))
        v.insertAtCursor(" ")

        // BEFORE FIX: this assert fails because applyReplace() calls displayLineMap.reset()
        assertTrue(v.displayLineMap.totalDisplayLines < v.document.lineCount,
            "Fold must survive a same-line insert. Got totalDisplayLines=${v.displayLineMap.totalDisplayLines}, lineCount=${v.document.lineCount}")
    }

    @Test
    fun c2_fold_state_preserved_after_single_char_delete() {
        val text = "{\n  \"key\": \"value\"\n}"
        val v = vm(text)
        v.displayLineMap.setFolded(0, v.document.lineCount - 1)
        val visibleAfterFold = v.displayLineMap.totalDisplayLines

        // Delete one character (backspace at the end)
        v.moveCursorTo(v.document.length - 1)
        v.deleteBeforeCursor()

        assertTrue(v.displayLineMap.totalDisplayLines < v.document.lineCount,
            "Fold must survive a single-char delete")
    }

    @Test
    fun c2_multiple_folds_preserved_after_edit() {
        // 7-line structured JSON with two fold-able sections
        val text = "{\n  \"a\": {\n    \"x\": 1\n  },\n  \"b\": {\n    \"y\": 2\n  }\n}"
        val v = vm(text)
        v.displayLineMap.setFolded(1, 3)  // fold "a": {...} — hides lines 2..3
        v.displayLineMap.setFolded(4, 6)  // fold "b": {...} — hides lines 5..6
        val visibleAfterFolds = v.displayLineMap.totalDisplayLines

        // Append a trailing space to the last line (same-line, no line count change)
        v.moveCursorTo(v.document.length)
        v.insertAtCursor(" ")

        assertEquals(visibleAfterFolds, v.displayLineMap.totalDisplayLines,
            "Both folds must survive after a trailing-space insert")
    }

    @Test
    fun c2_fold_state_adjusted_when_newline_inserted_before_fold() {
        // Lines: 0 = "{", 1 = "  \"a\": 1", 2 = "}"
        val text = "{\n  \"a\": 1\n}"
        val v = vm(text)
        v.displayLineMap.setFolded(0, v.document.lineCount - 1)  // fold lines 1..2 under line 0
        val visibleAfterFold = v.displayLineMap.totalDisplayLines  // should be 1

        // Insert a newline BEFORE the fold start (at position 0, before "{")
        v.moveCursorTo(0)
        v.insertAtCursor("\n")

        // After fix: fold region shifts to line 1..3; still folded
        // The total display lines should be 2 (the inserted blank line + fold header)
        assertTrue(v.displayLineMap.totalDisplayLines <= 3,
            "Fold must be re-anchored after newline insert before it. Got ${v.displayLineMap.totalDisplayLines}")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // M-1 — moveCursorPageUp / moveCursorPageDown
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun m1_moveCursorPageDown_moves_cursor_down_by_page_size() {
        // Build a 50-line document
        val text = (1..50).joinToString("\n") { "line $it" }
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(0)

        // BEFORE FIX: method does not exist → compilation error
        v.moveCursorPageDown(pageSize = 10)

        val cursorLine = v.document.lineAt(v.state.value.cursorOffset)
        assertEquals(10, cursorLine,
            "PageDown by 10 from line 0 must land on line 10, got $cursorLine")
    }

    @Test
    fun m1_moveCursorPageUp_moves_cursor_up_by_page_size() {
        val text = (1..50).joinToString("\n") { "line $it" }
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        // Place cursor on line 30
        v.moveCursorTo(v.document.lineStart(30))

        v.moveCursorPageUp(pageSize = 10)

        val cursorLine = v.document.lineAt(v.state.value.cursorOffset)
        assertEquals(20, cursorLine,
            "PageUp by 10 from line 30 must land on line 20, got $cursorLine")
    }

    @Test
    fun m1_moveCursorPageDown_clamps_at_document_end() {
        val text = (1..5).joinToString("\n") { "line $it" }
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(0)
        v.moveCursorPageDown(pageSize = 100)  // way beyond document end
        assertEquals(v.document.length, v.state.value.cursorOffset,
            "PageDown past document end must clamp to document end")
    }

    @Test
    fun m1_moveCursorPageUp_clamps_at_document_start() {
        val text = (1..5).joinToString("\n") { "line $it" }
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(v.document.length)
        v.moveCursorPageUp(pageSize = 100)  // way beyond document start
        assertEquals(0, v.state.value.cursorOffset,
            "PageUp past document start must clamp to position 0")
    }

    @Test
    fun m1_moveCursorPageDown_extends_selection_when_shift_pressed() {
        val text = (1..20).joinToString("\n") { "line $it" }
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(0)
        v.moveCursorPageDown(pageSize = 5, extendSelection = true)
        val st = v.state.value
        assertTrue(st.selectionStart >= 0, "Shift+PageDown must start a selection")
        assertTrue(st.selectionEnd > st.selectionStart, "Selection must be non-empty")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // M-2 — dedentAtCursor (Shift+Tab)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun m2_dedentAtCursor_removes_leading_two_spaces() {
        val text = "  hello"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(2) // cursor after the two leading spaces

        // BEFORE FIX: method does not exist
        v.dedentAtCursor()

        assertEquals("hello", v.getFullText(), "dedentAtCursor must remove 2 leading spaces")
    }

    @Test
    fun m2_dedentAtCursor_removes_tab_as_single_indent_unit() {
        val text = "\thello"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(1)
        v.dedentAtCursor()
        assertEquals("hello", v.getFullText(), "dedentAtCursor must remove a leading tab")
    }

    @Test
    fun m2_dedentAtCursor_does_nothing_on_non_indented_line() {
        val text = "hello"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(3)
        v.dedentAtCursor()
        assertEquals("hello", v.getFullText(), "dedentAtCursor must not modify a non-indented line")
    }

    @Test
    fun m2_dedentAtCursor_removes_only_one_indent_level() {
        val text = "    hello"  // 4 spaces
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(4)
        v.dedentAtCursor()
        assertEquals("  hello", v.getFullText(),
            "dedentAtCursor must remove exactly one indent level (2 spaces), leaving 2")
    }

    @Test
    fun m2_dedentAtCursor_on_multiline_cursor_on_second_line() {
        val text = "line1\n  line2"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(v.document.lineStart(1) + 2)  // on line 1
        v.dedentAtCursor()
        assertEquals("line1\nline2", v.getFullText(),
            "dedentAtCursor on second line must dedent that line only")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // M-3 — deleteWordBeforeCursor (Cmd/Ctrl+Backspace)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun m3_deleteWordBeforeCursor_deletes_previous_word() {
        val text = "hello world"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(11)  // end of "world"

        // BEFORE FIX: method does not exist
        v.deleteWordBeforeCursor()

        assertEquals("hello ", v.getFullText(),
            "deleteWordBeforeCursor must delete 'world'")
    }

    @Test
    fun m3_deleteWordBeforeCursor_skips_whitespace_then_deletes_word() {
        val text = "foo   bar"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(9)  // end
        v.deleteWordBeforeCursor()
        assertEquals("foo   ", v.getFullText(),
            "deleteWordBeforeCursor must delete 'bar' (skipping no extra whitespace in this position)")
    }

    @Test
    fun m3_deleteWordBeforeCursor_at_start_does_nothing() {
        val text = "hello"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(0)
        v.deleteWordBeforeCursor()
        assertEquals("hello", v.getFullText(), "deleteWordBeforeCursor at start must not change text")
    }

    @Test
    fun m3_deleteWordBeforeCursor_deletes_json_key() {
        val text = """{"myLongPropertyName": 1}"""
        val v = vm(text, LanguageMode.JSON)
        // Position cursor right after "myLongPropertyName"
        val pos = text.indexOf("\"", 2) // finds the closing quote after the key
        v.moveCursorTo(pos)
        v.deleteWordBeforeCursor()
        // "myLongPropertyName" should be deleted
        assertFalse(v.getFullText().contains("myLongPropertyName"),
            "deleteWordBeforeCursor must remove the preceding word")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // M-4 — insertNewlineWithAutoIndent (Enter)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun m4_insertNewlineWithAutoIndent_preserves_indentation() {
        // Cursor at end of "  \"items\": [" — current line has 2-space indent
        val text = "{\n  \"items\": ["
        val v = vm(text, LanguageMode.JSON)
        v.moveCursorTo(text.length)  // end of the second line

        // BEFORE FIX: method does not exist
        v.insertNewlineWithAutoIndent()

        val lines = v.getFullText().split('\n')
        assertEquals(3, lines.size, "Should be 3 lines after Enter")
        assertTrue(lines[2].startsWith("  "),
            "New line must start with the same 2-space indent as the previous line. Got: '${lines[2]}'")
    }

    @Test
    fun m4_insertNewlineWithAutoIndent_no_indent_on_unindented_line() {
        val text = "line1"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(text.length)
        v.insertNewlineWithAutoIndent()
        val lines = v.getFullText().split('\n')
        assertEquals(2, lines.size)
        assertEquals("", lines[1], "Non-indented line → new line must have no indentation")
    }

    @Test
    fun m4_insertNewlineWithAutoIndent_preserves_four_space_indent() {
        val text = "    deeplyIndented"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(text.length)
        v.insertNewlineWithAutoIndent()
        val lines = v.getFullText().split('\n')
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("    "),
            "4-space indent must be preserved on new line. Got: '${lines[1]}'")
    }

    @Test
    fun m4_insertNewlineWithAutoIndent_middle_of_line_splits_and_indents() {
        val text = "  abc def"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(5)  // between "abc" and " def"
        v.insertNewlineWithAutoIndent()
        val lines = v.getFullText().split('\n')
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("  "),
            "Split in middle: new line must carry the 2-space indent of line 0. Got: '${lines[1]}'")
    }

    @Test
    fun m4_insertNewlineWithAutoIndent_is_undoable() {
        val text = "  line"
        val v = vm(text, LanguageMode.PLAIN_TEXT)
        v.moveCursorTo(text.length)
        v.insertNewlineWithAutoIndent()
        v.undo()
        assertEquals("  line", v.getFullText(),
            "insertNewlineWithAutoIndent must be undoable in one step")
    }
}
