package com.reqlab.ui.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstLaunchStateTest {

    @Test
    fun defaults_start_clean_without_demo_data() {
        val state = AppState(openDefaultTab = false)

        assertTrue(state.openTabs.isEmpty())
        assertEquals(-1, state.activeTabIndex)
        assertTrue(state.collections.isEmpty())
        assertTrue(state.historyItems.isEmpty())
        assertTrue(state.globalVariables.isEmpty())
    }

    @Test
    fun defaults_have_zero_environments() {
        val state = AppState(openDefaultTab = false)

        assertTrue(state.environments.isEmpty())
        assertEquals(null, state.selectedEnvironment)
        assertTrue(state.activeVariableLayers().first().isEmpty())
    }

    @Test
    fun collections_and_environments_are_collapsed_by_default() {
        val state = AppState(openDefaultTab = false)

        assertFalse(state.collectionsExpanded)
        assertFalse(state.environmentsExpanded)
        assertFalse(state.historyExpanded)
    }

    @Test
    fun auto_save_requests_is_disabled_by_default() {
        val state = AppState(openDefaultTab = false)
        assertFalse(state.settings.autoSaveRequests)
    }
}
