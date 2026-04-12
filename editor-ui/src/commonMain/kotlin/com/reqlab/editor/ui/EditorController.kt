package com.reqlab.editor.ui

import com.reqlab.editor.core.*

/**
 * ViewModel-like state holder for the editor.
 * Manages EditorEngine + EditorState lifecycle.
 *
 * Usage:
 * ```kotlin
 * val controller = EditorController()
 * controller.loadText(json, LanguageMode.JSON)
 * // In Compose:
 * CodeEditorView(
 *     state = controller.state,
 *     onTextChange = { controller.onTextChange(it) },
 * )
 * ```
 */
class EditorController(
    initialText: String = "",
    initialMode: LanguageMode = LanguageMode.PLAIN_TEXT,
) {
    private val engine = EditorEngine()

    var state: EditorState = engine.createState(initialText, initialMode)
        private set

    fun loadText(text: String, mode: LanguageMode = state.languageMode) {
        state = engine.createState(text, mode)
    }

    fun onTextChange(newText: String) {
        state = engine.updateText(state, newText)
    }

    fun switchLanguage(mode: LanguageMode) {
        state = engine.switchMode(state, mode)
    }

    fun toggleFold(lineNumber: Int) {
        state = engine.toggleFold(state, lineNumber)
    }

    fun foldAll() {
        state = engine.foldAll(state)
    }

    fun unfoldAll() {
        state = engine.unfoldAll(state)
    }

    fun selectAll() {
        state = engine.selectAll(state)
    }

    fun validate(): List<InlineEditorError> = state.diagnostics

    fun tokenizeLine(lineNumber: Int): List<Token> =
        engine.tokenizeLine(state, lineNumber)

    fun tokenizeRange(startLine: Int, endLine: Int): Map<Int, List<Token>> =
        engine.tokenizeRange(state, startLine, endLine)

    fun visibleLines(): List<Pair<Int, String>> =
        engine.visibleLines(state)
}
