package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.MutableKeyValue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Validation tests for Global Variables functionality.
 *
 * Covers:
 * - Dialog open/close
 * - Adding/removing global variables
 * - Variable enable/disable and secret toggle
 * - Variable resolution priority (environment overrides global)
 * - Empty state on fresh install (no demo data)
 * - Demo data mode has pre-populated globals
 */
class GlobalVariablesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Dialog visibility ──

    @Test
    fun global_variables_dialog_not_shown_by_default() {
        composeRule.setContent { MainScreen(AppState()) }
        composeRule.onNodeWithTag("global-variables-dialog").assertDoesNotExist()
    }

    @Test
    fun global_variables_dialog_shows_when_state_flag_set() {
        val state = AppState().apply { showGlobalVariablesDialog = true }
        composeRule.setContent { MainScreen(state) }
        composeRule.onNodeWithTag("global-variables-dialog").assertIsDisplayed()
    }

    @Test
    fun global_variables_dialog_closes_via_close_button() {
        val state = AppState().apply { showGlobalVariablesDialog = true }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("global-variables-dialog").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertFalse(state.showGlobalVariablesDialog)
    }

    // ── Empty state (fresh install) ──

    @Test
    fun fresh_install_has_no_global_variables() {
        val state = AppState()
        assertTrue(state.globalVariables.isEmpty())
    }

    @Test
    fun fresh_install_dialog_shows_empty_message() {
        val state = AppState().apply { showGlobalVariablesDialog = true }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithText("No global variables defined").assertIsDisplayed()
    }

    // ── Demo data mode ──

    @Test
    fun demo_data_has_pre_populated_global_variables() {
        val state = AppState(withDemoData = true)
        assertEquals(2, state.globalVariables.size)
        assertEquals("appName", state.globalVariables[0].key)
        assertEquals("ReqLab", state.globalVariables[0].value)
        assertEquals("apiVersion", state.globalVariables[1].key)
        assertEquals("v1", state.globalVariables[1].value)
    }

    // ── Add / Remove ──

    @Test
    fun add_global_variable_button_adds_entry() {
        val state = AppState().apply { showGlobalVariablesDialog = true }
        composeRule.setContent { MainScreen(state) }

        assertEquals(0, state.globalVariables.size)

        composeRule.onNodeWithTag("add-global-variable").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("global-var-key-0").performTextReplacement("apiKey")

        // Working copy only until Save
        assertEquals(0, state.globalVariables.size)
        composeRule.onNodeWithTag("global-vars-save").performClick()
        composeRule.waitForIdle()

        assertEquals(1, state.globalVariables.size)
    }

    @Test
    fun multiple_adds_create_multiple_entries() {
        val state = AppState().apply { showGlobalVariablesDialog = true }
        composeRule.setContent { MainScreen(state) }

        repeat(3) {
            composeRule.onNodeWithTag("add-global-variable").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("global-var-key-$it").performTextReplacement("key$it")
        }

        composeRule.onNodeWithTag("global-vars-save").performClick()
        composeRule.waitForIdle()

        assertEquals(3, state.globalVariables.size)
    }

    @Test
    fun cancel_discards_unsaved_global_variable_changes() {
        val state = AppState().apply { showGlobalVariablesDialog = true }
        composeRule.setContent { MainScreen(state) }

        repeat(2) {
            composeRule.onNodeWithTag("add-global-variable").performClick()
        }
        composeRule.waitForIdle()
        assertEquals(0, state.globalVariables.size)

        composeRule.onNodeWithContentDescription("Close", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertTrue(state.globalVariables.isEmpty())
        assertFalse(state.showGlobalVariablesDialog)
    }

    @Test
    fun save_filters_blank_global_rows() {
        val state = AppState().apply { showGlobalVariablesDialog = true }
        composeRule.setContent { MainScreen(state) }

        repeat(2) { composeRule.onNodeWithTag("add-global-variable").performClick() }
        composeRule.onNodeWithTag("global-var-key-0").performTextReplacement("apiKey")
        composeRule.onNodeWithTag("global-vars-save").performClick()
        composeRule.waitForIdle()

        assertEquals(1, state.globalVariables.size)
        assertEquals("apiKey", state.globalVariables.first().key)
    }

    @Test
    fun type_toggle_sets_secret_and_eye_toggles_visibility() {
        val state = AppState().apply {
            globalVariables.add(MutableKeyValue("token", "abc"))
            showGlobalVariablesDialog = true
        }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("global-var-type-0").performClick()
        composeRule.onNodeWithTag("global-var-eye-0").assertIsDisplayed()
        composeRule.onNodeWithTag("global-var-eye-0").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("global-vars-save").performClick()
        composeRule.waitForIdle()
        assertTrue(state.globalVariables.first().secret)
    }

    // ── Variable properties (state-level) ──

    @Test
    fun new_global_variable_defaults() {
        val kv = MutableKeyValue()
        assertEquals("", kv.key)
        assertEquals("", kv.value)
        assertTrue(kv.enabled)
        assertFalse(kv.secret)
    }

    @Test
    fun global_variable_enable_disable() {
        val kv = MutableKeyValue("testKey", "testValue")
        assertTrue(kv.enabled)

        kv.enabled = false
        assertFalse(kv.enabled)

        kv.enabled = true
        assertTrue(kv.enabled)
    }

    @Test
    fun global_variable_secret_toggle() {
        val kv = MutableKeyValue("apiKey", "secret123")
        assertFalse(kv.secret)

        kv.secret = true
        assertTrue(kv.secret)
    }

    @Test
    fun disabled_global_variables_excluded_from_resolution() {
        val state = AppState()
        state.globalVariables.addAll(listOf(
            MutableKeyValue("active", "yes", enabled = true),
            MutableKeyValue("inactive", "no", enabled = false),
        ))

        val layers = state.activeVariableLayers()
        val globalLayer = layers[2] // global layer is third (after environment and collection)
        assertEquals("yes", globalLayer["active"])
        assertFalse(globalLayer.containsKey("inactive"))
    }

    // ── Variable resolution priority ──

    @Test
    fun environment_variables_override_global_variables() {
        val state = AppState().apply {
            environments.add(com.reqlab.ui.shared.state.EnvState("Dev"))
        }
        state.globalVariables.add(MutableKeyValue("baseUrl", "http://global.example.com"))
        state.selectedEnvironment!!.variables.add(MutableKeyValue("baseUrl", "http://env.example.com"))

        val layers = state.activeVariableLayers()
        // Environment layer is first (higher priority)
        assertEquals("http://env.example.com", layers[0]["baseUrl"])
        // Global layer is third (after collection layer) at lower priority
        assertEquals("http://global.example.com", layers[2]["baseUrl"])
    }

    @Test
    fun global_variables_available_when_environment_has_no_override() {
        val state = AppState()
        state.globalVariables.add(MutableKeyValue("appVersion", "2.0"))

        val layers = state.activeVariableLayers()
        val envLayer = layers[0]
        val globalLayer = layers[2] // global layer is third (after collection)

        assertFalse(envLayer.containsKey("appVersion"))
        assertEquals("2.0", globalLayer["appVersion"])
    }

    @Test
    fun remove_global_variable_from_list() {
        val state = AppState()
        state.globalVariables.addAll(listOf(
            MutableKeyValue("key1", "val1"),
            MutableKeyValue("key2", "val2"),
            MutableKeyValue("key3", "val3"),
        ))

        state.globalVariables.removeAt(1)

        assertEquals(2, state.globalVariables.size)
        assertEquals("key1", state.globalVariables[0].key)
        assertEquals("key3", state.globalVariables[1].key)
    }
}
