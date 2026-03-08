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
        val state = AppState()
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
        val state = AppState()
        val tabsBefore = state.openTabs.size

        state.addRequestToCollection("nonexistent-id")

        assertEquals(tabsBefore, state.openTabs.size)
    }

    @Test
    fun addTabInSelectedCollection_uses_selected_collection() {
        val state = AppState()
        val secondCollection = state.collections[1]
        state.selectedCollectionId = secondCollection.id
        val childrenBefore = secondCollection.children.size

        state.addTabInSelectedCollection()

        assertEquals(childrenBefore + 1, secondCollection.children.size)
    }

    @Test
    fun addTabInSelectedCollection_falls_back_to_first_collection() {
        val state = AppState()
        state.selectedCollectionId = null
        val firstCollection = state.collections.first()
        val childrenBefore = firstCollection.children.size

        state.addTabInSelectedCollection()

        assertEquals(childrenBefore + 1, firstCollection.children.size)
    }

    @Test
    fun addTabInSelectedCollection_creates_orphan_tab_when_no_collections() {
        val state = AppState()
        state.collections.clear()
        state.selectedCollectionId = null
        val tabsBefore = state.openTabs.size

        state.addTabInSelectedCollection()

        assertEquals(tabsBefore + 1, state.openTabs.size)
    }

    @Test
    fun selectedCollectionId_updates_on_assignment() {
        val state = AppState()
        assertNull(state.selectedCollectionId)

        state.selectedCollectionId = "c1"
        assertEquals("c1", state.selectedCollectionId)
    }

    @Test
    fun addRequestToCollection_generates_unique_name() {
        val state = AppState()
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
        val state = AppState()

        state.collapseAllCollections()
        assertTrue(state.collectionExpandedState.values.all { expanded -> !expanded })

        state.expandAllCollections()
        assertTrue(state.collectionExpandedState.values.all { expanded -> expanded })
    }

    @Test
    fun renameRequestEverywhere_updates_tab_and_sidebar_node_name() {
        val state = AppState()
        val requestId = "r1"

        state.openRequest(requestId = requestId, name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.renameRequestEverywhere(requestId, "Get all users v2")

        assertTrue(state.openTabs.any { it.id == requestId && it.name == "Get all users v2" })
        assertTrue(state.collections.flatMap { it.children }.any { it.id == requestId && it.name == "Get all users v2" })
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
        val state = AppState()
        state.selectedRequestId = null

        state.revealRequestInSidebar("r1")

        assertEquals("r1", state.selectedRequestId)
    }

    @Test
    fun revealRequestInSidebar_expands_ancestor_folder_for_collection_request() {
        val state = AppState()
        // Collapse the parent collection "c1" first
        state.collectionExpandedState["c1"] = false

        state.revealRequestInSidebar("r1")

        // Ancestor must be expanded so the node becomes visible
        assertTrue(state.collectionExpandedState["c1"] == true)
    }

    @Test
    fun revealRequestInSidebar_sets_selectedRequestId_even_for_orphan_request() {
        val state = AppState()
        // "orphan-999" is NOT in any collection; revealRequestInSidebar must
        // still record it as selected (it won't expand anything, but it
        // correctly tracks which request should be highlighted).
        state.revealRequestInSidebar("orphan-999")

        assertEquals("orphan-999", state.selectedRequestId)
    }

    @Test
    fun revealRequestInSidebar_sets_scroll_target_for_collection_request() {
        val state = AppState()

        state.revealRequestInSidebar("r2")

        assertEquals("r2", state.sidebarScrollToRequestId)
    }
}
