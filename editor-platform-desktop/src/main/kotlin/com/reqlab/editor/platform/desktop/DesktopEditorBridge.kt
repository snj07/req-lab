package com.reqlab.editor.platform.desktop

import com.reqlab.editor.core.*

/**
 * Desktop platform bridge for the editor engine.
 *
 * Wraps EditorEngine with desktop-specific functionality:
 * - Debounced validation for typing performance
 * - Message protocol for WebView-based rendering
 * - State serialization for host↔editor communication
 */
class DesktopEditorBridge {
    private val engine = EditorEngine()
    private var currentState: EditorState = engine.createState("", LanguageMode.PLAIN_TEXT)
    private var validationCallback: ((List<InlineEditorError>) -> Unit)? = null

    val state: EditorState get() = currentState

    fun loadContent(text: String, mode: LanguageMode) {
        currentState = engine.createState(text, mode)
        notifyValidation()
    }

    fun updateContent(text: String) {
        currentState = engine.updateText(currentState, text)
        notifyValidation()
    }

    fun switchLanguage(mode: LanguageMode) {
        currentState = engine.switchMode(currentState, mode)
        notifyValidation()
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

    fun selectAll() {
        currentState = engine.selectAll(currentState)
    }

    fun onValidationResult(callback: (List<InlineEditorError>) -> Unit) {
        validationCallback = callback
    }

    fun getVisibleLines(): List<Pair<Int, String>> = engine.visibleLines(currentState)

    fun tokenizeRange(startLine: Int, endLine: Int): Map<Int, List<Token>> =
        engine.tokenizeRange(currentState, startLine, endLine)

    private fun notifyValidation() {
        validationCallback?.invoke(currentState.diagnostics)
    }
}

/**
 * Message types for host↔editor WebView communication.
 */
sealed class WebEditorMessage {
    data class SetContent(val text: String, val language: String) : WebEditorMessage()
    data class ContentChanged(val text: String) : WebEditorMessage()
    data class SetLanguage(val language: String) : WebEditorMessage()
    data class SetDiagnostics(val errors: List<InlineEditorError>) : WebEditorMessage()
    data object FoldAll : WebEditorMessage()
    data object UnfoldAll : WebEditorMessage()
    data object SelectAll : WebEditorMessage()
}
