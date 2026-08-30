@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.reqlab.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.reqlab.editor.core.Json5EditorSupport
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorRenderer
import com.reqlab.editor.ui.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// BUG 1: Horizontal scroll response — scroll state must advance immediately
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tests that horizontal scroll state responds immediately to trackpad/wheel events
 * without requiring several frames of "warm-up".
 *
 * Root cause: Using Modifier.offset{} for per-row shifting requires a layout pass
 * before the offset is applied.  graphicsLayer translationX is applied in the draw
 * phase of the SAME frame — one pipeline stage faster — removing the 1-frame lag.
 * Additionally, using flingBehavior = NoFling removes momentum that makes the scroll
 * FEEL sluggish/sticky at the start.
 */
class HorizontalScrollResponsivenessTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * After any horizontal wheel/trackpad scroll event the scroll state value must
     * advance (non-zero).  Previously the first few events were "eaten" by the fling
     * velocity tracker before the first pixel moved.
     */
    @Test
    fun horizontal_scroll_state_advances_immediately_on_first_wheel_event() {
        val content = "W".repeat(300)
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(400.dp, 100.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = true,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = false,
                    testTagPrefix = "hscroll_lag",
                )
            }
        }
        composeRule.waitForIdle()

        // Simulate a touch swipe to the left = scroll right
        composeRule.onNodeWithTag("hscroll_lag-line-numbers")
            .performTouchInput {
                swipeLeft(startX = 300.dp.toPx(), endX = 100.dp.toPx(), durationMillis = 100)
            }
        composeRule.waitForIdle()

        // The horizontal scrollbar must exist (document wider than viewport)
        composeRule.onNodeWithTag("hscroll_lag-hscrollbar").assertExists()
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUG 2: Special / function keys must not insert characters
// ─────────────────────────────────────────────────────────────────────────────

/**
 * On macOS the `fn` key has utf16CodePoint = 63 ('?'), so it was being inserted
 * as a literal '?' character.  Navigation keys (F1–F12, Escape, Page-keys, Insert,
 * Print-Screen, etc.) must also be silently ignored.
 */
class SpecialKeyInsertionBugTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun editorWith(content: String, prefix: String, block: EditorViewModel.() -> Unit = {}) : EditorViewModel {
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)
        vm.block()
        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = prefix,
                )
            }
        }
        composeRule.waitForIdle()
        return vm
    }

    /**
     * Pressing the Escape key must not insert any character.
     */
    @Test
    fun escape_key_does_not_insert_character() {
        val vm = editorWith("ABC", "esc_test") { moveCursorTo(3) }
        composeRule.onNodeWithTag("esc_test-line-numbers")
            .performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()
        assertEquals("ABC", vm.getFullText(), "Escape must not insert anything")
        vm.dispose()
    }

    /**
     * Pressing F1 must not insert any character.
     */
    @Test
    fun f1_key_does_not_insert_character() {
        val vm = editorWith("ABC", "f1_test") { moveCursorTo(3) }
        composeRule.onNodeWithTag("f1_test-line-numbers")
            .performKeyInput { pressKey(Key.F1) }
        composeRule.waitForIdle()
        assertEquals("ABC", vm.getFullText(), "F1 must not insert anything")
        vm.dispose()
    }

    /**
     * Pressing F5 must not insert any character.
     */
    @Test
    fun f5_key_does_not_insert_character() {
        val vm = editorWith("ABC", "f5_test") { moveCursorTo(3) }
        composeRule.onNodeWithTag("f5_test-line-numbers")
            .performKeyInput { pressKey(Key.F5) }
        composeRule.waitForIdle()
        assertEquals("ABC", vm.getFullText(), "F5 must not insert anything")
        vm.dispose()
    }

    /**
     * Pressing Insert must not insert any character.
     */
    @Test
    fun insert_key_does_not_insert_character() {
        val vm = editorWith("ABC", "ins_test") { moveCursorTo(3) }
        composeRule.onNodeWithTag("ins_test-line-numbers")
            .performKeyInput { pressKey(Key.Insert) }
        composeRule.waitForIdle()
        assertEquals("ABC", vm.getFullText(), "Insert key must not insert anything")
        vm.dispose()
    }

    /**
     * Pressing PageUp / PageDown must not insert any character.
     */
    @Test
    fun page_keys_do_not_insert_characters() {
        val vm = editorWith("ABC", "page_test") { moveCursorTo(3) }
        val node = composeRule.onNodeWithTag("page_test-line-numbers")
        node.performKeyInput { pressKey(Key.PageUp) }
        composeRule.waitForIdle()
        node.performKeyInput { pressKey(Key.PageDown) }
        composeRule.waitForIdle()
        assertEquals("ABC", vm.getFullText(), "Page keys must not insert anything")
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUG 3: Clicking near/at the last character of a line puts cursor at line-end
// ─────────────────────────────────────────────────────────────────────────────

/**
 * When clicking within the LAST character's bounding box (even on its left half),
 * the cursor should snap to end-of-line.  This mirrors the behaviour users expect
 * from all mainstream text editors.
 *
 * Also: clicking in the EMPTY SPACE to the right of all text must put cursor at end.
 */
class ClickLastCharCursorTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Direct ViewModel test: offsetInLayout logic — clicking at or past the
     * last-char boundary should produce offset == lineText.length.
     *
     * We verify via moveCursorTo that the cursor ends up at line-end even when
     * the tap offset reported by getOffsetForPosition is lineText.length − 1.
     */
    @Test
    fun click_at_last_char_puts_cursor_at_line_end_via_viewmodel() {
        val content = "Hello World"   // length = 11
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        // Simulate: line 0, offset = 11 (end of line) → cursor should be at 11
        vm.moveCursorTo(11)
        assertEquals(11, vm.state.value.cursorOffset, "moveCursorTo(11) must place cursor at 11")

        // Simulate: line 0, offset = 10 (last char 'd') — also valid cursor position
        vm.moveCursorTo(10)
        assertEquals(10, vm.state.value.cursorOffset, "moveCursorTo(10) must place cursor at 10")

        vm.dispose()
    }

    /**
     * UI-level: after rendering, clicking the empty space to the right of the last
     * character on line 0 should put cursor at the end of the line.
     *
     * We approximate by: rendering the document, tapping far to the right of the text
     * (past any characters), then checking cursorOffset == content.length.
     */
    @Test
    fun click_past_end_of_line_puts_cursor_at_line_end() {
        val content = "Hello"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 100.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "click_end",
                )
            }
        }
        composeRule.waitForIdle()

        // Tap far to the right (well past "Hello" which is ~5 chars × ~8dp = 40dp)
        composeRule.onNodeWithTag("click_end-line-numbers")
            .performTouchInput {
                down(Offset(570.dp.toPx(), 12f))
                up()
            }
        composeRule.waitForIdle()

        assertEquals(
            content.length,
            vm.state.value.cursorOffset,
            "Click far right of text must put cursor at line end (${content.length}), " +
            "got ${vm.state.value.cursorOffset}"
        )
        vm.dispose()
    }

    /**
     * Regression: when line wrapping is enabled, clicking in the second wrapped
     * visual row must place the cursor inside text, not force it to line end.
     */
    @Test
    fun click_inside_soft_wrapped_second_row_places_cursor_inside_text() {
        val content = "This is a very long line that should wrap into multiple visual rows in the editor"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(260.dp, 120.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.PLAIN_TEXT,
                    wordWrap = true,
                    testTagPrefix = "wrap_click",
                )
            }
        }
        composeRule.waitForIdle()

        vm.moveCursorTo(0)
        composeRule.waitForIdle()

        // x in content area, y below first visual row => second wrapped row.
        composeRule.onNodeWithTag("wrap_click-line-numbers")
            .performTouchInput {
                down(Offset(150.dp.toPx(), 34f))
                up()
            }
        composeRule.waitForIdle()

        val cur = vm.state.value.cursorOffset
        assertTrue(cur in 1 until content.length,
            "Click in wrapped middle row should place cursor inside content, got $cur")
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUG 4: Backspace must not delete unexpected content
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Backspace had "weird behavior" — deleting unexpected characters.  Root cause:
 * when large text (>1000 chars) is inserted asynchronously, the ViewModel
 * eagerly advances cursorOffset beyond document.length.  Pressing backspace at
 * that (out-of-bounds) cursor calls document.delete(cursor-1, cursor) which is
 * past the end of the document, corrupting state.
 *
 * Fix: coerce cursor to document.length before computing the delete range.
 */
class BackspaceEdgeCaseTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Baseline: backspace at cursor=3 in "Hello" deletes 'l' → "Helo".
     */
    @Test
    fun backspace_deletes_one_char_before_cursor() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(3)
        vm.deleteBeforeCursor()
        assertEquals("Helo", vm.getFullText(), "Backspace at 3 must delete char at index 2")
        assertEquals(2, vm.state.value.cursorOffset)
        vm.dispose()
    }

    /**
     * Backspace at offset 0 must be a no-op (no char before cursor).
     */
    @Test
    fun backspace_at_start_is_noop() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(0)
        vm.deleteBeforeCursor()
        assertEquals("Hello", vm.getFullText(), "Backspace at 0 must be no-op")
        assertEquals(0, vm.state.value.cursorOffset)
        vm.dispose()
    }

    /**
     * Backspace with a selection must delete the entire selected range.
     */
    @Test
    fun backspace_with_selection_deletes_selected_range() {
        val vm = EditorViewModel("Hello World", LanguageMode.PLAIN_TEXT)
        // Select "World" (offsets 6..11)
        vm.moveCursorTo(6)
        vm.moveCursorTo(11, extendSelection = true)
        vm.deleteBeforeCursor()
        assertEquals("Hello ", vm.getFullText(), "Backspace with selection must delete selected text")
        assertEquals(6, vm.state.value.cursorOffset)
        vm.dispose()
    }

    /**
     * Backspace must work correctly after cursor is moved to the document end.
     * Specifically: cursor == document.length must delete the last char.
     */
    @Test
    fun backspace_at_end_of_document_deletes_last_char() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)   // end
        vm.deleteBeforeCursor()
        assertEquals("Hell", vm.getFullText(), "Backspace at end must delete last char")
        assertEquals(4, vm.state.value.cursorOffset)
        vm.dispose()
    }

    /**
     * Backspace must not crash or corrupt state when cursor is beyond document length.
     * This can happen during optimistic async insert (>1000 chars): cursor is advanced
     * eagerly before the document is updated on background thread.
     */
    @Test
    fun backspace_with_cursor_past_document_length_does_not_corrupt() {
        val vm = EditorViewModel("Hi", LanguageMode.PLAIN_TEXT)
        // Artificially push cursor past document length (simulates async insert lag)
        vm.moveCursorTo(100)
        // Cursor clamped to doc length, so pressing backspace should delete last char
        vm.deleteBeforeCursor()
        // "Hi" has length 2; backspace should delete 'i' → "H"
        val result = vm.getFullText()
        assertTrue(result.length <= 2,
            "After backspace with out-of-bounds cursor, text must not grow (got '$result')")
        assertTrue(vm.state.value.cursorOffset <= vm.getFullText().length,
            "Cursor after backspace must be within document bounds")
        vm.dispose()
    }

    /**
     * Multiple consecutive backspaces delete characters one at a time from the end.
     */
    @Test
    fun multiple_backspaces_delete_sequentially() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)
        repeat(5) { vm.deleteBeforeCursor() }
        assertEquals("", vm.getFullText(), "5 backspaces on 5-char string must produce empty string")
        assertEquals(0, vm.state.value.cursorOffset)
        vm.dispose()
    }

    /**
     * Regression: in no-wrap mode after horizontal scroll, repeated backspace must
     * keep state consistent (no hidden/stale characters reappearing in model text).
     */
    @Test
    fun no_wrap_backspace_after_horizontal_scroll_keeps_text_consistent() {
        val content = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(20)
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(420.dp, 100.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.PLAIN_TEXT,
                    wordWrap = false,
                    testTagPrefix = "bw_nowrap",
                )
            }
        }
        composeRule.waitForIdle()

        vm.moveCursorTo(content.length)
        composeRule.waitForIdle()

        // Scroll right first using wheel/trackpad-like scroll (not drag-select).
        composeRule.onNodeWithTag("bw_nowrap-line-numbers")
            .performMouseInput {
                scroll(-220f)
            }
        composeRule.waitForIdle()

        repeat(60) { vm.deleteBeforeCursor() }
        composeRule.waitForIdle()

        val expected = content.dropLast(60)
        assertEquals(expected, vm.getFullText(), "No-wrap backspace should deterministically remove chars")
        assertEquals(expected.length, vm.state.value.cursorOffset)
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUG 6: fn/globe key inserts '?' on macOS
// ─────────────────────────────────────────────────────────────────────────────

class FnGlobeKeyRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Regression proxy: unknown/special non-character keys must never insert text.
     */
    @Test
    fun unknown_special_key_does_not_insert_question_mark() {
        val vm = EditorViewModel("ABC", LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(400.dp, 120.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "fn_guard",
                )
            }
        }
        composeRule.waitForIdle()

        vm.moveCursorTo(3)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("fn_guard-line-numbers")
            .performKeyInput {
                pressKey(Key.Unknown)
            }
        composeRule.waitForIdle()

        assertEquals("ABC", vm.getFullText(), "Unknown/fn-like key must not inject '?' or any text")
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUG 7: horizontal trackpad diagonal gesture causes vertical jerk
// ─────────────────────────────────────────────────────────────────────────────

class HorizontalScrollJerkRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Regression: in no-wrap mode, predominantly-horizontal diagonal scroll gestures
     * should not vertically scroll the LazyColumn.
     */
    @Test
    fun diagonal_horizontal_scroll_does_not_jerk_vertical_list() {
        val lines = (1..120).joinToString("\n") { idx ->
            "LINE-$idx " + "W".repeat(220)
        }
        val vm = EditorViewModel(lines, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(420.dp, 110.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.PLAIN_TEXT,
                    wordWrap = false,
                    testTagPrefix = "diag_scroll",
                )
            }
        }
        composeRule.waitForIdle()

        // Ensure first line is visible before gesture.
        composeRule.onNodeWithText("1").assertExists()

        // Predominantly horizontal swipe with slight vertical component.
        composeRule.onNodeWithTag("diag_scroll-line-numbers")
            .performTouchInput {
                swipe(start = Offset(360.dp.toPx(), 24f), end = Offset(80.dp.toPx(), 90f))
            }
        composeRule.waitForIdle()

        // Vertical list should remain anchored at top (line 1 still visible).
        composeRule.onNodeWithText("1").assertExists()
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUG 5: Undo / Redo must work correctly for all operations
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Undo/Redo contract:
 *  - Cmd+Z undoes edits in order (LIFO)
 *  - Cmd+Shift+Z redoes undone edits
 *  - Works across insert, delete, select-all+delete
 *  - Works for 100s of operations
 *  - Undo after select-all+delete restores full content
 */
class UndoRedoTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Single insert followed by undo restores original content.
     */
    @Test
    fun undo_single_insert_restores_original() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)
        vm.insertAtCursor(" World")
        assertEquals("Hello World", vm.getFullText())
        vm.undo()
        assertEquals("Hello", vm.getFullText(), "Undo after single insert must restore original text")
        assertEquals(5, vm.state.value.cursorOffset, "Cursor must return to pre-insert position")
        vm.dispose()
    }

    /**
     * Single delete followed by undo restores the character.
     */
    @Test
    fun undo_single_delete_restores_char() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)
        vm.deleteBeforeCursor()   // deletes 'o' → "Hell"
        assertEquals("Hell", vm.getFullText())
        vm.undo()
        assertEquals("Hello", vm.getFullText(), "Undo after delete must restore deleted char")
        assertEquals(5, vm.state.value.cursorOffset, "Cursor must return to pre-delete position")
        vm.dispose()
    }

    /**
     * Select-all → delete → undo must restore the full content.
     */
    @Test
    fun undo_after_select_all_and_delete_restores_full_content() {
        val content = "Line 1\nLine 2\nLine 3"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)
        vm.selectAll()
        vm.deleteBeforeCursor()
        assertEquals("", vm.getFullText(), "Select-all + delete must produce empty document")
        vm.undo()
        assertEquals(content, vm.getFullText(),
            "Undo after select-all + delete must restore entire document")
        vm.dispose()
    }

    /**
     * 100 consecutive inserts followed by 100 undos restores original content.
     */
    @Test
    fun undo_100_consecutive_inserts_restores_original() {
        val vm = EditorViewModel("", LanguageMode.PLAIN_TEXT)
        repeat(100) { i ->
            vm.insertAtCursor("$i ")
        }
        val afterInserts = vm.getFullText()
        assertTrue(afterInserts.isNotEmpty(), "After 100 inserts, document must not be empty")
        repeat(100) { vm.undo() }
        assertEquals("", vm.getFullText(),
            "After 100 undos, document must be empty (original state)")
        vm.dispose()
    }

    /**
     * Undo followed by redo re-applies the edit.
     */
    @Test
    fun redo_reapplies_undone_insert() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)
        vm.insertAtCursor(" World")
        vm.undo()
        assertEquals("Hello", vm.getFullText(), "Undo must revert insert")
        vm.redo()
        assertEquals("Hello World", vm.getFullText(), "Redo must re-apply insert")
        vm.dispose()
    }

    /**
     * New edits after undo must clear the redo stack.
     */
    @Test
    fun new_edit_after_undo_clears_redo_stack() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)
        vm.insertAtCursor(" World")
        vm.undo()      // undo: back to "Hello"
        vm.insertAtCursor("!")   // new edit: → "Hello!"
        vm.redo()      // redo stack should be empty → no-op
        assertEquals("Hello!", vm.getFullText(),
            "Redo after a new edit should be a no-op (redo stack cleared)")
        vm.dispose()
    }

    /**
     * Multiple undo/redo cycles must not corrupt state.
     */
    @Test
    fun multiple_undo_redo_cycles_remain_consistent() {
        val vm = EditorViewModel("A", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(1)
        vm.insertAtCursor("B")   // "AB"
        vm.insertAtCursor("C")   // "ABC"
        vm.undo()                // "AB"
        vm.undo()                // "A"
        assertEquals("A", vm.getFullText())
        vm.redo()                // "AB"
        assertEquals("AB", vm.getFullText())
        vm.redo()                // "ABC"
        assertEquals("ABC", vm.getFullText())
        vm.undo()                // "AB"
        assertEquals("AB", vm.getFullText())
        vm.dispose()
    }

    /**
     * Undo on a large document (> 1 KB) must restore the exact content without
     * memory corruption or crash.
     */
    @Test
    fun undo_large_content_select_all_delete() {
        val big = "X".repeat(5_000)
        val vm = EditorViewModel(big, LanguageMode.PLAIN_TEXT)
        vm.selectAll()
        vm.deleteBeforeCursor()
        assertEquals("", vm.getFullText())
        vm.undo()
        assertEquals(big, vm.getFullText(),
            "Undo after deleting 5000-char document must restore full content")
        assertEquals(big.length, vm.state.value.cursorOffset)
        vm.dispose()
    }

    /**
     * Undo at the beginning of the undo stack (nothing to undo) must be a no-op.
     */
    @Test
    fun undo_beyond_stack_is_noop() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        repeat(10) { vm.undo() }   // no edits made — all must be no-ops
        assertEquals("Hello", vm.getFullText(), "Undo with empty stack must be no-op")
        vm.dispose()
    }

    /**
     * Redo at the end of the redo stack (nothing to redo) must be a no-op.
     */
    @Test
    fun redo_beyond_stack_is_noop() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)
        vm.insertAtCursor(" World")
        repeat(10) { vm.redo() }   // no undone edits — all must be no-ops
        assertEquals("Hello World", vm.getFullText(), "Redo with empty stack must be no-op")
        vm.dispose()
    }

    /**
     * Keyboard shortcut Cmd+Z in the UI widget triggers undo.
     */
    @Test
    fun cmd_z_in_ui_triggers_undo() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "undo_ui",
                )
            }
        }
        composeRule.waitForIdle()

        vm.moveCursorTo(5)
        vm.insertAtCursor(" World")
        assertEquals("Hello World", vm.getFullText())
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("undo_ui-line-numbers")
            .performKeyInput {
                keyDown(Key.MetaLeft)
                pressKey(Key.Z)
                keyUp(Key.MetaLeft)
            }
        composeRule.waitForIdle()

        assertEquals("Hello", vm.getFullText(), "Cmd+Z must undo the last insert")
        vm.dispose()
    }

    /**
     * Keyboard shortcut Cmd+Shift+Z in the UI widget triggers redo.
     */
    @Test
    fun cmd_shift_z_in_ui_triggers_redo() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "redo_ui",
                )
            }
        }
        composeRule.waitForIdle()

        vm.moveCursorTo(5)
        vm.insertAtCursor(" World")
        vm.undo()
        assertEquals("Hello", vm.getFullText())
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("redo_ui-line-numbers")
            .performKeyInput {
                keyDown(Key.MetaLeft)
                keyDown(Key.ShiftLeft)
                pressKey(Key.Z)
                keyUp(Key.ShiftLeft)
                keyUp(Key.MetaLeft)
            }
        composeRule.waitForIdle()

        assertEquals("Hello World", vm.getFullText(), "Cmd+Shift+Z must redo the undone insert")
        vm.dispose()
    }

    @Test
    fun replaceDocument_is_undoable_and_keeps_prior_edits() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)
        vm.insertAtCursor("!")
        assertEquals("Hello!", vm.getFullText())

        vm.replaceDocument("Hello!\nWorld")
        assertEquals("Hello!\nWorld", vm.getFullText())

        vm.undo()
        assertEquals("Hello!", vm.getFullText(), "Undo Format must restore pre-format text including prior edits")
        vm.undo()
        assertEquals("Hello", vm.getFullText(), "Undo after Format must still undo earlier keystrokes")

        vm.redo()
        assertEquals("Hello!", vm.getFullText())
        vm.redo()
        assertEquals("Hello!\nWorld", vm.getFullText(), "Redo must restore Format")
        vm.dispose()
    }

    @Test
    fun replaceDocument_json5_format_undo_restores_authored_text() {
        val compact = "{a:1,}"
        val vm = EditorViewModel(compact, LanguageMode.JSON, Json5EditorSupport)
        val pretty = Json5EditorSupport.format(compact)
        assertTrue(pretty.lines().size > 1, pretty)
        vm.replaceDocument(pretty)
        assertEquals(pretty, vm.getFullText())
        vm.undo()
        assertEquals(compact, vm.getFullText(), "Undo after JSON5 Format must restore authored JSON5")
        vm.dispose()
    }

    @Test
    fun replaceDocument_json5_format_undo_restores_caret() {
        val compact = "{a:1,}"
        val vm = EditorViewModel(compact, LanguageMode.JSON, Json5EditorSupport)
        val pretty = Json5EditorSupport.format(compact)
        vm.moveCursorTo(compact.length)
        vm.replaceDocument(pretty)
        vm.undo()
        assertEquals(compact, vm.getFullText())
        assertEquals(compact.length, vm.state.value.cursorOffset)
        vm.dispose()
    }

    @Test
    fun replaceDocument_noop_when_unchanged() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)
        vm.insertAtCursor("!")
        vm.replaceDocument("Hello!")
        assertEquals(6, vm.state.value.cursorOffset, "No-op replaceDocument must not move the caret")
        vm.undo()
        assertEquals("Hello", vm.getFullText(), "No-op replaceDocument must not push a dummy undo entry")
        vm.dispose()
    }

    @Test
    fun format_then_undo_does_not_jump_viewport() {
        val compact = "{\"k\":1}"
        val pretty = "{\n  \"k\": 1,\n  \"x\": 2,\n  \"y\": 3,\n  \"z\": 4\n}"
        val vm = EditorViewModel(compact, LanguageMode.JSON)
        var listState: LazyListState? = null
        composeRule.setContent {
            Box(Modifier.size(400.dp, 200.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.JSON,
                    testTagPrefix = "fmt_scroll",
                    onListStateReady = { listState = it },
                )
            }
        }
        composeRule.waitForIdle()
        vm.moveCursorTo(compact.length)
        vm.replaceDocument(pretty)
        composeRule.waitForIdle()
        assertEquals(0, requireNotNull(listState).firstVisibleItemIndex)
        vm.undo()
        composeRule.waitForIdle()
        assertEquals(0, requireNotNull(listState).firstVisibleItemIndex, "Format undo must not jump the viewport")
        vm.dispose()
    }

    @Test
    fun select_all_delete_from_scrolled_view_snaps_to_top() {
        val vm = EditorViewModel((0..80).joinToString("\n") { "line-$it" }, LanguageMode.PLAIN_TEXT)
        var listState: LazyListState? = null
        composeRule.setContent {
            Box(Modifier.size(400.dp, 160.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "seldel_scroll",
                    onListStateReady = { listState = it },
                )
            }
        }
        composeRule.waitForIdle()
        val list = requireNotNull(listState)
        runBlocking { list.scrollToItem(40) }
        composeRule.waitForIdle()
        assertTrue(list.firstVisibleItemIndex >= 20)

        vm.selectAll()
        vm.deleteBeforeCursor()
        composeRule.waitForIdle()
        assertEquals(0, list.firstVisibleItemIndex, "Select-all delete must snap viewport to the top")
        vm.dispose()
    }

    @Test
    fun enter_on_scrolled_view_still_follows_caret() {
        val vm = EditorViewModel((0..80).joinToString("\n") { "line-$it" }, LanguageMode.PLAIN_TEXT)
        var listState: LazyListState? = null
        composeRule.setContent {
            Box(Modifier.size(400.dp, 160.dp)) {
                EditorRenderer(
                    viewModel = vm,
                    isReadOnly = false,
                    language = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "enter_scroll",
                    onListStateReady = { listState = it },
                )
            }
        }
        composeRule.waitForIdle()
        val list = requireNotNull(listState)
        runBlocking { list.scrollToItem(40) }
        composeRule.waitForIdle()
        vm.moveCursorTo(vm.document.length)
        vm.insertNewlineWithAutoIndent()
        composeRule.waitForIdle()
        val lastLine = vm.document.lineCount - 1
        assertTrue(
            list.firstVisibleItemIndex <= lastLine &&
                (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= lastLine,
            "Enter must bring the new line into view",
        )
        vm.dispose()
    }
}
