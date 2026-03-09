package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ImportExportUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collections_section_has_import_button() {
        composeRule.setContent { MainScreen() }
        composeRule.onNodeWithTag("collection-import-button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun environments_section_has_import_button() {
        composeRule.setContent { MainScreen() }
        composeRule.onNodeWithTag("environment-import-button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun collection_actions_menu_shows_folder_actions_and_collection_actions() {
        composeRule.setContent { MainScreen(AppState(withDemoData = true)) }

        composeRule.onNodeWithTag("collection-actions-c1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Add Folder").assertIsDisplayed()
        composeRule.onNodeWithText("Add Request").assertIsDisplayed()
        composeRule.onNodeWithText("Export Collection").assertIsDisplayed()
        composeRule.onNodeWithText("Duplicate Collection").assertIsDisplayed()
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun environment_actions_menu_shows_export_duplicate_delete() {
        composeRule.setContent { MainScreen(AppState(withDemoData = true)) }

        composeRule.onNodeWithTag("env-actions-Development", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export Environment").assertIsDisplayed()
        composeRule.onNodeWithText("Duplicate Environment").assertIsDisplayed()
        composeRule.onNodeWithText("Delete Environment").assertIsDisplayed()
    }

    @Test
    fun settings_data_section_shows_workspace_backup_actions() {
        composeRule.setContent { MainScreen() }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Data").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Import workspace").assertIsDisplayed()
    }
}
