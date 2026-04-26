@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.reqlab.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
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
// BUG 1: Very wide single line crashes with Constraints overflow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compose's packed Constraints representation caps each dimension at
 * (1 shl 18) - 1 = 262_143 pixels. A monospace character at 13 sp renders at
 * roughly 7.8 px (density=1.0) to 23.4 px (density=3.0) wide. With
 * MAX_RENDER_CHARS_PER_LINE = 50_000, the rendered width can reach up to
 * 50_000 * 23.4 ≈ 1_170_000 px — far above the safe 262_143 limit.
 *
 * Both the Canvas size modifier and the dummy-spacer that drives hScrollState
 * must be capped below this limit or Compose throws:
 *   IllegalArgumentException: Can't represent a width of N and height of 0 in Constraints
 */
class EditorLongLineConstraintTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Regression test — a 10_000-character single line must render without error
     * in wordWrap=false mode. Before the fix the Canvas Modifier.size() and the
     * dummy Spacer width both exceeded Compose's packed-Constraints limit.
     */
    @Test
    fun long_single_line_does_not_crash_compose_constraints() {
        val content = "A".repeat(10_000)  // 10 K chars on one line
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        // Must not throw during composition or layout
        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = true,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = false,
                    testTagPrefix = "long_line",
                )
            }
        }
        composeRule.waitForIdle()

        // If we reach here without crashing, the fix is in place
        composeRule.onNodeWithTag("long_line-hscrollbar").assertExists(
            "Horizontal scrollbar must exist in wordWrap=false mode",
        )
        vm.dispose()
    }

    /**
     * Regression test — MAX_RENDER_CHARS_PER_LINE truncation must still allow
     * wordWrap=true without any crash (word-wrap path is unaffected by the width cap).
     */
    @Test
    fun long_single_line_word_wrap_mode_does_not_crash() {
        val content = "A".repeat(10_000)
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = true,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = true,
                    testTagPrefix = "long_wrap",
                )
            }
        }
        composeRule.waitForIdle()
        // Completion without exception is the assertion
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUG 2: Right-to-left drag selection anchor drifts on each event
// ─────────────────────────────────────────────────────────────────────────────

/**
 * When extending a selection with extendSelection=true, the ANCHOR should
 * remain fixed at the point where the drag started. The current bug:
 *
 *   anchor = if (selectionStart >= 0) selectionStart else cursorOffset
 *
 * After the first drag event, selectionStart is updated to the new selStart.
 * On the second drag event, anchor = new selectionStart (not the original
 * cursor). Each drag step "shifts" the anchor — the selection never extends
 * correctly to the left.
 *
 * Expected: drag right-to-left from offset 10 → 5 → 2 always keeps selEnd=10.
 * Bug:      each step moves selEnd leftward, producing selStart=selEnd−1 on each event.
 */
class RightToLeftSelectionAnchorTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Simulates moveCursorTo calls that mirror a drag leftward.
     * Verifies the anchor (selEnd) stays pinned at the initial cursor position.
     */
    @Test
    fun right_to_left_drag_selection_anchor_stays_fixed() {
        val content = "ABCDEFGHIJ"  // 10 chars, offsets 0-10
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        // Start: cursor at offset 8 (no selection)
        vm.moveCursorTo(8)
        assertEquals(-1, vm.state.value.selectionStart, "No selection initially")

        // Simulate drag leftward: 8 → 6 → 4 → 2
        vm.moveCursorTo(6, extendSelection = true)
        assertEquals(6, vm.state.value.selectionStart, "selStart should be 6")
        assertEquals(8, vm.state.value.selectionEnd,   "selEnd/anchor should stay at 8")

        vm.moveCursorTo(4, extendSelection = true)
        assertEquals(4, vm.state.value.selectionStart, "selStart should grow to 4")
        assertEquals(8, vm.state.value.selectionEnd,   "selEnd/anchor must remain at 8 — not drift to 6")

        vm.moveCursorTo(2, extendSelection = true)
        assertEquals(2, vm.state.value.selectionStart, "selStart should grow to 2")
        assertEquals(8, vm.state.value.selectionEnd,   "selEnd/anchor must remain at 8 — not drift to 4")

        vm.dispose()
    }

    /**
     * Verifies that reversing direction (drag right after dragging left)
     * correctly flips the selection rather than losing it.
     */
    @Test
    fun selection_flips_correctly_when_drag_reverses_direction() {
        val content = "ABCDEFGHIJ"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        // Start cursor at 5
        vm.moveCursorTo(5)

        // Drag LEFT to 2  → selection [2, 5]
        vm.moveCursorTo(2, extendSelection = true)
        assertEquals(2, vm.state.value.selectionStart)
        assertEquals(5, vm.state.value.selectionEnd)

        // Continue drag LEFT to 0 → selection [0, 5]
        vm.moveCursorTo(0, extendSelection = true)
        assertEquals(0, vm.state.value.selectionStart)
        assertEquals(5, vm.state.value.selectionEnd)

        // Now drag RIGHT past 5 to 8 → selection flips to [5, 8]
        vm.moveCursorTo(8, extendSelection = true)
        assertEquals(5, vm.state.value.selectionStart,  "After rightward reversal, selStart = anchor")
        assertEquals(8, vm.state.value.selectionEnd,    "After rightward reversal, selEnd = new cursor")

        vm.dispose()
    }

    /**
     * Touch-based drag test: press at x≈100dp (char ~4), drag LEFT to x≈72dp,
     * then continue to x≈72dp - 30dp. Selection must grow on each step.
     */
    @Test
    fun touch_drag_left_creates_growing_selection() {
        val content = "HELLO_WORLD_DRAG_LEFT"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 100.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "rtl_drag",
                )
            }
        }
        composeRule.waitForIdle()

        // Press at gutter+pad+4chars ≈ 72+32 = 104 dp, drag left to gutter+pad ≈ 72 dp
        composeRule.onNodeWithTag("rtl_drag-line-numbers")
            .performTouchInput {
                down(Offset(104.dp.toPx(), 10f))
                moveBy(Offset(-32.dp.toPx(), 0f))  // drag left ~4 chars
                up()
            }
        composeRule.waitForIdle()

        val st = vm.state.value
        assertTrue(
            st.selectionStart >= 0 && st.selectionEnd > st.selectionStart,
            "Left drag must produce non-empty selection " +
            "(selStart=${st.selectionStart}, selEnd=${st.selectionEnd})",
        )
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUG 3: No right-click context menu for copy/paste/cut operations
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Verifies that the editor exposes a right-click (secondary button) context menu
 * with standard operations: Copy, Cut, Paste, Select All.
 *
 * Since Compose test's performMouseInput supports secondary button presses,
 * we verify that after selecting text and right-clicking, the context menu
 * actions correctly delegate to the ViewModel or callback.
 */
class EditorContextMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Select all text via keyboard shortcut, then right-click → Copy from
     * context menu — asserts onCopyRequest was called with the selected text.
     */
    @Test
    fun right_click_copy_calls_onCopyRequest_with_selected_text() {
        val content = "COPY_ME_TEXT"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)
        var copiedText = ""

        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel      = vm,
                    isReadOnly     = false,
                    language       = LanguageMode.PLAIN_TEXT,
                    testTagPrefix  = "ctx_copy",
                    onCopyRequest  = { copiedText = it },
                )
            }
        }
        composeRule.waitForIdle()

        // Select all text via ViewModel
        vm.selectAll()
        composeRule.waitForIdle()

        // Right-click to open context menu
        composeRule.onNodeWithTag("ctx_copy-line-numbers")
            .performMouseInput {
                moveTo(Offset(80.dp.toPx(), 10f))
                press(MouseButton.Secondary)
                release(MouseButton.Secondary)
            }
        composeRule.waitForIdle()

        // Click "Copy" menu item
        composeRule.onNodeWithTag("ctx_copy-context-copy").performClick()
        composeRule.waitForIdle()

        assertEquals(content, copiedText, "Copy menu item must call onCopyRequest with selected text")
        vm.dispose()
    }

    /**
     * After selecting text, right-click → Cut should:
     * 1. Call onCopyRequest (copies to clipboard)
     * 2. Delete the selected text from the document
     */
    @Test
    fun right_click_cut_copies_and_removes_selected_text() {
        val content = "CUT_ME_TEXT"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)
        var copiedText = ""

        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel      = vm,
                    isReadOnly     = false,
                    language       = LanguageMode.PLAIN_TEXT,
                    testTagPrefix  = "ctx_cut",
                    onCopyRequest  = { copiedText = it },
                )
            }
        }
        composeRule.waitForIdle()

        vm.selectAll()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ctx_cut-line-numbers")
            .performMouseInput {
                moveTo(Offset(80.dp.toPx(), 10f))
                press(MouseButton.Secondary)
                release(MouseButton.Secondary)
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ctx_cut-context-cut").performClick()
        composeRule.waitForIdle()

        assertEquals(content, copiedText, "Cut must copy the selected text first")
        assertTrue(vm.getFullText().isEmpty(), "Cut must remove the selected text from the document (got: '${vm.getFullText()}')")
        vm.dispose()
    }

    /**
     * Right-click → Select All should select the entire document.
     */
    @Test
    fun right_click_select_all_selects_entire_document() {
        val content = "LINE_ONE\nLINE_TWO"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "ctx_sall",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ctx_sall-line-numbers")
            .performMouseInput {
                moveTo(Offset(80.dp.toPx(), 10f))
                press(MouseButton.Secondary)
                release(MouseButton.Secondary)
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ctx_sall-context-selectall").performClick()
        composeRule.waitForIdle()

        val st = vm.state.value
        assertEquals(0,              st.selectionStart, "Select All must set selectionStart=0")
        assertEquals(content.length, st.selectionEnd,   "Select All must set selectionEnd=document.length")
        vm.dispose()
    }

    /**
     * Right-click → Paste should insert clipboard text at the current cursor.
     */
    @Test
    fun right_click_paste_inserts_clipboard_text() {
        val initial   = "START "
        val clipboard = "PASTED"
        val vm = EditorViewModel(initial, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel        = vm,
                    isReadOnly       = false,
                    language         = LanguageMode.PLAIN_TEXT,
                    testTagPrefix    = "ctx_paste",
                    onPasteRequest   = { clipboard },
                )
            }
        }
        composeRule.waitForIdle()

        // Move cursor to end
        vm.moveCursorTo(initial.length)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ctx_paste-line-numbers")
            .performMouseInput {
                moveTo(Offset(80.dp.toPx(), 10f))
                press(MouseButton.Secondary)
                release(MouseButton.Secondary)
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ctx_paste-context-paste").performClick()
        composeRule.waitForIdle()

        assertEquals(
            initial + clipboard,
            vm.getFullText(),
            "Paste must insert clipboard text at cursor position",
        )
        vm.dispose()
    }
}
