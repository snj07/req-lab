@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.reqlab.ui.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.ResponseDefinition
import com.reqlab.core.model.ResponseMetrics
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestEditorTab
import org.junit.Rule
import org.junit.Test

class EditorCoreUxUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun openJsonBody(json: String): AppState {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = json
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        return state
    }

    @Test
    fun position_indicator_is_shown_when_enabled() {
        val state = openJsonBody("{\n  \"a\": 1\n}")
        composeRule.runOnUiThread { state.settings.showEditorPositionIndicator = true }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-status", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-position-indicator", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun position_indicator_is_hidden_by_default_for_request_and_response() {
        val state = openJsonBody("{\n  \"a\": 1\n}")
        composeRule.runOnUiThread {
            state.activeTab?.response = ResponseDefinition(
                requestId = state.activeTab!!.id,
                statusCode = 200,
                statusText = "OK",
                headers = emptyList(),
                cookies = emptyList(),
                bodyText = "{\"ok\":true}",
                contentType = "application/json",
                executedAtEpochMillis = 0L,
                metrics = ResponseMetrics(
                    statusCode = 200,
                    responseTimeMs = 1,
                    responseSizeBytes = 11,
                    dnsMs = -1,
                    connectMs = -1,
                    tlsMs = -1,
                    serverMs = -1,
                    downloadMs = -1,
                    ttfbMs = -1,
                    timeToFirstTokenMs = -1,
                    timeToLastTokenMs = -1,
                ),
            )
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-position-indicator", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("response-position-indicator", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("body-editor-status", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("response-status", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun search_shows_replace_field_in_editable_editor() {
        composeRule.setContent { MainScreen(openJsonBody("{\"a\":1}")) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-search-toggle", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-search-replace-input", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun goto_line_bar_opens_with_ctrl_g() {
        composeRule.setContent { MainScreen(openJsonBody("a\nb\nc")) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.G)
                keyUp(Key.CtrlLeft)
            }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-goto-bar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("body-editor-goto-input", useUnmergedTree = true).performTextInput("2")
        composeRule.onNodeWithTag("body-editor-goto-go", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-goto-bar", useUnmergedTree = true).assertDoesNotExist()
    }
}
