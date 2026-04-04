package com.reqlab.ui.desktop

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstLaunchUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun first_launch_has_clean_workspace_and_no_open_request() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("empty-workspace-view").assertIsDisplayed()
        composeRule.onAllNodesWithText("Untitled").assertCountEquals(0)
        assertTrue(state.openTabs.isEmpty())
        assertTrue(state.activeTabIndex == -1)
    }

    @Test
    fun first_launch_has_no_environments() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }

        assertTrue(state.environments.isEmpty())
    }

    @Test
    fun collections_and_environments_start_collapsed() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }

        assertFalse(state.collectionsExpanded)
        assertFalse(state.environmentsExpanded)
    }

    @Test
    fun import_icons_exist_for_collections_and_environments() {
        val state = AppState(openDefaultTab = false).apply {
            collectionsExpanded = true
            environmentsExpanded = true
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("collection-import-icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("environment-import-icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun create_request_from_empty_state_creates_default_collection_and_request() {
        val state = AppState(openDefaultTab = false)
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("empty-create-request").performClick()
        composeRule.waitForIdle()

        assertEquals(1, state.collections.size)
        assertEquals("Default Collection", state.collections.first().name)
        assertEquals(1, state.collections.first().children.size)
        assertEquals(1, state.openTabs.size)
        composeRule.onNodeWithTag("request-editor").assertIsDisplayed()
    }
}
