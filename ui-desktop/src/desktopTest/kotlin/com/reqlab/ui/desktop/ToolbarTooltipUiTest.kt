package com.reqlab.ui.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test

class ToolbarTooltipUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settings_tooltip_is_visible_on_hover() {
        composeRule.setContent { MainScreen(AppState()) }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("settings-button", useUnmergedTree = true)
            .performMouseInput { moveTo(Offset(10f, 10f)) }

        // The tooltip has a 200 ms debounce delay before becoming visible.
        // Advancing the test clock past that threshold lets the LaunchedEffect fire.
        composeRule.mainClock.advanceTimeBy(250L)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("toolbar-tooltip-settings", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun settings_click_works_while_hovered() {
        composeRule.setContent { MainScreen(AppState()) }
        composeRule.waitForIdle()

        val settingsNode = composeRule.onNodeWithTag("settings-button", useUnmergedTree = true)
        settingsNode.performMouseInput { moveTo(Offset(10f, 10f)) }
        composeRule.waitForIdle()

        settingsNode.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-dialog", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun global_variables_click_works_while_hovered() {
        composeRule.setContent { MainScreen(AppState()) }
        composeRule.waitForIdle()

        val globalNode = composeRule.onNodeWithTag("global-variables-button", useUnmergedTree = true)
        globalNode.performMouseInput { moveTo(Offset(10f, 10f)) }
        composeRule.waitForIdle()

        globalNode.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("global-variables-dialog", useUnmergedTree = true).assertIsDisplayed()
    }
}
