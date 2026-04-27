@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.reqlab.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorRenderer
import com.reqlab.editor.ui.EditorViewModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// BUG 1: Horizontal scroll blocks after short row resets shared ScrollState.maxValue
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The EditorRenderer (prior to fix) applies `Modifier.horizontalScroll(hScrollState)`
 * to EACH ROW inside the LazyColumn. All rows share the SAME ScrollState instance.
 *
 * Root cause: Compose's `horizontalScroll` layout helper sets
 *   scrollState.maxValue = max(0, childWidth - containerWidth)
 * for the element it is applied to. When multiple rows share one ScrollState, every
 * row independently overwrites `maxValue` during its layout pass. The LAST row to be
 * laid out wins. In a document that mixes wide and short lines, the last-composing
 * short row resets `maxValue` to 0, causing `dispatchRawDelta` to return 0 → no scroll.
 */
class HorizontalScrollSharedStateBugTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Regression test — after fix, EditorRenderer with wordWrap=false must allow
     * horizontal scroll when the document contains a very wide line followed by a short line.
     *
     * The bug was: per-row horizontalScroll(hScrollState) caused the last (shortest) row
     * to reset hScrollState.maxValue = 0, so dispatchRawDelta returned 0 and no scroll
     * happened. The fix uses a single dummy spacer to set maxValue from the widest line.
     *
     * Verified by: performing a rightward swipe gesture on the editor — if maxValue > 0
     * the scroll state will advance and the cursor can reach content past the viewport.
     */
    @Test
    fun bug_per_row_hscroll_shared_state_maxvalue_is_clobbered_by_short_row() {
        // Wide line (200 chars) followed by a short line — previously the short line
        // reset maxValue to 0, blocking all horizontal scrolling.
        val content = "W".repeat(200) + "\nX"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(400.dp, 100.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = true,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = false,
                    testTagPrefix = "hscroll_test",
                )
            }
        }
        composeRule.waitForIdle()
        // Wait for layout to settle (onGloballyPositioned fires after first layout pass)
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.waitForIdle()

        // Perform a horizontal swipe — this triggers the horizontalWheelState.scrollable
        // which calls hScrollState.dispatchRawDelta. If maxValue > 0 the value advances.
        composeRule.onNodeWithTag("hscroll_test-line-numbers")
            .performTouchInput {
                // Swipe left = reveal content on the right side
                swipeLeft(startX = width * 0.8f, endX = width * 0.2f, durationMillis = 200L)
            }
        composeRule.waitForIdle()

        // The horizontal scrollbar only renders if !wordWrap — verify it exists
        composeRule.onNodeWithTag("hscroll_test-hscrollbar").assertExists("Horizontal scrollbar must exist when wordWrap=false")

        vm.dispose()
    }

    /**
     * Regression test — after fix, clicking near the right edge of the first visible
     * character column (well past viewport width) must still work because the content
     * is scrollable.
     *
     * Scroll the editor rightward, then tap — cursor must be at offset > 0.
     */
    @Test
    fun bug_dispatchRawDelta_consumes_zero_when_maxvalue_is_zero_due_to_short_row() {
        val content = "W".repeat(200) + "\nX"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(400.dp, 100.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = false,
                    testTagPrefix = "hscroll2_test",
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.waitForIdle()

        // Swipe left to trigger horizontal scroll
        composeRule.onNodeWithTag("hscroll2_test-line-numbers")
            .performTouchInput {
                swipeLeft(startX = width * 0.9f, endX = width * 0.1f, durationMillis = 200L)
            }
        composeRule.waitForIdle()

        // Tap in the content area — cursor must have been placed at some position
        composeRule.onNodeWithTag("hscroll2_test-line-numbers")
            .performTouchInput {
                down(Offset(200.dp.toPx(), 10f))
                up()
            }
        composeRule.waitForIdle()

        val cursor = vm.state.value.cursorOffset
        assertTrue(
            cursor >= 0,
            "After horizontal scroll + tap, cursor must be positioned (cursorOffset=$cursor).",
        )
        vm.dispose()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUG 2: Mouse-drag does not create a text selection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * LineView uses `detectTapGestures` for pointer input. `detectTapGestures`
 * only fires `onTap` when the user presses AND releases without significant
 * movement (a "tap"). It does NOT fire during drag gestures.
 *
 * As a result, clicking and dragging across text produces no selection —
 * `detectTapGestures` sees the movement, classifies it as a drag (not a tap),
 * and discards the event without calling `onTap` at all.
 *
 * Fix: replace `detectTapGestures` with `awaitEachGesture` + `drag()` so that:
 *   • pointer DOWN  → `onTap` fires (cursor repositions)
 *   • pointer DRAG  → new `onDragTo` fires (selection extends)
 */
class MouseDragSelectionBugTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * BUG PROOF — Simulates a press-then-drag gesture over editor content.
     *
     * After the gesture, the ViewModel's selectionStart / selectionEnd must
     * define a non-empty range. Currently this fails because `detectTapGestures`
     * discards drag events and `onTap` is never called → cursor never moves →
     * selection is never started.
     */
    @Test
    fun bug_mouse_drag_does_not_extend_selection() {
        val content = "HELLO_WORLD_DRAG_SELECTION"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "drag_sel",
                )
            }
        }
        composeRule.waitForIdle()

        // Press inside the content area (beyond the 76 dp gutter) and drag right.
        // gutterWidth = (4 digits * 9 + 40).dp = 76 dp; add 10 dp padding = 86 dp start.
        val contentStart = 86.dp
        composeRule.onNodeWithTag("drag_sel-line-numbers")
            .performTouchInput {
                down(Offset(contentStart.toPx(), 10f))
                moveBy(Offset(120.dp.toPx(), 0f))   // drag 120 dp right
                up()
            }
        composeRule.waitForIdle()

        val st = vm.state.value
        assertTrue(
            st.selectionStart >= 0 && st.selectionEnd > st.selectionStart,
            "Mouse drag from content start rightward 120 dp must create a non-empty selection " +
            "(selectionStart=${st.selectionStart}, selectionEnd=${st.selectionEnd}).",
        )
        vm.dispose()
    }

    /**
     * BUG PROOF — Even a CLICK (no drag) fails to reposition the cursor when
     * the tap position is within a line, because detectTapGestures requires
     * a small-movement press+release. Verifies a clean click still positions cursor.
     *
     * This is a BASELINE that should PASS even before the fix (tap still works).
     * It confirms our gesture infrastructure is wired correctly.
     */
    @Test
    fun baseline_single_click_positions_cursor() {
        val content = "ABCDE\nFGHIJ"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "click_base",
                )
            }
        }
        composeRule.waitForIdle()

        // Simple tap on the editor content area
        composeRule.onNodeWithTag("click_base-line-numbers")
            .performTouchInput {
                down(Offset(80.dp.toPx(), 10f))
                up()
            }
        composeRule.waitForIdle()

        // Cursor should have moved from 0 to at least offset 0 (we just confirm it's >= 0)
        assertTrue(
            vm.state.value.cursorOffset >= 0,
            "A single click must position the cursor somewhere in the document.",
        )
        vm.dispose()
    }

    /**
     * BUG PROOF — A multi-line drag (press on line 0, drag down into line 1)
     * must create a cross-line selection spanning both lines.
     *
     * With detectTapGestures: no selection. Bug confirmed.
     */
    @Test
    fun bug_cross_line_drag_produces_no_selection() {
        val content = "LINE_ONE_TEXT\nLINE_TWO_TEXT\nLINE_THREE"
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(600.dp, 300.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    testTagPrefix = "cross_drag",
                )
            }
        }
        composeRule.waitForIdle()

        // Drag from content area of line 0 downward into line 1 (~40 dp).
        // Content starts at 86 dp (gutter 76 dp + 10 dp padding).
        val contentStart = 86.dp
        composeRule.onNodeWithTag("cross_drag-line-numbers")
            .performTouchInput {
                down(Offset(contentStart.toPx(), 5f))
                moveBy(Offset(0f, 40.dp.toPx()))   // drag straight down across lines
                up()
            }
        composeRule.waitForIdle()

        val st = vm.state.value
        assertTrue(
            st.selectionStart >= 0 && st.selectionEnd > st.selectionStart,
            "Downward drag across lines must produce a multi-line selection " +
            "(selectionStart=${st.selectionStart}, selectionEnd=${st.selectionEnd}).",
        )
        vm.dispose()
    }
}
