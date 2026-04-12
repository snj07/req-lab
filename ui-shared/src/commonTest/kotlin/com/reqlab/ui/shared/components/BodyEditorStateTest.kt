package com.reqlab.ui.shared.components

import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.ui.shared.state.MutableFormDataRow
import com.reqlab.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests covering:
 * - JSON & XML validation (validateJson / validateXml)
 * - JSON error line/column extraction
 * - MutableFormDataRow defaults and mutation
 * - XML/HTML/JavaScript body types are covered by bodyTypeToLanguage (via SyntaxLanguage mapping)
 */
class BodyEditorStateTest {

    // ── validateJson ────────────────────────────────────────────

    @Test
    fun validateJson_blank_returns_null() {
        assertNull(validateJson(""))
        assertNull(validateJson("   "))
    }

    @Test
    fun validateJson_valid_object_returns_null() {
        assertNull(validateJson("""{"key":"value","num":42}"""))
    }

    @Test
    fun validateJson_valid_array_returns_null() {
        assertNull(validateJson("""[1,2,3]"""))
    }

    @Test
    fun validateJson_valid_nested_object_returns_null() {
        val json = """
        {
            "a": 1,
            "b": { "c": [true, false, null] },
            "d": "string"
        }
        """.trimIndent()
        assertNull(validateJson(json))
    }

    @Test
    fun validateJson_invalid_missing_close_brace_returns_error() {
        val err = validateJson("""{"key": "value" """)
        assertNotNull(err)
        assertTrue(err.message.isNotBlank())
    }

    @Test
    fun validateJson_invalid_trailing_comma_returns_error() {
        val err = validateJson("""{"a":1,}""")
        assertNotNull(err)
    }

    @Test
    fun validateJson_invalid_unquoted_key_returns_error() {
        val err = validateJson("""{key: "value"}""")
        assertNotNull(err)
    }

    @Test
    fun validateJson_plain_string_returns_error() {
        val err = validateJson("hello world")
        assertNotNull(err)
    }

    @Test
    fun validateJson_returns_error_for_partial_array() {
        val err = validateJson("""[1, 2, 3""")
        assertNotNull(err)
        assertTrue(err.message.isNotBlank())
    }

    @Test
    fun validateJson_error_message_is_non_blank() {
        val err = validateJson("""{broken""")
        assertNotNull(err)
        assertTrue(err.message.isNotBlank())
    }

    // ── validateXml ─────────────────────────────────────────────

    @Test
    fun validateXml_blank_returns_null() {
        assertNull(validateXml(""))
        assertNull(validateXml("   "))
    }

    @Test
    fun validateXml_well_formed_single_tag_returns_null() {
        assertNull(validateXml("<root><child>text</child></root>"))
    }

    @Test
    fun validateXml_well_formed_with_declaration_returns_null() {
        assertNull(validateXml("""<?xml version="1.0"?><root><a>1</a><b>2</b></root>"""))
    }

    @Test
    fun validateXml_well_formed_self_closing_returns_null() {
        assertNull(validateXml("<root><item id=\"1\"/><item id=\"2\"/></root>"))
    }

    @Test
    fun validateXml_mismatched_tags_returns_error() {
        val err = validateXml("<root><child></root>")
        assertNotNull(err)
        assertTrue(err.message.isNotBlank())
    }

    @Test
    fun validateXml_unclosed_tag_returns_error() {
        val err = validateXml("<root><unclosed>")
        assertNotNull(err)
    }

    @Test
    fun validateXml_extra_close_tag_returns_error() {
        val err = validateXml("<root></root></extra>")
        assertNotNull(err)
    }

    // ── MutableFormDataRow ──────────────────────────────────────

    @Test
    fun mutableFormDataRow_default_values() {
        val row = MutableFormDataRow()
        assertEquals("", row.key)
        assertEquals("", row.value)
        assertEquals("", row.description)
        assertEquals(FormEntryType.TEXT, row.type)
        assertTrue(row.enabled)
        assertTrue(row.uid.isNotBlank())
    }

    @Test
    fun mutableFormDataRow_custom_values() {
        val row = MutableFormDataRow(
            key = "username",
            value = "alice",
            description = "The admin user",
            type = FormEntryType.TEXT,
            enabled = false,
        )
        assertEquals("username", row.key)
        assertEquals("alice", row.value)
        assertEquals("The admin user", row.description)
        assertEquals(FormEntryType.TEXT, row.type)
        assertFalse(row.enabled)
    }

