package com.reqlab.editor.web

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineErrorIntegrationTest {
    private val editor = CodeMirrorEditor()

    @Test
    fun invalid_json_has_inline_error_diagnostic_with_position() {
        val diagnostics = editor.buildInlineDiagnostics("{ \"a\": 1, }", LanguageMode.JSON)

        assertEquals(1, diagnostics.size)
        val first = diagnostics.first()
        assertEquals("error", first.severity)
        assertTrue(first.from >= 0)
        assertTrue(first.to >= first.from)
        assertTrue((first.message as String).contains("line"))
        assertTrue((first.message as String).contains("col"))
    }
}
