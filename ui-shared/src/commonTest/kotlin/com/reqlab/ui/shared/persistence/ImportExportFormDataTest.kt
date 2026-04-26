package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import com.reqlab.ui.shared.state.FormDataEntryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the form-data / url-encoded entries persistence in
 * [ImportExportRepository]:
 * - formDataEntries round-trip (export → import)
 * - urlencodedEntries round-trip
 * - backward-compat: missing keys produce empty lists
 * - XML / HTML / JAVASCRIPT body types preserved
 */
class ImportExportFormDataTest {

    private fun buildState(vararg nodes: CollectionNode): AppState {
        val state = AppState(openDefaultTab = false)
        val root = CollectionNode(
            id = "root-1",
            name = "Test Collection",
            isFolder = true,
            children = androidx.compose.runtime.mutableStateListOf(*nodes),
        )
        state.collections.add(root)
        return state
    }

    private fun requestNode(
        bodyType: BodyType,
        formDataEntries: List<FormDataEntryState> = emptyList(),
        urlencodedEntries: List<FormDataEntryState> = emptyList(),
        bodyContent: String? = null,
    ) = CollectionNode(
        id = "req-1",
        name = "Test Request",
        isFolder = false,
        method = com.reqlab.core.model.HttpMethodType.POST,
        url = "http://localhost/test",
        bodyType = bodyType,
        bodyContent = bodyContent,
        formDataEntries = formDataEntries,
        urlencodedEntries = urlencodedEntries,
    )

    // ── formDataEntries round-trip ──────────────────────────────

    @Test
    fun formDataEntries_roundtrip_text_fields() {
        val entries = listOf(
            FormDataEntryState(key = "username", type = FormEntryType.TEXT, value = "alice", description = "The user", enabled = true),
            FormDataEntryState(key = "password", type = FormEntryType.TEXT, value = "secret", description = "", enabled = false),
        )
        val node = requestNode(BodyType.FORM_DATA, formDataEntries = entries)
        val state = buildState(node)

        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())

        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val importedRoot = importState.collections.first()
        val importedRequest = importedRoot.children.first()

        assertEquals(2, importedRequest.formDataEntries.size, "Should have 2 form data entries")

        val alice = importedRequest.formDataEntries.first { it.key == "username" }
        assertEquals("alice", alice.value)
        assertEquals("The user", alice.description)
        assertEquals(FormEntryType.TEXT, alice.type)
        assertTrue(alice.enabled)

