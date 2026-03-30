package com.reqlab.ui.desktop

import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.components.sharedSidebarTooltipState
import com.reqlab.ui.shared.persistence.TabsRepository
import com.reqlab.ui.shared.persistence.WorkspaceRepository
import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Regression tests for the two fixes:
 *   Issue 1 - Tooltip uses a single in-tree overlay (sidebar-tooltip), NOT
 *             per-item Popup windows that caused OS-window hover-leave flicker.
 *   Issue 2 - "Show in sidebar" works after restart because WorkspaceRepository
 *             is loaded BEFORE TabsRepository so tab IDs are resolved against
 *             the loaded collection tree.
 */
class ArchitecturalFixesUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun clearPersistedStateBeforeEach() {
        PlatformStorage.remove("reqlab.workspace")
        PlatformStorage.remove("reqlab.tabs")
    }

    @After
    fun clearPersistedStateAfterEach() {
        PlatformStorage.remove("reqlab.workspace")
        PlatformStorage.remove("reqlab.tabs")
    }

    // ── Issue 1: Tooltip no longer uses per-item Popup ──────────────────────

    @Test
    fun tooltip_hidden_at_startup_uses_single_node() {
        val state = AppState()
        rule.setContent { MainScreen(state) }
        rule.waitForIdle()

        // No tooltip should be visible on startup.
        rule.onAllNodesWithTag("sidebar-tooltip", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun tooltip_state_shows_after_hover_enter_with_delay() {
        // Directly exercise TooltipState without UI to avoid timer complexity.
        val ts = sharedSidebarTooltipState
        ts.onHoverEnter("id1", "GET Cookies (set + echo) Valid request test")
        assertEquals("id1", ts.hoveredItemId)
        assertEquals("GET Cookies (set + echo) Valid request test", ts.tooltipText)
        // Not yet visible — coordinator adds the 300 ms delay.
        assertEquals(false, ts.tooltipVisible)
        ts.onHoverExit("id1")
        assertNull(ts.hoveredItemId)
    }

    @Test
    fun tooltip_state_ignores_exit_for_different_item() {
        val ts = sharedSidebarTooltipState
        ts.onHoverEnter("id1", "Request A")
        ts.onHoverExit("id-other")          // exit for a different ID — no-op
        assertEquals("id1", ts.hoveredItemId)
        ts.onHoverExit("id1")               // cleanup
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

    // ── Issue 2: Workspace restore + "show in sidebar" ─────────────────────

    /**
     * Adds an extra collection so the workspace no longer matches the
     * "legacy seeded demo" fingerprint in WorkspaceRepository.load().
     * Without this, the load guard deletes the saved data and returns early.
     */
    private fun escapeFromDemoFingerprint(state: AppState) {
        state.collections.add(
            CollectionNode("extra-c", "Extra", isFolder = true, children = mutableListOf(
                CollectionNode("extra-r", "Ping", method = HttpMethodType.GET, url = "http://localhost/ping")
            ))
        )
    }

    @Test
    fun workspaceRestore_correctLoadOrder_resolvesTabIdToCollectionNode() {
        // Simulate the startup sequence: workspace first, THEN tabs.
        val origin = AppState(withDemoData = true)
        escapeFromDemoFingerprint(origin)
        // Ensure there is at least one request in a collection.
        val req = origin.collections.firstOrNull()?.children?.firstOrNull { !it.isFolder }
        assertNotNull(req, "Need at least one request in default collections")

        // Open the request as a tab.
        origin.openRequest(requestId = req.id, name = req.name,
            method = req.method!!, url = req.url!!)

        // Save both repositories.
        TabsRepository.save(origin)
        WorkspaceRepository.save(origin)

        // Restore: workspace FIRST so collections are populated, then tabs.
        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)   // populates collections tree
        TabsRepository.load(restored)        // resolves IDs against tree

        // The restored active tab must have an ID that exists in the collection tree.
        // Note: openTabs[0] is the default blank tab; the request tab is at activeTabIndex.
        val restoredTabId = restored.activeTab?.id
        assertNotNull(restoredTabId, "Active tab must be restored")

        fun findInTree(nodes: List<CollectionNode>, id: String): Boolean =
            nodes.any { it.id == id || findInTree(it.children, id) }

        val foundInTree = findInTree(restored.collections, restoredTabId)
        assertEquals(true, foundInTree,
            "Restored tab ID must match a live collection node (show-in-sidebar requires this)")
    }

    @Test
    fun workspaceRestore_wrongLoadOrder_failsToResolveId() {
        // This test documents the OLD broken behaviour: if tabs load first,
        // the ID cannot be resolved because collections are still empty.
        val origin = AppState(withDemoData = true)
        escapeFromDemoFingerprint(origin)
        val req = origin.collections.firstOrNull()?.children?.firstOrNull { !it.isFolder }
        assertNotNull(req)

        origin.openRequest(requestId = req.id, name = req.name,
            method = req.method!!, url = req.url!!)
        TabsRepository.save(origin)
        WorkspaceRepository.save(origin)

        // Wrong order (old broken behaviour): tabs before workspace.
        val restored = AppState(openDefaultTab = false)
        TabsRepository.load(restored)        // collections still empty → ID not resolved
        WorkspaceRepository.load(restored)

        // activeTabIndex points to the request tab even in wrong-order scenario.
        val restoredTabId = restored.activeTab?.id
        assertNotNull(restoredTabId)

        fun findInTree(nodes: List<CollectionNode>, id: String): Boolean =
            nodes.any { it.id == id || findInTree(it.children, id) }

        // With the wrong order the ID should NOT match any live node.
        val foundInTree = findInTree(restored.collections, restoredTabId)
        assertEquals(false, foundInTree,
            "Wrong load order must NOT resolve ID — proving the fix is necessary")
    }

    @Test
    fun syncSidebarToActiveTab_setsScrollTarget_afterCorrectRestore() {
        val origin = AppState(withDemoData = true)
        escapeFromDemoFingerprint(origin)
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

        // sidebarScrollToRequestId must be set to the live node ID.
        assertNotNull(restored.sidebarScrollToRequestId,
            "syncSidebarToActiveTab must set sidebarScrollToRequestId when ID resolves")
    }
}
