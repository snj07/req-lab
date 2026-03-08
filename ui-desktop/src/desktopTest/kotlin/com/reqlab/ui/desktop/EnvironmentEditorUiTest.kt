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
        val state = AppState().apply {
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
        val state = AppState().apply {
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

    /**
     * Dragging the title-bar of the environment editor dialog must move the
     * card to a new position on screen.
     *
     * Verifies Issue 2: Environment Edit Window can be dragged.
     */
    @Test
    fun environment_dialog_title_bar_drag_moves_dialog() {
        val state = AppState().apply { openEnvEdit(0) }
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
        val state = AppState().apply { openEnvEdit(0) }
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
        assertTrue(after.left >= 0.dp, "Dialog left edge went off-screen: ${after.left}")
        assertTrue(after.top >= 0.dp, "Dialog top edge went off-screen: ${after.top}")
    }
}
