package com.reqlab.editor.desktop

import com.reqlab.editor.core.EditorEngine
import com.reqlab.editor.core.EditorState
import com.reqlab.editor.core.LanguageMode
import java.util.Timer
import java.util.TimerTask

/**
 * High-level bridge between the application ViewModel layer and the
 * CodeMirror WebView editor.
 *
 * For headless/test environments use [NoOpCodeEditorHost].
 * For desktop UI use [CefCodeEditorHost] (requires JBR/JCEF; see that class'
 * KDoc for setup instructions).
 *
 * ```kotlin
 * val host   = runCatching { CefCodeEditorHost() }.getOrElse { NoOpCodeEditorHost() }
 * val bridge = DesktopEditorBridge(host)
 * bridge.open(requestBody, LanguageMode.JSON)
 * ```
 */
class DesktopEditorBridge(
    private val host: CodeEditorHost = NoOpCodeEditorHost(),
    private val engine: EditorEngine = EditorEngine(),
    /** Debounce delay for text-change validation (milliseconds). */
    private val validationDebounceMs: Long = 500L,
) {
    private var _state: EditorState = EditorState("", LanguageMode.PLAIN_TEXT)
    private var debounceTimer: Timer? = null

    /** The last validated [EditorState] — updated on every text or mode change. */
    val state: EditorState get() = _state

    init {
        host.onTextChanged = { newText ->
            // Update text immediately (no lag), but debounce the expensive
            // validation step so that rapid keystrokes don't trigger a full
            // JSON parse / XML scan on every character.
            _state = _state.copy(text = newText)
            debounceValidation(newText)
        }
    }

    /**
     * Debounce validation: cancel any pending timer, then schedule a new
     * validation [validationDebounceMs] later.  For small documents the
     * delay is negligible; for 5–10 MB payloads it prevents repeated
     * multi-second freezes.
     */
    private fun debounceValidation(text: String) {
        debounceTimer?.cancel()
        debounceTimer = Timer("EditorBridgeValidation", true).apply {
            schedule(object : TimerTask() {
                override fun run() {
                    _state = engine.updateText(_state, text)
                }
            }, validationDebounceMs)
        }
    }

    /**
     * Load [text] in [languageMode] into the WebView editor.
     * Returns the initial validated [EditorState].
     */
    fun open(text: String, languageMode: LanguageMode): EditorState {
        _state = engine.createState(text = text, languageMode = languageMode)
        host.setText(text, languageMode)
        return _state
    }

    /**
     * Called when the user edits the text outside the WebView
     * (e.g. programmatic update from a test or template insertion).
     */
    fun onTextChanged(previous: EditorState, newText: String): EditorState {
        _state = engine.updateText(previous, newText)
        host.setText(newText, previous.languageMode)
        return _state
    }

    /**
     * Switch the editor to a different language mode without replacing text.
     * Re-validates and returns the new [EditorState].
     */
    fun onModeChanged(previous: EditorState, languageMode: LanguageMode): EditorState {
        _state = engine.switchMode(previous, languageMode)
        host.setText(previous.text, languageMode)
        return _state
    }

    /** Toggle the editor between editable and read-only modes. */
    fun setReadOnly(readOnly: Boolean) = host.setReadOnly(readOnly)

    /** Asynchronously fetch the current document text from the WebView. */
    fun getText(onResult: (String) -> Unit) = host.getText(onResult)
}

