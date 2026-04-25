package com.reqlab.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.reqlab.core.model.BodyType
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.MutableFormDataRow
import com.reqlab.ui.shared.state.RequestEditorTab
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BodyTableUxTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun form_data_enable_uses_checkbox_and_toggles_row_state() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.FORM_DATA
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
            state.activeTab?.formRows?.clear()
            state.activeTab?.formRows?.add(
                MutableFormDataRow(
                    key = "name",
                    value = "Alice",
                    enabled = true,
                    uid = "row-1",
                ),
            )
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-table-enabled-row-1", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        val enabledAfterToggle = state.activeTab?.formRows?.firstOrNull()?.enabled ?: true
        assertFalse(enabledAfterToggle, "Checkbox toggle should disable row")
    }

    @Test
    fun form_data_add_row_button_adds_new_row() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.FORM_DATA
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
            state.activeTab?.formRows?.clear()
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-form-add-row", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        val rowCount = state.activeTab?.formRows?.size ?: 0
        assertTrue(rowCount == 1, "Clicking Add row in form-data should add one row")
    }

    @Test
    fun urlencoded_add_row_button_adds_new_row() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.X_WWW_FORM_URLENCODED
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
            state.activeTab?.urlencodedRows?.clear()
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-urlencoded-add-row", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        val rowCount = state.activeTab?.urlencodedRows?.size ?: 0
        assertTrue(rowCount == 1, "Clicking Add row in urlencoded should add one row")
    }
}