        val password = importedRequest.formDataEntries.first { it.key == "password" }
        assertEquals("secret", password.value)
        assertEquals(false, password.enabled)
    }

    @Test
    fun formDataEntries_roundtrip_file_field() {
        val entries = listOf(
            FormDataEntryState(key = "attachment", type = FormEntryType.FILE, value = "report.pdf"),
        )
        val node = requestNode(BodyType.FORM_DATA, formDataEntries = entries)
        val state = buildState(node)

        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())
        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val imported = importState.collections.first().children.first()
        assertEquals(1, imported.formDataEntries.size)
        val entry = imported.formDataEntries.first()
        assertEquals(FormEntryType.FILE, entry.type)
        assertEquals("report.pdf", entry.value)
    }

    @Test
    fun formDataEntries_empty_list_roundtrip() {
        val node = requestNode(BodyType.FORM_DATA, formDataEntries = emptyList())
        val state = buildState(node)
        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())

        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val imported = importState.collections.first().children.first()
        assertTrue(imported.formDataEntries.isEmpty())
    }

    // ── urlencodedEntries round-trip ────────────────────────────

    @Test
    fun urlencodedEntries_roundtrip() {
        val entries = listOf(
            FormDataEntryState(key = "name", value = "Bob", description = "User name"),
            FormDataEntryState(key = "age", value = "30", enabled = true),
        )
        val node = requestNode(BodyType.X_WWW_FORM_URLENCODED, urlencodedEntries = entries)
        val state = buildState(node)

        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())
        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val imported = importState.collections.first().children.first()
        assertEquals(2, imported.urlencodedEntries.size)
        assertTrue(imported.urlencodedEntries.any { it.key == "name" && it.value == "Bob" })
        assertTrue(imported.urlencodedEntries.any { it.key == "age" && it.value == "30" })
    }

    // ── Body type round-trip for XML / HTML / JAVASCRIPT ───────

    @Test
    fun xmlBodyType_roundtrip() {
        val node = requestNode(BodyType.XML, bodyContent = "<root><a>1</a></root>")
        val state = buildState(node)
        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())

        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val imported = importState.collections.first().children.first()
        assertEquals(BodyType.XML, imported.bodyType)
        assertEquals("<root><a>1</a></root>", imported.bodyContent)
    }

    @Test
    fun htmlBodyType_roundtrip() {
        val node = requestNode(BodyType.HTML, bodyContent = "<html><body>hello</body></html>")
        val state = buildState(node)
        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())

        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val imported = importState.collections.first().children.first()
        assertEquals(BodyType.HTML, imported.bodyType)
        assertEquals("<html><body>hello</body></html>", imported.bodyContent)
    }

    @Test
    fun javascriptBodyType_roundtrip() {
        val node = requestNode(BodyType.JAVASCRIPT, bodyContent = "const x = 1;")
        val state = buildState(node)
        val exported = ImportExportRepository.exportCollectionToString(state.collections.first())

        val importState = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(importState, exported)

        val imported = importState.collections.first().children.first()
        assertEquals(BodyType.JAVASCRIPT, imported.bodyType)
        assertEquals("const x = 1;", imported.bodyContent)
    }

    // ── Backward compatibility ──────────────────────────────────

    @Test
    fun missing_formDataEntries_key_produces_empty_list() {
        // Manually construct a bare-minimum reqlab collection JSON without formDataEntries
        val legacyJson = """
        {
          "type": "reqLabCollection",
          "version": "1.0",
          "name": "Legacy",
          "folders": [],
          "requests": [
            {
              "name": "Legacy Request",
              "method": "POST",
              "url": "http://example.com",
              "body": { "type": "FORM_DATA" }
            }
          ]
        }
        """.trimIndent()

        val state = AppState(openDefaultTab = false)
        ImportExportRepository.importCollectionFromString(state, legacyJson)

        val imported = state.collections.first().children.first()
        assertTrue(imported.formDataEntries.isEmpty(), "Legacy collection without formDataEntries key should produce empty list")
        assertTrue(imported.urlencodedEntries.isEmpty(), "Legacy collection without urlencodedEntries key should produce empty list")
    }

        @Test
        fun legacy_form_data_content_is_parsed_into_formDataEntries() {
                val legacyJson = """
                {
                    "type": "reqLabCollection",
                    "version": "1.0",
                    "name": "Legacy Form Content",
                    "folders": [],
                    "requests": [
                        {
                            "name": "Legacy Form Request",
                            "method": "POST",
                            "url": "http://example.com/form",
                            "body": {
                                "type": "FORM_DATA",
                                "content": "name=Alice&role=tester&department=engineering"
                            }
                        }
                    ]
                }
                """.trimIndent()

                val state = AppState(openDefaultTab = false)
                ImportExportRepository.importCollectionFromString(state, legacyJson)

                val imported = state.collections.first().children.first()
                assertEquals(3, imported.formDataEntries.size)
                assertTrue(imported.formDataEntries.any { it.key == "name" && it.value == "Alice" })
                assertTrue(imported.formDataEntries.any { it.key == "role" && it.value == "tester" })
                assertTrue(imported.formDataEntries.any { it.key == "department" && it.value == "engineering" })
        }

        @Test
        fun legacy_urlencoded_content_is_parsed_into_urlencodedEntries() {
                val legacyJson = """
                {
                    "type": "reqLabCollection",
                    "version": "1.0",
                    "name": "Legacy Urlencoded Content",
                    "folders": [],
                    "requests": [
                        {
                            "name": "Legacy Urlencoded Request",
                            "method": "POST",
                            "url": "http://example.com/urlencoded",
                            "body": {
                                "type": "X_WWW_FORM_URLENCODED",
                                "content": "username=alice&password=secret123&remember=true"
                            }
                        }
                    ]
                }
                """.trimIndent()

                val state = AppState(openDefaultTab = false)
                ImportExportRepository.importCollectionFromString(state, legacyJson)

                val imported = state.collections.first().children.first()
                assertEquals(3, imported.urlencodedEntries.size)
                assertTrue(imported.urlencodedEntries.any { it.key == "username" && it.value == "alice" })
                assertTrue(imported.urlencodedEntries.any { it.key == "password" && it.value == "secret123" })
                assertTrue(imported.urlencodedEntries.any { it.key == "remember" && it.value == "true" })
        }
}
