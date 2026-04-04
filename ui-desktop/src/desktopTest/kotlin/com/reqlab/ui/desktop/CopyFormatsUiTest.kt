package com.reqlab.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test

class CopyFormatsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun copy_menu_shows_only_curl_python_powershell() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("copy-curl-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("cURL").assertIsDisplayed()
        composeRule.onNodeWithText("Python").assertIsDisplayed()
        composeRule.onNodeWithText("PowerShell").assertIsDisplayed()

        composeRule.onAllNodesWithText("cURL (raw template)").assertCountEquals(0)
        composeRule.onAllNodesWithText("HTTPie").assertCountEquals(0)
        composeRule.onAllNodesWithText("Python requests").assertCountEquals(0)
    }
}
