package com.reqlab.editor.web

import com.reqlab.editor.core.LanguageMode
import kotlin.test.Test
import kotlin.test.assertNotNull

@JsModule("@codemirror/language")
@JsNonModule
private external object CmLanguage {
    /**
     * Returns the fold range for the given line, or null if none exists.
     * Both lineStart (line.from) and lineEnd (line.to) are required by
     * CodeMirror's syntaxFolding implementation — omitting lineEnd causes
     * tree.resolveInner(undefined) to return no usable node.
     */
    fun foldable(state: dynamic, lineStart: Int, lineEnd: Int): dynamic
    /** Forces synchronous parse completion up to [upto] characters within [timeout] ms. */
    fun ensureSyntaxTree(state: dynamic, upto: Int, timeout: Double = definedExternally): dynamic
}

class CodeMirrorFoldingIntegrationTest {
    private val editor = CodeMirrorEditor()

    /**
     * Uses createParsedState (language-only, no linter) to avoid the
     * 'multiple @codemirror/state instances' error that occurs when
     * @codemirror/lint is mixed with language packages in Node resolution.
     * Calls ensureSyntaxTree first so the lezer parser completes before
     * foldable() is queried.  Passes both lineStart and lineEnd so
     * syntaxFolding can properly resolve nodes at the END of the line.
     */
    @Test
    fun json_folding_works_for_top_level_and_nested_blocks() {
        val json =
            """
            {
              "user": {
                "name": "sanjay",
                "roles": ["admin", "dev"]
              },
              "meta": {
                "active": true
              }
            }
            """.trimIndent()

        val state = editor.createParsedState(json, LanguageMode.JSON)
        // Force the lezer parser to finish before querying foldable ranges
        CmLanguage.ensureSyntaxTree(state, state.doc.length, 5000.0)

        val line1    = state.doc.line(1)
        val line2    = state.doc.line(2)
        val topLevel = CmLanguage.foldable(state, line1.from as Int, line1.to as Int)
        val nested   = CmLanguage.foldable(state, line2.from as Int, line2.to as Int)

        assertNotNull(topLevel, "Top-level JSON object must be foldable")
        assertNotNull(nested,   "Nested 'user' object must be foldable")
    }

    @Test
    fun xml_folding_works_for_tags_and_nested_blocks() {
        val xml =
            """
            <root>
              <user>
                <name>Sanjay</name>
                <meta><active>true</active></meta>
              </user>
            </root>
            """.trimIndent()

        val state     = editor.createParsedState(xml, LanguageMode.XML)
        CmLanguage.ensureSyntaxTree(state, state.doc.length, 5000.0)

        val line1      = state.doc.line(1)
        val line2      = state.doc.line(2)
        val rootFold   = CmLanguage.foldable(state, line1.from as Int, line1.to as Int)
        val nestedFold = CmLanguage.foldable(state, line2.from as Int, line2.to as Int)

        assertNotNull(rootFold,   "<root> tag must be foldable")
        assertNotNull(nestedFold, "<user> tag must be foldable")
    }
}
