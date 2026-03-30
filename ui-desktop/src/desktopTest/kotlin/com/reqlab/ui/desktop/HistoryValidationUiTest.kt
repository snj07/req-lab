package com.reqlab.ui.desktop

import com.reqlab.ui.shared.MainScreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.components.deleteRequestFromCollections
import com.reqlab.ui.shared.components.moveRequestToCollection
import com.reqlab.ui.shared.components.renameRequestInCollections
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
        assertEquals("r1", state.historyItems[0].requestId)
        assertEquals("Get all users", state.historyItems[0].name)
        assertEquals(HttpMethodType.GET, state.historyItems[0].method)
        assertEquals("Create user", state.historyItems[1].name)
        assertEquals(HttpMethodType.POST, state.historyItems[1].method)
        assertEquals("Login", state.historyItems[2].name)
        assertEquals(HttpMethodType.POST, state.historyItems[2].method)
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
        composeRule.onAllNodesWithTag("history-row-r1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun history_items_visible_when_expanded() {
        val state = AppState(withDemoData = true).apply { historyExpanded = true }
        composeRule.setContent { MainScreen(state) }

        composeRule.onNodeWithTag("history-row-r1", useUnmergedTree = true).assertIsDisplayed()
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
        composeRule.onAllNodesWithText("Get all users")[0].performClick()
        composeRule.waitForIdle()

        // Should open a new tab (or focus existing)
        assertTrue(state.openTabs.size >= tabCountBefore)
        assertEquals("r1", state.activeTab?.id)
    }

    // ── History data model ──

    @Test
    fun history_item_stores_reference_fields() {
        val now = currentTimeMillis()
        val item = HistoryItem(
            requestId = "r77",
            method = HttpMethodType.PUT,
            name = "Update user",
            url = "http://localhost:8080/users/1",
            timestamp = now,
            collectionId = "c-users",
            folderPath = listOf("Folder A", "Folder B"),
        )

        assertEquals("r77", item.requestId)
        assertEquals(HttpMethodType.PUT, item.method)
        assertEquals("Update user", item.name)
        assertEquals("http://localhost:8080/users/1", item.url)
        assertEquals(now, item.timestamp)
        assertEquals("c-users", item.collectionId)
        assertEquals(listOf("Folder A", "Folder B"), item.folderPath)
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
        val state = AppState(withDemoData = true)
        state.clearHistory()
        state.recordHistory("r1", HttpMethodType.GET, "Get all users", "{{baseUrl}}/users")
        state.recordHistory("r2", HttpMethodType.POST, "Create user", "{{baseUrl}}/users")
        state.recordHistory("r1", HttpMethodType.GET, "Get all users", "{{baseUrl}}/users")

        assertEquals(2, state.historyItems.size)
        assertEquals("r1", state.historyItems.first().requestId)
        assertEquals("Get all users", state.historyItems.first().name)
    }

    @Test
    fun reveal_request_in_sidebar_expands_collapsed_sidebar_and_selects_collection_request() {
        val state = AppState(withDemoData = true).apply {
            sidebarExpanded = false
            sidebarSearchQuery = "does-not-match"
        }
        val ok = state.revealRequestInSidebar("r1")

        assertTrue(ok)
        assertTrue(state.sidebarExpanded)
        assertEquals("", state.sidebarSearchQuery)
        assertEquals("r1", state.selectedRequestId)
        assertEquals("r1", state.sidebarScrollToRequestId)
    }

    @Test
    fun reveal_request_in_sidebar_for_missing_request_shows_error() {
        val state = AppState(withDemoData = true)
        val ok = state.revealRequestInSidebar("missing-404")

        assertFalse(ok)
        assertTrue(state.showErrorDialog)
        assertTrue(state.errorDialogMessage.contains("no longer exists", ignoreCase = true))
    }

    @Test
    fun open_history_item_links_to_collection_request_by_request_id() {
        val state = AppState(withDemoData = true)
        val item = state.historyItems.first { it.requestId == "r1" }

        state.openHistoryItem(item)

        assertEquals("r1", state.activeTab?.id)
        assertEquals("r1", state.selectedRequestId)
    }

    @Test
    fun history_row_can_be_removed_via_menu() {
        val state = AppState(withDemoData = true).apply {
            historyExpanded = true
        }

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history-actions-r1", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Remove from History").performClick()
        composeRule.waitForIdle()

        assertTrue(state.historyItems.none { it.requestId == "r1" })
    }

    @Test
    fun show_in_sidebar_from_filtered_state_targets_collection_request() {
        val state = AppState(withDemoData = true).apply {
            historyExpanded = true
            sidebarSearchQuery = "zzzz"
        }
        val item = state.historyItems.first { it.requestId == "r1" }

        val ok = state.goToCollectionFromHistory(item)

        assertTrue(ok)
        assertEquals("r1", state.selectedRequestId)
        assertEquals("r1", state.sidebarScrollToRequestId)
    }

    @Test
    fun opening_history_item_links_to_collection_request_when_signature_matches() {
        val state = AppState(withDemoData = true)
        val historyClone = HistoryItem(
            requestId = "history-alias-id",
            method = HttpMethodType.GET,
            name = "Get all users",
            url = "{{baseUrl}}/users",
            timestamp = currentTimeMillis(),
        )

        state.historyItems.add(0, historyClone)
        state.openHistoryItem(historyClone)

        assertEquals("r1", state.activeTab?.id)
        assertEquals("r1", state.selectedRequestId)
    }

    @Test
    fun deleting_request_removes_corresponding_history_entries() {
        val state = AppState(withDemoData = true)
        assertTrue(state.historyItems.any { it.requestId == "r2" })

        deleteRequestFromCollections(state.collections, "r2")
        state.notifyCollectionsChanged()

        assertTrue(state.historyItems.none { it.requestId == "r2" })
    }

    @Test
    fun renaming_request_updates_history_entry_name() {
        val state = AppState(withDemoData = true)

        renameRequestInCollections(state.collections, "r1", "Users - All")
        state.notifyCollectionsChanged()

        val updated = state.historyItems.first { it.requestId == "r1" }
        assertEquals("Users - All", updated.name)
    }

    @Test
    fun moving_request_still_resolves_from_history() {
        val state = AppState(withDemoData = true)
        val item = state.historyItems.first { it.requestId == "r1" }

        assertTrue(moveRequestToCollection(state.collections, "r1", "c2"))
        state.notifyCollectionsChanged()

        val ok = state.goToCollectionFromHistory(item)
        assertTrue(ok)
        assertEquals("r1", state.selectedRequestId)
    }

    @Test
    fun go_to_collection_for_broken_reference_shows_error() {
        val state = AppState(withDemoData = true)
        val broken = HistoryItem(
            requestId = "missing-1",
            method = HttpMethodType.GET,
            name = "Missing",
            url = "{{baseUrl}}/missing",
            timestamp = currentTimeMillis(),
        )
        state.historyItems.add(0, broken)

        val ok = state.goToCollectionFromHistory(broken)

        assertFalse(ok)
        assertTrue(state.showErrorDialog)
    }
}
