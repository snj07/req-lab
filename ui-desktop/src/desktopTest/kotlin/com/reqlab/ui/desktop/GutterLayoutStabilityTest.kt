package com.reqlab.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.reqlab.core.model.BodyType
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestEditorTab
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

class GutterLayoutStabilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun line_number_position_does_not_shift_when_fold_icon_appears() {
        val state = AppState()
        composeRule.runOnUiThread {
            state.activeTab?.bodyType = BodyType.JSON
            state.activeTab?.bodyContent = "{"
            state.activeTab?.selectedEditorTab = RequestEditorTab.BODY
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-line-number-0", useUnmergedTree = true)
            .assertIsDisplayed()
        val xBefore = composeRule
            .onNodeWithTag("body-editor-line-number-0", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .left

        val validFoldableJson = "{\n  \"a\": {\n    \"b\": 1\n  }\n}"
        composeRule.onNodeWithTag("body-editor-input", useUnmergedTree = true)
            .performTextReplacement(validFoldableJson)
        composeRule.waitForIdle()

        // Fold regions are recomputed with a debounce in the ViewModel.
        Thread.sleep(800)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("body-editor-fold-indicator-0", useUnmergedTree = true)
            .assertIsDisplayed()

        val xAfter = composeRule
            .onNodeWithTag("body-editor-line-number-0", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .left

        assertTrue(
            abs(xAfter - xBefore) <= 0.1f,
            "Line number x-position shifted after fold icon appeared. before=$xBefore after=$xAfter",
        )
    }
}
