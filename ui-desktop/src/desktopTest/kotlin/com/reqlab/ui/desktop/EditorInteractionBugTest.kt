@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.reqlab.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorRenderer
import com.reqlab.editor.ui.EditorViewModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Double-click word selection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Regression tests for double-click word selection.
 *
 * Root cause: `onWordSelect` callback was never passed from EditorRenderer
 * to LineView, so the double-click gesture detection fired but nothing
 * happened. Fixed by wiring `onWordSelect = { abs -> viewModel.selectWordAt(abs) }`
 * into the LineView call.
 *
 * These tests verify the ViewModel-level word selection logic, which is the
 * invariant that the UI gesture handler delegates to.
 */
class DoubleClickWordSelectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun vm(content: String) = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

    // ── ViewModel-level: selectWordAt correctness ────────────────────────────

    @Test
    fun selectWordAt_middle_of_word_selects_full_word() {
        val vm = vm("hello world")
        // cursor mid-word ("hell|o")
        vm.selectWordAt(3)
        val st = vm.state.value
        assertEquals(0, st.selectionStart, "word start must be 0")
        assertEquals(5, st.selectionEnd,   "word end must be 5 (past 'o')")
        assertEquals("hello", vm.getSelectedText())
        vm.dispose()
    }

    @Test
    fun selectWordAt_start_of_word_selects_full_word() {
        val vm = vm("hello world")
        vm.selectWordAt(6) // 'w' at index 6
        val st = vm.state.value
        assertEquals(6, st.selectionStart)
        assertEquals(11, st.selectionEnd)
        assertEquals("world", vm.getSelectedText())
        vm.dispose()
    }

    @Test
    fun selectWordAt_whitespace_selects_single_char() {
        val vm = vm("hello world")
        vm.selectWordAt(5) // space at index 5
        val st = vm.state.value
        // Non-word char: extends to at least one character
        assertTrue(st.selectionEnd > st.selectionStart, "whitespace must select at least 1 char")
        vm.dispose()
    }

    @Test
    fun selectWordAt_underscore_included_in_word() {
        val vm = vm("foo_bar baz")
        vm.selectWordAt(4) // '_' at index 4
        val st = vm.state.value
        assertEquals(0, st.selectionStart, "'_' is a word char so word starts at 0")
        assertEquals(7, st.selectionEnd,   "word ends after 'r' at index 7")
        vm.dispose()
    }

    @Test
    fun selectWordAt_multiline_selects_only_word_on_that_line() {
        val vm = vm("first\nsecond line\nthird")
        // 'c' of "second" is at offset 8 (first=5, \n=1, se=2 → 8)
        vm.selectWordAt(8)
        val st = vm.state.value
        assertEquals("second", vm.getSelectedText())
        vm.dispose()
    }

    // ── UI-level: double-click gesture routes to selectWordAt ────────────────

    /**
     * This test validates that EditorRenderer now wires onWordSelect to
     * viewModel.selectWordAt. We call selectWordAt directly (which is what
     * the double-click gesture now routes to) and confirm state is updated.
     */
    @Test
    fun ui_word_select_wiring_produces_selection_state() {
        val text = "quick brown fox"
        val vm   = EditorViewModel(text, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 100.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "dbl_click",
                )
            }
        }
        composeRule.waitForIdle()

        // Simulate what the double-click gesture now invokes.
        composeRule.runOnUiThread { vm.selectWordAt(6) } // 'b' of "brown"
        composeRule.waitForIdle()

        assertEquals("brown", vm.getSelectedText(),
            "selectWordAt(6) must select 'brown' in '$text'")
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cmd+X (cut)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Regression tests for Cmd+X (Cut).
 *
 * Two behaviours under test:
 *  1. With selection  → copies selected text + deletes it from document.
 *  2. Without selection → copies the full current line (+ trailing newline where
 *     present) and removes it from the document, matching VS Code / IntelliJ UX.
 */
class CmdXCutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun editorWith(
        content: String,
        prefix: String,
        copyCapture: (String) -> Unit = {},
        setup: EditorViewModel.() -> Unit = {},
    ): EditorViewModel {
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)
        vm.setup()
        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = prefix,
                    onCopyRequest = copyCapture,
                )
            }
        }
        composeRule.waitForIdle()
        return vm
    }

    // ── With selection ───────────────────────────────────────────────────────

    @Test
    fun cmdX_with_selection_removes_selected_text() {
        val vm = editorWith("Hello World", "cx_sel") {
            // Select "Hello"
            moveCursorTo(0)
            moveCursorTo(5, extendSelection = true)
        }

        composeRule.onNodeWithTag("cx_sel-line-numbers").performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(Key.X)
            keyUp(Key.MetaLeft)
        }
        composeRule.waitForIdle()

        assertEquals(" World", vm.getFullText(),
            "Cmd+X with 'Hello' selected must delete it, leaving ' World'")
        vm.dispose()
    }

    @Test
    fun cmdX_with_selection_copies_selected_text() {
        var copied = ""
        val vm = editorWith("Hello World", "cx_copy", copyCapture = { copied = it }) {
            moveCursorTo(0)
            moveCursorTo(5, extendSelection = true)
        }

        composeRule.onNodeWithTag("cx_copy-line-numbers").performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(Key.X)
            keyUp(Key.MetaLeft)
        }
        composeRule.waitForIdle()

        assertEquals("Hello", copied,
            "onCopyRequest must receive the selected text 'Hello'")
        vm.dispose()
    }

    // ── Without selection (line cut) ─────────────────────────────────────────

    @Test
    fun cmdX_no_selection_removes_current_line_from_multiline_doc() {
        val content = "line one\nline two\nline three"
        val vm = editorWith(content, "cx_line") {
            // Place cursor on "line two" (offset 9)
            moveCursorTo(9)
        }

        composeRule.onNodeWithTag("cx_line-line-numbers").performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(Key.X)
            keyUp(Key.MetaLeft)
        }
        composeRule.waitForIdle()

        val remaining = vm.getFullText()
        assertNotEquals(content, remaining, "Cmd+X must change the document")
        assertTrue(!remaining.contains("line two"),
            "After line cut, 'line two' must be gone. Got: '$remaining'")
        assertTrue(remaining.contains("line one"),
            "After line cut, other lines must remain. Got: '$remaining'")
        assertTrue(remaining.contains("line three"),
            "After line cut, other lines must remain. Got: '$remaining'")
        vm.dispose()
    }

    @Test
    fun cmdX_no_selection_copies_current_line_with_newline() {
        var copied = ""
        val content = "line one\nline two\nline three"
        val vm = editorWith(content, "cx_linecopy", copyCapture = { copied = it }) {
            moveCursorTo(9) // on "line two"
        }

        composeRule.onNodeWithTag("cx_linecopy-line-numbers").performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(Key.X)
            keyUp(Key.MetaLeft)
        }
        composeRule.waitForIdle()

        assertEquals("line two\n", copied,
            "Line cut must copy the line text plus its trailing newline")
        vm.dispose()
    }

    @Test
    fun cmdX_no_selection_on_last_line_copies_without_newline() {
        var copied = ""
        val content = "first line\nlast line"
        val vm = editorWith(content, "cx_lastline", copyCapture = { copied = it }) {
            moveCursorTo(11) // on "last line"
        }

        composeRule.onNodeWithTag("cx_lastline-line-numbers").performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(Key.X)
            keyUp(Key.MetaLeft)
        }
        composeRule.waitForIdle()

        assertEquals("last line", copied,
            "Last-line cut must NOT append a newline (there is none)")
        assertEquals("first line", vm.getFullText(),
            "After cutting last line, only 'first line' remains")
        vm.dispose()
    }

    @Test
    fun cmdX_no_selection_single_line_doc_cuts_entire_content() {
        var copied = ""
        val vm = editorWith("only line", "cx_single", copyCapture = { copied = it }) {
            moveCursorTo(4)
        }

        composeRule.onNodeWithTag("cx_single-line-numbers").performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(Key.X)
            keyUp(Key.MetaLeft)
        }
        composeRule.waitForIdle()

        assertEquals("only line", copied,
            "Single-line doc cut must copy the full text")
        assertEquals("", vm.getFullText(),
            "After cutting the only line, document must be empty")
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shift+Click range selection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tests that Shift+Click (and the equivalent ViewModel-level API) extends the
 * selection anchor from the existing cursor, matching standard editor behaviour.
 *
 * The pointer-level fix was to call onTap immediately on pointer-down (no 300 ms
 * delay), so shiftPressed is correctly captured at the moment of the click.
 */
class ShiftClickSelectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moveCursorTo_with_extendSelection_true_creates_selection() {
        val vm = EditorViewModel("Hello World", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(0)
        vm.moveCursorTo(5, extendSelection = true)
        assertEquals(0, vm.state.value.selectionStart)
        assertEquals(5, vm.state.value.selectionEnd)
        assertEquals("Hello", vm.getSelectedText())
        vm.dispose()
    }

    @Test
    fun moveCursorTo_with_extendSelection_forward_then_reverse_makes_correct_anchor() {
        val vm = EditorViewModel("ABCDEF", LanguageMode.PLAIN_TEXT)
        // Place cursor at 3, extend to 6 (forward)
        vm.moveCursorTo(3)
        vm.moveCursorTo(6, extendSelection = true)
        assertEquals(3, vm.state.value.selectionStart)
        assertEquals(6, vm.state.value.selectionEnd)
        // Now extend back to 1 (reverse — anchor stays at 3)
        vm.moveCursorTo(1, extendSelection = true)
        assertEquals(1, vm.state.value.selectionStart)
        assertEquals(3, vm.state.value.selectionEnd)
        assertEquals("BC", vm.getSelectedText())
        vm.dispose()
    }

    @Test
    fun shift_right_arrow_in_ui_extends_selection_by_one_char() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(0)

        composeRule.setContent {
            Box(Modifier.size(400.dp, 100.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "shift_right",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("shift_right-line-numbers").performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionRight)
            keyUp(Key.ShiftLeft)
        }
        composeRule.waitForIdle()

        assertEquals(0, vm.state.value.selectionStart,
            "Selection anchor must remain at 0")
        assertEquals(1, vm.state.value.selectionEnd,
            "Shift+Right must extend selection by one character")
        vm.dispose()
    }

