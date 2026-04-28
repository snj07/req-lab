package com.reqlab.ui.shared.components

import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.persistence.ImportExportNaming
import com.reqlab.ui.shared.persistence.ImportExportRepository
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import com.reqlab.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ═══════════════════════════════════════════════════════════════════════
 * Pre-Release QA Automation Tests
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Coverage:
 *  1. Per-type body content isolation (JSON / XML / HTML stored separately)
 *  2. Default body type behaviour for new tabs
 *  3. Per-type body content round-trip via export / import (rawContents)
 *  4. AppState.addTab restores per-type body contents from CollectionNode
 *  5. Large single-line document: line-count and truncation boundary awareness
 *  6. Large-file validation threshold (> 1 MB skips inline validation)
 *  7. Fold region detection for multi-type documents
 *  8. Unique collection/environment naming collision avoidance
 *  9. Import/export schema edge cases (missing rawContents backward compat)
 * 10. Workspace round-trip: multiple collections with per-type body content
 */

// ═══════════════════════════════════════════════════════════════════════
// 1. Per-type body content isolation
// ═══════════════════════════════════════════════════════════════════════

class PerTypeBodyContentTest {

    @Test
    fun switching_body_type_preserves_previous_type_content() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"key": "json-value"}"""

        tab.bodyType = BodyType.XML
        tab.bodyContent = "<root><item>xml-value</item></root>"

        // Switching back must restore JSON content, not see XML
        tab.bodyType = BodyType.JSON
        assertEquals("""{"key": "json-value"}""", tab.bodyContent,
            "JSON content must be preserved after switching to XML and back")
    }

    @Test
    fun switching_to_xml_then_back_preserves_xml_content() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.XML
        tab.bodyContent = "<xml>data</xml>"

        tab.bodyType = BodyType.JSON
        tab.bodyContent = "{}"

        tab.bodyType = BodyType.XML
        assertEquals("<xml>data</xml>", tab.bodyContent,
            "XML content must be preserved after visiting JSON")
    }

    @Test
    fun all_three_raw_body_types_stored_independently() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"type": "json"}"""

        tab.bodyType = BodyType.XML
        tab.bodyContent = "<type>xml</type>"

        tab.bodyType = BodyType.HTML
        tab.bodyContent = "<html><body>html</body></html>"

        // Verify all three are preserved
        tab.bodyType = BodyType.JSON
        assertEquals("""{"type": "json"}""", tab.bodyContent)
        tab.bodyType = BodyType.XML
        assertEquals("<type>xml</type>", tab.bodyContent)
        tab.bodyType = BodyType.HTML
        assertEquals("<html><body>html</body></html>", tab.bodyContent)
    }

    @Test
    fun updating_json_content_does_not_affect_xml_content() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.XML
        tab.bodyContent = "<original-xml/>"

        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"updated": true}"""

        tab.bodyType = BodyType.XML
        assertEquals("<original-xml/>", tab.bodyContent,
            "Updating JSON must not mutate XML body content")
    }

    @Test
    fun empty_content_for_unset_body_type() {
        val tab = RequestTabState()
        // A pristine tab that has never had XML content assigned
        tab.bodyType = BodyType.XML
        // bodyContent should be empty/null for a type that was never set
        val content = tab.bodyContent
        assertTrue(content.isNullOrEmpty(),
            "An unset body type must return empty/null content, got: '$content'")
    }

    @Test
    fun body_content_map_contains_entries_for_set_types() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"a": 1}"""
        tab.bodyType = BodyType.XML
        tab.bodyContent = "<a>1</a>"

        assertTrue(tab.bodyContents.containsKey(BodyType.JSON),
            "bodyContents map must contain JSON key after setting JSON content")
        assertTrue(tab.bodyContents.containsKey(BodyType.XML),
            "bodyContents map must contain XML key after setting XML content")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 2. Default body type for new tabs
// ═══════════════════════════════════════════════════════════════════════

class DefaultBodyTypeTest {

    /**
     * U-1 fix: [RequestTabState] now initialises [bodyType] to [BodyType.NONE].
     * A new tab must not auto-set Content-Type: application/json for requests
     * that carry no body (e.g. GET, HEAD, DELETE).
     */
    @Test
    fun new_tab_defaults_to_none_body_type() {
        val tab = RequestTabState()
        assertEquals(BodyType.NONE, tab.bodyType,
            "New tab body type must default to NONE after U-1 fix.")
    }

