package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.reqlab.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests covering:
 *  - Settings dialog opens / closes via toolbar button
 *  - Confirm-delete dialog shows, dismisses, and calls the supplied action
 */
class SettingsDialogUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Settings dialog ─────────────────────────────────────────────────────

    @Test
    fun settings_dialog_opens_when_settings_button_is_clicked() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-dialog", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun settings_dialog_closes_when_close_button_is_clicked() {
        composeRule.setContent { MainScreen() }

        // Open
        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-dialog", useUnmergedTree = true).assertIsDisplayed()

        // Close
        composeRule.onNodeWithTag("settings-close-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-dialog", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Confirm delete dialog ───────────────────────────────────────────────

    @Test
    fun confirm_dialog_is_shown_when_state_showConfirm_is_called() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.showConfirm("Delete item?", "This cannot be undone.") { /* no-op */ }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-dialog", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun confirm_dialog_dismisses_on_cancel_without_invoking_action() {
        val state = AppState()
        var actionCalled = false
        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.showConfirm("Delete?", "Are you sure?") { actionCalled = true }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-cancel-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-dialog", useUnmergedTree = true).assertDoesNotExist()
        assert(!actionCalled) { "Cancel should NOT invoke the pending action" }
    }

    @Test
    fun confirm_dialog_invokes_action_on_confirm_click() {
        val state = AppState()
        var actionCalled = false
        composeRule.setContent { MainScreen(state = state) }

        composeRule.runOnUiThread {
            state.showConfirm("Delete?", "Are you sure?") { actionCalled = true }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-ok-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-dialog", useUnmergedTree = true).assertDoesNotExist()
        assert(actionCalled) { "Confirm should invoke the pending action" }
    }
}