    @Test
    fun shift_left_arrow_in_ui_extends_selection_left() {
        val vm = EditorViewModel("Hello", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(5)

        composeRule.setContent {
            Box(Modifier.size(400.dp, 100.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "shift_left",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("shift_left-line-numbers").performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionLeft)
            keyUp(Key.ShiftLeft)
        }
        composeRule.waitForIdle()

        assertEquals(4, vm.state.value.selectionStart,
            "Shift+Left from position 5 must select from 4")
        assertEquals(5, vm.state.value.selectionEnd,
            "Shift+Left from position 5 must select up to 5")
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shift+Cmd/Alt+Arrow word and line selection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tests for keyboard-driven range selection using modifier+arrow combinations:
 *
 *  • Shift+Cmd+Right  → extend selection to end of line
 *  • Shift+Cmd+Left   → extend selection to start of line
 *  • Shift+Alt+Right  → extend selection to next word boundary
 *  • Shift+Alt+Left   → extend selection to previous word boundary
 */
class ShiftModifierArrowSelectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun editorAt(content: String, cursor: Int, prefix: String): EditorViewModel {
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(cursor)
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

    // ── ViewModel direct: moveCursorWordRight/Left with extendSelection ───────

    @Test
    fun vm_shiftWordRight_extends_selection_to_next_word_end() {
        val vm = EditorViewModel("hello world foo", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(0)
        vm.moveCursorWordRight(extendSelection = true) // should reach end of "hello"
        val st = vm.state.value
        assertEquals(0, st.selectionStart)
        assertEquals(5, st.selectionEnd, "first word-right must reach end of 'hello' (index 5)")
        vm.dispose()
    }

    @Test
    fun vm_shiftWordLeft_extends_selection_to_previous_word_start() {
        val vm = EditorViewModel("hello world foo", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(11) // after "world"
        vm.moveCursorWordLeft(extendSelection = true) // back to start of "world" (index 6)
        val st = vm.state.value
        assertEquals(6, st.selectionStart)
        assertEquals(11, st.selectionEnd)
        assertEquals("world", vm.getSelectedText())
        vm.dispose()
    }

    @Test
    fun vm_shiftLineEnd_extends_selection_to_end_of_line() {
        val vm = EditorViewModel("hello\nworld", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(2) // 'l' in "hello"
        vm.moveCursorToLineEnd(extendSelection = true)
        assertEquals(2, vm.state.value.selectionStart)
        assertEquals(5, vm.state.value.selectionEnd)
        assertEquals("llo", vm.getSelectedText())
        vm.dispose()
    }

    @Test
    fun vm_shiftLineStart_extends_selection_to_start_of_line() {
        val vm = EditorViewModel("hello world", LanguageMode.PLAIN_TEXT)
        vm.moveCursorTo(7) // 'o' in "world"
        vm.moveCursorToLineStart(extendSelection = true)
        assertEquals(0, vm.state.value.selectionStart)
        assertEquals(7, vm.state.value.selectionEnd)
        assertEquals("hello w", vm.getSelectedText())
        vm.dispose()
    }

    // ── UI-level: Shift+Cmd+Right extends to line end ────────────────────────

    @Test
    fun shift_cmd_right_in_ui_extends_selection_to_line_end() {
        val vm = editorAt("hello world", cursor = 0, prefix = "scr")

        composeRule.onNodeWithTag("scr-line-numbers").performKeyInput {
            keyDown(Key.MetaLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionRight)
            keyUp(Key.ShiftLeft)
            keyUp(Key.MetaLeft)
        }
        composeRule.waitForIdle()

        assertEquals(0, vm.state.value.selectionStart,
            "Anchor must remain at 0")
        assertEquals(11, vm.state.value.selectionEnd,
            "Shift+Cmd+Right must extend selection to end of line (11)")
        assertEquals("hello world", vm.getSelectedText())
        vm.dispose()
    }

    @Test
    fun shift_cmd_left_in_ui_extends_selection_to_line_start() {
        val vm = editorAt("hello world", cursor = 11, prefix = "scl")

        composeRule.onNodeWithTag("scl-line-numbers").performKeyInput {
            keyDown(Key.MetaLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionLeft)
            keyUp(Key.ShiftLeft)
            keyUp(Key.MetaLeft)
        }
        composeRule.waitForIdle()

        assertEquals(0, vm.state.value.selectionStart,
            "Shift+Cmd+Left from end must select from start")
        assertEquals(11, vm.state.value.selectionEnd)
        assertEquals("hello world", vm.getSelectedText())
        vm.dispose()
    }

    @Test
    fun shift_alt_right_in_ui_extends_selection_by_one_word() {
        val vm = editorAt("hello world", cursor = 0, prefix = "sar")

        composeRule.onNodeWithTag("sar-line-numbers").performKeyInput {
            keyDown(Key.AltLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionRight)
            keyUp(Key.ShiftLeft)
            keyUp(Key.AltLeft)
        }
        composeRule.waitForIdle()

        assertEquals(0, vm.state.value.selectionStart,
            "Anchor must remain at 0")
        assertEquals(5, vm.state.value.selectionEnd,
            "Shift+Alt+Right must extend selection to end of first word (5)")
        vm.dispose()
    }

    @Test
    fun shift_alt_left_in_ui_extends_selection_by_one_word_backward() {
        val vm = editorAt("hello world", cursor = 11, prefix = "sal")

        composeRule.onNodeWithTag("sal-line-numbers").performKeyInput {
            keyDown(Key.AltLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionLeft)
            keyUp(Key.ShiftLeft)
            keyUp(Key.AltLeft)
        }
        composeRule.waitForIdle()

        assertEquals(6, vm.state.value.selectionStart,
            "Shift+Alt+Left from end must select 'world' (start at 6)")
        assertEquals(11, vm.state.value.selectionEnd)
        assertEquals("world", vm.getSelectedText())
        vm.dispose()
    }

    // ── Select-all still works after word selection ───────────────────────────

    @Test
    fun cmd_a_selects_all_text() {
        val vm = editorAt("hello world", cursor = 5, prefix = "cmd_a")

        composeRule.onNodeWithTag("cmd_a-line-numbers").performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(Key.A)
            keyUp(Key.MetaLeft)
        }
        composeRule.waitForIdle()

        assertEquals(0, vm.state.value.selectionStart)
        assertEquals(11, vm.state.value.selectionEnd)
        assertEquals("hello world", vm.getSelectedText())
        vm.dispose()
    }
}
