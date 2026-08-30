package com.reqlab.ui.shared.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateBehaviorTest {

    @Test
    fun create_request_on_empty_workspace_creates_default_collection_and_request() {
        val state = AppState(openDefaultTab = false)

        state.addTabInSelectedCollection()

        assertEquals(1, state.collections.size)
        val defaultCollection = state.collections.first()
        assertTrue(defaultCollection.isFolder)
        assertEquals("Default Collection", defaultCollection.name)
        assertEquals(defaultCollection.id, state.selectedCollectionId)
        assertEquals(1, defaultCollection.children.size)
        assertFalse(defaultCollection.children.first().isFolder)
        assertEquals(1, state.openTabs.size)
        assertEquals(0, state.activeTabIndex)
    }

    @Test
    fun create_request_with_existing_collection_adds_request_to_that_collection() {
        val state = AppState(openDefaultTab = false)
        val collection = CollectionNode(
            id = "coll-1",
            name = "My Collection",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(),
        )
        state.collections.add(collection)

        state.addTabInSelectedCollection()

        assertEquals(1, state.collections.size)
        assertEquals(1, collection.children.size)
        assertFalse(collection.children.first().isFolder)
        assertEquals(collection.id, state.selectedCollectionId)
        assertEquals(1, state.openTabs.size)
    }

    @Test
    fun prune_empty_global_variables_removes_blank_rows_only() {
        val state = AppState(openDefaultTab = false)
        state.globalVariables.add(MutableKeyValue("", ""))
        state.globalVariables.add(MutableKeyValue("apiKey", ""))
        state.globalVariables.add(MutableKeyValue("", "token"))

        state.pruneEmptyGlobalVariables()

        assertEquals(2, state.globalVariables.size)
        assertTrue(state.globalVariables.any { it.key == "apiKey" })
        assertTrue(state.globalVariables.any { it.value == "token" })
    }

    @Test
    fun prune_empty_environment_variables_removes_blank_rows_only() {
        val state = AppState(openDefaultTab = false)
        state.environments.add(EnvState("Dev"))
        val env = state.environments.first()
        env.variables.add(MutableKeyValue("", ""))
        env.variables.add(MutableKeyValue("baseUrl", ""))

        state.pruneEmptyVariablesForEnvironment(0)

        assertEquals(1, env.variables.size)
        assertEquals("baseUrl", env.variables.first().key)
    }

    @Test
    fun auto_save_requests_default_is_false() {
        val settings = AppSettings()
        assertFalse(settings.autoSaveRequests)
    }

    // ── System header value editability ────────────────────────────────────

    @Test
    fun syncSystemHeaders_preserves_user_overridden_accept_value() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        // User overrides Accept header
        val acceptHeader = tab.headers.first { it.key == "Accept" }
        acceptHeader.value = "application/xml"

        // Body type change triggers syncSystemHeaders again
        tab.syncSystemHeaders()

        assertEquals(
            "application/xml",
            tab.headers.first { it.key == "Accept" }.value,
            "User-set Accept value must not be overwritten by syncSystemHeaders",
        )
    }

    @Test
    fun syncSystemHeaders_preserves_user_overridden_user_agent_value() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        val uaHeader = tab.headers.first { it.key == "User-Agent" }
        uaHeader.value = "MyClient/2.0"

        tab.syncSystemHeaders()

        assertEquals(
            "MyClient/2.0",
            tab.headers.first { it.key == "User-Agent" }.value,
            "User-set User-Agent value must not be overwritten by syncSystemHeaders",
        )
    }

    @Test
    fun syncSystemHeaders_always_updates_content_type_to_match_body_type() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        tab.bodyType = com.reqlab.core.model.BodyType.XML
        tab.syncSystemHeaders()

        assertEquals(
            "application/xml",
            tab.headers.first { it.key == "Content-Type" }.value,
            "Content-Type must always be updated to match the current body type",
        )
    }

    @Test
    fun syncSystemHeaders_inserts_accept_if_missing() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        tab.headers.removeAll { it.key == "Accept" }
        tab.syncSystemHeaders()

        val accept = tab.headers.firstOrNull { it.key == "Accept" }
        assertEquals("application/json", accept?.value, "Accept must be inserted with default when missing")
    }

    @Test
    fun syncSystemHeaders_called_multiple_times_does_not_reset_user_value() {
        val state = AppState(openDefaultTab = false)
        state.addTabInSelectedCollection()
        val tab = state.activeTab!!

        tab.headers.first { it.key == "Accept" }.value = "text/plain"

        repeat(3) { tab.syncSystemHeaders() }

        assertEquals(
            "text/plain",
            tab.headers.first { it.key == "Accept" }.value,
            "Repeated syncSystemHeaders calls must not reset a user-overridden Accept value",
        )
    }

    @Test
    fun mcp_session_is_reused_until_tab_is_closed() {
        val state = AppState()
        val tab = state.openTabs.first()
        val first = state.getOrCreateMcpSession(tab.id)
        val second = state.getOrCreateMcpSession(tab.id)
        assertTrue(first === second, "MCP session must survive tab switches")
        val tabId = tab.id
        state.closeTab(0)
        val afterClose = state.getOrCreateMcpSession(tabId)
        assertTrue(first !== afterClose, "Closing the tab must dispose the MCP session")
    }
}
