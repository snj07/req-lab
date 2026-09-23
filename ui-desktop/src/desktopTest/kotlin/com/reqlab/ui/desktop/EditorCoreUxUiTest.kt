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
    fun status_bar_shows_line_and_column() {
        composeRule.setContent { MainScreen(openJsonBody("{\n  \"a\": 1\n}")) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("body-editor-status", useUnmergedTree = true).assertIsDisplayed()
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
