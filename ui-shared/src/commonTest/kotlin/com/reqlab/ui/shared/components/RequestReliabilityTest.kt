package com.reqlab.ui.shared.components

import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.core.model.KeyValueEntry
import com.reqlab.core.model.McpOAuthConfig
import com.reqlab.ui.shared.persistence.ImportExportRepository
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import com.reqlab.ui.shared.state.MutableFormDataRow
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestReliabilityTest {
    @Test
    fun query_codec_decodes_once_and_rebuilds_literal_values_and_templates() {
        val tab = RequestTabState(url = "https://example.test/path?x=1&x=2&space=a%20b&plus=a%2Bb&literal=a+b&and=a%26b&eq=a%3Db&pct=%25&unicode=%E2%9C%93&empty=&token={{value}}#part")
        syncParamsFromUrl(tab, tab.url)
        assertEquals(listOf("1", "2"), tab.params.filter { it.key == "x" }.map { it.value })
        assertEquals("a+b", tab.params.first { it.key == "plus" }.value)
        assertEquals("a+b", tab.params.first { it.key == "literal" }.value)
        assertEquals("a&b", tab.params.first { it.key == "and" }.value)
        assertEquals("✓", tab.params.first { it.key == "unicode" }.value)
        syncUrlFromParams(tab)
        assertTrue(tab.url.contains("plus=a%2Bb"))
        assertTrue(tab.url.contains("literal=a%2Bb"))
        assertTrue(tab.url.contains("and=a%26b"))
        assertTrue(tab.url.contains("pct=%25"))
        assertTrue(tab.url.contains("token={{value}}"))
        assertTrue(tab.url.endsWith("#part"))
        assertEquals(listOf("1", "2"), parseQueryPairs(splitRequestUrl(tab.url).query).filter { it.first == "x" }.map { it.second })
    }

    @Test
    fun graphql_tab_prepares_envelope_instead_of_unused_raw_content() {
        val tab = RequestTabState().apply { bodyType = BodyType.GRAPHQL; bodyContent = "query { users { id } }" }
        val body = buildRequestBody(tab, tab.bodyContent)
        assertEquals(null, body.content)
        assertEquals("query { users { id } }", body.graphQl?.query)
        assertTrue(buildCurlCommand(tab).contains("query"))
        assertTrue(buildPythonCommand(tab).contains("query"))
    }

    @Test
    fun every_dirty_notification_advances_autosave_revision_and_mcp_headers_affect_dirty() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "abc"
        tab.markSaved()
        val first = tab.persistenceRevision
        tab.bodyContent = "xyz"
        tab.markDirty()
        assertEquals(first + 1, tab.persistenceRevision)
        assertTrue(tab.isDirty)
        tab.markSaved()
        tab.mcpConfig = tab.mcpConfig.copy(headers = listOf(KeyValueEntry("X-MCP", "changed")))
        tab.markDirty()
        assertTrue(tab.isDirty)
        val a = tab.mcpConfig.copy(
            args = listOf("--mode", "dev"), env = mapOf("A" to "1", "B" to "2"),
            workingDir = "/tmp/service", oauth = McpOAuthConfig(redirectPort = 9000),
        )
        tab.mcpConfig = a
        val fingerprint = tab.mcpClientFingerprint()
        tab.mcpConfig = a.copy(env = mapOf("B" to "2", "A" to "1"))
        assertEquals(fingerprint, tab.mcpClientFingerprint())
        tab.mcpConfig = a.copy(oauth = a.oauth?.copy(redirectPort = 9001))
        assertTrue(fingerprint != tab.mcpClientFingerprint())
    }

    @Test
    fun collection_roundtrip_preserves_disabled_repeated_secret_rows_and_api_placement() {
        val state = AppState(openDefaultTab = false)
        val request = CollectionNode(
            id = "request", name = "Rows", method = HttpMethodType.GET,
            url = "https://example.test/path?x=1&x=2",
            queryEntries = listOf(
                KeyValueEntry("x", "1", secret = true),
                KeyValueEntry("x", "2"),
                KeyValueEntry("disabled", "value", enabled = false),
            ),
            headerEntries = listOf(
                KeyValueEntry("X-Repeat", "one", secret = true),
                KeyValueEntry("X-Repeat", "two"),
                KeyValueEntry("X-Off", "no", enabled = false),
            ),
            authType = AuthType.API_KEY, authApiKey = "key", authApiValue = "secret",
            authApiPlacement = "query",
        )
        state.collections.add(CollectionNode("collection", "Collection", isFolder = true).also { it.children.add(request) })
        val serialized = ImportExportRepository.exportCollectionToString(state.collections.single())
        assertTrue(serialized.contains("\"headerEntries\""))
        assertTrue(serialized.contains("\"queryEntries\""))
        assertTrue(serialized.contains("\"headers\""))
        val loaded = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(loaded, serialized)
        val node = loaded.collections.single().children.single()
        assertEquals(request.queryEntries, node.queryEntries)
        assertEquals(request.headerEntries, node.headerEntries)
        assertEquals("query", node.authApiPlacement)
        assertFalse(node.url.orEmpty().contains("disabled="))
        loaded.openRequest(node.id, node.name, HttpMethodType.GET, node.url.orEmpty())
        assertEquals(listOf("1", "2", "value"), loaded.activeTab!!.params.map { it.value })
        assertFalse(loaded.activeTab!!.params.last().enabled)
        assertEquals(listOf("one", "two", "no"), loaded.activeTab!!.headers.map { it.value })
    }

    @Test
    fun copy_menu_disables_unrepresentable_bodies_and_duplicate_headers() {
        val state = AppState()
        val tab = state.activeTab!!
        tab.headers.add(MutableKeyValue("X-Repeat", "one"))
        tab.headers.add(MutableKeyValue("X-Repeat", "two"))
        val options = buildCopyFormats(tab, state)
        assertTrue(options.first().available)
        assertFalse(options[1].available)
        assertFalse(options[2].available)
        assertTrue(options[1].reason.orEmpty().contains("Duplicate"))
        tab.bodyType = BodyType.FORM_DATA
        tab.formRows.add(MutableFormDataRow("file", FormEntryType.FILE, "attachment.txt", bytesBase64 = "YQ=="))
        assertTrue(buildCopyFormats(tab, state).all { !it.available && it.reason.orEmpty().contains("file") })
    }

    @Test
    fun legacy_one_point_zero_collection_uses_url_and_pair_headers_with_header_api_key() {
        val legacy = """
            {"type":"reqLabCollection","version":"1.0","name":"Legacy","folders":[],
             "requests":[{"name":"Old","method":"GET","url":"https://example.test/?x=1&x=2",
               "headers":[{"key":"X-Old","value":"yes"}],
               "auth":{"type":"API_KEY","apiKey":"key","apiValue":"value"}}]}
        """.trimIndent()
        val state = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(state, legacy)
        val node = state.collections.single().children.single()
        assertEquals(null, node.queryEntries)
        assertEquals(null, node.headerEntries)
        state.openRequest(node.id, node.name, HttpMethodType.GET, node.url.orEmpty())
        assertEquals(listOf("1", "2"), state.activeTab!!.params.map { it.value })
        assertTrue(state.activeTab!!.headers.any { it.key == "Content-Type" })
        assertTrue(state.activeTab!!.headers.any { it.key == "X-Old" })
        assertEquals("header", state.activeTab!!.authApiPlacement)
    }
}
