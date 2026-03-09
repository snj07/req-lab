package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.i18n.AppLanguage
import com.reqlab.ui.shared.theme.ReqLabTheme
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

    @Test
    fun clear_history_requires_confirmation_and_clears_entries() {
        val state = AppState(withDemoData = true)
        composeRule.setContent { MainScreen(state = state) }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Data", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("clear-history-action", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("confirm-dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Are you sure you want to clear request history?", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("confirm-ok-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assert(state.historyItems.isEmpty()) { "History should be cleared after confirmation" }
        composeRule.onAllNodesWithText("List users", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun language_selection_updates_settings_language() {
        val state = AppState()
        composeRule.setContent { MainScreen(state = state) }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Language", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("language-es", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assert(state.settings.language == AppLanguage.ES) { "Language should switch to Spanish" }
    }

    @Test
    fun language_switch_updates_sidebar_label_without_restart() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent {
            ReqLabTheme(appTheme = state.settings.theme, language = state.settings.language) {
                MainScreen(state = state)
            }
        }

        composeRule.onNodeWithText("History", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Language", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("language-es", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Historial", useUnmergedTree = true).assertIsDisplayed()
    }
}
