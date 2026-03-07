package com.reqlab.ui.desktop.persistence

import com.reqlab.ui.desktop.state.AppState
import org.junit.Test
import java.io.File
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
        val state = AppState()
        val file = File.createTempFile("reqlab-workspace", ".json")
        file.deleteOnExit()

        ImportExportRepository.exportWorkspaceToFile(state, file)

        val json = file.readText()
        assertTrue(json.contains("\"type\": \"reqLabWorkspace\""))
        assertTrue(json.contains("\"version\": \"1.0\""))
        assertTrue(json.contains("\"collections\""))
        assertTrue(json.contains("\"environments\""))
    }

    @Test
    fun exportWorkspace_then_importWorkspace_restoresCollectionsAndEnvironments() {
        val source = AppState()
        val file = File.createTempFile("reqlab-workspace-roundtrip", ".json")
        file.deleteOnExit()

        ImportExportRepository.exportWorkspaceToFile(source, file)

        val target = AppState().also {
            it.collections.clear()
            it.environments.clear()
        }
        val result = ImportExportRepository.importWorkspaceFromFile(target, file)

        assertEquals(source.collections.size, result.importedCollections)
        assertEquals(source.environments.size, result.importedEnvironments)
        assertEquals(source.collections.size, target.collections.size)
        assertEquals(source.environments.size, target.environments.size)
    }

    @Test
    fun importWorkspace_renamesDuplicateCollectionsAndEnvironments() {
        val source = AppState()
        val file = File.createTempFile("reqlab-workspace-dup", ".json")
        file.deleteOnExit()

        ImportExportRepository.exportWorkspaceToFile(source, file)

        val target = AppState()
        val beforeCollections = target.collections.map { it.name }.toSet()
        val beforeEnvironments = target.environments.map { it.name }.toSet()

        val result = ImportExportRepository.importWorkspaceFromFile(target, file)

        assertTrue(result.importedCollections > 0)
        assertTrue(result.importedEnvironments > 0)

        val afterCollections = target.collections.map { it.name }
        val afterEnvironments = target.environments.map { it.name }

        assertTrue(afterCollections.any { it.endsWith("(1)") && it.removeSuffix(" (1)") in beforeCollections })
        assertTrue(afterEnvironments.any { it.endsWith("(1)") && it.removeSuffix(" (1)") in beforeEnvironments })
    }

    @Test
    fun exportAndImportSingleCollection_roundTripsWithCollectionSchema() {
        val source = AppState()
        val collection = source.collections.first()
        val file = File.createTempFile("reqlab-collection", ".json")
        file.deleteOnExit()

        ImportExportRepository.exportCollectionToFile(collection, file)
        val content = file.readText()
        assertTrue(content.contains("\"type\": \"reqLabCollection\""))
        assertTrue(content.contains("\"folders\""))
        assertTrue(content.contains("\"requests\""))

        val target = AppState().also { it.collections.clear() }
        val importedName = ImportExportRepository.importCollectionFromFile(target, file)

        assertEquals(collection.name, importedName)
        assertEquals(1, target.collections.size)
    }

    @Test
    fun exportAndImportEnvironment_roundTripsWithEnvironmentSchema() {
        val source = AppState()
        val environment = source.environments.first()
        val file = File.createTempFile("reqlab-env", ".json")
        file.deleteOnExit()

        ImportExportRepository.exportEnvironmentToFile(environment, file)
        val content = file.readText()
        assertTrue(content.contains("\"type\": \"reqLabEnvironment\""))
        assertTrue(content.contains("\"variables\""))

        val target = AppState().also { it.environments.clear() }
        val importedName = ImportExportRepository.importEnvironmentFromFile(target, file)

        assertEquals(environment.name, importedName)
        assertEquals(1, target.environments.size)
        assertEquals(environment.variables.size, target.environments.first().variables.size)
    }

    @Test(expected = ImportExportException::class)
    fun importWorkspace_invalidSchema_throws() {
        val file = File.createTempFile("reqlab-invalid", ".json")
        file.deleteOnExit()
        file.writeText("""
            {
              "type": "notWorkspace",
              "version": "1.0",
              "collections": [],
              "environments": []
            }
        """.trimIndent())

        ImportExportRepository.importWorkspaceFromFile(AppState(), file)
    }

    @Test
    fun end_to_end_workspace_backup_restore_flow() {
        val app = AppState()
        app.collections.add(
            com.reqlab.ui.desktop.state.CollectionNode(
                id = "custom-c1",
                name = "Custom API",
                isFolder = true,
                children = androidx.compose.runtime.mutableStateListOf(
                    com.reqlab.ui.desktop.state.CollectionNode(
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
            com.reqlab.ui.desktop.state.EnvState(
                "CI",
                variables = listOf(com.reqlab.ui.desktop.state.MutableKeyValue("baseUrl", "https://example.com")),
            )
        )

        val file = File.createTempFile("reqlab-e2e-workspace", ".json")
        file.deleteOnExit()

        ImportExportRepository.exportWorkspaceToFile(app, file)

        app.collections.clear()
        app.environments.clear()
        assertEquals(0, app.collections.size)
        assertEquals(0, app.environments.size)

        val result = ImportExportRepository.importWorkspaceFromFile(app, file)
        assertTrue(result.importedCollections > 0)
        assertTrue(result.importedEnvironments > 0)
        assertTrue(app.collections.any { it.name == "Custom API" })
        assertTrue(app.environments.any { it.name == "CI" })
    }
}
