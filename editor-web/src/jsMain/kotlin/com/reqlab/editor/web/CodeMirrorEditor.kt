@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

package com.reqlab.editor.web

import com.reqlab.editor.core.EditorEngine
import com.reqlab.editor.core.InlineEditorError
import com.reqlab.editor.core.LanguageMode
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

// ─── @codemirror/view ────────────────────────────────────────────────────────

@JsModule("@codemirror/view")
@JsNonModule
private external class EditorView(config: dynamic) {
    val state: dynamic
    fun dispatch(transaction: dynamic)

    companion object {
        fun theme(spec: dynamic, options: dynamic = definedExternally): dynamic
        val lineWrapping: dynamic
    }
}

@JsModule("@codemirror/view")
@JsNonModule
private external object CmView {
    fun lineNumbers(): dynamic
    fun highlightActiveLine(): dynamic
    fun highlightActiveLineGutter(): dynamic
    fun drawSelection(): dynamic
    fun rectangularSelection(): dynamic
    fun crosshairCursor(): dynamic
}

// ─── @codemirror/state ───────────────────────────────────────────────────────

@JsModule("@codemirror/state")
@JsNonModule
private external object CmState {
    val EditorState: dynamic
    val Compartment: dynamic
}

// ─── @codemirror/language ────────────────────────────────────────────────────

@JsModule("@codemirror/language")
@JsNonModule
private external object CmLanguage {
    fun indentOnInput(): dynamic
    fun bracketMatching(): dynamic
    fun foldGutter(config: dynamic = definedExternally): dynamic
    fun codeFolding(): dynamic
    fun foldable(state: dynamic, lineStart: Int, lineEnd: Int): dynamic
    fun ensureSyntaxTree(state: dynamic, upto: Int, timeout: Double = definedExternally): dynamic
}

// ─── @codemirror/commands ────────────────────────────────────────────────────

@JsModule("@codemirror/commands")
@JsNonModule
private external object CmCommands {
    val defaultKeymap: dynamic
    val historyKeymap: dynamic
    val foldKeymap: dynamic
    fun history(): dynamic
    fun indentWithTab(anything: dynamic = definedExternally): dynamic
}

// ─── Language packs ──────────────────────────────────────────────────────────

@JsModule("@codemirror/lang-json")
@JsNonModule
private external object CmJsonLanguage {
    fun json(): dynamic
}

@JsModule("@codemirror/lang-xml")
@JsNonModule
private external object CmXmlLanguage {
    fun xml(): dynamic
}

@JsModule("@codemirror/lang-html")
@JsNonModule
private external object CmHtmlLanguage {
    fun html(options: dynamic = definedExternally): dynamic
}

@JsModule("@codemirror/lang-javascript")
@JsNonModule
private external object CmJsLanguage {
    fun javascript(options: dynamic = definedExternally): dynamic
}

// ─── Linting ─────────────────────────────────────────────────────────────────

@JsModule("@codemirror/lint")
@JsNonModule
private external object CmLint {
    fun linter(source: (dynamic) -> Array<dynamic>, config: dynamic = definedExternally): dynamic
    fun lintGutter(): dynamic
}

// ─── Indentation markers (visual guides) ─────────────────────────────────────

@JsModule("@replit/codemirror-indentation-markers")
@JsNonModule
private external object CmIndentMarkers {
    fun indentationMarkers(config: dynamic = definedExternally): dynamic
}

// ─── Public API ──────────────────────────────────────────────────────────────

/**
 * Kotlin/JS wrapper around CodeMirror 6.
 *
 * Language support matrix:
 * | Mode        | Syntax highlighting | Folding | Inline errors | Indent guides |
 * |-------------|---------------------|---------|---------------|---------------|
 * | JSON        | ✅                  | ✅      | ✅            | ✅            |
 * | XML         | ✅                  | ✅      | ✅            | ✅            |
 * | HTML        | ✅                  | ✅      | ✅ (warnings) | ✅            |
 * | JavaScript  | ✅                  | ✅      | ✅            | ✅            |
 * | PLAIN_TEXT  | —                   | —       | —             | —             |
 */
