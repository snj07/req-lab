package com.reqlab.ui.shared.persistence

import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.HistoryItem
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.platform.currentTimeMillis
import org.junit.Test
import kotlin.test.assertEquals
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
}