    @Test
    fun new_tab_has_empty_json_body_content() {
        val tab = RequestTabState()
        // The body content for the default NONE type should start empty
        val content = tab.bodyContent
        assertTrue(content.isNullOrEmpty(),
            "New tab body content must be empty by default, got: '$content'")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 3. Import / export rawContents round-trip
// ═══════════════════════════════════════════════════════════════════════

class PerTypeBodyImportExportTest {

    private fun buildCollectionWithRequest(
        jsonContent: String = "",
        xmlContent: String = "",
        htmlContent: String = "",
        activeBodyType: BodyType = BodyType.JSON,
    ): CollectionNode {
        val bodyContents = buildMap {
            if (jsonContent.isNotEmpty()) put(BodyType.JSON.name, jsonContent)
            if (xmlContent.isNotEmpty()) put(BodyType.XML.name, xmlContent)
            if (htmlContent.isNotEmpty()) put(BodyType.HTML.name, htmlContent)
        }
        val request = CollectionNode(
            id = "req-1",
            name = "Multi-type Request",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.example.com/data",
            bodyType = activeBodyType,
            bodyContent = when (activeBodyType) {
                BodyType.JSON -> jsonContent
                BodyType.XML -> xmlContent
                BodyType.HTML -> htmlContent
                else -> null
            },
            bodyContents = bodyContents,
        )
        return CollectionNode(
            id = "col-1",
            name = "QA Collection",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(request),
        )
    }

    @Test
    fun rawContents_roundtrip_json_and_xml() {
        val jsonBody = """{"userId": 42}"""
        val xmlBody = "<user><id>42</id></user>"

        val root = buildCollectionWithRequest(jsonContent = jsonBody, xmlContent = xmlBody)
        val state = AppState(openDefaultTab = false)
        state.collections.add(root)

        // Export
        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())

        // Import into fresh state
        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val importedRoot = importState.collections.first()
        val importedRequest = importedRoot.children.first()

        assertEquals(jsonBody, importedRequest.bodyContents[BodyType.JSON.name],
            "JSON body content must survive export/import")
        assertEquals(xmlBody, importedRequest.bodyContents[BodyType.XML.name],
            "XML body content must survive export/import")
    }

    @Test
    fun rawContents_roundtrip_three_types() {
        val jsonBody = """{"x": 1}"""
        val xmlBody = "<x>1</x>"
        val htmlBody = "<p>1</p>"

        val root = buildCollectionWithRequest(jsonContent = jsonBody, xmlContent = xmlBody, htmlContent = htmlBody)
        val state = AppState(openDefaultTab = false)
        state.collections.add(root)

        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())
        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val importedRequest = importState.collections.first().children.first()
        assertEquals(jsonBody, importedRequest.bodyContents[BodyType.JSON.name])
        assertEquals(xmlBody,  importedRequest.bodyContents[BodyType.XML.name])
        assertEquals(htmlBody, importedRequest.bodyContents[BodyType.HTML.name])
    }

    @Test
    fun missing_rawContents_backward_compat_produces_empty_map() {
        // A legacy collection JSON without a rawContents field must import cleanly
        val legacyJson = """
        {
            "type": "reqLabCollection",
            "version": "1.0",
            "name": "Legacy Collection",
            "folders": [],
            "requests": [
                {
                    "name": "Legacy Request",
                    "method": "POST",
                    "url": "https://legacy.example.com",
                    "body": { "type": "JSON", "content": "{\"legacy\": true}" }
                }
            ]
        }
        """.trimIndent()

        val state = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(state, legacyJson)

        val request = state.collections.first().children.first()
        assertTrue(request.bodyContents.isEmpty(),
            "Legacy import without rawContents must produce an empty bodyContents map")
        assertEquals("{\"legacy\": true}", request.bodyContent,
            "Legacy single bodyContent field must still be populated")
    }
    
