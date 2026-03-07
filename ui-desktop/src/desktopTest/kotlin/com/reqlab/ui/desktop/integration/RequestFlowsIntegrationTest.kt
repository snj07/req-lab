package com.reqlab.ui.desktop.integration

import com.reqlab.ui.desktop.components.deleteRequestFromCollections
import com.reqlab.ui.desktop.components.duplicateRequestInCollections
import com.reqlab.ui.desktop.components.moveRequestAfterRequest
import com.reqlab.ui.desktop.components.moveRequestBeforeRequest
import com.reqlab.ui.desktop.persistence.TabsRepository
import com.reqlab.ui.desktop.state.AppState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestFlowsIntegrationTest {

    @Test
    fun duplicate_request_flow_updates_collection_state() {
        val state = AppState()
        val usersCollection = state.collections.first()
        val before = usersCollection.children.size

        val duplicatedName = duplicateRequestInCollections(state.collections, "r1")

        assertNotNull(duplicatedName)
        assertEquals(before + 1, usersCollection.children.size)
        assertTrue(usersCollection.children.any { it.name == duplicatedName })
    }

    @Test
    fun delete_request_flow_updates_collection_state() {
        val state = AppState()
        val usersCollection = state.collections.first()
        val before = usersCollection.children.size

        val removed = deleteRequestFromCollections(state.collections, "r2")

        assertTrue(removed)
        assertEquals(before - 1, usersCollection.children.size)
        assertFalse(usersCollection.children.any { it.id == "r2" })
    }

    @Test
    fun drag_reorder_before_updates_collection_order() {
        val state = AppState()
        val usersCollection = state.collections.first()

        val moved = moveRequestBeforeRequest(state.collections, "r3", "r1")

        assertTrue(moved)
        assertEquals("r3", usersCollection.children.first().id)
    }

    @Test
    fun drag_reorder_after_updates_collection_order() {
        val state = AppState()
        val usersCollection = state.collections.first()

        // Move r1 to after r2 → order should be r2, r1, r3
        val moved = moveRequestAfterRequest(state.collections, "r1", "r2")

        assertTrue(moved)
        assertEquals(3, usersCollection.children.size)
        assertEquals("r2", usersCollection.children[0].id)
        assertEquals("r1", usersCollection.children[1].id)
        assertEquals("r3", usersCollection.children[2].id)
    }

    @Test
    fun dirty_state_resets_on_save() {
        val state = AppState()
        val tab = state.activeTab ?: error("active tab missing")
        tab.url = "https://example.com"
        tab.markDirty()
        assertTrue(tab.isDirty)

        TabsRepository.save(state)
        tab.markSaved()

        assertFalse(tab.isDirty)
    }

    // ── Mutation-speed guards ──────────────────────────────────────

    /**
     * State mutation (add request) + notifyCollectionsChanged must complete within 30 ms.
     * This guards against accidentally putting blocking work on the mutation path.
     */
    @Test
    fun add_request_mutation_completes_within_30ms() {
        val state = AppState()
        val collectionId = state.collections.first().id

        val start = System.currentTimeMillis()
        state.addRequestToCollection(collectionId)
        state.notifyCollectionsChanged()
        val elapsed = System.currentTimeMillis() - start

        assertTrue(elapsed < 30, "add mutation took ${elapsed}ms (expected <30ms)")
    }

    /**
     * State mutation (delete request) + notifyCollectionsChanged must complete within 30 ms.
     */
    @Test
    fun delete_request_mutation_completes_within_30ms() {
        val state = AppState()

        val start = System.currentTimeMillis()
        deleteRequestFromCollections(state.collections, "r1")
        state.notifyCollectionsChanged()
        val elapsed = System.currentTimeMillis() - start

        assertTrue(elapsed < 30, "delete mutation took ${elapsed}ms (expected <30ms)")
    }

    // ── Tab management integration ─────────────────────────────────

    @Test
    fun close_tab_removes_it_from_open_tabs() {
        val state = AppState()
        state.addTab(name = "Extra Tab")
        val idToClose = state.activeTab!!.id
        val before = state.openTabs.size

        state.closeTab(state.activeTabIndex)

        assertEquals(before - 1, state.openTabs.size)
        assertFalse(state.openTabs.any { it.id == idToClose })
    }

    @Test
    fun close_tab_cannot_close_last_remaining_tab() {
        val state = AppState()
        assertEquals(1, state.openTabs.size)

        state.closeTab(0)

        // Still 1 tab - cannot drop below 1
        assertEquals(1, state.openTabs.size)
    }

    @Test
    fun active_tab_index_adjusted_after_closing_tab_to_the_left() {
        val state = AppState()
        state.addTab(name = "B")
        state.addTab(name = "C")
        // Tabs: [0]=Untitled, [1]=B, [2]=C; active = 2

        state.activeTabIndex = 2
        state.closeTab(0)   // close first tab → tabs shift left
        // After close: [0]=B, [1]=C; active should be coerced to last valid
        assertTrue(state.activeTabIndex <= state.openTabs.lastIndex)
    }

    @Test
    fun tab_ordering_preserved_after_move() {
        val state = AppState()
        state.addTab(name = "B")
        state.addTab(name = "C")
        // tabs: [0]=Untitled, [1]=B, [2]=C
        val idA = state.openTabs[0].id
        val idB = state.openTabs[1].id

        state.moveTab(0, 1)  // move Untitled after B
        // expected: [0]=B, [1]=Untitled, [2]=C
        assertEquals(idB, state.openTabs[0].id)
        assertEquals(idA, state.openTabs[1].id)
    }

    @Test
    fun dirty_tab_flag_cleared_after_markSaved() {
        val state = AppState()
        state.activeTab!!.markDirty()
        assertTrue(state.activeTab!!.isDirty)

        state.activeTab!!.markSaved()
        assertFalse(state.activeTab!!.isDirty)
    }
}
