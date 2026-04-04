package com.reqlab.ui.desktop.integration

import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistorySidebarSyncIntegrationTest {

    @Test
    fun go_to_collection_from_history_syncs_sidebar_scroll_selection_and_active_tab() {
        val state = AppState(withDemoData = true)
        state.collectionExpandedState["c1"] = false

        state.openRequest(
            requestId = "r4",
            name = "Login",
            method = HttpMethodType.POST,
            url = "{{baseUrl}}/auth/login",
        )

        state.recordHistory(
            requestId = "r1",
            method = HttpMethodType.GET,
            name = "Get all users",
            url = "{{baseUrl}}/users",
        )

        val item = state.historyItems.first { it.requestId == "r1" }
        val ok = state.goToCollectionFromHistory(item)

        assertTrue(ok)
        assertEquals("r1", state.selectedRequestId)
        assertTrue(state.openTabs.any { it.id == "r1" })
        assertEquals("r1", state.activeTab?.id)
        assertEquals("r1", state.sidebarScrollToRequestId)
        assertTrue(state.collectionExpandedState["c1"] == true)
    }

    @Test
    fun history_entries_track_collection_changes_for_rename_and_delete() {
        val state = AppState(withDemoData = true)

        state.recordHistory(
            requestId = "r2",
            method = HttpMethodType.GET,
            name = "Get user by ID",
            url = "{{baseUrl}}/users/1",
        )

        state.renameRequestEverywhere("r2", "Get user details")

        val renamed = state.historyItems.first { it.requestId == "r2" }
        assertEquals("Get user details", renamed.name)

        assertTrue(removeRequestById(state.collections, "r2"))
        state.notifyCollectionsChanged()

        assertFalse(state.historyItems.any { it.requestId == "r2" })
    }

    private fun removeRequestById(nodes: MutableList<CollectionNode>, requestId: String): Boolean {
        val iterator = nodes.listIterator()
        while (iterator.hasNext()) {
            val node = iterator.next()
            if (!node.isFolder && node.id == requestId) {
                iterator.remove()
                return true
            }
            if (node.isFolder && removeRequestById(node.children, requestId)) {
                return true
            }
        }
        return false
    }
}