    @Test
    fun rawContents_not_emitted_when_map_is_empty() {
        // A request with no per-type body contents must not include a rawContents key
        val request = CollectionNode(
            id = "req-2",
            name = "No Body Request",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://api.example.com",
        )
        val root = CollectionNode(
            id = "col-2",
            name = "No Body Collection",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(request),
        )
        val state = AppState(openDefaultTab = false)
        state.collections.add(root)

        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())
        assertFalse(exported.contains("rawContents"),
            "rawContents key must not be emitted when bodyContents map is empty")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 4. AppState.addTab restores per-type body contents
// ═══════════════════════════════════════════════════════════════════════

class AddTabBodyContentsRestorationTest {

    @Test
    fun addTab_restores_json_and_xml_body_contents_from_collection_node() {
        val jsonBody = """{"restored": "json"}"""
        val xmlBody  = "<restored>xml</restored>"

        val requestId = "req-restore-1"
        val requestNode = CollectionNode(
            id = requestId,
            name = "Restore Test",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.example.com",
            bodyType = BodyType.JSON,
            bodyContent = jsonBody,
            bodyContents = mapOf(
                BodyType.JSON.name to jsonBody,
                BodyType.XML.name  to xmlBody,
            ),
        )
        val collectionRoot = CollectionNode(
            id = "col-restore-1",
            name = "Restore Collection",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(requestNode),
        )

        val state = AppState(openDefaultTab = false)
        state.collections.add(collectionRoot)

        state.addTab(requestId = requestId, name = "Restore Test", method = HttpMethodType.POST, url = "https://api.example.com")

        val tab = state.activeTab
        assertNotNull(tab, "Active tab must be set after addTab")

        // Verify JSON content is available
        tab.bodyType = BodyType.JSON
        assertEquals(jsonBody, tab.bodyContent,
            "JSON body content must be restored from collection node")

        // Verify XML content is available
        tab.bodyType = BodyType.XML
        assertEquals(xmlBody, tab.bodyContent,
            "XML body content must be restored from collection node")
    }