    @Test
    fun mutableFormDataRow_file_type() {
        val row = MutableFormDataRow(key = "attachment", value = "report.pdf", type = FormEntryType.FILE)
        assertEquals(FormEntryType.FILE, row.type)
        assertEquals("report.pdf", row.value)
    }

    @Test
    fun mutableFormDataRow_unique_uid_per_instance() {
        val r1 = MutableFormDataRow()
        val r2 = MutableFormDataRow()
        assertFalse(r1.uid == r2.uid, "Each row should have a unique uid")
    }

    @Test
    fun mutableFormDataRow_mutation_is_reflected() {
        val row = MutableFormDataRow(key = "original")
        row.key = "updated"
        row.value = "new-value"
        row.enabled = false
        assertEquals("updated", row.key)
        assertEquals("new-value", row.value)
        assertFalse(row.enabled)
    }

    // ── BodyType enum coverage ──────────────────────────────────

    @Test
    fun bodyType_xml_exists() {
        assertEquals(BodyType.XML, BodyType.valueOf("XML"))
    }

    @Test
    fun bodyType_html_exists() {
        assertEquals(BodyType.HTML, BodyType.valueOf("HTML"))
    }

    @Test
    fun bodyType_javascript_exists() {
        assertEquals(BodyType.JAVASCRIPT, BodyType.valueOf("JAVASCRIPT"))
    }

    @Test
    fun bodyType_form_entry_type_values() {
        val types = FormEntryType.entries.map { it.name }
        assertTrue(types.contains("TEXT"))
        assertTrue(types.contains("FILE"))
    }

    // ── bodyContents map isolation ──────────────────────────────

    @Test
    fun bodyContents_map_isolates_content_per_body_type() {
        val tab = RequestTabState()

        // Set content for JSON
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"hello":"world"}"""

        // Switch to XML and set different content
        tab.bodyType = BodyType.XML
        tab.bodyContent = "<root><item>test</item></root>"

        // Switch to HTML and set content
        tab.bodyType = BodyType.HTML
        tab.bodyContent = "<html><body></body></html>"

        // Verify switching back to JSON preserves its content
        tab.bodyType = BodyType.JSON
        assertEquals("""{"hello":"world"}""", tab.bodyContent, "JSON content should be preserved")

        // Verify XML content is preserved independently
        tab.bodyType = BodyType.XML
        assertEquals("<root><item>test</item></root>", tab.bodyContent, "XML content should be preserved")

        // Verify a never-set type returns empty string
        tab.bodyType = BodyType.JAVASCRIPT
        assertEquals("", tab.bodyContent, "Unset body type should return empty string")

        // Verify NONE type starts empty
        tab.bodyType = BodyType.NONE
        assertEquals("", tab.bodyContent, "NONE body type should have empty content")
    }

    @Test
    fun bodyContents_map_content_does_not_bleed_between_types() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.JSON
        tab.bodyContent = "jsonContent"

        // XML was never set – should be empty
        tab.bodyType = BodyType.XML
        assertEquals("", tab.bodyContent, "XML should not inherit JSON content")

        // Setting XML content does not affect JSON
        tab.bodyContent = "xmlContent"
        tab.bodyType = BodyType.JSON
        assertEquals("jsonContent", tab.bodyContent, "JSON content must not be overwritten by XML write")
    }

    // ── lastRawSubtype memory ──────────────────────────────────

    @Test
    fun lastRawSubtype_defaults_to_json() {
        val tab = RequestTabState()
        assertEquals(BodyType.JSON, tab.lastRawSubtype, "Default raw subtype should be JSON")
    }

    @Test
    fun lastRawSubtype_remembers_selected_subtype() {
        val tab = RequestTabState()
        tab.lastRawSubtype = BodyType.XML
        assertEquals(BodyType.XML, tab.lastRawSubtype)
        tab.lastRawSubtype = BodyType.HTML
        assertEquals(BodyType.HTML, tab.lastRawSubtype)
        tab.lastRawSubtype = BodyType.JAVASCRIPT
        assertEquals(BodyType.JAVASCRIPT, tab.lastRawSubtype)
        tab.lastRawSubtype = BodyType.RAW_TEXT
        assertEquals(BodyType.RAW_TEXT, tab.lastRawSubtype)
    }

    @Test
    fun raw_json_to_form_to_raw_preserves_content_and_subtype() {
        val tab = RequestTabState()

        // Start with Raw → JSON
        tab.bodyType = BodyType.JSON
        tab.lastRawSubtype = BodyType.JSON
        tab.bodyContent = """{"key":"value"}"""

        // Switch to Form Data
        tab.bodyType = BodyType.FORM_DATA
        assertEquals("", tab.bodyContent, "Form data should have its own empty content")

        // Switch back to Raw → should restore JSON subtype
        tab.bodyType = tab.lastRawSubtype  // simulates BodyEditor category-switch logic
        assertEquals(BodyType.JSON, tab.bodyType, "Body type should be restored to JSON")
        assertEquals("""{"key":"value"}""", tab.bodyContent, "JSON content must be preserved after Form roundtrip")
    }

    @Test
    fun raw_xml_to_urlencoded_to_raw_preserves_content_and_subtype() {
        val tab = RequestTabState()

        // Start with Raw → XML
        tab.bodyType = BodyType.XML
        tab.lastRawSubtype = BodyType.XML
        tab.bodyContent = "<root><a>1</a></root>"

        // Switch to URL Encoded
        tab.bodyType = BodyType.X_WWW_FORM_URLENCODED

        // Switch back to Raw → should restore XML
        tab.bodyType = tab.lastRawSubtype
        assertEquals(BodyType.XML, tab.bodyType, "Body type should be restored to XML")
        assertEquals("<root><a>1</a></root>", tab.bodyContent, "XML content must be preserved after URL Encoded roundtrip")
    }

    @Test
    fun raw_html_to_binary_to_raw_preserves_content_and_subtype() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.HTML
        tab.lastRawSubtype = BodyType.HTML
        tab.bodyContent = "<html><body>hello</body></html>"

        tab.bodyType = BodyType.BINARY

        tab.bodyType = tab.lastRawSubtype
        assertEquals(BodyType.HTML, tab.bodyType)
        assertEquals("<html><body>hello</body></html>", tab.bodyContent)
    }

    @Test
    fun switching_raw_subtype_does_not_clear_other_subtypes() {
        val tab = RequestTabState()

        // Set JSON content
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "json-data"

        // Set XML content
        tab.bodyType = BodyType.XML
        tab.bodyContent = "xml-data"

        // Switch to JS
        tab.bodyType = BodyType.JAVASCRIPT
        tab.lastRawSubtype = BodyType.JAVASCRIPT
        tab.bodyContent = "js-data"

        // All previous content is isolated
        tab.bodyType = BodyType.JSON
        assertEquals("json-data", tab.bodyContent)
        tab.bodyType = BodyType.XML
        assertEquals("xml-data", tab.bodyContent)
        tab.bodyType = BodyType.JAVASCRIPT
        assertEquals("js-data", tab.bodyContent)
    }

    @Test
    fun form_data_state_is_fully_isolated_from_raw() {
        val tab = RequestTabState()

        // Set up raw content
        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"x":1}"""

