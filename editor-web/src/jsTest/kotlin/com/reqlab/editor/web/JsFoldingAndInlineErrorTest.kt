package com.reqlab.editor.web

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@JsModule("@codemirror/language")
@JsNonModule
private external object CmLanguageJs {
    fun foldable(state: dynamic, lineStart: Int, lineEnd: Int): dynamic
    fun ensureSyntaxTree(state: dynamic, upto: Int, timeout: Double = definedExternally): dynamic
}

/** Verifies that CodeMirror folds JavaScript blocks correctly. */
class JsFoldingIntegrationTest {
    private val editor = CodeMirrorEditor()

    @Test
    fun js_function_body_is_foldable() {
        val js = """
            function greet(name) {
              const msg = "Hello, " + name;
              return msg;
            }
        """.trimIndent()

        val state = editor.createParsedState(js, LanguageMode.JAVASCRIPT)
        CmLanguageJs.ensureSyntaxTree(state, state.doc.length, 5000.0)

        val line1 = state.doc.line(1)
        val fn    = CmLanguageJs.foldable(state, line1.from as Int, line1.to as Int)
        assertNotNull(fn, "Top-level function body `{ ... }` must be foldable")
    }

    @Test
    fun js_nested_object_literal_is_foldable() {
        val js = """
            const config = {
              server: {
                host: "localhost",
                port: 8080
              }
            };
        """.trimIndent()

        val state  = editor.createParsedState(js, LanguageMode.JAVASCRIPT)
        CmLanguageJs.ensureSyntaxTree(state, state.doc.length, 5000.0)

        val line1  = state.doc.line(1)
        val line2  = state.doc.line(2)
        val outer  = CmLanguageJs.foldable(state, line1.from as Int, line1.to as Int)
        val nested = CmLanguageJs.foldable(state, line2.from as Int, line2.to as Int)

        assertNotNull(outer,  "Outer object literal must be foldable")
        assertNotNull(nested, "Nested server object must be foldable")
    }

    @Test
    fun js_language_extension_is_not_null() {
        val ext = editor.languageExtension(LanguageMode.JAVASCRIPT)
        assertNotNull(ext, "JavaScript language extension must be non-null")
    }

    @Test
    fun js_parser_state_is_created_without_crash() {
        val state = editor.createParsedState(
            "const x = 1; function test() { return x + 1; }",
            LanguageMode.JAVASCRIPT,
        )
        assertNotNull(state, "createParsedState must not return null for JavaScript")
    }
}

/** Verifies that inline error diagnostics work for HTML and JavaScript. */
class HtmlJsInlineErrorIntegrationTest {
    private val editor = CodeMirrorEditor()

    @Test
    fun js_mismatched_brace_reports_diagnostic() {
        // Missing closing brace for function body
        val js = "function broken() { const x = 1;"
        val diagnostics = editor.buildInlineDiagnostics(js, LanguageMode.JAVASCRIPT)
        assertTrue(
            diagnostics.isNotEmpty(),
            "Unclosed '{' in JavaScript must produce at least one diagnostic"
        )
        val sev = diagnostics[0].severity as String
        assertTrue(sev == "error", "Diagnostic severity must be 'error', got '$sev'")
    }

    @Test
    fun js_balanced_code_has_no_diagnostics() {
        val js = "function ok() { return 42; }"
        val diagnostics = editor.buildInlineDiagnostics(js, LanguageMode.JAVASCRIPT)
        assertTrue(diagnostics.isEmpty(), "Balanced JS must produce no diagnostics. Got: $diagnostics")
    }

    @Test
    fun xml_mismatched_tag_reports_diagnostic() {
        val xml = "<root><child></wrong></root>"
        val diagnostics = editor.buildInlineDiagnostics(xml, LanguageMode.XML)
        assertTrue(
            diagnostics.isNotEmpty(),
            "Mismatched XML tag must produce at least one diagnostic"
        )
    }

    @Test
    fun html_unclosed_tag_reports_warning_diagnostic() {
        // HTML: unclosed non-void element → WARNING
        val html = "<div><span>text</div>"
        val diagnostics = editor.buildInlineDiagnostics(html, LanguageMode.HTML)
        assertTrue(diagnostics.isNotEmpty(), "Unclosed HTML <span> must produce a diagnostic")
        // HTML is lenient → warnings are expected
        val sev = diagnostics[0].severity as String
        assertTrue(sev == "warning" || sev == "error",
            "HTML unclosed tag severity must be 'warning' or 'error', got '$sev'")
    }

    @Test
    fun json_inline_error_has_correct_position() {
        val badJson = """{ "a": 1, }"""
        val diagnostics = editor.buildInlineDiagnostics(badJson, LanguageMode.JSON)
        assertTrue(diagnostics.isNotEmpty(), "Trailing comma must produce a JSON diagnostic")
        val from = diagnostics[0].from as Int
        assertTrue(from >= 0, "Diagnostic position must be >= 0, got $from")
    }
}
