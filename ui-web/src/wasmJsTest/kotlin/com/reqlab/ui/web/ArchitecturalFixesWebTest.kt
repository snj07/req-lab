package com.reqlab.ui.web

import com.reqlab.ui.shared.components.sharedSidebarTooltipState
import com.reqlab.ui.shared.persistence.TabsRepository
import com.reqlab.ui.shared.persistence.WorkspaceRepository
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Web-side regression tests mirroring ArchitecturalFixesUiTest on desktop.
 *
 *  Issue 1 — Tooltip is a single in-tree overlay (not per-item Popup).
 *  Issue 2 — "Show in sidebar" works after restart because WorkspaceRepository
 *             is loaded BEFORE TabsRepository.
 */
class ArchitecturalFixesWebTest {

    // ── Issue 1: Tooltip state unit tests (no UI rendering needed) ──────────

    @Test
    fun tooltip_state_shows_after_hover_enter() {
        val ts = sharedSidebarTooltipState
        ts.onHoverEnter("id1", "GET Cookies (set + echo) Valid request test")
        assertEquals("id1", ts.hoveredItemId)
        assertEquals("GET Cookies (set + echo) Valid request test", ts.tooltipText)
        assertEquals(false, ts.tooltipVisible)   // coordinator delay not fired
        ts.onHoverExit("id1")
        assertNull(ts.hoveredItemId)
    }

    @Test
    fun tooltip_state_ignores_exit_for_different_item() {
        val ts = sharedSidebarTooltipState
        ts.onHoverEnter("id1", "Request A")
        ts.onHoverExit("id-other")
        assertEquals("id1", ts.hoveredItemId)
        ts.onHoverExit("id1")
    }

    @Test
    fun tooltip_state_replaces_previous_when_new_item_hovered() {
        val ts = sharedSidebarTooltipState
        ts.onHoverEnter("id1", "First Item")
        ts.onHoverEnter("id2", "GET Cookies (set + echo) Valid request test")
        assertEquals("id2", ts.hoveredItemId)
        assertEquals("GET Cookies (set + echo) Valid request test", ts.tooltipText)
        ts.onHoverExit("id2")
    }

    // ── Issue 2: Workspace restore uses correct load order ──────────────────

    @Test
    fun workspaceRestore_correctLoadOrder_resolvesTabIdToCollectionNode() {
        val origin = AppState(withDemoData = true)
        val req = origin.collections.firstOrNull()?.children?.firstOrNull { !it.isFolder }
        assertNotNull(req, "Need at least one request in default collections")

        origin.openRequest(requestId = req.id, name = req.name,
            method = req.method!!, url = req.url!!)
        TabsRepository.save(origin)
        WorkspaceRepository.save(origin)

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)   // collections first
        TabsRepository.load(restored)        // resolves IDs against tree

        val restoredTabId = restored.activeTab?.id
        assertNotNull(restoredTabId, "Active tab must be restored")

        fun findInTree(nodes: List<CollectionNode>, id: String): Boolean =
            nodes.any { it.id == id || findInTree(it.children, id) }

        assertEquals(true, findInTree(restored.collections, restoredTabId),
            "Restored tab ID must match a live collection node")
    }

    @Test
    fun workspaceRestore_wrongLoadOrder_failsToResolveId() {
        val origin = AppState(withDemoData = true)
        val req = origin.collections.firstOrNull()?.children?.firstOrNull { !it.isFolder }
        assertNotNull(req)

        origin.openRequest(requestId = req.id, name = req.name,
            method = req.method!!, url = req.url!!)
        TabsRepository.save(origin)
        WorkspaceRepository.save(origin)

        val restored = AppState(openDefaultTab = false)
        TabsRepository.load(restored)        // wrong order: collections still empty
        WorkspaceRepository.load(restored)

        val restoredTabId = restored.activeTab?.id
        assertNotNull(restoredTabId)

        fun findInTree(nodes: List<CollectionNode>, id: String): Boolean =
            nodes.any { it.id == id || findInTree(it.children, id) }

        assertEquals(false, findInTree(restored.collections, restoredTabId),
            "Wrong load order must NOT resolve ID")
    }

    @Test
    fun workspaceRestore_retainsTabs() {
        val state = AppState(withDemoData = true)
        state.addTabInSelectedCollection()

        TabsRepository.save(state)

        val restoredState = AppState()
        TabsRepository.load(restoredState)

        assertEquals(state.openTabs.size, restoredState.openTabs.size)
    }

    @Test
    fun syncSidebarToActiveTab_setsScrollTarget_afterCorrectRestore() {
        val origin = AppState(withDemoData = true)
        val req = origin.collections.firstOrNull()?.children?.firstOrNull { !it.isFolder }
        assertNotNull(req)

        origin.openRequest(requestId = req.id, name = req.name,
            method = req.method!!, url = req.url!!)
        TabsRepository.save(origin)
        WorkspaceRepository.save(origin)

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)
        TabsRepository.load(restored)
        restored.syncSidebarToActiveTab()

        assertNotNull(restored.sidebarScrollToRequestId,
            "syncSidebarToActiveTab must set sidebarScrollToRequestId after correct restore")
    }
}
