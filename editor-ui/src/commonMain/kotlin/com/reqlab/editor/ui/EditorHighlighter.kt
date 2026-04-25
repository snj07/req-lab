package com.reqlab.editor.ui

import androidx.compose.ui.text.AnnotatedString
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.LanguageRegistry

/**
 * Top-level highlighting entry points.
 *
 * These functions are stable public API.  The actual highlight logic lives in
 * [SyntaxHighlighter] implementations registered in [SyntaxHighlighterRegistry],
 * so new languages and custom renderers can be plugged in without changing this file.
 */

private fun ensureHighlighters(mode: LanguageMode) {
    if (!SyntaxHighlighterRegistry.hasHighlighter(mode)) SyntaxHighlighterRegistry.registerBuiltinHighlighters()
}

/**
 * Highlight [text] for [mode].
 * Delegates to the [SyntaxHighlighter] registered for [mode] in [SyntaxHighlighterRegistry].
 */
fun highlightText(text: String, mode: LanguageMode): AnnotatedString {
    ensureHighlighters(mode)
    return SyntaxHighlighterRegistry.getHighlighter(mode).highlight(text)
}

/**
 * Highlight a single [line] for [mode].
 * Delegates to [SyntaxHighlighter.highlightLine].
 */
fun highlightLine(line: String, mode: LanguageMode): AnnotatedString {
    ensureHighlighters(mode)
    return SyntaxHighlighterRegistry.getHighlighter(mode).highlightLine(line)
}

/**
 * Auto-format [text] for [mode].
 * Delegates to the [com.reqlab.editor.core.TextFormatter] (via [LanguageRegistry]) registered
 * for [mode], so custom formatters registered with [LanguageRegistry.register] are picked up.
 */
fun autoFormat(text: String, mode: LanguageMode): String {
    if (!LanguageRegistry.hasProvider(mode)) LanguageRegistry.registerBuiltins()
    return LanguageRegistry.getProvider(mode).format(text)
}