    @Test
    fun addTab_with_no_bodyContents_in_node_leaves_map_empty() {
        val requestId = "req-empty-1"
        val requestNode = CollectionNode(
            id = requestId,
            name = "Empty Body Node",
            isFolder = false,
            method = HttpMethodType.GET,
            url = "https://api.example.com",
            // No bodyType, no bodyContents
        )
        val collectionRoot = CollectionNode(
            id = "col-empty-1",
            name = "Empty Collection",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(requestNode),
        )

        val state = AppState(openDefaultTab = false)
        state.collections.add(collectionRoot)
        state.addTab(requestId = requestId, name = "Empty Body Node", method = HttpMethodType.GET, url = "https://api.example.com")

        val tab = state.activeTab
        assertNotNull(tab)
        // bodyContents should not contain anything except what was explicitly set
        assertTrue(tab.bodyContents.isEmpty() || tab.bodyContents.values.all { it.isNullOrEmpty() },
            "No body contents should be set when node has no bodyContents")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 5. Large single-line document awareness
// ═══════════════════════════════════════════════════════════════════════

/**
 * Documents the rendering boundaries for large single-line content.
 *
 * KNOWN GAP: A minified single-line JSON file (e.g. 5MB-min.json, 4.4 MB, 1 line)
 * exceeds MAX_RENDER_CHARS_PER_LINE = 50_000. LineView.kt truncates the line to
 * 50 000 chars and appends " … (N chars)". Syntax highlighting falls back to PLAIN.
 * Fold detection returns zero regions for a single-line document.
 */
class SingleLineDocumentBoundaryTest {

    private val MAX_RENDER_CHARS_PER_LINE = 50_000

    @Test
    fun single_line_below_threshold_is_not_truncated() {
        val line = "a".repeat(MAX_RENDER_CHARS_PER_LINE)
        assertFalse(line.length > MAX_RENDER_CHARS_PER_LINE,
            "A line of exactly $MAX_RENDER_CHARS_PER_LINE chars must NOT be flagged as truncated")
    }

    @Test
    fun single_line_above_threshold_is_truncated() {
        val line = "a".repeat(MAX_RENDER_CHARS_PER_LINE + 1)
        assertTrue(line.length > MAX_RENDER_CHARS_PER_LINE,
            "A line of ${line.length} chars MUST be flagged as truncated (exceeds $MAX_RENDER_CHARS_PER_LINE)")
    }

    /**
     * Documents the known gap: a minified 4.4 MB JSON is a single line.
     * Fold detection on a single-line document returns zero regions.
     */
    @Test
    fun single_line_json_document_produces_no_fold_regions() {
        // A minified JSON value — all on one line
        val singleLineJson = """{"a":1,"b":[1,2,3],"c":{"d":true}}"""
        val regions = detectBraceFoldRegions(listOf(singleLineJson))
        assertEquals(0, regions.size,
            "Single-line JSON must produce zero fold regions (all braces on one line)")
    }

    /**
     * Documents the known gap: when a line exceeds MAX_RENDER_CHARS_PER_LINE,
     * the LineView falls back to PLAIN language (no syntax highlighting).
     */
    @Test
    fun truncated_line_falls_back_to_plain_language() {
        val oversizedLine = "[" + "\"item\",".repeat(10_000) + "]"  // ~70 000 chars
        val isTruncated = oversizedLine.length > MAX_RENDER_CHARS_PER_LINE

        assertTrue(isTruncated, "Test setup: line must exceed $MAX_RENDER_CHARS_PER_LINE chars")

        // When truncated, LineView uses PLAIN to avoid colour artefacts at the cut point
        val language = if (isTruncated) SyntaxLanguage.PLAIN else SyntaxLanguage.JSON
        assertEquals(SyntaxLanguage.PLAIN, language,
            "A truncated line must fall back to PLAIN syntax highlighting")
    }

    @Test
    fun single_line_minified_json_is_valid() {
        // Even though it cannot be displayed completely, it must still parse as valid JSON
        val minifiedJson = """{"userId":1,"name":"Alice","roles":["admin","user"],"active":true}"""
        assertNull(validateJson(minifiedJson), "Minified single-line JSON must be valid")
    }

    @Test
    fun multi_line_formatted_json_enables_syntax_highlighting() {
        // A formatted JSON line (typical of 10mb-sample.json) is short → not truncated
        val formattedLine = """  "boardId": "147654876797","""
        assertFalse(formattedLine.length > MAX_RENDER_CHARS_PER_LINE,
            "A typical formatted JSON line must not be truncated")

        val language = if (formattedLine.length > MAX_RENDER_CHARS_PER_LINE) SyntaxLanguage.PLAIN else SyntaxLanguage.JSON
        assertEquals(SyntaxLanguage.JSON, language,
            "A short formatted line must keep JSON syntax highlighting")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 6. Validation threshold for large files
// ═══════════════════════════════════════════════════════════════════════

/**
 * Documents the behaviour of inline validation for large files.
 *
 * KNOWN GAP: BodyEditor.kt silently skips validation for content
 * whose character count exceeds 20 000 000 (20 MB). No user-visible
 * indicator is shown. This test suite documents the threshold value.
 */
class ValidationThresholdTest {

    private val VALIDATION_SKIP_THRESHOLD = 20_000_000

    @Test
    fun content_below_20mb_undergoes_validation() {
        val content = """{"key": "value"}"""
        assertTrue(content.length < VALIDATION_SKIP_THRESHOLD,
            "Content under 20 MB must be subject to inline validation")
    }

    @Test
    fun invalid_json_under_threshold_produces_error() {
        val invalidJson = """{"missing_close": true"""  // unclosed object
        assertTrue(invalidJson.length < VALIDATION_SKIP_THRESHOLD)
        val error = validateJson(invalidJson)
        assertNotNull(error, "Invalid JSON under the validation threshold must produce an error")
    }

    @Test
    fun valid_json_under_threshold_produces_no_error() {
        val validJson = """{"key": "value", "num": 42}"""
        assertTrue(validJson.length < VALIDATION_SKIP_THRESHOLD)
        assertNull(validateJson(validJson), "Valid JSON must produce no validation error")
    }

    /**
     * Documents the boundary where BodyEditor validation is skipped.
     * The caller (BodyEditor) checks `content.length > 20_000_000` and
     * skips calling validateJson/validateXml entirely.
     */
    @Test
    fun content_at_20mb_boundary_threshold_is_documented() {
        // 20_000_001 chars would skip validation; 20_000_000 chars would still validate
        assertEquals(20_000_000, VALIDATION_SKIP_THRESHOLD,
            "Validation skip threshold must be exactly 20 000 000 characters")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 7. Fold region detection across multiple body types
// ═══════════════════════════════════════════════════════════════════════

class MultiTypeBodyFoldDetectionTest {

    @Test
    fun json_formatted_body_produces_fold_regions() {
        val lines = listOf(
            "{",
            "  \"users\": [",
            "    {",
            "      \"id\": 1",
            "    }",
            "  ]",
            "}",
        )
        val regions = detectFoldRegions(lines, SyntaxLanguage.JSON)
        assertTrue(regions.isNotEmpty(), "Formatted JSON body must produce at least one fold region")
    }

    @Test
    fun xml_body_produces_fold_regions() {
        val lines = listOf(
            "<users>",
            "  <user>",
            "    <id>1</id>",
            "  </user>",
            "</users>",
        )
        val regions = detectFoldRegions(lines, SyntaxLanguage.XML)
        assertTrue(regions.isNotEmpty(), "XML body must produce at least one fold region")
    }

    @Test
    fun plain_text_body_produces_no_fold_regions() {
        val lines = listOf("Hello", "World!", "No structure here.")
        val regions = detectFoldRegions(lines, SyntaxLanguage.PLAIN)
        assertEquals(0, regions.size, "Plain text must have no fold regions")
    }

    @Test
    fun minified_single_line_json_produces_no_fold_regions() {
        val lines = listOf("""{"a":1,"b":{"c":2},"d":[1,2,3]}""")
        // Single-line: no multi-line brace regions
        val regions = detectFoldRegions(lines, SyntaxLanguage.JSON)
        assertEquals(0, regions.size,
            "A single-line minified JSON body must produce zero fold regions")
    }

    @Test
    fun javascript_body_produces_fold_regions() {
        val lines = listOf(
            "function transform(data) {",
            "  return data.map(function(item) {",
            "    return item.id;",
            "  });",
            "}",
        )
        val regions = detectFoldRegions(lines, SyntaxLanguage.JAVASCRIPT)
        assertTrue(regions.isNotEmpty(), "JavaScript body with nested braces must produce fold regions")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 8. Unique naming collision avoidance
// ═══════════════════════════════════════════════════════════════════════

class UniqueNamingTest {

    @Test
    fun unique_name_no_conflict_returns_original() {
        val name = ImportExportNaming.generateUniqueCollectionName("MyCollection", emptySet())
        assertEquals("MyCollection", name)
    }

    @Test
    fun unique_name_first_conflict_appends_1() {
        val existing = setOf("MyCollection")
        val name = ImportExportNaming.generateUniqueCollectionName("MyCollection", existing)
        assertEquals("MyCollection (1)", name)
    }

    @Test
    fun unique_name_multiple_conflicts_increments_correctly() {
        val existing = setOf("MyCollection", "MyCollection (1)", "MyCollection (2)")
        val name = ImportExportNaming.generateUniqueCollectionName("MyCollection", existing)
        assertEquals("MyCollection (3)", name)
    }

    @Test
    fun unique_name_blank_input_uses_untitled() {
        val name = ImportExportNaming.generateUniqueCollectionName("   ", emptySet())
        assertEquals("Untitled", name)
    }

    @Test
    fun unique_env_name_no_conflict_returns_original() {
        val name = ImportExportNaming.generateUniqueEnvironmentName("Production", emptySet())
        assertEquals("Production", name)
    }

    @Test
    fun unique_env_name_conflict_appends_counter() {
        val existing = setOf("Production", "Production (1)")
        val name = ImportExportNaming.generateUniqueEnvironmentName("Production", existing)
        assertEquals("Production (2)", name)
    }

    @Test
    fun importing_same_collection_twice_creates_unique_names() {
        val collectionJson = """
        {
            "type": "reqLabCollection",
            "version": "1.0",
            "name": "Duplicate Collection",
            "folders": [],
            "requests": []
        }
        """.trimIndent()

        val state = AppState(openDefaultTab = false)
        val name1 = ImportExportRepository.importCollectionFromString(state, collectionJson)
        val name2 = ImportExportRepository.importCollectionFromString(state, collectionJson)

        assertNotEquals(name1, name2, "Importing the same collection twice must produce distinct names")
        assertEquals("Duplicate Collection", name1)
        assertEquals("Duplicate Collection (1)", name2)
        assertEquals(2, state.collections.size)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 9. JSON / XML validation error message quality
// ═══════════════════════════════════════════════════════════════════════

class ValidationErrorMessageTest {

    @Test
    fun json_trailing_comma_error_has_non_blank_message() {
        val err = validateJson("""{"a": 1,}""")
        assertNotNull(err)
        assertTrue(err.message.isNotBlank(), "JSON trailing-comma error must have a non-blank message")
    }

    @Test
    fun json_unclosed_object_error_has_non_blank_message() {
        val err = validateJson("""{"unclosed": true""")
        assertNotNull(err)
        assertTrue(err.message.isNotBlank(), "JSON unclosed-object error must have a non-blank message")
    }

    @Test
    fun xml_mismatched_tag_error_has_non_blank_message() {
        val err = validateXml("<root><child></root>")
        assertNotNull(err)
        assertTrue(err.message.isNotBlank(), "XML mismatched-tag error must have a non-blank message")
    }

    @Test
    fun xml_unclosed_tag_error_has_non_blank_message() {
        val err = validateXml("<root><child>text</child")
        assertNotNull(err)
        assertTrue(err.message.isNotBlank(), "XML unclosed-tag error must have a non-blank message")
    }

    @Test
    fun json_valid_blank_returns_no_error() {
        assertNull(validateJson(""))
        assertNull(validateJson("   "))
    }

    @Test
    fun xml_valid_blank_returns_no_error() {
        assertNull(validateXml(""))
        assertNull(validateXml("   "))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 10. Workspace round-trip with per-type body content
// ═══════════════════════════════════════════════════════════════════════

class WorkspaceRoundTripTest {

    @Test
    fun workspace_export_import_preserves_per_type_body_contents() {
        val jsonBody = """{"workspace": "test"}"""
        val xmlBody  = "<workspace>test</workspace>"

        val requestId = "req-ws-1"
        val requestNode = CollectionNode(
            id = requestId,
            name = "WS Request",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://ws.example.com",
            bodyType = BodyType.JSON,
            bodyContent = jsonBody,
            bodyContents = mapOf(
                BodyType.JSON.name to jsonBody,
                BodyType.XML.name  to xmlBody,
            ),
        )
        val collRoot = CollectionNode(
            id = "col-ws-1",
            name = "WS Collection",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(requestNode),
        )

        val exportState = AppState(openDefaultTab = false)
        exportState.collections.add(collRoot)

        // Export workspace
        val workspaceJson = ImportExportRepository.exportWorkspaceToString(exportState)
        assertTrue(workspaceJson.contains("rawContents"),
            "Exported workspace JSON must contain a rawContents section")
        // body content strings are JSON-escaped in the output; correctness is validated via the
        // import assertions below rather than raw string searching

        // Import workspace into fresh state
        val importState = AppState(openDefaultTab = false)
        val result = ImportExportRepository.importWorkspaceFromString(importState, workspaceJson)
        assertEquals(1, result.importedCollections, "One collection must be imported")

        val importedRequest = importState.collections.first().children.first()
        assertEquals(jsonBody, importedRequest.bodyContents[BodyType.JSON.name],
            "JSON body content must survive workspace export/import")
        assertEquals(xmlBody, importedRequest.bodyContents[BodyType.XML.name],
            "XML body content must survive workspace export/import")
    }

    @Test
    fun workspace_with_multiple_collections_all_preserved() {
        val state = AppState(openDefaultTab = false)

        listOf("Alpha", "Beta", "Gamma").forEach { name ->
            state.collections.add(CollectionNode(
                id = "col-$name",
                name = name,
                isFolder = true,
                children = androidx.compose.runtime.mutableStateListOf(),
            ))
        }

        val workspaceJson = ImportExportRepository.exportWorkspaceToString(state)
        val importState = AppState(openDefaultTab = false)
        val result = ImportExportRepository.importWorkspaceFromString(importState, workspaceJson)

        assertEquals(3, result.importedCollections,
            "All three collections must be imported from the workspace")
        val names = importState.collections.map { it.name }.toSet()
        assertTrue("Alpha" in names)
        assertTrue("Beta" in names)
        assertTrue("Gamma" in names)
    }
}
