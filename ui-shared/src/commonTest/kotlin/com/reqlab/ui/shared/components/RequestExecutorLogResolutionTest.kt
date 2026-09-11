package com.reqlab.ui.shared.components

import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.ui.shared.state.MutableFormDataRow
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestExecutorLogResolutionTest {

    @Test
    fun resolveUrlForLog_resolves_from_variable_layers() {
        val url = "{{baseUrl}}/api/token"
        val layers = listOf(
            mapOf("baseUrl" to "http://localhost:8080"),
        )

        val resolved = resolveUrlForLog(url, layers)

        assertEquals("http://localhost:8080/api/token", resolved)
    }

    @Test
    fun resolveUrlForLog_prefers_request_scoped_variables() {
        val url = "{{baseUrl}}/api/token"
        val layers = listOf(
            mapOf("baseUrl" to "http://env.example.com"),
        )
        val requestScoped = mapOf("baseUrl" to "http://script.example.com")

        val resolved = resolveUrlForLog(
            url = url,
            variableLayers = layers,
            requestScopedVars = requestScoped,
        )

        assertEquals("http://script.example.com/api/token", resolved)
    }

    @Test
    fun resolveUrlForLog_keeps_unresolved_placeholder_when_missing() {
        val url = "{{baseUrl}}/api/token"

        val resolved = resolveUrlForLog(url, emptyList())

        assertEquals("{{baseUrl}}/api/token", resolved)
    }

    @Test
    fun enabledHeadersForSend_keeps_repeated_header_values_in_order() {
        val tab = RequestTabState()
        tab.headers.add(MutableKeyValue("X-Tag", "kotlin"))
        tab.headers.add(MutableKeyValue("X-Tag", "ktor"))

        val sent = enabledHeadersForSend(tab).filter { it.key == "X-Tag" }.map { it.value }

        assertEquals(
            listOf("kotlin", "ktor"),
            sent,
            "Repeated header rows must both reach Send, not last-wins associate()",
        )
    }

    @Test
    fun buildRequestBody_passes_file_bytes_for_form_data_file_row() {
        val tab = RequestTabState()
        tab.bodyType = BodyType.FORM_DATA
        tab.formRows.add(
            MutableFormDataRow(
                key = "upload",
                type = FormEntryType.FILE,
                value = "note.txt",
                bytesBase64 = "aGVsbG8tZmlsZQ==",
            ),
        )

        val body = buildRequestBody(tab, "")
        val file = body.formDataEntries.single { it.key == "upload" }

        assertEquals(FormEntryType.FILE, file.type)
        assertEquals("note.txt", file.value)
        assertEquals("aGVsbG8tZmlsZQ==", file.bytesBase64)
    }
}
