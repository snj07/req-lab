package com.reqlab.ui.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.persistence.ImportExportRepository
import com.reqlab.ui.shared.persistence.TabsRepository
import com.reqlab.ui.shared.persistence.WorkspaceRepository
import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Regression tests for large-payload save, restore, and import/export.
 *
 * Covers:
 *  1. Clicking Save syncs a large body back to the [CollectionNode] (fixes the
 *     export/import regression where export always read the stale node value).
 *  2. Tab persist/restore keeps exact content through a restart cycle.
 *  3. Collection export → import roundtrips a large body without truncation.
 *  4. Workspace export → import roundtrips a large body without truncation.
 *  5. WorkspaceRepository.save/load roundtrip keeps the large body via
 *     two-tier [PlatformStorage] (file-backed path for values > 64 KB).
 *  6. Clicking Save twice overwrites the first value in the [CollectionNode].
 */
class LargePayloadSaveUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() {
        PlatformStorage.remove("reqlab.tabs")
        PlatformStorage.remove("reqlab.workspace")
    }

    @After
    fun tearDown() {
        PlatformStorage.remove("reqlab.tabs")
        PlatformStorage.remove("reqlab.workspace")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun buildLargeJson(sizeHint: Int = 200_000) =
        "{\"data\":\"" + "X".repeat(sizeHint) + "\"}"

    /**
     * Returns a fresh [AppState] with one collection "MyAPI" containing one
     * POST request "Big POST". If [initialBody] is provided it is stored in
     * the [CollectionNode] at creation time (simulates a request that was
     * previously saved and is being re-exported).
     */
    private fun buildStateWithCollection(initialBody: String = ""): AppState {
        val request = CollectionNode(
            id = "req-large-1",
            name = "Big POST",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.example.com/data",
            bodyType = if (initialBody.isNotEmpty()) BodyType.JSON else null,
            bodyContent = initialBody.ifBlank { null },
            bodyContents = if (initialBody.isNotEmpty()) mapOf(BodyType.JSON.name to initialBody) else emptyMap(),
        )
        val collection = CollectionNode(
            id = "col-large-1",
            name = "MyAPI",
            isFolder = true,
        ).also { it.children.add(request) }
        return AppState(openDefaultTab = false).also { it.collections.add(collection) }
    }

    // ── 1. Clicking Save syncs large body into CollectionNode ────────────────

    @Test
    fun save_button_click_syncs_large_body_to_collection_node() {
        val largeBody = buildLargeJson()
        val state = buildStateWithCollection()

        // Open the request as an editor tab and enter the large body
        state.openRequest(requestId = "req-large-1", name = "Big POST",
            method = HttpMethodType.POST, url = "https://api.example.com/data")
        val tab = state.activeTab!!
        tab.bodyType  = BodyType.JSON
        tab.bodyContent = largeBody
        tab.markDirty()

        rule.setContent { MainScreen(state) }
        rule.waitForIdle()

        rule.onNodeWithTag("save-button").performClick()

        // Wait for the async save coroutine + CollectionNode sync to complete
        rule.waitUntil(timeoutMillis = 8_000) {
            state.collections
                .firstOrNull { it.name == "MyAPI" }
                ?.children?.firstOrNull()
                ?.bodyContent?.length == largeBody.length
        }

        val updatedNode = state.collections
            .firstOrNull { it.name == "MyAPI" }
            ?.children?.firstOrNull()

        assertNotNull(updatedNode, "CollectionNode must exist after save")
        assertEquals(largeBody, updatedNode.bodyContent,
            "CollectionNode.bodyContent must exactly match the saved tab body")
        assertEquals(largeBody, updatedNode.bodyContents[BodyType.JSON.name],
            "CollectionNode.bodyContents[JSON] must also reflect the saved body")
    }

    // ── 2. Tab persist/restore keeps large body through restart cycle ─────────

    @Test
    fun large_body_tab_survives_app_restart_cycle() {
        val largeBody = buildLargeJson(150_000)

        val firstSession = AppState()
        val tab = firstSession.activeTab!!
        tab.bodyType    = BodyType.JSON
        tab.bodyContent = largeBody
        TabsRepository.save(firstSession)

        val secondSession = AppState(openDefaultTab = false)
        TabsRepository.load(secondSession)

        val restoredTab = secondSession.activeTab
        assertNotNull(restoredTab, "Tab must be restored after restart")
        assertEquals(largeBody.length, restoredTab.bodyContent.length,
            "Restored tab body length must match original")
        assertEquals(largeBody, restoredTab.bodyContent,
            "Restored tab body content must match original exactly")
    }

    // ── 3. Collection export → import roundtrips a large body ───────────────

    @Test
    fun collection_export_import_roundtrips_large_body() {
        val largeBody = buildLargeJson(200_000)
        val state = buildStateWithCollection(initialBody = largeBody)

        val json = ImportExportRepository.exportCollectionToString(state.collections.first())

        val fresh = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(fresh, json)

        val imported = fresh.collections.first().children.first()
        assertEquals(largeBody.length, imported.bodyContent?.length,
            "Collection export/import: body length must match")
        assertEquals(largeBody, imported.bodyContent,
            "Collection export/import: body content must match exactly")
        assertEquals(largeBody, imported.bodyContents[BodyType.JSON.name],
            "Collection export/import: bodyContents[JSON] must match")
    }

    // ── 4. Workspace export → import roundtrips a large body ────────────────

    @Test
    fun workspace_export_import_roundtrips_large_body() {
        val largeBody = buildLargeJson(200_000)
        val state = buildStateWithCollection(initialBody = largeBody)

        val json = ImportExportRepository.exportWorkspaceToString(state)

        val fresh = AppState(openDefaultTab = false)
        ImportExportRepository.importWorkspaceFromString(fresh, json)

        val imported = fresh.collections
            .firstOrNull { it.name == "MyAPI" }?.children?.firstOrNull()
        assertNotNull(imported, "Request node must be present after workspace import")
        assertEquals(largeBody.length, imported.bodyContent?.length,
            "Workspace export/import: body length must match")
        assertEquals(largeBody, imported.bodyContent,
            "Workspace export/import: body content must match exactly")
    }

    // ── 5. WorkspaceRepository.save/load roundtrips a large body ────────────

    @Test
    fun workspace_repository_save_load_roundtrips_large_body() {
        val largeBody = buildLargeJson(200_000)
        val state = buildStateWithCollection(initialBody = largeBody)

        WorkspaceRepository.save(state)

        val fresh = AppState(openDefaultTab = false)
        WorkspaceRepository.load(fresh)

        val loaded = fresh.collections
            .firstOrNull { it.name == "MyAPI" }?.children?.firstOrNull()
        assertNotNull(loaded, "Request node must be present after WorkspaceRepository.load")
        assertEquals(largeBody.length, loaded.bodyContent?.length,
            "WorkspaceRepository roundtrip: body length must match")
        assertEquals(largeBody, loaded.bodyContent,
            "WorkspaceRepository roundtrip: body content must match exactly")
    }

    // ── 6. Second Save click overwrites first in CollectionNode ──────────────

    @Test
    fun second_save_click_overwrites_first_body_in_collection_node() {
        val firstBody  = buildLargeJson(80_000)
        val secondBody = "{\"v\":2,\"payload\":\"" + "Y".repeat(90_000) + "\"}"

        val state = buildStateWithCollection()
        state.openRequest(requestId = "req-large-1", name = "Big POST",
            method = HttpMethodType.POST, url = "https://api.example.com/data")
        val tab = state.activeTab!!

        rule.setContent { MainScreen(state) }
        rule.waitForIdle()

        // ── First save ──
        tab.bodyType    = BodyType.JSON
        tab.bodyContent = firstBody
        tab.markDirty()
        rule.onNodeWithTag("save-button").performClick()
        rule.waitUntil(timeoutMillis = 8_000) {
            state.collections
                .firstOrNull { it.name == "MyAPI" }
                ?.children?.firstOrNull()
                ?.bodyContent?.length == firstBody.length
        }

        // ── Second save with different content ──
        tab.bodyContent = secondBody
        tab.markDirty()
        rule.onNodeWithTag("save-button").performClick()
        rule.waitUntil(timeoutMillis = 8_000) {
            state.collections
                .firstOrNull { it.name == "MyAPI" }
                ?.children?.firstOrNull()
                ?.bodyContent?.length == secondBody.length
        }

        val finalNode = state.collections
            .firstOrNull { it.name == "MyAPI" }
            ?.children?.firstOrNull()

        assertNotNull(finalNode)
        assertEquals(secondBody, finalNode.bodyContent,
            "CollectionNode must reflect the SECOND saved body, not the first")
    }
}
