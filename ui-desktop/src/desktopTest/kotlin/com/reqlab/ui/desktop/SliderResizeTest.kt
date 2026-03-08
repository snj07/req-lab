package com.reqlab.ui.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for smooth slider / resize-handle behaviour.
 *
 * Root cause of jerkiness:
 *  - [Modifier.draggable] reports delta in **pixels**.
 *  - [AppState.sidebarWidth] and [AppState.bottomPanelHeight] are stored in **dp**.
 *  - On a Retina (2× density) display, a raw pixel delta is twice as large as
 *    the equivalent dp value, so the handle moves 2× faster than the cursor.
 *  - Fix: divide delta by [LocalDensity.current.density] before applying it,
 *    converting px → dp and making movement 1:1 with the cursor at every density.
 *
 * Compared to the SplitPane which stores a dimensionless fraction (delta_px / size_px),
 * where density cancels out. The sidebar/bottom-panel store absolute dp sizes, so
 * the explicit conversion is required.
 */
class SliderResizeTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── State defaults (Float, no int-truncation) ─────────────────────────────

    @Test
    fun `sidebarWidth default is 260f dp`() {
        assertEquals(260f, AppState().sidebarWidth)
    }

    @Test
    fun `bottomPanelHeight default is 200f dp`() {
        assertEquals(200f, AppState().bottomPanelHeight)
    }

    @Test
    fun `sidebarWidth accepts sub-pixel float values without truncation`() {
        val state = AppState()
        state.sidebarWidth = 283.6f
        assertEquals(283.6f, state.sidebarWidth)
    }

    @Test
    fun `bottomPanelHeight accepts sub-pixel float values without truncation`() {
        val state = AppState()
        state.bottomPanelHeight = 214.9f
        assertEquals(214.9f, state.bottomPanelHeight)
    }

    // ── Handle presence ───────────────────────────────────────────────────────

    @Test
    fun `sidebar-resize-divider is visible in the main screen`() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sidebar-resize-divider", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `bottom-panel-resize-handle is visible when panel is expanded`() {
        val state = AppState().also { it.bottomPanelExpanded = true }
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bottom-panel-resize-handle", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    // ── Sidebar drag direction ────────────────────────────────────────────────

    @Test
    fun `dragging sidebar divider right increases sidebarWidth`() {
        val state = AppState()
        val before = state.sidebarWidth
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sidebar-resize-divider", useUnmergedTree = true)
            .performTouchInput { down(center); moveBy(Offset(300f, 0f)); up() }
        composeRule.waitForIdle()

        assertTrue(state.sidebarWidth > before,
            "Sidebar should widen after dragging right (before=$before after=${state.sidebarWidth})")
    }

    @Test
    fun `dragging sidebar divider left decreases sidebarWidth`() {
        val state = AppState()
        val before = state.sidebarWidth
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sidebar-resize-divider", useUnmergedTree = true)
            .performTouchInput { down(center); moveBy(Offset(-300f, 0f)); up() }
        composeRule.waitForIdle()

        assertTrue(state.sidebarWidth < before,
            "Sidebar should narrow after dragging left (before=$before after=${state.sidebarWidth})")
    }

    @Test
    fun `sidebarWidth stays within max bound after extreme right drag`() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sidebar-resize-divider", useUnmergedTree = true)
            .performTouchInput { down(center); moveBy(Offset(100_000f, 0f)); up() }
        composeRule.waitForIdle()

        assertTrue(state.sidebarWidth <= 500f,
            "sidebarWidth must be ≤ 500 dp, was ${state.sidebarWidth}")
    }

    @Test
    fun `sidebarWidth stays within min bound after extreme left drag`() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sidebar-resize-divider", useUnmergedTree = true)
            .performTouchInput { down(center); moveBy(Offset(-100_000f, 0f)); up() }
        composeRule.waitForIdle()

        assertTrue(state.sidebarWidth >= 150f,
            "sidebarWidth must be ≥ 150 dp, was ${state.sidebarWidth}")
    }

    // ── Bottom-panel drag direction ───────────────────────────────────────────

    @Test
    fun `dragging resize handle up increases bottomPanelHeight`() {
        val state = AppState().also { it.bottomPanelExpanded = true }
        val before = state.bottomPanelHeight
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        // Handle is at the top of the panel; dragging it up (negative Y delta)
        // means the panel top moves up, so the panel grows: height -= (-dy) = height + |dy|.
        composeRule.onNodeWithTag("bottom-panel-resize-handle", useUnmergedTree = true)
            .performTouchInput { down(center); moveBy(Offset(0f, -300f)); up() }
        composeRule.waitForIdle()

        assertTrue(state.bottomPanelHeight > before,
            "Panel should be taller after dragging handle up (before=$before after=${state.bottomPanelHeight})")
    }

    @Test
    fun `dragging resize handle down decreases bottomPanelHeight`() {
        val state = AppState().also { it.bottomPanelExpanded = true }
        val before = state.bottomPanelHeight
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("bottom-panel-resize-handle", useUnmergedTree = true)
            .performTouchInput { down(center); moveBy(Offset(0f, 300f)); up() }
        composeRule.waitForIdle()

        assertTrue(state.bottomPanelHeight < before,
            "Panel should be shorter after dragging handle down (before=$before after=${state.bottomPanelHeight})")
    }

    @Test
    fun `bottomPanelHeight stays within max bound after extreme upward drag`() {
        val state = AppState().also { it.bottomPanelExpanded = true }
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("bottom-panel-resize-handle", useUnmergedTree = true)
            .performTouchInput { down(center); moveBy(Offset(0f, -100_000f)); up() }
        composeRule.waitForIdle()

        assertTrue(state.bottomPanelHeight <= 560f,
            "bottomPanelHeight must be ≤ 560 dp, was ${state.bottomPanelHeight}")
    }

    @Test
    fun `bottomPanelHeight stays within min bound after extreme downward drag`() {
        val state = AppState().also { it.bottomPanelExpanded = true }
        composeRule.setContent { MainScreen(state = state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("bottom-panel-resize-handle", useUnmergedTree = true)
            .performTouchInput { down(center); moveBy(Offset(0f, 100_000f)); up() }
        composeRule.waitForIdle()

        assertTrue(state.bottomPanelHeight >= 120f,
            "bottomPanelHeight must be ≥ 120 dp, was ${state.bottomPanelHeight}")
    }
}
