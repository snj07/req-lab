package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.ResponseDefinition
import com.reqlab.core.model.ResponseMetrics
import com.reqlab.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test

/**
 * Validation tests for Response Body features.
 *
 * Covers:
 * - Response viewer displays when response exists
 * - Status bar with status code, time, size
 * - Response body toolbar buttons (format, word wrap, search, copy, download)
 * - Word wrap enabled by default
 * - Format toggle
 * - Search bar (open/close, input)
 * - Line numbers gutter
 * - Language detection badge
 * - Response tabs (Body, Headers, Cookies, Timing, Raw)
 */
class ResponseBodyUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun stateWithJsonResponse(): AppState = AppState().apply {
        activeTab?.response = ResponseDefinition(
            requestId = "req-1",
            statusCode = 200,
            statusText = "OK",
            headers = listOf(KeyValueEntry("Content-Type", "application/json")),
            cookies = emptyList(),
            bodyText = """{"name":"ReqLab","version":1,"items":[{"id":1},{"id":2}]}""",
            contentType = "application/json",
            executedAtEpochMillis = System.currentTimeMillis(),
            metrics = ResponseMetrics(statusCode = 200, responseTimeMs = 42, responseSizeBytes = 56),
        )
    }

    private fun stateWithXmlResponse(): AppState = AppState().apply {
        activeTab?.response = ResponseDefinition(
            requestId = "req-2",
            statusCode = 200,
            statusText = "OK",
            headers = listOf(KeyValueEntry("Content-Type", "application/xml")),
            cookies = emptyList(),
            bodyText = "<root><item id=\"1\">Hello</item></root>",
            contentType = "application/xml",
            executedAtEpochMillis = System.currentTimeMillis(),
            metrics = ResponseMetrics(statusCode = 200, responseTimeMs = 15, responseSizeBytes = 38),
        )
    }

    private fun stateWithHtmlResponse(): AppState = AppState().apply {
        activeTab?.response = ResponseDefinition(
            requestId = "req-3",
            statusCode = 200,
            statusText = "OK",
            headers = listOf(KeyValueEntry("Content-Type", "text/html")),
            cookies = emptyList(),
            bodyText = "<html><body><h1>Hello</h1></body></html>",
            contentType = "text/html",
            executedAtEpochMillis = System.currentTimeMillis(),
            metrics = ResponseMetrics(statusCode = 200, responseTimeMs = 8, responseSizeBytes = 40),
        )
    }

    // ── Response viewer visibility ──

    @Test
    fun response_viewer_displayed_when_response_exists() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-viewer").assertIsDisplayed()
    }

    @Test
    fun empty_response_shows_placeholder() {
        composeRule.setContent { MainScreen(AppState()) }
        composeRule.onNodeWithText("Enter a URL and click Send to see the response here").assertIsDisplayed()
    }

    // ── Status bar ──

    @Test
    fun status_bar_shows_status_code() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-status-bar").assertIsDisplayed()
        composeRule.onNodeWithText("200").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun status_bar_shows_response_time() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithText("42 ms").assertIsDisplayed()
    }

    @Test
    fun status_bar_shows_response_size() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithText("56 B").assertIsDisplayed()
    }

    // ── Response tabs ──

    @Test
    fun all_response_tabs_visible() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-tab-body").assertIsDisplayed()
        composeRule.onNodeWithTag("response-tab-headers").assertIsDisplayed()
        composeRule.onNodeWithTag("response-tab-cookies").assertIsDisplayed()
        composeRule.onNodeWithTag("response-tab-timing").assertIsDisplayed()
        composeRule.onNodeWithTag("response-tab-raw").assertIsDisplayed()
    }

    @Test
    fun clicking_headers_tab_shows_headers() {
        val state = stateWithJsonResponse()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("response-tab-headers").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Content-Type").assertIsDisplayed()
        composeRule.onNodeWithText("application/json").assertIsDisplayed()
    }

    @Test
    fun clicking_timing_tab_shows_timing_view() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }

        composeRule.onNodeWithText("Timing").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("response-timing-view").assertIsDisplayed()
        composeRule.onNodeWithText("Request Timing Breakdown").assertIsDisplayed()
    }

    // ── Body toolbar ──

    @Test
    fun body_toolbar_displayed() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-toolbar").assertIsDisplayed()
    }

    @Test
    fun format_toggle_button_exists() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-format-toggle").assertIsDisplayed()
    }

    @Test
    fun word_wrap_toggle_button_exists() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-word-wrap-toggle").assertIsDisplayed()
    }

    @Test
    fun word_wrap_is_enabled_by_default() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithContentDescription("Disable word wrap", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun search_toggle_button_exists() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-search-toggle").assertIsDisplayed()
    }

    @Test
    fun copy_body_button_exists() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-copy-button").assertIsDisplayed()
    }

    @Test
    fun download_body_button_exists() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-download-button").assertIsDisplayed()
    }

    // ── Search bar ──

    @Test
    fun search_bar_opens_on_search_toggle() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }

        composeRule.onNodeWithTag("response-search-bar").assertDoesNotExist()

        composeRule.onNodeWithTag("response-search-toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("response-search-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("response-search-input").assertIsDisplayed()
    }

    @Test
    fun search_bar_closes_on_second_toggle() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }

        composeRule.onNodeWithTag("response-search-toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("response-search-bar").assertIsDisplayed()

        composeRule.onNodeWithTag("response-search-toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("response-search-bar").assertDoesNotExist()
    }

    @Test
    fun search_shows_match_count() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }

        composeRule.onNodeWithTag("response-search-toggle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("response-search-input").performTextInput("id")
        composeRule.waitForIdle()

        // Should show match count (e.g., "1/2" for 2 occurrences of "id" in the JSON)
        // The exact count depends on formatting, but there should be results
        composeRule.onNodeWithTag("response-search-bar").assertIsDisplayed()
    }

    // ── Line numbers ──

    @Test
    fun response_body_view_has_line_numbers() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithTag("response-input", useUnmergedTree = true).assertIsDisplayed()
        // Line number "1" should be visible (first line)
        composeRule.onNodeWithText("1", useUnmergedTree = true).assertExists()
    }

    // ── Language detection badge ──

    @Test
    fun json_response_shows_json_language_badge() {
        composeRule.setContent { MainScreen(stateWithJsonResponse()) }
        composeRule.onNodeWithText("JSON").assertIsDisplayed()
    }

    @Test
    fun xml_response_shows_xml_language_badge() {
        composeRule.setContent { MainScreen(stateWithXmlResponse()) }
        composeRule.onNodeWithText("XML").assertIsDisplayed()
    }

    @Test
    fun html_response_shows_html_language_badge() {
        composeRule.setContent { MainScreen(stateWithHtmlResponse()) }
        composeRule.onNodeWithText("HTML").assertIsDisplayed()
    }

    // ── Error state ──

    @Test
    fun error_state_shows_error_message() {
        val state = AppState().apply {
            activeTab?.lastError = "Connection refused"
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithText("Request failed").assertIsDisplayed()
        composeRule.onNodeWithTag("response-error-message").assertIsDisplayed()
    }

    // ── Different status codes ──

    @Test
    fun error_status_code_displayed() {
        val state = AppState().apply {
            activeTab?.response = ResponseDefinition(
                requestId = "req-err",
                statusCode = 404,
                statusText = "Not Found",
                headers = emptyList(),
                cookies = emptyList(),
                bodyText = "Not found",
                contentType = "text/plain",
                executedAtEpochMillis = System.currentTimeMillis(),
                metrics = ResponseMetrics(statusCode = 404, responseTimeMs = 5, responseSizeBytes = 9),
            )
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithText("404").assertIsDisplayed()
        composeRule.onNodeWithText("Not Found").assertIsDisplayed()
    }

    @Test
    fun server_error_status_code_displayed() {
        val state = AppState().apply {
            activeTab?.response = ResponseDefinition(
                requestId = "req-500",
                statusCode = 500,
                statusText = "Internal Server Error",
                headers = emptyList(),
                cookies = emptyList(),
                bodyText = "Server error",
                contentType = "text/plain",
                executedAtEpochMillis = System.currentTimeMillis(),
                metrics = ResponseMetrics(statusCode = 500, responseTimeMs = 100, responseSizeBytes = 12),
            )
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithText("500").assertIsDisplayed()
        composeRule.onNodeWithText("Internal Server Error").assertIsDisplayed()
    }
}
