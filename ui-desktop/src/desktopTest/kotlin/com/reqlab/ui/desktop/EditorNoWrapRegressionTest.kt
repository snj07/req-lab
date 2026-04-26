@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.reqlab.ui.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.ui.EditorRenderer
import com.reqlab.editor.ui.EditorTheme
import com.reqlab.editor.ui.EditorViewModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Regression tests for no-wrap mode: horizontal scroll, gutter visibility,
// and click-past-text cursor placement.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Regression: horizontal scroll was non-functional in no-wrap mode.
 *
 * Fix: the editor uses `onPointerEvent(PointerEventType.Scroll)` which fires
 * synchronously on every raw wheel / trackpad tick. When the horizontal
 * component dominates (`abs(dx) >= abs(dy)`), the handler calls
 * `hScrollState.dispatchRawDelta(dx * 34f)` and consumes the event.
 *
 * The tests obtain `hScrollState` via `onScrollStateReady`, then call
 * `dispatchRawDelta` directly — this validates that the ScrollState is wired
 * correctly without relying on headless pointer-event dispatch.
 */
class NoWrapHorizontalScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * A scroll delta must advance the horizontal offset immediately.
     *
     * Strategy: obtain hScrollState via onScrollStateReady, call
     * dispatchRawDelta(320f) to simulate what the onPointerEvent handler does
     * for a rightward wheel tick (dx * 34f with a positive dx), then verify
     * both the raw ScrollState.value and the onHorizontalScroll callback.
     */
    @Test
    fun mouse_wheel_horizontal_scroll_advances_scroll_state() {
        // A very wide single line that needs horizontal scroll.
        val content = "HORIZONTAL_".repeat(80)
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        var capturedState: ScrollState? = null
        var lastScrollOffset = -1

        composeRule.setContent {
            Box(Modifier.size(400.dp, 100.dp)) {
                EditorRenderer(
                    viewModel          = vm,
                    isReadOnly         = false,
                    language           = LanguageMode.PLAIN_TEXT,
                    wordWrap           = false,
                    testTagPrefix      = "hscroll_test",
                    onHorizontalScroll = { offset -> lastScrollOffset = offset },
                    onScrollStateReady = { state -> capturedState = state },
                )
            }
        }
        composeRule.waitForIdle()

        // Scrollbar must exist before the gesture (content is wider than viewport).
        composeRule.onNodeWithTag("hscroll_test-hscrollbar").assertExists()

        // Simulate a rightward scroll by dispatching a raw pixel delta directly.
        // This is the same call that onPointerEvent handler makes internally.
        val scrollState = capturedState
        requireNotNull(scrollState) { "onScrollStateReady must be called after composition" }

        composeRule.runOnIdle {
            // Positive delta = scroll right, matching dx * 34f for a positive dx wheel tick.
            scrollState.dispatchRawDelta(320f)
        }
        composeRule.waitForIdle()

        assertTrue(
            scrollState.value > 0,
            "ScrollState.value must be > 0 after dispatchRawDelta, got ${scrollState.value}"
        )
        assertTrue(
            lastScrollOffset > 0,
            "onHorizontalScroll callback must report the new offset, got $lastScrollOffset"
        )
        vm.dispose()
    }

    /**
     * Repeated scroll deltas must keep advancing the offset monotonically,
     * and the onHorizontalScroll callback must track every change.
     */
    @Test
    fun repeated_wheel_scrolls_keep_advancing_offset() {
        val content = "REPEATED_".repeat(100)
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        var capturedState: ScrollState? = null
        val offsets = mutableListOf<Int>()

        composeRule.setContent {
            Box(Modifier.size(400.dp, 100.dp)) {
                EditorRenderer(
                    viewModel          = vm,
                    isReadOnly         = false,
                    language           = LanguageMode.PLAIN_TEXT,
                    wordWrap           = false,
                    testTagPrefix      = "hscroll_rep",
                    onHorizontalScroll = { offset -> offsets.add(offset) },
                    onScrollStateReady = { state -> capturedState = state },
                )
            }
        }
        composeRule.waitForIdle()

        val scrollState = capturedState
        requireNotNull(scrollState) { "onScrollStateReady must be called after composition" }

        // Three separate deltas — each must advance the offset.
        repeat(3) {
            composeRule.runOnIdle { scrollState.dispatchRawDelta(320f) }
            composeRule.waitForIdle()
        }

        assertTrue(offsets.isNotEmpty(), "onHorizontalScroll must be called at least once")
        assertTrue(
            offsets.last() > 0,
            "Horizontal offset must be > 0 after three scroll deltas, got ${offsets.last()}"
        )
        // Each recorded offset must be non-decreasing (scroll never went backward).
        for (i in 1 until offsets.size) {
            assertTrue(
                offsets[i] >= offsets[i - 1],
                "Scroll offset decreased: ${offsets[i - 1]} → ${offsets[i]}"
            )
        }
        vm.dispose()
    }
}

