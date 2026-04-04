package com.reqlab.ui.web.state

import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Web (wasmJs) contract tests verifying that the tab-management and sidebar
 * selection fixes work identically in the web target.
 *
 * These are pure state-logic tests — no Compose UI or platform test runner
 * is required, matching the existing VariablePopupContractsWebTest pattern.
 */
class TabAndSidebarContractsWebTest {

    // ── Issue 1: Closing last tab ────────────────────────────────────

    @Test
    fun closeTab_closing_last_tab_empties_openTabs_on_web() {
        val state = AppState()
        assertEquals(1, state.openTabs.size)

        state.closeTab(0)

        assertTrue(state.openTabs.isEmpty())
    }

    @Test
    fun closeTab_closing_last_tab_sets_activeTabIndex_to_minus_one_on_web() {
        val state = AppState()
        state.closeTab(0)

        assertEquals(-1, state.activeTabIndex)
    }

    @Test
    fun closeTab_closing_last_tab_clears_activeTab_on_web() {
        val state = AppState()
        state.closeTab(0)

        assertNull(state.activeTab)
    }

    @Test
    fun closeTab_does_not_retain_closed_tab_in_openTabs_on_web() {
        val state = AppState()
        val lastTabId = state.openTabs.first().id

        state.closeTab(0)

        assertFalse(state.openTabs.any { it.id == lastTabId })
    }

    // ── Issue 2: revealRequestInSidebar always selects ──────────────

    @Test
    fun revealRequestInSidebar_always_sets_selectedRequestId_on_web() {
        val state = AppState(withDemoData = true)
        state.selectedRequestId = null

        state.revealRequestInSidebar("r1")

        assertEquals("r1", state.selectedRequestId)
    }

    @Test
    fun revealRequestInSidebar_sets_selectedRequestId_for_orphan_request_on_web() {
        val state = AppState()
        // "orphan-xyz" exists in no collection — request should not be revealed.
        val revealed = state.revealRequestInSidebar("orphan-xyz")

        assertFalse(revealed)
        assertNull(state.selectedRequestId)
    }

    @Test
    fun revealRequestInSidebar_sets_scroll_target_for_known_request_on_web() {
        val state = AppState(withDemoData = true)
        state.revealRequestInSidebar("r2")

        assertEquals("r2", state.sidebarScrollToRequestId)
    }

    @Test
    fun revealRequestInSidebar_expands_ancestor_collection_on_web() {
        val state = AppState(withDemoData = true)
        state.collectionExpandedState["c1"] = false

        state.revealRequestInSidebar("r1")

        assertTrue(state.collectionExpandedState["c1"] == true)
    }

    // ── Issue 1 (new): Show in Sidebar switches active tab ──────────

    @Test
    fun revealRequestInSidebar_switches_active_tab_to_matching_request_on_web() {
        val state = AppState(withDemoData = true)
        state.openRequest(requestId = "r1", name = "A", method = HttpMethodType.GET, url = "u1")
        state.openRequest(requestId = "r4", name = "B", method = HttpMethodType.POST, url = "u2")
        assertEquals("r4", state.activeTab?.id)

        state.revealRequestInSidebar("r1")

        assertEquals("r1", state.selectedRequestId)
        assertEquals("r1", state.activeTab?.id)
    }

    // ── Issue 3 (new): syncSidebarToActiveTab ───────────────────────

    @Test
    fun syncSidebarToActiveTab_sets_selectedRequestId_and_expands_ancestors_on_web() {
        val state = AppState(withDemoData = true)
        state.openRequest(requestId = "r1", name = "A", method = HttpMethodType.GET, url = "u1")
        state.collectionExpandedState["c1"] = false
        state.selectedRequestId = null

        state.syncSidebarToActiveTab()

        assertEquals("r1", state.selectedRequestId)
        assertTrue(state.collectionExpandedState["c1"] == true)
    }

    @Test
    fun syncSidebarToActiveTab_does_nothing_when_no_tabs_open_on_web() {
        val state = AppState(openDefaultTab = false)
        state.selectedRequestId = null

        state.syncSidebarToActiveTab()

        assertNull(state.selectedRequestId)
    }

    @Test
    fun tab_switch_syncs_sidebar_selection_on_web() {
        val state = AppState(openDefaultTab = false)
        state.openRequest(requestId = "r1", name = "A", method = HttpMethodType.GET, url = "u1")
        state.openRequest(requestId = "r4", name = "B", method = HttpMethodType.POST, url = "u2")

        state.activeTabIndex = 0
        state.syncSidebarToActiveTab()
        assertEquals("r1", state.selectedRequestId)

        state.activeTabIndex = 1
        state.syncSidebarToActiveTab()
        assertEquals("r4", state.selectedRequestId)
    }

    @Test
    fun restored_tab_id_matches_collection_node_id_on_web() {
        val state = AppState(openDefaultTab = false, withDemoData = true)
        state.openTabs.add(RequestTabState(id = "r1", name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users"))
        state.activeTabIndex = 0

        state.syncSidebarToActiveTab()

        assertEquals("r1", state.selectedRequestId)
        assertEquals("r1", state.sidebarScrollToRequestId)
    }
}