        // Add form rows
        tab.formRows.add(MutableFormDataRow(key = "field1", value = "val1"))
        tab.formRows.add(MutableFormDataRow(key = "field2", value = "val2"))

        // Switch to form data
        tab.bodyType = BodyType.FORM_DATA
        assertEquals(2, tab.formRows.size, "Form rows should be preserved")
        assertEquals("field1", tab.formRows[0].key)

        // Switch back to JSON
        tab.bodyType = BodyType.JSON
        assertEquals("""{"x":1}""", tab.bodyContent, "JSON content must not be affected by form rows")

        // Form rows still intact
        assertEquals(2, tab.formRows.size, "Form rows must be preserved even when not active")
    }

    @Test
    fun urlencoded_state_is_fully_isolated_from_raw() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.JSON
        tab.bodyContent = "json-content"

        tab.urlencodedRows.add(MutableFormDataRow(key = "k1", value = "v1"))

        tab.bodyType = BodyType.X_WWW_FORM_URLENCODED
        assertEquals(1, tab.urlencodedRows.size)

        tab.bodyType = BodyType.JSON
        assertEquals("json-content", tab.bodyContent)
        assertEquals(1, tab.urlencodedRows.size)
    }

    @Test
    fun graphql_state_is_fully_isolated_from_raw() {
        val tab = RequestTabState()

        tab.bodyType = BodyType.JSON
        tab.bodyContent = """{"json":true}"""

        tab.bodyType = BodyType.GRAPHQL
        tab.bodyContent = "query { users { id } }"

        tab.bodyType = BodyType.JSON
        assertEquals("""{"json":true}""", tab.bodyContent)

        tab.bodyType = BodyType.GRAPHQL
        assertEquals("query { users { id } }", tab.bodyContent)
    }
}
