package com.reqlab.editor.platform.web

import com.reqlab.editor.core.*

/**
 * Web platform editor bridge.
 *
 * Wraps CodeMirror internally but exposes the generic EditorEngine API.
 * All CodeMirror details are hidden behind this facade.
 */
class WebEditorBridge {
    private val engine = EditorEngine()
    private var currentState: EditorState = engine.createState("", LanguageMode.PLAIN_TEXT)

    val state: EditorState get() = currentState

    fun loadContent(text: String, mode: LanguageMode) {
        currentState = engine.createState(text, mode)
    }

    fun updateContent(text: String) {
        currentState = engine.updateText(currentState, text)
    }

    fun switchLanguage(mode: LanguageMode) {
        currentState = engine.switchMode(currentState, mode)
    }

    fun toggleFold(lineNumber: Int) {
        currentState = engine.toggleFold(currentState, lineNumber)
    }

    fun foldAll() {
        currentState = engine.foldAll(currentState)
    }

    fun unfoldAll() {
        currentState = engine.unfoldAll(currentState)
    }

    fun validate(): List<InlineEditorError> = currentState.diagnostics

    fun tokenizeRange(startLine: Int, endLine: Int): Map<Int, List<Token>> =
        engine.tokenizeRange(currentState, startLine, endLine)

    fun getVisibleLines(): List<Pair<Int, String>> =
        engine.visibleLines(currentState)

    /**
     * Convert LanguageMode to CodeMirror language extension name.
     */
    fun codeMirrorLanguageId(mode: LanguageMode): String = when (mode) {
        LanguageMode.JSON -> "json"
        LanguageMode.XML -> "xml"
        LanguageMode.HTML -> "html"
        LanguageMode.JAVASCRIPT -> "javascript"
        LanguageMode.PLAIN_TEXT -> ""
    }
}
