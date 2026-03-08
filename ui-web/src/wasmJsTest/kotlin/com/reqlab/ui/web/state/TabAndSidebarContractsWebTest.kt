package com.reqlab.ui.web.state

import com.reqlab.ui.shared.state.AppState
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
        val state = AppState()
        state.selectedRequestId = null

        state.revealRequestInSidebar("r1")

        assertEquals("r1", state.selectedRequestId)
    }

    @Test
    fun revealRequestInSidebar_sets_selectedRequestId_for_orphan_request_on_web() {
        val state = AppState()
        // "orphan-xyz" exists in no collection — selectedRequestId must still be set.
        state.revealRequestInSidebar("orphan-xyz")

        assertEquals("orphan-xyz", state.selectedRequestId)
    }

    @Test
    fun revealRequestInSidebar_sets_scroll_target_for_known_request_on_web() {
        val state = AppState()
        state.revealRequestInSidebar("r2")

        assertEquals("r2", state.sidebarScrollToRequestId)
    }

    @Test
    fun revealRequestInSidebar_expands_ancestor_collection_on_web() {
        val state = AppState()
        state.collectionExpandedState["c1"] = false

        state.revealRequestInSidebar("r1")

        assertTrue(state.collectionExpandedState["c1"] == true)
    }
}
