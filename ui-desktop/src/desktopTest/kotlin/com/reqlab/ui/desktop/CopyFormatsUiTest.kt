package com.reqlab.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.components.AuthEditor
import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.MutableKeyValue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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

    @Test
    fun copy_menu_visibly_disables_map_formats_for_duplicate_headers() {
        val state = AppState()
        state.activeTab!!.headers.add(MutableKeyValue("X-Repeat", "one"))
        state.activeTab!!.headers.add(MutableKeyValue("X-Repeat", "two"))
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("copy-curl-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("copy-format-curl").assertIsEnabled()
        composeRule.onNodeWithTag("copy-format-python").assertIsNotEnabled()
        composeRule.onNodeWithTag("copy-format-powershell").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Duplicate headers cannot be represented by this API", substring = true)
            .assertCountEquals(2)
    }

    @Test
    fun copy_menu_visibly_disables_every_format_for_embedded_binary() {
        val state = AppState()
        state.activeTab!!.bodyType = BodyType.BINARY
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("copy-curl-button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("copy-format-curl").assertIsNotEnabled()
        composeRule.onNodeWithTag("copy-format-python").assertIsNotEnabled()
        composeRule.onNodeWithTag("copy-format-powershell").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Embedded binary has no reusable file path", substring = true)
            .assertCountEquals(3)
    }

    @Test
    fun api_key_placement_selector_updates_state_and_marks_the_tab_dirty() {
        val state = AppState()
        val tab = state.activeTab!!.apply { authType = AuthType.API_KEY }
        var dirtyCalls = 0
        composeRule.setContent { AuthEditor(tab, state) { dirtyCalls++ } }

        composeRule.onNodeWithTag("api-key-placement-query").performClick()
        composeRule.runOnIdle {
            assertEquals("query", tab.authApiPlacement)
            assertEquals(1, dirtyCalls)
        }
    }

    @Test
    fun repeated_main_screen_teardown_disposes_each_application_scope_idempotently() {
        val current = mutableStateOf(AppState())
        val show = mutableStateOf(true)
        composeRule.setContent { if (show.value) MainScreen(current.value) }
        repeat(3) {
            val state = current.value
            state.getOrCreateMcpSession("session-$it")
            composeRule.runOnIdle { show.value = false }
            composeRule.waitUntil(5_000) { state.appScope.coroutineContext[Job]?.isActive == false }
            assertSame(state.dispose(), state.dispose())
            if (it < 2) {
                composeRule.runOnIdle {
                    current.value = AppState()
                    show.value = true
                }
                composeRule.waitForIdle()
            }
        }
    }
}