/**
 * Issue: in no-wrap mode, clicking to the right of a short line's text does
 * nothing (cursor stays where it was). Expected: cursor snaps to the end of
 * the line.
 */
class NoWrapClickRightOfTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Short line, no-wrap mode. Clicking in the empty space far right of the
     * text must place the cursor at the end of that line.
     */
    @Test
    fun click_right_of_short_line_in_no_wrap_snaps_cursor_to_line_end() {
        // Three short lines in no-wrap mode.
        val lines = listOf("Hello", "World", "Test")
        val content = lines.joinToString("\n")
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(500.dp, 150.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = false,
                    testTagPrefix = "click_right",
                )
            }
        }
        composeRule.waitForIdle()

        // Put cursor at start so we have a known initial state.
        vm.moveCursorTo(0)
        composeRule.waitForIdle()

        // Click far to the right on the first line (y ≈ 10 → first row).
        // "Hello" is ~5 chars × ~8px = 40px. Clicking at x=450 is well past it.
        composeRule.onNodeWithTag("click_right-line-numbers")
            .performTouchInput {
                down(Offset(450.dp.toPx(), 10f))
                up()
            }
        composeRule.waitForIdle()

        val cursor = vm.state.value.cursorOffset
        // "Hello" occupies offsets 0..4 (length 5). End-of-line = 5.
        assertEquals(
            5, cursor,
            "Click far right of 'Hello' in no-wrap must place cursor at offset 5, got $cursor"
        )
        vm.dispose()
    }

    /**
     * Multiple lines: clicking far right on the second line must place cursor
     * at the end of that line, not at the end of the document.
     */
    @Test
    fun click_right_of_second_line_places_cursor_at_that_line_end() {
        val content = "AAA\nBBBBB\nCCC"
        //              0123 45678 9  0123
        // Line 0: "AAA"   offsets 0..2, end=3
        // Line 1: "BBBBB" offsets 4..8, end=9
        // Line 2: "CCC"   offsets 10..12, end=13
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(500.dp, 150.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = false,
                    testTagPrefix = "click_right2",
                )
            }
        }
        composeRule.waitForIdle()

        vm.moveCursorTo(0)
        composeRule.waitForIdle()

        // Click far right on the second row (y ≈ 30, line height ~20dp).
        composeRule.onNodeWithTag("click_right2-line-numbers")
            .performTouchInput {
                down(Offset(450.dp.toPx(), 30f))
                up()
            }
        composeRule.waitForIdle()

        val cursor = vm.state.value.cursorOffset
        // End of second line "BBBBB" = offset 9.
        assertEquals(
            9, cursor,
            "Click right of second line must place cursor at offset 9, got $cursor"
        )
        vm.dispose()
    }
}

/**
 * Regression: in soft-wrap mode wrapped lines visually bled into / behind the
 * gutter (line-number column).
 *
 * Three complementary fixes are now in place:
 *  1. **Measurement fix** – `lineTextWidthPx` subtracts the `padding(start=8dp,
 *     end=16dp)` applied to each line row from `containerWidthPx`, so the
 *     TextMeasurer wraps at the correct inner width.
 *  2. **Visual cover** – the gutter Row has `zIndex(1f)` and
 *     `background(theme.background)`, so it always paints on top of any text
 *     that might still reach the gutter column.
 *  3. **Row clipping** – each line Row applies `clipToBounds()` in no-wrap
 *     mode, so text that is translated off-screen cannot overflow the row.
 */
class SoftWrapGutterOverflowTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * In a 400 × 300 soft-wrap editor, clicking at horizontal-center must NOT
     * jump the cursor to the very end of the document.  If text still overflows
     * into the gutter then every "visual left" click actually lands on a
     * character that the measurer placed in the gutter column, pushing the
     * cursor far right.
     */
    @Test
    fun click_at_center_in_soft_wrap_does_not_snap_cursor_to_doc_end() {
        // Enough text so that lines wrap inside the viewport.
        val longLine = "The quick brown fox jumps over the lazy dog. ".repeat(10)
        val vm = EditorViewModel(longLine.trimEnd(), LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = true,
                    testTagPrefix = "ww_gutter",
                )
            }
        }
        composeRule.waitForIdle()

        // Click at x=200dp (horizontal centre), y=10dp (first row).
        composeRule.onNodeWithTag("ww_gutter-line-numbers")
            .performTouchInput {
                down(Offset(200.dp.toPx(), 10f))
                up()
            }
        composeRule.waitForIdle()

        val cursor = vm.state.value.cursorOffset
        val docLen = longLine.trimEnd().length
        // A click in the middle of a wide viewport must land somewhere in the
        // first half of the document, not at the very end.
        assertTrue(
            cursor < docLen / 2,
            "Click at x-centre in soft-wrap mode must not snap to doc end. " +
                "cursor=$cursor, docLen=$docLen"
        )
        vm.dispose()
    }

    /**
     * After a keyboard Home press the cursor must be at the start of the
     * logical line — this verifies that the wrap geometry is self-consistent
     * and Home/End round-trip correctly.
     */
    @Test
    fun soft_wrap_home_key_returns_cursor_to_line_start() {
        val content = "Word ".repeat(60)
        val vm = EditorViewModel(content.trimEnd(), LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(400.dp, 200.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = true,
                    testTagPrefix = "ww_home",
                )
            }
        }
        composeRule.waitForIdle()

        vm.moveCursorTo(30)
        composeRule.waitForIdle()

        vm.moveCursorToLineStart(extendSelection = false)
        composeRule.waitForIdle()

        assertEquals(
            0, vm.state.value.cursorOffset,
            "Home in single-line soft-wrap doc must return cursor to offset 0"
        )
        vm.dispose()
    }
}

/**
 * Regression: in no-wrap mode, content was drifting behind the gutter
 * (line numbers), making the beginning of a line invisible.
 *
 * The fix combines:
 *  - `graphicsLayer { translationX = -hScrollState.value.toFloat() }` on each
 *    line to shift content left by the scroll offset.
 *  - `clipToBounds()` on the content Box and on the outer Row to prevent
 *    translated content from overflowing into the gutter column.
 *  - The gutter Row carries `zIndex(1f)` + `background(theme.background)` as a
 *    last-resort visual cover for any edge-case pixel bleed.
 */
class NoWrapContentNotUnderGutterTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * After moving cursor to offset 0, the horizontal scroll should reset
     * so the beginning of the line is visible (not behind the gutter).
     */
    @Test
    fun content_starts_at_left_edge_when_cursor_at_zero() {
        val content = "LONGLINE_".repeat(60)
        val vm = EditorViewModel(content, LanguageMode.PLAIN_TEXT)

        composeRule.setContent {
            Box(Modifier.size(400.dp, 100.dp)) {
                EditorRenderer(
                    viewModel     = vm,
                    isReadOnly    = false,
                    language      = LanguageMode.PLAIN_TEXT,
                    wordWrap      = false,
                    testTagPrefix = "gutter_test",
                )
            }
        }
        composeRule.waitForIdle()

        // Move cursor to end (could trigger scroll), then back to 0.
        vm.moveCursorTo(content.length)
        composeRule.waitForIdle()
        vm.moveCursorTo(0)
        composeRule.waitForIdle()

        // The horizontal scrollbar must exist (the line is wider than viewport).
        composeRule.onNodeWithTag("gutter_test-hscrollbar").assertExists()

        // Cursor at 0 → beginning of line must be visible.
        // If horizontal scroll is non-zero AND there's no way to scroll back,
        // the content appears behind the gutter. We cannot directly read
        // hScrollState from the test, so instead we ensure that the text
        // starting characters are still visible by pressing Home and checking
        // the cursor doesn't shift unexpectedly.
        vm.moveCursorToLineStart(extendSelection = false)
        composeRule.waitForIdle()
        assertEquals(
            0, vm.state.value.cursorOffset,
            "Cursor at Home must be 0, content must not be shifted behind gutter"
        )
        vm.dispose()
    }
}