class CodeMirrorEditor(
    private val engine: EditorEngine = EditorEngine(),
) {

    // ── Browser (production) state ─────────────────────────────────────────

    /**
     * Creates a full CodeMirror EditorState with:
     *  – language extension (syntax highlighting + folding)
     *  – indentation markers
     *  – inline error linter
     *  – line numbers, fold gutter, bracket matching
     * For use when mounting into a real DOM host element.
     */
    fun createEditorState(text: String, languageMode: LanguageMode): dynamic {
        val extensions = buildExtensionsArray(text, languageMode, includeLinter = true)
        return CmState.EditorState.create(js("({ doc: text, extensions: extensions })"))
    }

    /**
     * Creates a minimal EditorState with ONLY the language extension.
     * Safe for headless (Node.js) tests — avoids mixing @codemirror/lint
     * which may have a separate @codemirror/state instance in some yarn
     * hoisting scenarios.
     */
    fun createParsedState(text: String, languageMode: LanguageMode): dynamic {
        val langExt = languageExtension(languageMode)
        val extensions: Array<dynamic> = if (langExt != null) arrayOf(langExt) else emptyArray()
        return CmState.EditorState.create(js("({ doc: text, extensions: extensions })"))
    }

    // ── DOM mount ──────────────────────────────────────────────────────────

    /**
     * Creates and mounts an [EditorView] into [host].
     * Returns the EditorView so callers can dispatch transactions.
     */
    fun create(host: HTMLElement, text: String, languageMode: LanguageMode, readOnly: Boolean = false): dynamic {
        val state = createEditorState(text, languageMode)
        return EditorView(js("({ state: state, parent: host })"))
    }

    // ── Diagnostics ────────────────────────────────────────────────────────

    /**
     * Maps [EditorEngine.validate] errors into CodeMirror diagnostic objects
     * `{ from, to, severity, message }` suitable for the linter extension.
     */
    fun buildInlineDiagnostics(text: String, languageMode: LanguageMode): Array<dynamic> =
        engine.validate(text, languageMode).map { toDiagnostic(text, it) }.toTypedArray()

    // ── Language extension ─────────────────────────────────────────────────

    fun languageExtension(languageMode: LanguageMode): dynamic =
        when (languageMode) {
            LanguageMode.JSON        -> CmJsonLanguage.json()
            LanguageMode.XML         -> CmXmlLanguage.xml()
            LanguageMode.HTML        -> CmHtmlLanguage.html()
            LanguageMode.JAVASCRIPT  -> CmJsLanguage.javascript()
            LanguageMode.PLAIN_TEXT  -> null
        }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun buildExtensionsArray(
        text: String,
        languageMode: LanguageMode,
        includeLinter: Boolean,
    ): Array<dynamic> {
        val list = mutableListOf<dynamic>()

        // Syntax highlighting + folding for the chosen language
        val langExt: dynamic = languageExtension(languageMode)
        if (langExt != null) list.add(langExt)

        // Structural editing
        list.add(CmLanguage.indentOnInput())
        list.add(CmLanguage.bracketMatching())
        list.add(CmLanguage.foldGutter(asciiFoldGutterConfig()))
        list.add(CmLanguage.codeFolding())

        // Indentation guides (vertical dotted lines for nested blocks)
        try { list.add(CmIndentMarkers.indentationMarkers()) } catch (_: Throwable) { /* optional */ }

        // Inline error gutter + linter
        if (includeLinter) {
            list.add(CmLint.lintGutter())
            list.add(inlineErrorLinter(text, languageMode))
        }

        // Editor chrome
        list.add(CmView.lineNumbers())
        list.add(CmView.highlightActiveLine())
        list.add(CmView.highlightActiveLineGutter())
        list.add(CmView.drawSelection())
        list.add(CmCommands.history())

        // Pixel-perfect theme: scrollable, sticky line numbers, wavy underline for errors
        list.add(lineNumberAlignmentTheme())

        return list.toTypedArray()
    }

    private fun asciiFoldGutterConfig(): dynamic {
        val config = js("({})")
        config.markerDOM = { open: Boolean ->
            val span = document.createElement("span")
            span.textContent = if (open) "v" else ">"
            span.setAttribute(
                "style",
                "font-family:'JetBrains Mono','Fira Code','Cascadia Code',monospace;font-size:10px;display:inline-block;width:10px;text-align:center;"
            )
            span
        }
        return config
    }

    private fun inlineErrorLinter(text: String, languageMode: LanguageMode): dynamic =
        CmLint.linter({ _ -> buildInlineDiagnostics(text, languageMode) })

    private fun toDiagnostic(text: String, error: InlineEditorError): dynamic {
        val from = lineColToOffset(text, error.line, error.col)
        val to   = (from + 1).coerceAtMost(text.length)
        val sev  = error.severity.name.lowercase()  // "error" or "warning"
        val msg  = "${error.message} (line ${error.line}, col ${error.col})"
        return js("({ from: from, to: to, severity: sev, message: msg })")
    }

    private fun lineColToOffset(text: String, line: Int, col: Int): Int {
        var currentLine = 1
        var currentCol  = 1
        var index       = 0
        while (index < text.length) {
            if (currentLine == line && currentCol == col) return index
            if (text[index] == '\n') { currentLine++; currentCol = 1 }
            else currentCol++
            index++
        }
        return text.length
    }

    private fun lineNumberAlignmentTheme(): dynamic =
        EditorView.theme(
            js("""({
              '.cm-scroller':   { overflow: 'auto', fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace", fontSize: '14px', lineHeight: '1.5' },
              '.cm-gutters':    { position: 'sticky', left: 0, zIndex: 1, backgroundColor: '#1e1e1e', borderRight: '1px solid #3c3c3c', color: '#858585' },
              '.cm-lineNumbers .cm-gutterElement': { padding: '0 8px 0 4px', minWidth: '2.5em', textAlign: 'right' },
              '.cm-content':    { paddingLeft: '4px' },
              '.cm-diagnostic': { textDecoration: 'underline wavy', textDecorationThickness: '2px' },
              '.cm-diagnostic-error':   { textDecorationColor: '#f44747' },
              '.cm-diagnostic-warning': { textDecorationColor: '#ffcc00' },
              '.cm-foldGutter': { width: '16px' },
              '.cm-indent-markers': { opacity: '0.35' }
            })"""),
            js("({ dark: true })"),
        )
}

