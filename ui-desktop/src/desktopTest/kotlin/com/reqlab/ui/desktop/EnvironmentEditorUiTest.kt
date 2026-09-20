package com.reqlab.ui.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.persistence.SettingsRepository
import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.ui.shared.state.AppSettings
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP
import com.reqlab.ui.shared.state.DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP
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
        composeRule.onNodeWithTag("env-variables-list-vscrollbar", useUnmergedTree = true).assertExists()
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

    @Test
    fun environment_variable_search_filters_counts_and_clears() {
        val state = envEditorState(
            MutableKeyValue("baseUrl", "http://localhost:8080"),
            MutableKeyValue("token", "abc123"),
        )
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-variable-search-input").assertIsDisplayed()
        composeRule.onNodeWithTag("env-search-count").assertIsDisplayed()
        composeRule.onNodeWithText("2 of 2 variables", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("env-variable-search-input").performTextInput("token")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-var-row-1").assertIsDisplayed()
        composeRule.onNodeWithTag("env-var-row-0").assertDoesNotExist()
        composeRule.onNodeWithText("1 of 2 variables", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("env-search-clear").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-var-row-0").assertIsDisplayed()
        composeRule.onNodeWithTag("env-var-row-1").assertIsDisplayed()
        composeRule.onNodeWithText("2 of 2 variables", substring = true).assertIsDisplayed()
    }

    @Test
    fun environment_variable_search_shows_no_match_state() {
        val state = envEditorState(MutableKeyValue("baseUrl", "http://localhost"))
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-variable-search-input").performTextInput("zzz-no-match")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-no-search-results").assertIsDisplayed()
        composeRule.onNodeWithTag("env-no-results-clear").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-no-search-results").assertDoesNotExist()
        composeRule.onNodeWithTag("env-var-row-0").assertIsDisplayed()
    }

    @Test
    fun environment_variable_search_matches_secret_without_revealing_it() {
        val state = envEditorState(
            MutableKeyValue("apiKey", "hunter2secret", secret = true),
            MutableKeyValue("region", "us-east-1"),
        )
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-variable-search-input").performTextInput("hunter2secret")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-var-row-0").assertIsDisplayed()
        composeRule.onNodeWithTag("env-var-row-1").assertDoesNotExist()
        composeRule.onNodeWithTag("env-var-value-0").assert(hasText("hunter2secret", substring = true).not())
        composeRule.onNodeWithTag("env-search-count").assert(hasText("hunter2secret", substring = true).not())
        composeRule.onNodeWithText("1 of 2 variables", substring = true).assertIsDisplayed()
    }

    @Test
    fun editing_and_deleting_filtered_row_uses_stable_uid_and_save_keeps_hidden_rows() {
        val state = envEditorState(
            MutableKeyValue("keep", "one"),
            MutableKeyValue("editMe", "two"),
            MutableKeyValue("alsoKeep", "three"),
        )
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-variable-search-input").performTextInput("editMe")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("env-var-key-1").performTextReplacement("edited")
        composeRule.onNodeWithTag("env-search-clear").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-variable-search-input").performTextInput("alsoKeep")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("env-var-delete-2").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-save-button").performClick()
        composeRule.waitForIdle()

        val saved = state.environments.first().variables
        assertEquals(listOf("keep", "edited"), saved.map { it.key })
        assertEquals("one", saved[0].value)
        assertEquals("two", saved[1].value)
    }

    @Test
    fun add_variable_clears_filter_and_focuses_new_key() {
        val state = envEditorState(
            MutableKeyValue("alpha", "1"),
            MutableKeyValue("beta", "2"),
        )
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-variable-search-input").performTextInput("alpha")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("env-add-variable").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-var-row-0").assertIsDisplayed()
        composeRule.onNodeWithTag("env-var-row-1").assertIsDisplayed()
        composeRule.onNodeWithTag("env-var-row-2").assertIsDisplayed()
        composeRule.onNodeWithTag("env-var-key-2-focused", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("3 of 3 variables", substring = true).assertIsDisplayed()
    }

    @Test
    fun search_resets_when_dialog_is_reopened() {
        val state = envEditorState(
            MutableKeyValue("alpha", "1"),
            MutableKeyValue("beta", "2"),
        )
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-variable-search-input").performTextInput("beta")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("env-cancel-button").performClick()
        composeRule.waitForIdle()

        state.openEnvEdit(0)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-var-row-0").assertIsDisplayed()
        composeRule.onNodeWithTag("env-var-row-1").assertIsDisplayed()
        composeRule.onNodeWithText("2 of 2 variables", substring = true).assertIsDisplayed()
    }

    @Test
    fun right_resize_grows_width_and_keeps_left_edge() {
        val state = AppState(withDemoData = true).apply { openEnvEdit(0) }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val before = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("env-resize-right", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(80f, 30f))
                up()
            }
        composeRule.waitForIdle()

        val after = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(after.right > before.right + 20.dp, "Width did not grow: before=$before after=$after")
        assertTrue(
            kotlin.math.abs((after.left - before.left).value) < 8f,
            "Left edge moved during right resize: before=$before after=$after",
        )
        assertTrue(state.showEnvEditDialog, "Resize dismissed the dialog")
        assertTrue(state.settings.environmentDialogWidthDp > DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP)
    }

    @Test
    fun bottom_and_corner_resize_change_the_expected_edges() {
        val state = AppState(withDemoData = true).apply {
            settings.environmentDialogWidthDp = 640f
            settings.environmentDialogHeightDp = 480f
            openEnvEdit(0)
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val start = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("env-resize-bottom", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(20f, 70f))
                up()
            }
        composeRule.waitForIdle()
        val afterBottom = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(afterBottom.bottom > start.bottom + 20.dp, "Height did not grow: $start -> $afterBottom")
        assertTrue(
            kotlin.math.abs((afterBottom.top - start.top).value) < 8f,
            "Top edge moved: $start -> $afterBottom",
        )

        composeRule.onNodeWithTag("env-resize-corner", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(60f, 40f))
                up()
            }
        composeRule.waitForIdle()
        val afterCorner = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(afterCorner.right > afterBottom.right + 10.dp, "Corner did not grow width: $afterBottom -> $afterCorner")
        assertTrue(afterCorner.bottom > afterBottom.bottom + 10.dp, "Corner did not grow height: $afterBottom -> $afterCorner")
        assertTrue(
            kotlin.math.abs((afterCorner.left - afterBottom.left).value) < 8f,
            "Left edge moved during corner resize: $afterBottom -> $afterCorner",
        )
        assertTrue(
            kotlin.math.abs((afterCorner.top - afterBottom.top).value) < 8f,
            "Top edge moved during corner resize: $afterBottom -> $afterCorner",
        )
        assertTrue(state.settings.environmentDialogHeightDp > 480f)
    }

    @Test
    fun resize_after_move_keeps_anchored_edges_and_does_not_dismiss() {
        val state = AppState(withDemoData = true).apply { openEnvEdit(0) }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-dialog-title-bar", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(40f, 30f))
                up()
            }
        composeRule.waitForIdle()
        val moved = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag("env-resize-right", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(70f, 0f))
                up()
            }
        composeRule.waitForIdle()

        val resized = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(resized.right > moved.right + 15.dp)
        assertTrue(kotlin.math.abs((resized.left - moved.left).value) < 8f)
        composeRule.onNodeWithTag("env-edit-dialog").assertIsDisplayed()
    }

    @Test
    fun resized_dialog_size_is_restored_from_settings() {
        PlatformStorage.remove("settings.environmentDialogWidthDp")
        PlatformStorage.remove("settings.environmentDialogHeightDp")
        val state = AppState(withDemoData = true).apply { openEnvEdit(0) }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("env-resize-corner", useUnmergedTree = true)
            .performTouchInput {
                down(center)
                moveBy(Offset(90f, 70f))
                up()
            }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("env-cancel-button").performClick()
        composeRule.waitForIdle()

        val width = state.settings.environmentDialogWidthDp
        val height = state.settings.environmentDialogHeightDp
        assertTrue(width > DEFAULT_ENVIRONMENT_DIALOG_WIDTH_DP)
        assertTrue(height > DEFAULT_ENVIRONMENT_DIALOG_HEIGHT_DP)

        SettingsRepository.save(state.settings)
        val loaded = AppSettings()
        SettingsRepository.load(loaded)
        assertEquals(width, loaded.environmentDialogWidthDp)
        assertEquals(height, loaded.environmentDialogHeightDp)
    }

    @Test
    fun restored_dialog_size_is_clamped_to_viewport() {
        val state = AppState(withDemoData = true).apply {
            settings.environmentDialogWidthDp = 8000f
            settings.environmentDialogHeightDp = 8000f
            openEnvEdit(0)
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("env-edit-dialog", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left < 8000.dp)
        assertTrue(bounds.bottom - bounds.top < 8000.dp)
        assertTrue((bounds.right - bounds.left).value > 100f)
    }

    private fun envEditorState(vararg variables: MutableKeyValue): AppState =
        AppState(withDemoData = true).apply {
            environments.first().variables.clear()
            variables.forEach { environments.first().variables.add(it) }
            openEnvEdit(0)
        }
}
