package com.reqlab.ui.shared.persistence

import com.reqlab.ui.shared.state.AppState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportExportFixturesIntegrationTest {

    private val collectionFixture = resolveFixture("reqlab-test-collection.json")
    private val environmentFixture = resolveFixture("reqlab-test-environment.json")

    @Test
    fun imports_collection_and_environment_from_deterministic_fixtures() {
        val state = AppState(openDefaultTab = false, withDemoData = false)

        val importedCollection = ImportExportRepository.importCollectionFromString(state, collectionFixture.readText())
        val importedEnvironment = ImportExportRepository.importEnvironmentFromString(state, environmentFixture.readText())

        assertEquals("ReqLab Test Suite", importedCollection)
        assertEquals("Local Dev – Sample Server", importedEnvironment)

        assertTrue(state.collections.isNotEmpty())
        assertTrue(state.environments.isNotEmpty())
        assertTrue(state.environments.first().variables.any { it.key == "baseUrl" && it.value == "http://localhost:8080" })
    }

    @Test
    fun fixture_workspace_roundtrip_restores_nested_collection_and_variables() {
        val source = AppState(openDefaultTab = false, withDemoData = false)

        ImportExportRepository.importCollectionFromString(source, collectionFixture.readText())
        ImportExportRepository.importEnvironmentFromString(source, environmentFixture.readText())

        val exported = ImportExportRepository.exportWorkspaceToString(source)
        val decoded = ImportExportRepository.decodeWorkspace(exported)

        val restored = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.replaceWorkspaceState(restored, decoded)

        assertEquals(source.collections.size, restored.collections.size)
        assertEquals(source.environments.size, restored.environments.size)

        val root = restored.collections.firstOrNull { it.name == "ReqLab Test Suite" }
        assertTrue(root != null)
        assertTrue(root.children.any { it.isFolder && it.name == "HTTP Methods" })

        val env = restored.environments.firstOrNull { it.name == "Local Dev – Sample Server" }
        assertTrue(env != null)
        assertTrue(env.variables.any { it.key == "graphqlUserId" && it.value == "1" })
    }

    private fun resolveFixture(name: String): File {
        val candidates = listOf(
            File("qa-tests/fixtures/$name"),
            File("../qa-tests/fixtures/$name"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Fixture not found: $name")
    }
}
