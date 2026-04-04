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
}
