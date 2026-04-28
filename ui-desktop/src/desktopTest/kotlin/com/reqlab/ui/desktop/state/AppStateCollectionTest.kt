package com.reqlab.ui.shared.state

import com.reqlab.core.model.HttpMethodType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateCollectionTest {

    @Test
    fun addRequestToCollection_adds_child_and_opens_tab() {
        val state = AppState(withDemoData = true)
        val collectionId = state.collections.first().id
        val childrenBefore = state.collections.first().children.size
        val tabsBefore = state.openTabs.size

        state.addRequestToCollection(collectionId)

        assertEquals(childrenBefore + 1, state.collections.first().children.size)
        assertEquals(tabsBefore + 1, state.openTabs.size)
        assertEquals(collectionId, state.selectedCollectionId)
    }

    @Test
    fun addRequestToCollection_ignores_nonexistent_collection() {
        val state = AppState(withDemoData = true)
        val tabsBefore = state.openTabs.size

        state.addRequestToCollection("nonexistent-id")

        assertEquals(tabsBefore, state.openTabs.size)
    }

    @Test
    fun addTabInSelectedCollection_uses_selected_collection() {
        val state = AppState(withDemoData = true)
        val secondCollection = state.collections[1]
        state.selectedCollectionId = secondCollection.id
        val childrenBefore = secondCollection.children.size

        state.addTabInSelectedCollection()

        assertEquals(childrenBefore + 1, secondCollection.children.size)
    }

    @Test
    fun addTabInSelectedCollection_falls_back_to_first_collection() {
        val state = AppState(withDemoData = true)
        state.selectedCollectionId = null
        val firstCollection = state.collections.first()
        val childrenBefore = firstCollection.children.size

        state.addTabInSelectedCollection()

        assertEquals(childrenBefore + 1, firstCollection.children.size)
    }

    @Test
    fun addTabInSelectedCollection_creates_orphan_tab_when_no_collections() {
        val state = AppState(withDemoData = true)
        state.collections.clear()
        state.selectedCollectionId = null
        val tabsBefore = state.openTabs.size

        state.addTabInSelectedCollection()

        assertEquals(tabsBefore + 1, state.openTabs.size)
    }

    @Test
    fun selectedCollectionId_updates_on_assignment() {
        val state = AppState(withDemoData = true)
        assertNull(state.selectedCollectionId)

        state.selectedCollectionId = "c1"
        assertEquals("c1", state.selectedCollectionId)
    }

    @Test
    fun addRequestToCollection_generates_unique_name() {
        val state = AppState(withDemoData = true)
        val collectionId = state.collections.first().id

        state.addRequestToCollection(collectionId)
        state.addRequestToCollection(collectionId)

        val children = state.collections.first().children
        val newNames = children.takeLast(2).map { it.name }
        // Both should be different
        assertEquals(2, newNames.toSet().size)
    }

    @Test
    fun markDirty_sets_isDirty_true_when_state_differs_from_saved_snapshot() {
        val tab = RequestTabState(name = "Test")
        tab.url = "https://example.com"
        tab.markDirty()
        assertTrue(tab.isDirty)
    }

    @Test
    fun markDirty_keeps_clean_when_state_matches_saved_snapshot() {
        val tab = RequestTabState(name = "Test")
        tab.markDirty()
        assertEquals(false, tab.isDirty)
    }

    @Test
    fun markSaved_clears_isDirty() {
        val tab = RequestTabState(name = "Test")
        tab.url = "https://changed.example.com"
        tab.markDirty()
        assertTrue(tab.isDirty)

        tab.markSaved()
        assertEquals(false, tab.isDirty)
        assertNotNull(tab.lastSavedTimestamp)
    }

    @Test
    fun markSaved_large_body_uses_lightweight_saved_snapshot() {
        val tab = RequestTabState(name = "Large")
        tab.bodyContent = "{\"items\":[" + (0 until 320_000).joinToString(",") { "{\"k\":$it}" } + "]}"
        tab.markDirty()
        assertTrue(tab.isDirty)

        tab.markSaved()

        assertFalse(tab.isDirty)
        val persistedSnapshot = tab.savedSnapshotForPersistence()
        assertTrue(persistedSnapshot.startsWith("LARGE#"),
            "Large-body markSaved must persist lightweight snapshot prefix")
        assertTrue(persistedSnapshot.length < 10_000,
            "Saved snapshot for large body must stay compact for responsive saves")
    }

    @Test
    fun markDirty_after_large_saved_snapshot_sets_dirty_without_full_snapshot_compare() {
        val tab = RequestTabState(name = "Large")
        tab.bodyContent = "x".repeat(250_000)
        tab.markSaved()
        assertFalse(tab.isDirty)

        tab.bodyContent += "y"
        tab.markDirty()

        assertTrue(tab.isDirty)
    }

    @Test
    fun dirty_state_resets_when_changes_are_reverted_to_saved_snapshot() {
        val tab = RequestTabState(name = "Test", url = "https://example.com")
        tab.markSaved()

        tab.url = "https://example.com/v2"
        tab.markDirty()
        assertTrue(tab.isDirty)

        tab.url = "https://example.com"
        tab.markDirty()
        assertEquals(false, tab.isDirty)
    }

    @Test
    fun moveTab_swaps_positions() {
        val state = AppState()
        state.addTab(name = "Tab A")
        state.addTab(name = "Tab B")
        val tabB = state.openTabs.last()
        val fromIndex = state.openTabs.indexOf(tabB)

        state.moveTab(fromIndex, fromIndex - 1)

        assertEquals(tabB, state.openTabs[fromIndex - 1])
    }

    @Test
    fun closeTab_removes_tab_and_adjusts_activeIndex() {
        val state = AppState()
        state.addTab(name = "Tab 2")
        val countBefore = state.openTabs.size
        state.activeTabIndex = countBefore - 1

        state.closeTab(countBefore - 1)

        assertEquals(countBefore - 1, state.openTabs.size)
        assertTrue(state.activeTabIndex < state.openTabs.size)
    }

    @Test
    fun openRequest_sets_selectedRequestId() {
        val state = AppState()
        state.openRequest(requestId = "r99", name = "R", method = HttpMethodType.GET, url = "https://x")
        assertEquals("r99", state.selectedRequestId)
    }

    @Test
    fun addTab_does_not_duplicate_existing_request_id() {
        val state = AppState()
        val existingId = state.openTabs.first().id
        val before = state.openTabs.size

        state.addTab(requestId = existingId, name = "Duplicate", method = HttpMethodType.POST, url = "https://x")

        assertEquals(before, state.openTabs.size)
        assertEquals(existingId, state.activeTab?.id)
    }

    @Test
    fun collapse_and_expand_all_collections_updates_folder_state_map() {
        val state = AppState(withDemoData = true)

        state.collapseAllCollections()
        assertTrue(state.collectionExpandedState.values.all { expanded -> !expanded })

        state.expandAllCollections()
        assertTrue(state.collectionExpandedState.values.all { expanded -> expanded })
    }

    @Test
    fun renameRequestEverywhere_updates_tab_and_sidebar_node_name() {
        val state = AppState(withDemoData = true)
        val requestId = "r1"

        state.openRequest(requestId = requestId, name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.renameRequestEverywhere(requestId, "Get all users v2")

        assertTrue(state.openTabs.any { it.id == requestId && it.name == "Get all users v2" })
        assertTrue(state.collections.flatMap { it.children }.any { it.id == requestId && it.name == "Get all users v2" })
    }

    @Test
    fun renameRequestEverywhere_name_persists_after_syncTabToCollectionNode() {
        // Regression test for Bug 2: rename from sidebar used to be overwritten
        // when the user pressed Cmd+S because the open tab still held the old name.
        val state = AppState(withDemoData = true)
        val requestId = "r1"

        state.openRequest(requestId = requestId, name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.renameRequestEverywhere(requestId, "Renamed request")

        val tab = state.openTabs.first { it.id == requestId }
        // Simulate Cmd+S: syncTabToCollectionNode writes tab.name back to node
        state.syncTabToCollectionNode(tab)

        // Both must still carry the new name
        assertEquals("Renamed request", tab.name)
        val node = state.collections.flatMap { it.children }.first { it.id == requestId }
        assertEquals("Renamed request", node.name)
    }

    // ── Issue 1: Closing last tab ────────────────────────────────────

    @Test
    fun closeTab_closing_last_tab_sets_active_index_to_minus_one() {
        val state = AppState()
        assertEquals(1, state.openTabs.size)

        state.closeTab(0)

        assertTrue(state.openTabs.isEmpty())
        assertEquals(-1, state.activeTabIndex)
        assertEquals(null, state.activeTab)
    }

    @Test
    fun closeTab_closing_last_tab_clears_selectedRequestId() {
        val state = AppState()
        state.selectedRequestId = state.openTabs.first().id

        state.closeTab(0)

        assertEquals(null, state.selectedRequestId)
    }

    @Test
    fun closeTab_no_longer_blocks_closing_single_tab() {
        val state = AppState()
        val singleTabId = state.openTabs.first().id

        state.closeTab(0)

        // Previously this returned early when size <= 1; now it must close.
        assertFalse(state.openTabs.any { it.id == singleTabId })
    }

    // ── Issue 2: revealRequestInSidebar always selects ──────────────

    @Test
    fun revealRequestInSidebar_sets_selectedRequestId_for_collection_request() {
        val state = AppState(withDemoData = true)
        state.selectedRequestId = null

        state.revealRequestInSidebar("r1")

        assertEquals("r1", state.selectedRequestId)
    }

    @Test
    fun revealRequestInSidebar_expands_ancestor_folder_for_collection_request() {
        val state = AppState(withDemoData = true)
        // Collapse the parent collection "c1" first
        state.collectionExpandedState["c1"] = false

        state.revealRequestInSidebar("r1")

        // Ancestor must be expanded so the node becomes visible
        assertTrue(state.collectionExpandedState["c1"] == true)
    }

    @Test
    fun revealRequestInSidebar_sets_selectedRequestId_even_for_orphan_request() {
        val state = AppState(withDemoData = true)
        // "orphan-999" is NOT in any collection; revealRequestInSidebar should
        // fail and surface a not-found error.
        val ok = state.revealRequestInSidebar("orphan-999")

        assertFalse(ok)
        assertTrue(state.showErrorDialog)
    }

    @Test
    fun revealRequestInSidebar_sets_scroll_target_for_collection_request() {
        val state = AppState(withDemoData = true)

        state.revealRequestInSidebar("r2")

        assertEquals("r2", state.sidebarScrollToRequestId)
    }

    @Test
    fun revealRequestInSidebar_for_request_not_in_history_does_not_mutate_history() {
        val state = AppState(withDemoData = true)
        // Ensure r3 exists in collections but is not currently in demo history.
        assertTrue(state.historyItems.none { it.requestId == "r3" })

        // Add a legacy/broken history entry to verify reveal does not opportunistically clean it up.
        state.historyItems.add(
            0,
            com.reqlab.ui.shared.state.HistoryItem(
                requestId = "legacy-missing",
                method = HttpMethodType.GET,
                name = "Legacy Missing",
                url = "{{baseUrl}}/legacy",
                timestamp = 1L,
            ),
        )
        val beforeIds = state.historyItems.map { it.requestId }

        val ok = state.revealRequestInSidebar("r3")

        assertTrue(ok)
        assertEquals(beforeIds, state.historyItems.map { it.requestId })
        assertEquals("r3", state.selectedRequestId)
    }

    // ── Issue 1: Show in Sidebar switches active tab ──────────────

    @Test
    fun revealRequestInSidebar_switches_active_tab_to_matching_request() {
        val state = AppState(withDemoData = true)
        // Open two requests from collections as tabs
        state.openRequest(requestId = "r1", name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.openRequest(requestId = "r4", name = "Login", method = HttpMethodType.POST, url = "{{baseUrl}}/auth/login")
        // Active tab is now r4 (the last opened)
        assertEquals("r4", state.activeTab?.id)

        // Reveal r1 in sidebar — must also switch the active tab
        state.revealRequestInSidebar("r1")

        assertEquals("r1", state.selectedRequestId)
        assertEquals("r1", state.activeTab?.id)
    }

    // ── Issue 3: syncSidebarToActiveTab after restoration ─────────

    @Test
    fun syncSidebarToActiveTab_sets_selectedRequestId_and_expands_ancestors() {
        val state = AppState(withDemoData = true)
        // Simulate restored state: tab open for r1, ancestors collapsed
        state.openRequest(requestId = "r1", name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.collectionExpandedState["c1"] = false
        state.selectedRequestId = null

        state.syncSidebarToActiveTab()

        assertEquals("r1", state.selectedRequestId)
        assertTrue(state.collectionExpandedState["c1"] == true, "Parent folder should be expanded")
    }

    @Test
    fun syncSidebarToActiveTab_does_nothing_when_no_tabs_open() {
        val state = AppState(openDefaultTab = false)
        state.selectedRequestId = null

        state.syncSidebarToActiveTab()

        assertNull(state.selectedRequestId)
    }

    @Test
    fun tab_switch_syncs_sidebar_selection() {
        val state = AppState(openDefaultTab = false)
        state.openRequest(requestId = "r1", name = "A", method = HttpMethodType.GET, url = "u1")
        state.openRequest(requestId = "r4", name = "B", method = HttpMethodType.POST, url = "u2")

        // Switch to first tab (r1)
        state.activeTabIndex = 0
        state.syncSidebarToActiveTab()

        assertEquals("r1", state.selectedRequestId)

        // Switch to second tab (r4)
        state.activeTabIndex = 1
        state.syncSidebarToActiveTab()

        assertEquals("r4", state.selectedRequestId)
    }

    // ── Workspace restoration: restored tab IDs match collection IDs ──

    @Test
    fun restored_tab_id_matches_collection_node_id() {
        // Simulate: TabsRepository restores a tab with id "r1",
        // WorkspaceRepository restores collections containing node id "r1".
        // After syncSidebarToActiveTab, sidebar should highlight it.
        val state = AppState(openDefaultTab = false, withDemoData = true)
        // Manually add tab as TabsRepository.load would
        state.openTabs.add(RequestTabState(id = "r1", name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users"))
        state.activeTabIndex = 0
        // Collections already have r1 from the default AppState collections
        // Now sync
        state.syncSidebarToActiveTab()

        assertEquals("r1", state.selectedRequestId)
        assertEquals("r1", state.sidebarScrollToRequestId)
    }
}
