package com.reqlab.ui.desktop.state

import com.reqlab.core.model.HttpMethodType
import org.junit.Test
import kotlin.test.assertEquals
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
    fun markDirty_sets_isDirty_true() {
        val tab = RequestTabState(name = "Test")
        tab.markDirty()
        assertTrue(tab.isDirty)
    }

    @Test
    fun markDirty_always_marks_dirty() {
        val tab = RequestTabState(name = "Test")
        tab.markDirty()
        assertEquals(true, tab.isDirty)
    }

    @Test
    fun markSaved_clears_isDirty() {
        val tab = RequestTabState(name = "Test")
        tab.markDirty()
        assertTrue(tab.isDirty)

        tab.markSaved()
        assertEquals(false, tab.isDirty)
        assertNotNull(tab.lastSavedTimestamp)
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
}
