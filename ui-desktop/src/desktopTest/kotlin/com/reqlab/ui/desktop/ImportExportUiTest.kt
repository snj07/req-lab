package com.reqlab.ui.desktop

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
        composeRule.setContent { DesktopShell() }
        composeRule.onNodeWithTag("collection-import-button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun environments_section_has_import_button() {
        composeRule.setContent { DesktopShell() }
        composeRule.onNodeWithTag("environment-import-button", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun collection_actions_menu_shows_export_duplicate_rename_delete() {
        composeRule.setContent { DesktopShell() }

        composeRule.onNodeWithTag("collection-actions-c1", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export Collection").assertIsDisplayed()
        composeRule.onNodeWithText("Duplicate Collection").assertIsDisplayed()
        composeRule.onNodeWithText("Rename Collection").assertIsDisplayed()
        composeRule.onNodeWithText("Delete Collection").assertIsDisplayed()
    }

    @Test
    fun environment_actions_menu_shows_export_duplicate_delete() {
        composeRule.setContent { DesktopShell() }

        composeRule.onNodeWithTag("env-actions-Development", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export Environment").assertIsDisplayed()
        composeRule.onNodeWithText("Duplicate Environment").assertIsDisplayed()
        composeRule.onNodeWithText("Delete Environment").assertIsDisplayed()
    }

    @Test
    fun settings_data_section_shows_workspace_backup_actions() {
        composeRule.setContent { DesktopShell() }

        composeRule.onNodeWithTag("settings-button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Data").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Export workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Import workspace").assertIsDisplayed()
    }
}
