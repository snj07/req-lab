package com.reqlab.editor.web

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertNotNull

@JsModule("@codemirror/language")
@JsNonModule
private external object CmLanguageHtml {
    fun foldable(state: dynamic, lineStart: Int, lineEnd: Int): dynamic
    fun ensureSyntaxTree(state: dynamic, upto: Int, timeout: Double = definedExternally): dynamic
}

/** Verifies that CodeMirror folds HTML tags correctly. */
class HtmlFoldingIntegrationTest {
    private val editor = CodeMirrorEditor()

    @Test
    fun html_root_element_is_foldable() {
        val html = """
            <html>
              <body>
                <div>
                  <p>Hello World</p>
                </div>
              </body>
            </html>
        """.trimIndent()

        val state = editor.createParsedState(html, LanguageMode.HTML)
        CmLanguageHtml.ensureSyntaxTree(state, state.doc.length, 5000.0)

        val line1 = state.doc.line(1)
        val root  = CmLanguageHtml.foldable(state, line1.from as Int, line1.to as Int)
        assertNotNull(root, "<html> root element must be foldable")
    }

    @Test
    fun html_nested_div_is_foldable() {
        val html = """
            <div class="outer">
              <div class="inner">
                <p>content</p>
              </div>
            </div>
        """.trimIndent()

        val state = editor.createParsedState(html, LanguageMode.HTML)
        CmLanguageHtml.ensureSyntaxTree(state, state.doc.length, 5000.0)

        val line1  = state.doc.line(1)
        val line2  = state.doc.line(2)
        val outer  = CmLanguageHtml.foldable(state, line1.from as Int, line1.to as Int)
        val inner  = CmLanguageHtml.foldable(state, line2.from as Int, line2.to as Int)

        assertNotNull(outer, "<div class='outer'> must be foldable")
        assertNotNull(inner, "<div class='inner'> must be foldable")
    }

    @Test
    fun html_language_extension_is_not_null() {
        val ext = editor.languageExtension(LanguageMode.HTML)
        assertNotNull(ext, "HTML language extension must be non-null")
    }
}
