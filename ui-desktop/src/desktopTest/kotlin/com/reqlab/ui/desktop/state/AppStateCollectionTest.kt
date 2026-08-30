package com.reqlab.ui.shared.state

import androidx.compose.runtime.mutableStateListOf
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.McpRoot
import com.reqlab.core.model.McpSamplingMode
import com.reqlab.core.model.RequestKind
import com.reqlab.ui.shared.components.moveRequestToCollection
import com.reqlab.ui.shared.persistence.ImportExportRepository
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateCollectionTest {

    @Test
    fun addRequestToCollection_adds_child_and_opens_tab() {
        val state = AppState(withDemoData = true)
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
        val state = AppState(withDemoData = true)
        val tabsBefore = state.openTabs.size

        state.addRequestToCollection("nonexistent-id")

        assertEquals(tabsBefore, state.openTabs.size)
    }

    @Test
    fun addRequestToCollection_adds_into_nested_folder() {
        val state = AppState(withDemoData = true)
        val root = state.collections.first()
        val nested = CollectionNode(
            id = "nested-folder",
            name = "Nested",
            isFolder = true,
            children = mutableStateListOf(),
        )
        root.children.add(nested)
        val rootCount = root.children.size

        state.addRequestToCollection(nested.id)

        assertEquals(rootCount, root.children.size)
        assertEquals(1, nested.children.size)
        assertEquals("New Request", nested.children.single().name)
        assertEquals(root.id, state.selectedCollectionId)
        assertEquals(nested.children.single().id, state.selectedRequestId)
    }

    @Test
    fun addMcpAndSse_add_into_nested_folder() {
        val state = AppState(withDemoData = true)
        val root = state.collections.first()
        val nested = CollectionNode(
            id = "nested-folder-mcp-sse",
            name = "Nested",
            isFolder = true,
            children = mutableStateListOf(),
        )
        root.children.add(nested)

        state.addMcpConnectionToCollection(nested.id)
        state.addSseRequestToCollection(nested.id)

        assertEquals(2, nested.children.size)
        assertEquals(RequestKind.MCP, nested.children[0].kind)
        assertEquals("New MCP Connection", nested.children[0].name)
        assertEquals(RequestKind.HTTP, nested.children[1].kind)
        assertEquals("New SSE Request", nested.children[1].name)
        assertTrue(nested.children[1].hasSseAccept())
        assertEquals(root.id, state.selectedCollectionId)
    }

    @Test
    fun addTabInSelectedCollection_uses_selected_collection() {
        val state = AppState(withDemoData = true)
        val secondCollection = state.collections[1]
        state.selectedCollectionId = secondCollection.id
        val childrenBefore = secondCollection.children.size

        state.addTabInSelectedCollection()

        assertEquals(childrenBefore + 1, secondCollection.children.size)
    }

    @Test
    fun addTabInSelectedCollection_falls_back_to_first_collection() {
        val state = AppState(withDemoData = true)
        state.selectedCollectionId = null
        val firstCollection = state.collections.first()
        val childrenBefore = firstCollection.children.size

        state.addTabInSelectedCollection()

        assertEquals(childrenBefore + 1, firstCollection.children.size)
    }

    @Test
    fun addTabInSelectedCollection_creates_orphan_tab_when_no_collections() {
        val state = AppState(withDemoData = true)
        state.collections.clear()
        state.selectedCollectionId = null
        val tabsBefore = state.openTabs.size

        state.addTabInSelectedCollection()

        assertEquals(tabsBefore + 1, state.openTabs.size)
    }

    @Test
    fun selectedCollectionId_updates_on_assignment() {
        val state = AppState(withDemoData = true)
        assertNull(state.selectedCollectionId)

        state.selectedCollectionId = "c1"
        assertEquals("c1", state.selectedCollectionId)
    }

    @Test
    fun addRequestToCollection_generates_unique_name() {
        val state = AppState(withDemoData = true)
        val collectionId = state.collections.first().id

        state.addRequestToCollection(collectionId)
        state.addRequestToCollection(collectionId)

        val children = state.collections.first().children
        val newNames = children.takeLast(2).map { it.name }
        // Both should be different
        assertEquals(2, newNames.toSet().size)
    }

    @Test
    fun markDirty_sets_isDirty_true_when_state_differs_from_saved_snapshot() {
        val tab = RequestTabState(name = "Test")
        tab.url = "https://example.com"
        tab.markDirty()
        assertTrue(tab.isDirty)
    }

    @Test
    fun markDirty_keeps_clean_when_state_matches_saved_snapshot() {
        val tab = RequestTabState(name = "Test")
        tab.markDirty()
        assertEquals(false, tab.isDirty)
    }

    @Test
    fun markSaved_clears_isDirty() {
        val tab = RequestTabState(name = "Test")
        tab.url = "https://changed.example.com"
        tab.markDirty()
        assertTrue(tab.isDirty)

        tab.markSaved()
        assertEquals(false, tab.isDirty)
        assertNotNull(tab.lastSavedTimestamp)
    }

    @Test
    fun markSaved_large_body_uses_lightweight_saved_snapshot() {
        val tab = RequestTabState(name = "Large")
        tab.bodyContent = "{\"items\":[" + (0 until 320_000).joinToString(",") { "{\"k\":$it}" } + "]}"
        tab.markDirty()
        assertTrue(tab.isDirty)

        tab.markSaved()

        assertFalse(tab.isDirty)
        val persistedSnapshot = tab.savedSnapshotForPersistence()
        assertTrue(persistedSnapshot.startsWith("LARGE#"),
            "Large-body markSaved must persist lightweight snapshot prefix")
        assertTrue(persistedSnapshot.length < 10_000,
            "Saved snapshot for large body must stay compact for responsive saves")
    }

    @Test
    fun markDirty_after_large_saved_snapshot_sets_dirty_without_full_snapshot_compare() {
        val tab = RequestTabState(name = "Large")
        tab.bodyContent = "x".repeat(250_000)
        tab.markSaved()
        assertFalse(tab.isDirty)

        tab.bodyContent += "y"
        tab.markDirty()

        assertTrue(tab.isDirty)
    }

    @Test
    fun dirty_state_resets_when_changes_are_reverted_to_saved_snapshot() {
        val tab = RequestTabState(name = "Test", url = "https://example.com")
        tab.markSaved()

        tab.url = "https://example.com/v2"
        tab.markDirty()
        assertTrue(tab.isDirty)

        tab.url = "https://example.com"
        tab.markDirty()
        assertEquals(false, tab.isDirty)
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

    @Test
    fun addTab_does_not_duplicate_existing_request_id() {
        val state = AppState()
        val existingId = state.openTabs.first().id
        val before = state.openTabs.size

        state.addTab(requestId = existingId, name = "Duplicate", method = HttpMethodType.POST, url = "https://x")

        assertEquals(before, state.openTabs.size)
        assertEquals(existingId, state.activeTab?.id)
    }

    @Test
    fun collapse_and_expand_all_collections_updates_folder_state_map() {
        val state = AppState(withDemoData = true)

        state.collapseAllCollections()
        assertTrue(state.collectionExpandedState.values.all { expanded -> !expanded })

        state.expandAllCollections()
        assertTrue(state.collectionExpandedState.values.all { expanded -> expanded })
    }

    @Test
    fun renameRequestEverywhere_updates_tab_and_sidebar_node_name() {
        val state = AppState(withDemoData = true)
        val requestId = "r1"

        state.openRequest(requestId = requestId, name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.renameRequestEverywhere(requestId, "Get all users v2")

        assertTrue(state.openTabs.any { it.id == requestId && it.name == "Get all users v2" })
        assertTrue(state.collections.flatMap { it.children }.any { it.id == requestId && it.name == "Get all users v2" })
    }

    @Test
    fun renameRequestEverywhere_name_persists_after_syncTabToCollectionNode() {
        // Regression test for Bug 2: rename from sidebar used to be overwritten
        // when the user pressed Cmd+S because the open tab still held the old name.
        val state = AppState(withDemoData = true)
        val requestId = "r1"

        state.openRequest(requestId = requestId, name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.renameRequestEverywhere(requestId, "Renamed request")

        val tab = state.openTabs.first { it.id == requestId }
        // Simulate Cmd+S: syncTabToCollectionNode writes tab.name back to node
        state.syncTabToCollectionNode(tab)

        // Both must still carry the new name
        assertEquals("Renamed request", tab.name)
        val node = state.collections.flatMap { it.children }.first { it.id == requestId }
        assertEquals("Renamed request", node.name)
    }

    @Test
    fun syncTabToCollectionNode_writes_mcp_config_for_export() {
        val state = AppState(withDemoData = true)
        val collectionId = state.collections.first().id
        state.addMcpConnectionToCollection(collectionId)
        val tab = state.openTabs.last()
        tab.mcpConfig = tab.mcpConfig.copy(
            samplingMode = McpSamplingMode.MANUAL,
            args = listOf("--keep"),
            roots = listOf(McpRoot("file:///tmp/reqlab", "tmp")),
        )
        assertTrue(state.syncTabToCollectionNode(tab))
        val node = state.collections.first().children.first { it.id == tab.id }
        assertEquals(RequestKind.MCP, node.kind)
        assertEquals(McpSamplingMode.MANUAL, node.mcpConfig!!.samplingMode)
        assertEquals(listOf("--keep"), node.mcpConfig!!.args)
        assertEquals("file:///tmp/reqlab", node.mcpConfig!!.roots.single().uri)
        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())
        assertTrue(exported.contains("MANUAL"))
        assertTrue(exported.contains("file:///tmp/reqlab"))
        assertTrue(exported.contains("--keep"))
    }

    @Test
    fun mcp_client_field_change_marks_tab_dirty() {
        val tab = RequestTabState()
        tab.kind = RequestKind.MCP
        tab.markSaved()
        assertFalse(tab.isDirty)
        tab.mcpConfig = tab.mcpConfig.copy(autoRespondElicitation = false)
        tab.markDirty()
        assertTrue(tab.isDirty)
        tab.markSaved()
        assertFalse(tab.isDirty)
        tab.mcpConfig = tab.mcpConfig.copy(roots = listOf(McpRoot("file:///tmp/reqlab", "tmp")))
        tab.markDirty()
        assertTrue(tab.isDirty)
    }

    @Test
    fun addSseRequestToCollection_prefills_get_and_accept_header() {
        val state = AppState(withDemoData = true)
        val collectionId = state.collections.first().id
        state.addSseRequestToCollection(collectionId)
        val node = state.collections.first().children.first { it.name == "New SSE Request" }
        assertEquals(RequestKind.HTTP, node.kind)
        assertEquals(HttpMethodType.GET, node.method)
        assertEquals("{{baseUrl}}/sse", node.url)
        assertTrue(node.hasSseAccept())
        val tab = state.openTabs.last()
        assertEquals("New SSE Request", tab.name)
        assertEquals(HttpMethodType.GET, tab.method)
        assertEquals("{{baseUrl}}/sse", tab.url)
        assertTrue(tab.hasSseAccept())
        val accept = tab.headers.first { it.key.equals("Accept", ignoreCase = true) }
        assertEquals("text/event-stream", accept.value)
    }

    @Test
    fun addSseRequestToCollection_generates_unique_name() {
        val state = AppState(withDemoData = true)
        val collectionId = state.collections.first().id
        state.addSseRequestToCollection(collectionId)
        state.addSseRequestToCollection(collectionId)
        val names = state.collections.first().children.map { it.name }
        assertTrue("New SSE Request" in names)
        assertTrue("New SSE Request 2" in names)
    }

    @Test
    fun syncTabToCollectionNode_persists_sse_accept_and_clears_when_json() {
        val state = AppState(withDemoData = true)
        val collectionId = state.collections.first().id
        state.addSseRequestToCollection(collectionId)
        val tab = state.openTabs.last()
        assertTrue(state.syncTabToCollectionNode(tab))
        val nodeAfterSave = state.collections.first().children.first { it.id == tab.id }
        assertTrue(nodeAfterSave.hasSseAccept())
        assertTrue(
            nodeAfterSave.userHeaders.any {
                it.first.equals("Accept", ignoreCase = true) && it.second.contains("text/event-stream")
            },
        )
        tab.headers.first { it.key.equals("Accept", ignoreCase = true) }.value = "application/json"
        assertTrue(state.syncTabToCollectionNode(tab))
        val nodeAfterClear = state.collections.first().children.first { it.id == tab.id }
        assertFalse(nodeAfterClear.hasSseAccept())
    }

    // ── Issue 1: Closing last tab ────────────────────────────────────

    @Test
    fun closeTab_closing_last_tab_sets_active_index_to_minus_one() {
        val state = AppState()
        assertEquals(1, state.openTabs.size)

        state.closeTab(0)

        assertTrue(state.openTabs.isEmpty())
        assertEquals(-1, state.activeTabIndex)
        assertEquals(null, state.activeTab)
    }

    @Test
    fun closeTab_closing_last_tab_clears_selectedRequestId() {
        val state = AppState()
        state.selectedRequestId = state.openTabs.first().id

        state.closeTab(0)

        assertEquals(null, state.selectedRequestId)
    }

    @Test
    fun closeTab_no_longer_blocks_closing_single_tab() {
        val state = AppState()
        val singleTabId = state.openTabs.first().id

        state.closeTab(0)

        // Previously this returned early when size <= 1; now it must close.
        assertFalse(state.openTabs.any { it.id == singleTabId })
    }

    // ── Issue 2: revealRequestInSidebar always selects ──────────────

    @Test
    fun revealRequestInSidebar_sets_selectedRequestId_for_collection_request() {
        val state = AppState(withDemoData = true)
        state.selectedRequestId = null

        state.revealRequestInSidebar("r1")

        assertEquals("r1", state.selectedRequestId)
    }

    @Test
    fun revealRequestInSidebar_expands_ancestor_folder_for_collection_request() {
        val state = AppState(withDemoData = true)
        // Collapse the parent collection "c1" first
        state.collectionExpandedState["c1"] = false

        state.revealRequestInSidebar("r1")

        // Ancestor must be expanded so the node becomes visible
        assertTrue(state.collectionExpandedState["c1"] == true)
    }

    @Test
    fun revealRequestInSidebar_sets_selectedRequestId_even_for_orphan_request() {
        val state = AppState(withDemoData = true)
        // "orphan-999" is NOT in any collection; revealRequestInSidebar should
        // fail and surface a not-found error.
        val ok = state.revealRequestInSidebar("orphan-999")

        assertFalse(ok)
        assertTrue(state.showErrorDialog)
    }

    @Test
    fun revealRequestInSidebar_sets_scroll_target_for_collection_request() {
        val state = AppState(withDemoData = true)

        state.revealRequestInSidebar("r2")

        assertEquals("r2", state.sidebarScrollToRequestId)
    }

    @Test
    fun revealRequestInSidebar_for_request_not_in_history_does_not_mutate_history() {
        val state = AppState(withDemoData = true)
        // Ensure r3 exists in collections but is not currently in demo history.
        assertTrue(state.historyItems.none { it.requestId == "r3" })

        // Add a legacy/broken history entry to verify reveal does not opportunistically clean it up.
        state.historyItems.add(
            0,
            com.reqlab.ui.shared.state.HistoryItem(
                requestId = "legacy-missing",
                method = HttpMethodType.GET,
                name = "Legacy Missing",
                url = "{{baseUrl}}/legacy",
                timestamp = 1L,
            ),
        )
        val beforeIds = state.historyItems.map { it.requestId }

        val ok = state.revealRequestInSidebar("r3")

        assertTrue(ok)
        assertEquals(beforeIds, state.historyItems.map { it.requestId })
        assertEquals("r3", state.selectedRequestId)
    }

    // ── Issue 1: Show in Sidebar switches active tab ──────────────

    @Test
    fun revealRequestInSidebar_switches_active_tab_to_matching_request() {
        val state = AppState(withDemoData = true)
        // Open two requests from collections as tabs
        state.openRequest(requestId = "r1", name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.openRequest(requestId = "r4", name = "Login", method = HttpMethodType.POST, url = "{{baseUrl}}/auth/login")
        // Active tab is now r4 (the last opened)
        assertEquals("r4", state.activeTab?.id)

        // Reveal r1 in sidebar — must also switch the active tab
        state.revealRequestInSidebar("r1")

        assertEquals("r1", state.selectedRequestId)
        assertEquals("r1", state.activeTab?.id)
    }

    @Test
    fun revealRequestInSidebar_uses_tab_collection_and_signature_when_tab_id_is_stale() {
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val requestA = CollectionNode("ra", "Users", isFolder = false, method = HttpMethodType.GET, url = "https://a.example.com/users")
        val requestB = CollectionNode("rb", "Users", isFolder = false, method = HttpMethodType.GET, url = "https://b.example.com/users")
        state.collections.add(CollectionNode("ca", "API A", isFolder = true, children = mutableStateListOf(requestA)))
        state.collections.add(CollectionNode("cb", "API B", isFolder = true, children = mutableStateListOf(requestB)))

        // Tab points to API B by metadata, but carries a stale id "ra" that exists in API A.
        state.openTabs.add(
            RequestTabState(
                id = "ra",
                name = "Users",
                method = HttpMethodType.GET,
                url = "https://b.example.com/users",
                collectionName = "API B",
                collectionId = "cb",
            )
        )
        state.activeTabIndex = 0

        val ok = state.revealRequestInSidebar("ra")

        assertTrue(ok)
        assertEquals("rb", state.selectedRequestId, "Show in sidebar must resolve by tab scope+signature, not stale tab id")
    }

    @Test
    fun syncSidebarToActiveTab_prefers_signature_over_existing_stale_tab_id_for_duplicates() {
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val requestA = CollectionNode("ra", "Users", isFolder = false, method = HttpMethodType.GET, url = "https://a.example.com/users")
        val requestB = CollectionNode("rb", "Users", isFolder = false, method = HttpMethodType.GET, url = "https://b.example.com/users")
        state.collections.add(CollectionNode("ca", "API", isFolder = true, children = mutableStateListOf(requestA)))
        state.collections.add(CollectionNode("cb", "API", isFolder = true, children = mutableStateListOf(requestB)))

        // Simulate old persisted tab: stale id points to existing duplicate in collection A,
        // while method/url points to request B in collection B.
        state.openTabs.add(
            RequestTabState(
                id = "ra",
                name = "Users",
                method = HttpMethodType.GET,
                url = "https://b.example.com/users",
                collectionName = "API",
                collectionId = null,
            )
        )
        state.activeTabIndex = 0

        state.syncSidebarToActiveTab()

        assertEquals("rb", state.selectedRequestId, "Sidebar sync must resolve by signature, not stale tab id")
        assertEquals("rb", state.sidebarScrollToRequestId, "Sidebar must scroll to the resolved request")
    }

    @Test
    fun syncSidebarToActiveTab_keeps_valid_duplicate_request_id_after_other_duplicate_is_moved() {
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val c1r1 = CollectionNode("c1-r1", "r1", isFolder = false, method = HttpMethodType.GET, url = "https://api.example.com/r1")
        val c2r1 = CollectionNode("c2-r1", "r1", isFolder = false, method = HttpMethodType.GET, url = "https://api.example.com/r1")
        state.collections.add(CollectionNode("c1", "c1", isFolder = true, children = mutableStateListOf(c1r1)))
        state.collections.add(CollectionNode("c2", "c2", isFolder = true, children = mutableStateListOf(c2r1)))
        state.collections.add(CollectionNode("c3", "c3", isFolder = true, children = mutableStateListOf()))

        state.openRequest(requestId = "c1-r1", name = "r1", method = HttpMethodType.GET, url = "https://api.example.com/r1")
        state.openRequest(requestId = "c2-r1", name = "r1", method = HttpMethodType.GET, url = "https://api.example.com/r1")

        val moved = moveRequestToCollection(state.collections, "c1-r1", "c3")
        assertTrue(moved)
        state.notifyCollectionsChanged()

        val c2TabIndex = state.openTabs.indexOfFirst { it.id == "c2-r1" }
        assertTrue(c2TabIndex >= 0)
        state.activeTabIndex = c2TabIndex
        state.syncSidebarToActiveTab()

        assertEquals("c2-r1", state.selectedRequestId, "Tab from c2 must continue resolving to c2 request")
    }

    @Test
    fun notifyCollectionsChanged_updates_open_tab_collection_metadata_after_request_move() {
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val c1r1 = CollectionNode("c1-r1", "r1", isFolder = false, method = HttpMethodType.GET, url = "https://api.example.com/r1")
        state.collections.add(CollectionNode("c1", "c1", isFolder = true, children = mutableStateListOf(c1r1)))
        state.collections.add(CollectionNode("c3", "c3", isFolder = true, children = mutableStateListOf()))

        state.openRequest(requestId = "c1-r1", name = "r1", method = HttpMethodType.GET, url = "https://api.example.com/r1")
        val movedTab = state.openTabs.first { it.id == "c1-r1" }
        assertEquals("c1", movedTab.collectionId)
        assertEquals("c1", movedTab.collectionName)

        val moved = moveRequestToCollection(state.collections, "c1-r1", "c3")
        assertTrue(moved)
        state.notifyCollectionsChanged()

        assertEquals("c3", movedTab.collectionId)
        assertEquals("c3", movedTab.collectionName)
        assertEquals(emptyList(), movedTab.folderPath)
    }

    @Test
    fun revealRequestInSidebar_prefers_signature_when_duplicate_request_names_exist() {
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val reqA = CollectionNode("req-a", "Users", isFolder = false, method = HttpMethodType.GET, url = "https://api-a.example.com/users")
        val reqB = CollectionNode("req-b", "Users", isFolder = false, method = HttpMethodType.GET, url = "https://api-b.example.com/users")
        state.collections.add(CollectionNode("coll-a", "API", isFolder = true, children = mutableStateListOf(reqA)))
        state.collections.add(CollectionNode("coll-b", "API", isFolder = true, children = mutableStateListOf(reqB)))

        // Simulate a restored tab with stale tab id and no collectionId metadata.
        state.openTabs.add(
            RequestTabState(
                id = "stale-tab-id",
                name = "Users",
                method = HttpMethodType.GET,
                url = "https://api-b.example.com/users",
                collectionName = "API",
                collectionId = null,
                folderPath = emptyList(),
            )
        )
        state.activeTabIndex = 0

        val ok = state.revealRequestInSidebar("stale-tab-id")

        assertTrue(ok)
        assertEquals("req-b", state.selectedRequestId, "Show in sidebar must resolve to matching method+url, not first duplicate by name")
    }

    // ── Issue 3: syncSidebarToActiveTab after restoration ─────────

    @Test
    fun syncSidebarToActiveTab_sets_selectedRequestId_and_expands_ancestors() {
        val state = AppState(withDemoData = true)
        // Simulate restored state: tab open for r1, ancestors collapsed
        state.openRequest(requestId = "r1", name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.collectionExpandedState["c1"] = false
        state.selectedRequestId = null

        state.syncSidebarToActiveTab()

        assertEquals("r1", state.selectedRequestId)
        assertTrue(state.collectionExpandedState["c1"] == true, "Parent folder should be expanded")
    }

    @Test
    fun syncSidebarToActiveTab_does_nothing_when_no_tabs_open() {
        val state = AppState(openDefaultTab = false)
        state.selectedRequestId = null

        state.syncSidebarToActiveTab()

        assertNull(state.selectedRequestId)
    }

    @Test
    fun tab_switch_syncs_sidebar_selection() {
        val state = AppState(openDefaultTab = false)
        state.openRequest(requestId = "r1", name = "A", method = HttpMethodType.GET, url = "u1")
        state.openRequest(requestId = "r4", name = "B", method = HttpMethodType.POST, url = "u2")

        // Switch to first tab (r1)
        state.activeTabIndex = 0
        state.syncSidebarToActiveTab()

        assertEquals("r1", state.selectedRequestId)

        // Switch to second tab (r4)
        state.activeTabIndex = 1
        state.syncSidebarToActiveTab()

        assertEquals("r4", state.selectedRequestId)
    }

    @Test
    fun resolveScriptRequestTarget_resolves_unique_name_across_collections() {
        val state = AppState(openDefaultTab = false)
        state.collections.clear()
        state.collections.add(
            CollectionNode(
                id = "c1",
                name = "Collection 1",
                isFolder = true,
                children = mutableStateListOf(
                    CollectionNode(id = "r1", name = "Login", method = HttpMethodType.POST, url = "/login", requestRef = "ref-login")
                ),
            )
        )

        val target = state.resolveScriptRequestTarget("Login")

        assertNotNull(target)
        assertEquals("r1", target.requestId)
        assertEquals(HttpMethodType.POST, target.method)
    }

    @Test
    fun resolveScriptRequestTarget_returns_null_for_ambiguous_name_without_collection_hint() {
        val state = AppState(openDefaultTab = false)
        state.collections.clear()
        state.collections.add(
            CollectionNode(
                id = "c1",
                name = "Collection 1",
                isFolder = true,
                children = mutableStateListOf(
                    CollectionNode(id = "r1", name = "Login", method = HttpMethodType.POST, url = "/login")
                ),
            )
        )
        state.collections.add(
            CollectionNode(
                id = "c2",
                name = "Collection 2",
                isFolder = true,
                children = mutableStateListOf(
                    CollectionNode(id = "r2", name = "Login", method = HttpMethodType.POST, url = "/login2")
                ),
            )
        )

        val target = state.resolveScriptRequestTarget("Login")

        assertNull(target)
    }

    @Test
    fun syncSidebarToActiveTab_does_not_scroll_when_request_already_selected() {
        val state = AppState(withDemoData = true)
        // Simulate sidebar click path: request already selected before active-tab sync runs.
        state.openRequest(requestId = "r1", name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")
        state.selectedRequestId = "r1"
        state.sidebarScrollToRequestId = null

        state.syncSidebarToActiveTab()

        assertEquals("r1", state.selectedRequestId)
        assertNull(state.sidebarScrollToRequestId, "Sidebar click selection should not trigger auto-scroll nudge")
    }

    // ── Workspace restoration: restored tab IDs match collection IDs ──

    @Test
    fun restored_tab_id_matches_collection_node_id() {
        // Simulate: TabsRepository restores a tab with id "r1",
        // WorkspaceRepository restores collections containing node id "r1".
        // After syncSidebarToActiveTab, sidebar should highlight it.
        val state = AppState(openDefaultTab = false, withDemoData = true)
        // Manually add tab as TabsRepository.load would
        state.openTabs.add(RequestTabState(id = "r1", name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users"))
        state.activeTabIndex = 0
        // Collections already have r1 from the default AppState collections
        // Now sync
        state.syncSidebarToActiveTab()

        assertEquals("r1", state.selectedRequestId)
        assertEquals("r1", state.sidebarScrollToRequestId)
    }

    // ─────────────────────────────────────────────────────────────
    // Regression Bug #2: Dirty-state false positive after reverting
    // an edit when the tab was opened from the collection sidebar.
    //
    // Root cause: addTab calls syncSystemHeaders() AFTER the
    // RequestTabState.init{} block has already captured savedSnapshot
    // (without system headers). Once headers diverge from savedSnapshot,
    // recomputeDirty() always returns true even when the user reverts
    // their last edit to the original URL.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun openRequest_from_collection_does_not_produce_false_dirty_after_reverting_url_edit() {
        // Use a node with bodyType set so that addTab calls syncSystemHeaders(),
        // which adds Content-Type / Accept / User-Agent after savedSnapshot is captured.
        val state = AppState(openDefaultTab = false)
        val node = CollectionNode(
            id = "req-dirty-test",
            name = "Post Data",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.com/data",
            bodyType = BodyType.JSON,
        )
        state.collections.add(
            CollectionNode(
                id = "coll-dirty-test",
                name = "Dirty Bug Collection",
                isFolder = true,
                children = mutableStateListOf(node),
            )
        )

        state.openRequest(requestId = node.id, name = node.name, method = node.method!!, url = node.url!!)
        val tab = state.activeTab ?: error("no active tab")
        assertFalse(tab.isDirty, "Tab should not be dirty immediately after opening from collection")

        val originalUrl = tab.url
        tab.url = originalUrl + "/extra"
        tab.markDirty()  // simulates onUrlChanged
        assertTrue(tab.isDirty)

        // User reverts the change – dirty flag should clear
        tab.url = originalUrl
        tab.markDirty()  // simulates onUrlChanged

        assertFalse(
            tab.isDirty,
            "Reverting URL to its saved value must clear the dirty flag. " +
                "Bug: savedSnapshot captured before syncSystemHeaders() ran, " +
                "so system headers are missing from savedSnapshot and isDirty never resets.",
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Regression Bug #3: Params table is empty when opening a
    // request from the collection sidebar, even when the stored URL
    // contains a query string.
    //
    // Root cause: CollectionNode has no params field, and addTab does
    // not call syncParamsFromUrl(). The user must manually type in
    // the URL bar to populate the params table.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun openRequest_from_collection_leaves_params_empty_when_url_has_query_params() {
        val state = AppState(openDefaultTab = false)
        val node = CollectionNode(
            id = "req-params-test",
            name = "Search",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://api.com/search?q=test&page=1",
        )
        state.collections.add(
            CollectionNode(
                id = "coll-params-test",
                name = "Params Bug Collection",
                isFolder = true,
                children = mutableStateListOf(node),
            )
        )

        state.openRequest(requestId = node.id, name = node.name, method = node.method!!, url = node.url!!)
        val tab = state.activeTab ?: error("no active tab")

        assertEquals(
            2,
            tab.params.size,
            "Opening from collection must populate params from the URL query string. " +
                "Bug: addTab does not call syncParamsFromUrl(), so params table is always empty " +
                "even when tab.url = 'https://api.com/search?q=test&page=1'.",
        )
        assertEquals("q", tab.params[0].key)
        assertEquals("test", tab.params[0].value)
        assertEquals("page", tab.params[1].key)
        assertEquals("1", tab.params[1].value)
    }

    // ─────────────────────────────────────────────────────────────
    // Rename dirty-state regression tests (Bug #5)
    //
    // Root cause: renameRequestEverywhere updated tab.name but left
    // savedSnapshot with the old name. Any subsequent recomputeDirty()
    // call would always find the name mismatch, so the tab stayed
    // dirty even after the user reverted every other edit.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `rename from sidebar does not make clean tab dirty`() {
        val state = AppState(withDemoData = true)
        state.openRequest("r1", "Get all users", HttpMethodType.GET, "{{baseUrl}}/users")
        val tab = state.openTabs.first { it.id == "r1" }
        assertFalse(tab.isDirty, "Tab must be clean after opening from collection")

        state.renameRequestEverywhere("r1", "Renamed request")

        assertFalse(tab.isDirty,
            "Renaming a clean tab from the sidebar must not mark it dirty")
    }

    @Test
    fun `after rename, reverting url edit clears dirty flag`() {
        val state = AppState(openDefaultTab = false)
        val node = CollectionNode(
            id = "req-rename-test",
            name = "Original Name",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://api.com/users",
        )
        state.collections.add(
            CollectionNode(
                id = "coll-rename-test",
                name = "Rename Test",
                isFolder = true,
                children = mutableStateListOf(node),
            )
        )
        state.openRequest(requestId = node.id, name = node.name, method = node.method!!, url = node.url!!)
        val tab = state.activeTab ?: error("no active tab")
        assertFalse(tab.isDirty)

        // Step 1: rename from sidebar
        state.renameRequestEverywhere(node.id, "New Name")
        assertFalse(tab.isDirty, "Tab must remain clean immediately after rename")

        // Step 2: edit URL
        val originalUrl = tab.url
        tab.url = "$originalUrl/extra"
        tab.markDirty()
        assertTrue(tab.isDirty, "Tab must be dirty after URL edit")

        // Step 3: revert URL — must become clean again
        tab.url = originalUrl
        tab.markDirty()
        assertFalse(
            tab.isDirty,
            "Reverting URL to original after rename must clear dirty flag. " +
                "Bug: savedSnapshot still had old name, so isDirty always stayed true.",
        )
    }

    @Test
    fun `after rename, dirty tab that had url changes stays dirty`() {
        val state = AppState(openDefaultTab = false)
        val node = CollectionNode(
            id = "req-rename-dirty",
            name = "My Request",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://api.com/data",
        )
        state.collections.add(
            CollectionNode(
                id = "coll-rename-dirty",
                name = "Rename Dirty Test",
                isFolder = true,
                children = mutableStateListOf(node),
            )
        )
        state.openRequest(requestId = node.id, name = node.name, method = node.method!!, url = node.url!!)
        val tab = state.activeTab ?: error("no active tab")

        // Make tab dirty first (unsaved URL change)
        tab.url = "https://api.com/data/v2"
        tab.markDirty()
        assertTrue(tab.isDirty)

        // Rename should not clear the pending URL change
        state.renameRequestEverywhere(node.id, "My Renamed Request")
        assertTrue(tab.isDirty,
            "A tab with unsaved URL changes must stay dirty after rename")
    }

    @Test
    fun `rename from tab chip updates both tab and collection node`() {
        val state = AppState(withDemoData = true)
        val requestId = "r1"

        state.openRequest(requestId = requestId, name = "Get all users", method = HttpMethodType.GET, url = "{{baseUrl}}/users")

        // Simulate tab-chip inline rename (same code path as sidebar rename)
        state.renameRequestEverywhere(requestId, "Users List")

        val tab = state.openTabs.first { it.id == requestId }
        assertEquals("Users List", tab.name,
            "Tab name must update after rename via tab chip")

        val node = state.collections.flatMap { it.children }.first { it.id == requestId }
        assertEquals("Users List", node.name,
            "Collection node name must update after rename via tab chip")
    }

    // ─────────────────────────────────────────────────────────────
    // Delete-request closes open tab regression tests (Bug #6)
    //
    // Root cause: deleteRequestFromCollections removed the node from
    // the tree but never called closeTabsByIds(), so the deleted
    // request's tab stayed open in the topbar.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `deleteRequest closes the matching open tab`() {
        val state = AppState(withDemoData = true)
        state.openRequest("r1", "Get all users", HttpMethodType.GET, "{{baseUrl}}/users")
        assertTrue(state.openTabs.any { it.id == "r1" }, "Tab must be open before delete")

        state.closeTabsByIds(listOf("r1"))

        assertFalse(state.openTabs.any { it.id == "r1" },
            "Deleted request tab must be removed from topbar")
    }

    @Test
    fun `closeTabsByIds removes multiple tabs and keeps others`() {
        val state = AppState(withDemoData = true)
        state.openRequest("r1", "Get all users", HttpMethodType.GET, "{{baseUrl}}/users")
        state.openRequest("r2", "Create user", HttpMethodType.POST, "{{baseUrl}}/users")
        state.openRequest("r3", "Update user", HttpMethodType.PUT, "{{baseUrl}}/users/1")
        assertEquals(4, state.openTabs.size) // default tab + 3 opened

        state.closeTabsByIds(listOf("r1", "r3"))

        assertFalse(state.openTabs.any { it.id == "r1" }, "r1 tab must be closed")
        assertFalse(state.openTabs.any { it.id == "r3" }, "r3 tab must be closed")
        assertTrue(state.openTabs.any { it.id == "r2" }, "r2 tab must still be open")
    }

    @Test
    fun `closeTabsByIds with non-open id is a no-op`() {
        val state = AppState(withDemoData = true)
        val tabsBefore = state.openTabs.size

        state.closeTabsByIds(listOf("nonexistent-id"))

        assertEquals(tabsBefore, state.openTabs.size,
            "closeTabsByIds with unknown id must not change open tabs")
    }

    @Test
    fun `closeTabsByIds sets activeTabIndex correctly when active tab is closed`() {
        val state = AppState(withDemoData = true)
        state.openRequest("r1", "Get all users", HttpMethodType.GET, "{{baseUrl}}/users")
        state.openRequest("r2", "Create user", HttpMethodType.POST, "{{baseUrl}}/users")
        // Make r2 the active tab
        state.activeTabIndex = state.openTabs.indexOfFirst { it.id == "r2" }

        state.closeTabsByIds(listOf("r2"))

        assertTrue(state.activeTabIndex < state.openTabs.size,
            "activeTabIndex must be within bounds after active tab is closed")
        assertFalse(state.openTabs.any { it.id == "r2" })
    }

    @Test
    fun `closeTabsByIds clears all tabs when every tab is deleted`() {
        val state = AppState(openDefaultTab = false)
        val node1 = CollectionNode("req-del-1", "A", isFolder = false, method = HttpMethodType.GET, url = "/a")
        val node2 = CollectionNode("req-del-2", "B", isFolder = false, method = HttpMethodType.GET, url = "/b")
        state.collections.add(
            CollectionNode("coll-del", "Delete All", isFolder = true,
                children = mutableStateListOf(node1, node2))
        )
        state.openRequest("req-del-1", "A", HttpMethodType.GET, "/a")
        state.openRequest("req-del-2", "B", HttpMethodType.GET, "/b")
        assertEquals(2, state.openTabs.size)

        state.closeTabsByIds(listOf("req-del-1", "req-del-2"))

        assertTrue(state.openTabs.isEmpty(), "All tabs must be closed")
        assertEquals(-1, state.activeTabIndex, "activeTabIndex must be -1 with no open tabs")
        assertNull(state.activeTab)
    }

    // ─────────────────────────────────────────────────────────────
    // Bug #7: Rename collection root updates tab.collectionName
    //
    // Root cause: RequestTabState.collectionName was a val so it
    // couldn't be updated when the root collection was renamed.
    // resolveSidebarRequestId() then failed to find the collection
    // by name, breaking sidebar reveal and history recording.
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `updateTabsCollectionName updates matching tabs`() {
        val state = AppState(withDemoData = true)
        // Open a request from the "API Demo" collection (c1 in demo data)
        state.openRequest("r1", "Get all users", HttpMethodType.GET, "{{baseUrl}}/users")
        val tab = state.openTabs.first { it.id == "r1" }
        val oldName = tab.collectionName ?: error("tab must belong to a collection")

        state.updateTabsCollectionName(oldName, "API Demo v2")

        assertEquals("API Demo v2", tab.collectionName,
            "Tab.collectionName must be updated after root collection rename")
    }

    @Test
    fun `updateTabsCollectionName does not touch tabs from other collections`() {
        val state = AppState(withDemoData = true)
        state.openRequest("r1", "Get all users", HttpMethodType.GET, "{{baseUrl}}/users")
        state.openRequest("r4", "Login", HttpMethodType.POST, "{{baseUrl}}/auth/login")
        val r1Tab = state.openTabs.first { it.id == "r1" }
        val r4Tab = state.openTabs.first { it.id == "r4" }

        // Only rename the collection that r1 belongs to
        val oldName = r1Tab.collectionName ?: return
        state.updateTabsCollectionName(oldName, "Renamed Collection")

        assertEquals("Renamed Collection", r1Tab.collectionName)
        // r4 is in a different collection or no collection — its name must not change
        assertFalse(r4Tab.collectionName == "Renamed Collection",
            "Tabs from other collections must not be affected by rename")
    }

    @Test
    fun `updateTabsCollectionName with empty open tabs is a no-op`() {
        val state = AppState(openDefaultTab = false)
        assertTrue(state.openTabs.isEmpty())
        // Should not throw
        state.updateTabsCollectionName("oldName", "newName")
    }

    @Test
    fun syncSidebarToActiveTab_uses_requestRef_when_tab_id_is_stale() {
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val req = CollectionNode(
            id = "real-id",
            name = "Users",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://api.example.com/users",
            requestRef = "ref-users",
        )
        state.collections.add(
            CollectionNode(
                id = "c1",
                name = "Users API",
                isFolder = true,
                children = mutableStateListOf(req),
            )
        )

        state.openTabs.add(
            RequestTabState(
                id = "stale-id",
                requestRef = "ref-users",
                name = "Users",
                method = HttpMethodType.GET,
                url = "https://api.example.com/users",
            )
        )
        state.activeTabIndex = 0

        state.syncSidebarToActiveTab()

        assertEquals("real-id", state.selectedRequestId)
    }

    @Test
    fun revealRequestInSidebar_uses_requestRef_when_tab_id_is_stale() {
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val req = CollectionNode(
            id = "real-id",
            name = "Users",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://api.example.com/users",
            requestRef = "ref-users",
        )
        state.collections.add(
            CollectionNode(
                id = "c1",
                name = "Users API",
                isFolder = true,
                children = mutableStateListOf(req),
            )
        )

        state.openTabs.add(
            RequestTabState(
                id = "stale-id",
                requestRef = "ref-users",
                name = "Users",
                method = HttpMethodType.GET,
                url = "https://api.example.com/users",
            )
        )
        state.activeTabIndex = 0

        val ok = state.revealRequestInSidebar("stale-id")

        assertTrue(ok)
        assertEquals("real-id", state.selectedRequestId)
    }

    // ── Bug repro: show-in-sidebar for same-name requests in different collections ──

    @Test
    fun revealRequestInSidebar_resolves_correct_request_when_same_name_and_url_in_different_collections() {
        // Two collections each containing a request with identical name + method + url.
        // Opening both from the sidebar (within the same session) gives each tab a
        // correct collectionId.  Show-in-sidebar must resolve to the right one.
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val r1a = CollectionNode("r1a", "GetUsers", isFolder = false, method = HttpMethodType.GET, url = "https://api.example.com/users")
        val r1b = CollectionNode("r1b", "GetUsers", isFolder = false, method = HttpMethodType.GET, url = "https://api.example.com/users")
        state.collections.add(CollectionNode("c1", "c1", isFolder = true, children = mutableStateListOf(r1a)))
        state.collections.add(CollectionNode("c2", "c2", isFolder = true, children = mutableStateListOf(r1b)))

        state.openRequest(requestId = "r1a", name = "GetUsers", method = HttpMethodType.GET, url = "https://api.example.com/users")
        state.openRequest(requestId = "r1b", name = "GetUsers", method = HttpMethodType.GET, url = "https://api.example.com/users")

        // Show in sidebar for c1's request
        val ok1 = state.revealRequestInSidebar("r1a")
        assertTrue(ok1)
        assertEquals("r1a", state.selectedRequestId, "Show in sidebar must resolve to c1's request, not c2's duplicate")

        // Show in sidebar for c2's request
        val ok2 = state.revealRequestInSidebar("r1b")
        assertTrue(ok2)
        assertEquals("r1b", state.selectedRequestId, "Show in sidebar must resolve to c2's request, not c1's duplicate")
    }

    @Test
    fun syncSidebarToActiveTab_resolves_correct_request_when_same_name_and_url_in_different_collections() {
        // Clicking a tab in the topbar must sync the sidebar to the correct duplicate.
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val r1a = CollectionNode("r1a", "GetUsers", isFolder = false, method = HttpMethodType.GET, url = "https://api.example.com/users")
        val r1b = CollectionNode("r1b", "GetUsers", isFolder = false, method = HttpMethodType.GET, url = "https://api.example.com/users")
        state.collections.add(CollectionNode("c1", "c1", isFolder = true, children = mutableStateListOf(r1a)))
        state.collections.add(CollectionNode("c2", "c2", isFolder = true, children = mutableStateListOf(r1b)))

        state.openRequest(requestId = "r1a", name = "GetUsers", method = HttpMethodType.GET, url = "https://api.example.com/users")
        state.openRequest(requestId = "r1b", name = "GetUsers", method = HttpMethodType.GET, url = "https://api.example.com/users")

        // Switch to c1's tab
        state.activeTabIndex = 0
        state.syncSidebarToActiveTab()
        assertEquals("r1a", state.selectedRequestId, "Sidebar sync for c1 tab must select c1's request")

        // Switch to c2's tab
        state.activeTabIndex = 1
        state.syncSidebarToActiveTab()
        assertEquals("r1b", state.selectedRequestId, "Sidebar sync for c2 tab must select c2's request")
    }

    @Test
    fun tabMatchesRequestScope_uses_collectionId_only_ignoring_stale_collection_name() {
        // After a collection rename between sessions, the stored collectionName on the tab
        // may be stale.  The resolver must trust collectionId, not the name.
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val req = CollectionNode("req1", "GetUsers", isFolder = false, method = HttpMethodType.GET, url = "https://api.example.com/users")
        state.collections.add(CollectionNode("c1", "New API Name", isFolder = true, children = mutableStateListOf(req)))

        // Tab has the old collection name but the correct collection ID
        state.openTabs.add(
            RequestTabState(
                id = "req1",
                name = "GetUsers",
                method = HttpMethodType.GET,
                url = "https://api.example.com/users",
                collectionId = "c1",
                collectionName = "Old API Name",  // stale — collection was renamed
            )
        )
        state.activeTabIndex = 0

        // Show in sidebar must succeed despite stale collection name
        val ok = state.revealRequestInSidebar("req1")
        assertTrue(ok)
        assertEquals("req1", state.selectedRequestId, "Stale collectionName must not block resolution when collectionId matches")
    }
}
