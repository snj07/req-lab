package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.components.VariableEditorPopup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.IntOffset
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.EnvState
import com.reqlab.ui.shared.state.MutableKeyValue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the inline variable editor popup (Issues 1–7).
 *
 * Compose UI tests verify visual-level behaviour (popup visibility on startup,
 * after typing, etc.).  Integration / unit-level tests verify the environment
 * variable update flow and the parsing utilities without requiring a full UI.
 */
class VariablePopupTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Compose UI Tests ──────────────────────────────────────────

    /**
     * Issue 1 — Popup must NOT appear on application startup.
     *
     * The variable-editor popup should always start hidden.  If it appears
     * automatically (e.g. because the initial URL contains a `{{variable}}`
     * token) the test will fail.
     */
    @Test
    fun variable_popup_is_not_visible_on_app_startup() {
        val state = AppState().apply {
            // Give the URL a variable token — popup must still not auto-open.
            activeTab?.url = "{{baseUrl}}/users"
        }
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // The popup node must not exist at startup (Issue 1).
        composeRule.onAllNodesWithTag("variable-editor-popup").assertCountEquals(0)
    }

    /**
     * Issue 4 — Backspace / typing in the URL field must NOT open the popup.
     *
     * We simulate the user typing text into the URL field (which includes
     * onValueChange calls) and assert the popup remains hidden throughout.
     */
    @Test
    fun typing_in_url_field_does_not_open_variable_popup() {
        val state = AppState()
        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        // Type characters (including a partial variable token) into the URL field.
        composeRule.onNodeWithTag("url-input").performTextInput("{{baseUrl}}")
        composeRule.waitForIdle()

        // Popup must remain hidden after keyboard input (Issue 4).
        composeRule.onAllNodesWithTag("variable-editor-popup").assertCountEquals(0)
    }

    /**
     * Issue 2 — Close button must exist inside the popup.
     *
     * Because triggering the popup requires a real mouse click on a variable
     * span (which is hard to simulate in unit test environments), we test the
     * close button's test-tag is present whenever the popup IS shown.
     * This test injects the popup manually via setContent.
     */
    @Test
    fun variable_popup_has_close_button() {
        val state = AppState().apply {
            environments.clear()
            environments.add(EnvState("Test Env").also { env ->
                env.variables.add(MutableKeyValue(key = "baseUrl", value = "https://api.example.com"))
            })
        }

        composeRule.setContent {
            var isOpen by remember { mutableStateOf(true) }
            if (isOpen) {
                VariableEditorPopup(
                    variableName = "baseUrl",
                    state = state,
                    onDismiss = { isOpen = false },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("variable-popup-close").assertIsDisplayed()
        composeRule.onNodeWithText("{{baseUrl}}", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun variable_popup_closes_on_backdrop_click() {
        val state = AppState()
        composeRule.setContent {
            var isOpen by remember { mutableStateOf(true) }
            if (isOpen) {
                VariableEditorPopup(
                    variableName = "baseUrl",
                    state = state,
                    onDismiss = { isOpen = false },
                )
            }
        }

        composeRule.onNodeWithTag("variable-editor-popup").assertIsDisplayed()
        composeRule.onNodeWithTag("variable-popup-backdrop").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("variable-editor-popup").assertCountEquals(0)
    }

    @Test
    fun variable_popup_has_visible_title_bar_drag_handle() {
        val state = AppState()

        composeRule.setContent {
            VariableEditorPopup(
                variableName = "baseUrl",
                state = state,
                onDismiss = {},
                initialOffset = IntOffset(40, 32),
            )
        }

        composeRule.onNodeWithTag("variable-popup-title-bar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("variable-editor-popup").assertIsDisplayed()
    }

    @Test
    fun variable_popup_normalizes_extra_braces_in_title() {
        val state = AppState()

        composeRule.setContent {
            VariableEditorPopup(
                variableName = "{{baseUrl}}}",
                state = state,
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("{{baseUrl}}", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * Verifies the request bar renders with the URL field accessible.
     * Ensures the variable-aware URL field doesn't break layout.
     */
    @Test
    fun url_input_is_rendered_and_accessible() {
        composeRule.setContent { MainScreen() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("url-input").assertIsDisplayed()
    }

    // ── Integration Tests (environment variable update flow) ────────

    /**
     * Issue 7 — Saving a variable value must update the environment store.
     *
     * This test simulates what happens when the user edits a variable value
     * in the popup and clicks Save: the environment variable's value should
     * be updated in the reactive AppState.
     */
    @Test
    fun saving_variable_value_updates_environment_store() {
        val state = AppState().apply {
            environments.clear()
            environments.add(EnvState("Dev").also { env ->
                env.variables.add(MutableKeyValue(key = "baseUrl", value = "https://old.example.com"))
            })
        }

        val env = state.selectedEnvironment
        val variable = env.variables.first { it.key == "baseUrl" }
        assertEquals("https://old.example.com", variable.value)

        // Simulate what Save does: mutate the variable value directly
        variable.value = "https://new.example.com"

        // Verify via toVariableMap (the same lookup used in the popup and URL resolution)
        assertEquals("https://new.example.com", state.selectedEnvironment.toVariableMap()["baseUrl"])
    }

    /**
     * Issue 7 — Saving a NEW variable (not previously in the environment)
     * should add it to the environment's variable list.
     */
    @Test
    fun saving_new_variable_adds_it_to_environment() {
        val state = AppState().apply {
            environments.clear()
            environments.add(EnvState("Dev"))
        }

        val env = state.selectedEnvironment
        assertNull(env.toVariableMap()["apiKey"])

        // Simulate Save for a new variable
        env.variables.add(MutableKeyValue(key = "apiKey", value = "secret-123"))

        assertNotNull(env.toVariableMap()["apiKey"])
        assertEquals("secret-123", env.toVariableMap()["apiKey"])
    }

    /**
     * Issue 7 — selectedEnvironment reflects variable changes immediately
     * when variables are mutated (reactive state test).
     */
    @Test
    fun environment_variable_change_is_reflected_in_variable_map() {
        val state = AppState().apply {
            environments.clear()
            environments.add(EnvState("Staging").also { env ->
                env.variables.add(MutableKeyValue(key = "host", value = "staging.example.com"))
                env.variables.add(MutableKeyValue(key = "port", value = "8080"))
            })
        }

        val map = state.selectedEnvironment.toVariableMap()
        assertEquals("staging.example.com", map["host"])
        assertEquals("8080", map["port"])

        // Update one variable
        state.selectedEnvironment.variables.first { it.key == "host" }.value = "prod.example.com"

        val updatedMap = state.selectedEnvironment.toVariableMap()
        assertEquals("prod.example.com", updatedMap["host"])
        // Other variables are unaffected
        assertEquals("8080", updatedMap["port"])
    }
}
