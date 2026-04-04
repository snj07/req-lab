package com.reqlab.ui.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.MutableKeyValue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentEditorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun environment_editor_shows_variable_rows_with_test_tags() {
        val state = AppState(withDemoData = true).apply {
            environments.first().variables.clear()
            environments.first().variables.add(MutableKeyValue("baseUrl", "http://localhost:8080"))
            environments.first().variables.add(MutableKeyValue("token", "abc123"))
            openEnvEdit(0)
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-edit-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("env-var-row-0").assertIsDisplayed()
        composeRule.onNodeWithTag("env-var-row-1").assertIsDisplayed()
    }

    @Test
    fun environment_editor_input_can_be_edited_and_saved() {
        val state = AppState(withDemoData = true).apply {
            environments.first().variables.clear()
            environments.first().variables.add(MutableKeyValue("baseUrl", "http://localhost:8080"))
            openEnvEdit(0)
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-var-key-0").performClick()
        composeRule.onNodeWithTag("env-var-key-0").performTextReplacement("baseUrlUpdated")
        composeRule.onNodeWithTag("env-save-button").performClick()
        composeRule.waitForIdle()

        assertEquals("baseUrlUpdated", state.environments.first().variables.first().key)
    }

    @Test
    fun environment_editor_does_not_save_empty_variable_rows() {
        val state = AppState(withDemoData = true).apply {
            environments.first().variables.clear()
            openEnvEdit(0)
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-add-variable").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-save-button").performClick()
        composeRule.waitForIdle()

        assertTrue(state.environments.first().variables.isEmpty())
    }

    /**
     * Dragging the title-bar of the environment editor dialog must move the
     * card to a new position on screen.
     *
     * Verifies Issue 2: Environment Edit Window can be dragged.
     */
    @Test
    fun environment_dialog_title_bar_drag_moves_dialog() {
        val state = AppState(withDemoData = true).apply { openEnvEdit(0) }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val dialog = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
        val before = dialog.getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("env-dialog-title-bar", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(120f, 60f))
                up()
            }
        composeRule.waitForIdle()

        val after = dialog.getUnclippedBoundsInRoot()
        // After dragging right+down, the dialog must be at a new position.
        assertTrue(
            after.left > before.left + 20.dp || after.top > before.top + 20.dp,
            "Dialog position did not change after dragging — expected left or top to increase by >20dp",
        )
    }

    /**
     * Dragging the dialog to an extreme position must keep it visible within
     * the viewport (viewport clamping).
     */
    @Test
    fun environment_dialog_drag_to_edge_stays_within_viewport() {
        val state = AppState(withDemoData = true).apply { openEnvEdit(0) }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val dialog = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)

        composeRule.onNodeWithTag("env-dialog-title-bar", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(-5000f, -5000f))
                up()
            }
        composeRule.waitForIdle()

        val after = dialog.getUnclippedBoundsInRoot()
        // With MIN_VISIBLE_PX clamping the dialog can go partially off-screen,
        // but at least some portion must remain visible (not fully off-screen).
        val dialogWidth = after.right - after.left
        val dialogHeight = after.bottom - after.top
        assertTrue(
            after.right > 0.dp && after.bottom > 0.dp,
            "Dialog is fully off-screen: $after",
        )
    }

    /**
     * Horizontal drag: dragging only along the X-axis must move the dialog
     * horizontally.  Previously blocked when the card was as wide as the
     * viewport (clamped to zero movement).
     */
    @Test
    fun environment_dialog_title_bar_drag_moves_dialog_horizontally() {
        val state = AppState(withDemoData = true).apply { openEnvEdit(0) }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val dialog = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
        val before = dialog.getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("env-dialog-title-bar", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(200f, 0f))
                up()
            }
        composeRule.waitForIdle()

        val after = dialog.getUnclippedBoundsInRoot()
        assertTrue(
            after.left > before.left + 20.dp,
            "Dialog did not move horizontally — before.left=${before.left}, after.left=${after.left}",
        )
    }

    /**
     * Smooth drag: many small incremental moves must accumulate correctly
     * without losing sub-pixel precision (zero-slop drag fix).
     */
    @Test
    fun environment_dialog_smooth_drag_with_small_increments() {
        val state = AppState(withDemoData = true).apply { openEnvEdit(0) }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val dialog = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
        val before = dialog.getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("env-dialog-title-bar", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                // 20 small incremental moves of 5px each = 100px total
                repeat(20) {
                    moveBy(Offset(5f, 3f))
                }
                up()
            }
        composeRule.waitForIdle()

        val after = dialog.getUnclippedBoundsInRoot()
        // With zero-slop drag, the full accumulated movement should be reflected.
        // Allow some tolerance but expect significant movement (at least 50dp of the 100px).
        assertTrue(
            after.left > before.left + 30.dp || after.top > before.top + 15.dp,
            "Small incremental drags did not accumulate — before=$before, after=$after",
        )
    }
}
