package com.reqlab.ui.shared.persistence

import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import com.reqlab.ui.shared.state.HistoryItem
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.platform.currentTimeMillis
import androidx.compose.runtime.mutableStateListOf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportExportRepositoryTest {

    @Test
    fun generateUniqueCollectionName_appendsIncrementingSuffix() {
        val existing = setOf("User API", "User API (1)", "User API (2)")
        val unique = ImportExportNaming.generateUniqueCollectionName("User API", existing)
        assertEquals("User API (3)", unique)
    }

    @Test
    fun generateUniqueEnvironmentName_appendsIncrementingSuffix() {
        val existing = setOf("Dev", "Dev (1)")
        val unique = ImportExportNaming.generateUniqueEnvironmentName("Dev", existing)
        assertEquals("Dev (2)", unique)
    }

    @Test
    fun exportWorkspace_writesExpectedSchemaEnvelope() {
        val state = AppState(withDemoData = true)

        val json = ImportExportRepository.exportWorkspaceToString(state)

        assertTrue(json.contains("\"type\": \"reqLabWorkspace\""))
        assertTrue(json.contains("\"version\": \"1.0\""))
        assertTrue(json.contains("\"collections\""))
        assertTrue(json.contains("\"environments\""))
        assertTrue(json.contains("\"globalVariables\""))
        assertTrue(json.contains("\"history\""))
    }

    @Test
    fun exportWorkspace_then_importWorkspace_restoresCollectionsAndEnvironments() {
        val source = AppState(withDemoData = true)

        val json = ImportExportRepository.exportWorkspaceToString(source)

        val target = AppState().also {
            it.collections.clear()
            it.environments.clear()
        }
        val result = ImportExportRepository.importWorkspaceFromString(target, json)

        assertEquals(source.collections.size, result.importedCollections)
        assertEquals(source.environments.size, result.importedEnvironments)
        assertEquals(source.collections.size, target.collections.size)
        assertEquals(source.environments.size, target.environments.size)
    }

    @Test
    fun importWorkspace_renamesDuplicateCollectionsAndEnvironments() {
        val source = AppState(withDemoData = true)

        val json = ImportExportRepository.exportWorkspaceToString(source)

        val target = AppState(withDemoData = true)
        val beforeCollections = target.collections.map { it.name }.toSet()
        val beforeEnvironments = target.environments.map { it.name }.toSet()

        val result = ImportExportRepository.importWorkspaceFromString(target, json)

        assertTrue(result.importedCollections > 0)
        assertTrue(result.importedEnvironments > 0)

        val afterCollections = target.collections.map { it.name }
        val afterEnvironments = target.environments.map { it.name }

        assertTrue(afterCollections.any { it.endsWith("(1)") && it.removeSuffix(" (1)") in beforeCollections })
        assertTrue(afterEnvironments.any { it.endsWith("(1)") && it.removeSuffix(" (1)") in beforeEnvironments })
    }

    @Test
    fun exportAndImportSingleCollection_roundTripsWithCollectionSchema() {
        val source = AppState(withDemoData = true)
        val collection = source.collections.first()

        val content = ImportExportRepository.exportCollectionToString(collection)
        assertTrue(content.contains("\"type\": \"reqLabCollection\""))
        assertTrue(content.contains("\"folders\""))
        assertTrue(content.contains("\"requests\""))

        val target = AppState().also { it.collections.clear() }
        val importedName = ImportExportRepository.importCollectionFromString(target, content)

        assertEquals(collection.name, importedName)
        assertEquals(1, target.collections.size)
    }

    @Test
    fun exportAndImportEnvironment_roundTripsWithEnvironmentSchema() {
        val source = AppState(withDemoData = true)
        val environment = source.environments.first()

        val content = ImportExportRepository.exportEnvironmentToString(environment)
        assertTrue(content.contains("\"type\": \"reqLabEnvironment\""))
        assertTrue(content.contains("\"variables\""))

        val target = AppState().also { it.environments.clear() }
        val importedName = ImportExportRepository.importEnvironmentFromString(target, content)

        assertEquals(environment.name, importedName)
        assertEquals(1, target.environments.size)
        assertEquals(environment.variables.size, target.environments.first().variables.size)
    }

    @Test(expected = ImportExportException::class)
    fun importWorkspace_invalidSchema_throws() {
        val json = """
            {
              "type": "notWorkspace",
              "version": "1.0",
              "collections": [],
              "environments": []
            }
        """.trimIndent()

        ImportExportRepository.importWorkspaceFromString(AppState(), json)
    }

    @Test
    fun end_to_end_workspace_backup_restore_flow() {
        val app = AppState(withDemoData = true)
        app.collections.add(
            com.reqlab.ui.shared.state.CollectionNode(
                id = "custom-c1",
                name = "Custom API",
                isFolder = true,
                children = androidx.compose.runtime.mutableStateListOf(
                    com.reqlab.ui.shared.state.CollectionNode(
                        id = "custom-r1",
                        name = "Ping",
                        isFolder = false,
                        method = com.reqlab.core.model.HttpMethodType.GET,
                        url = "https://example.com/ping",
                    )
                ),
            )
        )
        app.environments.add(
            com.reqlab.ui.shared.state.EnvState(
                "CI",
                variables = listOf(com.reqlab.ui.shared.state.MutableKeyValue("baseUrl", "https://example.com")),
            )
        )

        val json = ImportExportRepository.exportWorkspaceToString(app)

        app.collections.clear()
        app.environments.clear()
        assertEquals(0, app.collections.size)
        assertEquals(0, app.environments.size)

        val result = ImportExportRepository.importWorkspaceFromString(app, json)
        assertTrue(result.importedCollections > 0)
        assertTrue(result.importedEnvironments > 0)
        assertTrue(app.collections.any { it.name == "Custom API" })
        assertTrue(app.environments.any { it.name == "CI" })
    }

    @Test
    fun replaceWorkspaceState_restores_global_variables_and_history() {
        val source = AppState().apply {
            globalVariables.add(com.reqlab.ui.shared.state.MutableKeyValue("apiKey", "abc123"))
            historyItems.add(
                HistoryItem(
                    requestId = "hist-1",
                    method = HttpMethodType.GET,
                    name = "Ping",
                    url = "https://example.com/ping",
                    timestamp = currentTimeMillis(),
                )
            )
        }

        val json = ImportExportRepository.exportWorkspaceToString(source)
        val target = AppState()
        target.globalVariables.clear()
        target.historyItems.clear()

        val workspace = ImportExportRepository.decodeWorkspace(json)
        ImportExportRepository.replaceWorkspaceState(target, workspace)

        assertTrue(target.globalVariables.any { it.key == "apiKey" && it.value == "abc123" })
        assertTrue(target.historyItems.any { it.requestId == "hist-1" && it.name == "Ping" })
    }

    // ── Large-body export / import (pure serialisation — no PlatformStorage) ───

    @Test
    fun exportCollection_with_large_body_roundtrips_content_exactly() {
        val largeBody = "{\"items\":[" + (0 until 30_000).joinToString(",") { "{\"id\":$it}" } + "]}"

        val request = com.reqlab.ui.shared.state.CollectionNode(
            id = "req-large-body-1",
            name = "Large Body Request",
            isFolder = false,
            method = com.reqlab.core.model.HttpMethodType.POST,
            url = "https://api.example.com/bulk",
            bodyType = com.reqlab.core.model.BodyType.JSON,
            bodyContent = largeBody,
            bodyContents = mapOf(com.reqlab.core.model.BodyType.JSON.name to largeBody),
        )
        val collection = com.reqlab.ui.shared.state.CollectionNode(
            id = "col-large-body-1",
            name = "Large Body Collection",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(request),
        )
        val source = AppState(openDefaultTab = false)
        source.collections.add(collection)

        val exported = ImportExportRepository.exportCollectionToString(source.collections.first())

        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val imported = importState.collections.first().children.first()
        assertEquals(
            largeBody.length,
            imported.bodyContent?.length,
            "Large body length must survive collection export/import",
        )
        assertEquals(
            largeBody,
            imported.bodyContent,
            "Large body content must survive collection export/import exactly",
        )
    }

    @Test
    fun exportWorkspace_with_large_body_roundtrips_content_exactly() {
        // 200 KB body embedded in the workspace JSON
        val largeBody = "{\"data\":\"" + "W".repeat(200_000) + "\"}"

        val request = com.reqlab.ui.shared.state.CollectionNode(
            id = "req-ws-large-1",
            name = "Large WS Body",
            isFolder = false,
            method = com.reqlab.core.model.HttpMethodType.POST,
            url = "https://api.example.com/data",
            bodyType = com.reqlab.core.model.BodyType.JSON,
            bodyContent = largeBody,
            bodyContents = mapOf(com.reqlab.core.model.BodyType.JSON.name to largeBody),
        )
        val collection = com.reqlab.ui.shared.state.CollectionNode(
            id = "col-ws-large-1",
            name = "Large WS Collection",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(request),
        )
        val source = AppState(openDefaultTab = false)
        source.collections.add(collection)

        val exported = ImportExportRepository.exportWorkspaceToString(source)

        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importWorkspaceFromString(importState, exported)

        val imported = importState.collections.first().children.first()
        assertEquals(
            largeBody.length,
            imported.bodyContent?.length,
            "Large body length must survive workspace export/import",
        )
        assertEquals(
            largeBody,
            imported.bodyContent,
            "Large body content must survive workspace export/import exactly",
        )
    }

    // ── Bug 1: per-collection expanded state must survive workspace save/load ──

    @Test
    fun replaceWorkspaceState_restores_collection_expanded_state() {
        // Bug 1 BEFORE FIX: collectionExpandedState was never written to/read from JSON.
        // Any folder the user collapsed would appear expanded again after restart.
        // AFTER FIX: the map is serialised in exportWorkspaceToString and
        // re-applied by replaceWorkspaceState so the UI shows the correct state.
        val source = AppState(openDefaultTab = false)
        val collection = CollectionNode(
            id = "coll-test-1",
            name = "My Collection",
            isFolder = true,
            children = mutableStateListOf(
                CollectionNode("folder-a", "Folder A", isFolder = true, children = mutableStateListOf()),
                CollectionNode("folder-b", "Folder B", isFolder = true, children = mutableStateListOf()),
            ),
        )
        source.collections.add(collection)
        // Explicitly collapse one folder, leave the other at default (expanded)
        source.collectionExpandedState["folder-a"] = false
        source.collectionExpandedState["coll-test-1"] = false

        val json = ImportExportRepository.exportWorkspaceToString(source)

        val target = AppState(openDefaultTab = false)
        val workspace = ImportExportRepository.decodeWorkspace(json)
        ImportExportRepository.replaceWorkspaceState(target, workspace)

        assertEquals(false, target.collectionExpandedState["folder-a"],
            "folder-a was collapsed — must be restored as collapsed")
        assertEquals(false, target.collectionExpandedState["coll-test-1"],
            "coll-test-1 was collapsed — must be restored as collapsed")
        // folder-b was never written → absent means expanded (default)
        assertNull(target.collectionExpandedState["folder-b"],
            "folder-b was never explicitly set — must remain absent (= expanded by default)")
    }

    @Test
    fun exportWorkspaceToString_includes_collectionExpandedState_field() {
        val source = AppState(openDefaultTab = false)
        source.collections.add(
            CollectionNode("coll-x", "X", isFolder = true, children = mutableStateListOf())
        )
        source.collectionExpandedState["coll-x"] = false

        val json = ImportExportRepository.exportWorkspaceToString(source)

        assertTrue(json.contains("collectionExpandedState"),
            "Exported workspace JSON must contain the collectionExpandedState field")
    }

    @Test
    fun importWorkspaceFromString_preserves_collection_and_folder_node_ids() {
        // Root cause of Bug 1: collectionDtoToNode / folderDtoToNode called generateUuid()
        // on every load so node IDs changed after restart, making collectionExpandedState
        // keys stale.  AFTER FIX: IDs are written to JSON and restored on import.
        val source = AppState(openDefaultTab = false)
        val folderId = "folder-persist-1"
        val collId   = "coll-persist-1"
        source.collections.add(
            CollectionNode(
                id = collId, name = "Persist Test", isFolder = true,
                children = mutableStateListOf(
                    CollectionNode(id = folderId, name = "Sub Folder", isFolder = true,
                        children = mutableStateListOf()),
                ),
            )
        )

        val json   = ImportExportRepository.exportWorkspaceToString(source)
        val target = AppState(openDefaultTab = false)
        ImportExportRepository.importWorkspaceFromString(target, json)

        val importedColl = target.collections.first { it.name == "Persist Test" }
        assertEquals(collId,   importedColl.id,
            "Collection ID must be preserved through workspace export/import")

        val importedFolder = importedColl.children.first { it.name == "Sub Folder" }
        assertEquals(folderId, importedFolder.id,
            "Folder ID must be preserved through workspace export/import")
    }

    @Test
    fun expandedState_survives_full_importWorkspaceFromString_roundtrip() {
        // End-to-end regression test for Bug 1 (Collection expand state).
        // Before the fix, nodes got new UUIDs on every load so the expand-state
        // map keys never matched the reloaded nodes.
        //
        // The actual startup path is: exportWorkspaceToString → decodeWorkspace →
        // replaceWorkspaceState (done by WorkspaceRepository.load).
        val source   = AppState(openDefaultTab = false)
        val collId   = "coll-expand-rt-1"
        val folderId = "folder-expand-rt-1"
        source.collections.add(
            CollectionNode(
                id = collId, name = "Round-trip Coll", isFolder = true,
                children = mutableStateListOf(
                    CollectionNode(id = folderId, name = "Round-trip Folder", isFolder = true,
                        children = mutableStateListOf()),
                ),
            )
        )
        source.collectionExpandedState[collId]   = false
        source.collectionExpandedState[folderId] = false

        val json      = ImportExportRepository.exportWorkspaceToString(source)
        val target    = AppState(openDefaultTab = false)
        val workspace = ImportExportRepository.decodeWorkspace(json)
        ImportExportRepository.replaceWorkspaceState(target, workspace)

        // Nodes must be found by their RESTORED IDs
        val restoredColl   = target.collections.first { it.name == "Round-trip Coll" }
        val restoredFolder = restoredColl.children.first { it.name == "Round-trip Folder" }

        assertEquals(collId,   restoredColl.id,   "Collection ID must survive workspace restart")
        assertEquals(folderId, restoredFolder.id, "Folder ID must survive workspace restart")

        assertEquals(false, target.collectionExpandedState[restoredColl.id],
            "Collection collapsed state must survive workspace restart")
        assertEquals(false, target.collectionExpandedState[restoredFolder.id],
            "Folder collapsed state must survive workspace restart")
    }
}
