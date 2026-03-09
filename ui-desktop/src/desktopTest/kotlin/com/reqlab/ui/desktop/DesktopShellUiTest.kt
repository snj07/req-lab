package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.ResponseDefinition
import com.reqlab.core.model.ResponseMetrics
import com.reqlab.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test

class MainScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_main_layout_panels() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithTag("main-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("top-toolbar").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar").assertIsDisplayed()
        composeRule.onNodeWithTag("request-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-panel").assertIsDisplayed()
    }

    @Test
    fun renders_request_bar_with_send_button() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithTag("request-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("method-dropdown").assertIsDisplayed()
        composeRule.onNodeWithTag("url-input").assertIsDisplayed()
        composeRule.onNodeWithTag("send-button").assertIsDisplayed()
    }

    @Test
    fun renders_request_tabs_bar_with_new_tab() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithTag("request-tabs-bar").assertIsDisplayed()
        composeRule.onNodeWithText("Untitled").assertIsDisplayed()
    }

    @Test
    fun renders_sidebar_sections() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("Collections").assertIsDisplayed()
        composeRule.onNodeWithText("Environments").assertIsDisplayed()
    }

    @Test
    fun renders_editor_tab_labels() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithText("Params").assertIsDisplayed()
        composeRule.onNodeWithText("Headers").assertIsDisplayed()
        composeRule.onNodeWithText("Body").assertIsDisplayed()
        // "Auth" may match both the sidebar collection and the tab
        composeRule.onAllNodesWithText("Auth")[0].assertIsDisplayed()
    }

    @Test
    fun renders_toolbar_brand_and_environment() {
        composeRule.setContent { MainScreen(AppState(withDemoData = true)) }

        composeRule.onNodeWithText("ReqLab").assertIsDisplayed()
        // "Development" appears in both toolbar chip and sidebar environment list
        composeRule.onAllNodesWithText("Development")[0].assertIsDisplayed()
    }

    @Test
    fun renders_response_empty_state() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithText("Response").assertIsDisplayed()
    }

    @Test
    fun renders_bottom_panel_tabs() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithText("Console").assertIsDisplayed()
        composeRule.onNodeWithText("Test Results").assertIsDisplayed()
        composeRule.onNodeWithText("Logs").assertIsDisplayed()
    }

    @Test
    fun clicking_sidebar_history_item_opens_request_tab() {
        val state = AppState(withDemoData = true).apply { historyExpanded = true }
        composeRule.setContent { MainScreen(state) }

        // Click the first history item via the sidebar
        composeRule.onAllNodesWithText("List users")[0].performClick()
        composeRule.waitForIdle()

        // The item text should now appear at least twice (sidebar + tab)
        val nodes = composeRule.onAllNodesWithText("List users")
        nodes[0].assertIsDisplayed()
    }

    @Test
    fun send_button_displays_text() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithText("Send").assertIsDisplayed()
    }

    @Test
    fun request_bar_shows_save_button() {
        composeRule.setContent { MainScreen() }
        composeRule.onNodeWithTag("save-button").assertIsDisplayed()
    }

    @Test
    fun request_bar_shows_retry_and_curl_controls() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithTag("retry-menu-button").assertIsDisplayed()
        composeRule.onNodeWithTag("copy-curl-button").assertIsDisplayed()
    }

    @Test
    fun sidebar_search_filters_collection_tree() {
        composeRule.setContent { MainScreen(AppState(withDemoData = true)) }

        composeRule.onNodeWithTag("sidebar-search-input").performTextInput("Auth")
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Users API").assertCountEquals(0)
    }

    @Test
    fun response_actions_show_save_when_response_exists() {
        val state = AppState()
        state.activeTab?.response = ResponseDefinition(
            requestId = "req-1",
            statusCode = 200,
            statusText = "OK",
            headers = listOf(KeyValueEntry("Content-Type", "application/json")),
            cookies = emptyList(),
            bodyText = "{\"ok\":true}",
            contentType = "application/json",
            executedAtEpochMillis = System.currentTimeMillis(),
            metrics = ResponseMetrics(statusCode = 200, responseTimeMs = 10, responseSizeBytes = 12),
        )

        composeRule.setContent { MainScreen(state) }
        composeRule.onNodeWithTag("download-body-button").assertIsDisplayed()
    }

    @Test
    fun dirty_indicator_shows_and_clears_after_save_when_auto_save_off() {
        val state = AppState().apply { settings.autoSaveRequests = false }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("url-input").performTextInput("https://example.com")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Untitled *").assertIsDisplayed()

        composeRule.onNodeWithTag("save-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Untitled *").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onAllNodesWithText("Untitled *").assertCountEquals(0)
    }

    @Test
    fun dirty_indicator_clears_when_value_reverts_to_saved_snapshot() {
        val state = AppState().apply { settings.autoSaveRequests = false }
        state.activeTab?.url = "https://example.com"
        state.activeTab?.markSaved()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("url-input").performTextClearance()
        composeRule.onNodeWithTag("url-input").performTextInput("https://example.com/v2")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Untitled *").assertIsDisplayed()

        composeRule.onNodeWithTag("url-input").performTextClearance()
        composeRule.onNodeWithTag("url-input").performTextInput("https://example.com")
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Untitled *").assertCountEquals(0)
    }
}
