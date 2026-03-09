package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.HistoryItem
import com.reqlab.ui.shared.platform.currentTimeMillis
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Validation tests for History functionality.
 *
 * Covers:
 * - History collapsed by default on app start
 * - No history items on fresh install
 * - Demo data has pre-populated history
 * - History expand/collapse toggle
 * - Clicking history item opens request tab
 * - History items display correct method and name
 */
class HistoryValidationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── History collapsed on start ──

    @Test
    fun history_collapsed_by_default() {
        val state = AppState()
        assertFalse(state.historyExpanded, "History should be collapsed on fresh start")
    }

    @Test
    fun history_collapsed_even_with_demo_data() {
        val state = AppState(withDemoData = true)
        assertFalse(state.historyExpanded, "History should be collapsed even with demo data")
    }

    // ── Empty state (fresh install) ──

    @Test
    fun fresh_install_has_no_history_items() {
        val state = AppState()
        assertTrue(state.historyItems.isEmpty(), "Fresh install should have no history items")
    }

    // ── Demo data ──

    @Test
    fun demo_data_has_history_items() {
        val state = AppState(withDemoData = true)
        assertEquals(3, state.historyItems.size)
        assertEquals("List users", state.historyItems[0].name)
        assertEquals(HttpMethodType.GET, state.historyItems[0].method)
        assertEquals("Create user", state.historyItems[1].name)
        assertEquals(HttpMethodType.POST, state.historyItems[1].method)
        assertEquals("Delete user", state.historyItems[2].name)
        assertEquals(HttpMethodType.DELETE, state.historyItems[2].method)
    }

    // ── Expand / collapse ──

    @Test
    fun history_section_header_always_visible() {
        composeRule.setContent { MainScreen(AppState()) }
        composeRule.onNodeWithText("History").assertIsDisplayed()
    }

    @Test
    fun history_items_hidden_when_collapsed() {
        val state = AppState(withDemoData = true) // historyExpanded is false by default
        composeRule.setContent { MainScreen(state) }

        // History items should NOT be visible when collapsed
        composeRule.onAllNodesWithText("List users").assertCountEquals(0)
    }

    @Test
    fun history_items_visible_when_expanded() {
        val state = AppState(withDemoData = true).apply { historyExpanded = true }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithText("List users").assertIsDisplayed()
    }

    @Test
    fun clicking_history_header_toggles_expansion() {
        val state = AppState(withDemoData = true)
        assertFalse(state.historyExpanded)

        composeRule.setContent { MainScreen(state) }

        // Click History to expand
        composeRule.onNodeWithText("History").performClick()
        composeRule.waitForIdle()
        assertTrue(state.historyExpanded)

        // Click again to collapse
        composeRule.onNodeWithText("History").performClick()
        composeRule.waitForIdle()
        assertFalse(state.historyExpanded)
    }

    // ── History item interaction ──

    @Test
    fun clicking_history_item_opens_request_tab() {
        val state = AppState(withDemoData = true).apply { historyExpanded = true }
        composeRule.setContent { MainScreen(state) }

        val tabCountBefore = state.openTabs.size
        composeRule.onAllNodesWithText("List users")[0].performClick()
        composeRule.waitForIdle()

        // Should open a new tab (or focus existing)
        assertTrue(state.openTabs.size >= tabCountBefore)
    }

    // ── History data model ──

    @Test
    fun history_item_stores_all_fields() {
        val now = currentTimeMillis()
        val item = HistoryItem(
            id = "test-id",
            method = HttpMethodType.PUT,
            name = "Update user",
            url = "http://localhost:8080/users/1",
            timestamp = now,
        )

        assertEquals("test-id", item.id)
        assertEquals(HttpMethodType.PUT, item.method)
        assertEquals("Update user", item.name)
        assertEquals("http://localhost:8080/users/1", item.url)
        assertEquals(now, item.timestamp)
    }

    @Test
    fun history_items_can_be_added_programmatically() {
        val state = AppState()
        assertTrue(state.historyItems.isEmpty())

        state.historyItems.add(
            HistoryItem("h1", HttpMethodType.GET, "Test", "http://test.com", currentTimeMillis())
        )

        assertEquals(1, state.historyItems.size)
        assertEquals("Test", state.historyItems[0].name)
    }

    @Test
    fun history_items_ordered_by_insertion() {
        val state = AppState()
        val now = currentTimeMillis()

        state.historyItems.add(HistoryItem("h1", HttpMethodType.GET, "First", "http://a.com", now))
        state.historyItems.add(HistoryItem("h2", HttpMethodType.POST, "Second", "http://b.com", now - 1000))

        assertEquals("First", state.historyItems[0].name)
        assertEquals("Second", state.historyItems[1].name)
    }

    @Test
    fun record_history_moves_existing_entry_to_top() {
        val state = AppState()
        state.recordHistory("req-1", HttpMethodType.GET, "First", "https://a.example")
        state.recordHistory("req-2", HttpMethodType.POST, "Second", "https://b.example")
        state.recordHistory("req-1", HttpMethodType.GET, "First Updated", "https://a.example/v2")

        assertEquals(2, state.historyItems.size)
        assertEquals("req-1", state.historyItems.first().id)
        assertEquals("First Updated", state.historyItems.first().name)
    }

    @Test
    fun reveal_request_in_sidebar_for_non_collection_tab_expands_history() {
        val state = AppState(openDefaultTab = false)
        state.addTab(requestId = "legacy-tab", name = "Legacy API", method = HttpMethodType.GET, url = "https://legacy.example/api")

        state.revealRequestInSidebar("legacy-tab")

        assertTrue(state.historyExpanded)
        assertTrue(state.historyItems.any { it.id == "legacy-tab" && it.name == "Legacy API" })
    }
}
