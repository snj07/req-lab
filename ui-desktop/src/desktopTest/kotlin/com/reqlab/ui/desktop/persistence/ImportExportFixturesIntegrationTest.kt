package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.McpSamplingMode
import com.reqlab.core.model.McpTransportType
import com.reqlab.core.model.RequestKind
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        assertTrue(root.children.any { it.isFolder && it.name == "LLM (OpenAI-compatible)" })
        assertTrue(root.children.any { it.isFolder && it.name == "SSE" })
        assertTrue(root.children.any { it.isFolder && it.name == "MCP (Model Context Protocol)" })

        val env = restored.environments.firstOrNull { it.name == "Local Dev – Sample Server" }
        assertTrue(env != null)
        assertTrue(env.variables.any { it.key == "graphqlUserId" && it.value == "1" })
        assertTrue(env.variables.any { it.key == "llmBaseUrl" && it.value == "http://localhost:8080" })
        assertTrue(env.variables.any { it.key == "llmApiKey" && it.value == "llm-test-key" })
        assertTrue(env.variables.any { it.key == "mcpBaseUrl" && it.value == "http://localhost:8080/mcp" })
        assertTrue(env.variables.any { it.key == "mcpAuthedUrl" && it.value == "http://localhost:8080/mcp/authed" })
        assertTrue(env.variables.any { it.key == "mcpBearerUrl" && it.value == "http://localhost:8080/mcp/auth/bearer" })
        assertTrue(env.variables.any { it.key == "mcpTenantUrl" && it.value.contains("requireTenant=true") })
        assertTrue(env.variables.any { it.key == "mcpLegacyUrl" && it.value.contains("/mcp/sse") })
        assertTrue(env.variables.any { it.key == "mcpStdioCommand" && it.value == "sample-server" })

        val llm = findRequest(restored.collections, "MCP Sampling LLM")
        assertNotNull(llm)
        assertEquals(RequestKind.MCP, llm.kind)
        assertEquals(McpSamplingMode.FORWARD_LLM, llm.mcpConfig?.samplingMode)
        assertEquals("{{llmBaseUrl}}/v1/chat/completions", llm.mcpConfig?.samplingForwardUrl)
        val stdio = findRequest(restored.collections, "MCP stdio")
        assertNotNull(stdio)
        assertEquals(McpTransportType.STDIO, stdio.mcpConfig?.transport)
        assertEquals("{{mcpStdioCommand}}", stdio.mcpConfig?.command)
    }

    @Test
    fun imported_mcp_sampling_llm_and_stdio_fields_are_present() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, collectionFixture.readText())
        val llm = findRequest(state.collections, "MCP Sampling LLM")
        assertNotNull(llm)
        assertEquals(McpSamplingMode.FORWARD_LLM, llm.mcpConfig!!.samplingMode)
        assertEquals("{{llmBaseUrl}}/v1/chat/completions", llm.mcpConfig!!.samplingForwardUrl)
        val stdio = findRequest(state.collections, "MCP stdio")
        assertNotNull(stdio)
        assertEquals(McpTransportType.STDIO, stdio.mcpConfig!!.transport)
        assertEquals("{{mcpStdioCommand}}", stdio.mcpConfig!!.command)
    }

    @Test
    fun imported_sse_folder_has_accept_event_stream_and_stays_http() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, collectionFixture.readText())
        val getEvents = findRequest(state.collections, "SSE GET events")
        assertNotNull(getEvents)
        assertEquals(RequestKind.HTTP, getEvents.kind)
        assertTrue(
            getEvents.userHeaders.any {
                it.first.equals("Accept", ignoreCase = true) && it.second.contains("text/event-stream")
            },
        )
        val postEvents = findRequest(state.collections, "SSE POST events")
        assertNotNull(postEvents)
        assertEquals(RequestKind.HTTP, postEvents.kind)
        assertEquals(HttpMethodType.POST, postEvents.method)
    }

    @Test
    fun imported_json5_folder_keeps_authored_comments() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, collectionFixture.readText())

        val bodyTypes = findFolder(state.collections, "Body Types")
        assertNotNull(bodyTypes)
        assertTrue(bodyTypes.children.any { it.isFolder && it.name == "JSON5" })

        val comments = findRequest(state.collections, "POST JSON5 comments")
        assertNotNull(comments)
        val authored = comments.bodyContent.orEmpty().ifBlank {
            comments.bodyContents["JSON"].orEmpty()
        }
        assertTrue(authored.contains("//"), authored)
        assertTrue(authored.contains("role"), authored)

        assertNotNull(findRequest(state.collections, "POST JSON5 trailing comma"))
        assertNotNull(findRequest(state.collections, "POST JSON5 unquoted keys"))
    }

    private fun findFolder(nodes: List<CollectionNode>, name: String): CollectionNode? {
        for (node in nodes) {
            if (node.isFolder && node.name == name) return node
            if (node.isFolder) findFolder(node.children, name)?.let { return it }
        }
        return null
    }

    private fun findRequest(nodes: List<CollectionNode>, name: String): CollectionNode? {
        for (node in nodes) {
            if (!node.isFolder && node.name == name) return node
            if (node.isFolder) findRequest(node.children, name)?.let { return it }
        }
        return null
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
