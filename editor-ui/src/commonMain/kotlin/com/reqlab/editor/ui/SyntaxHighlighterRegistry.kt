package com.reqlab.editor.ui

import com.reqlab.editor.core.LanguageMode

/**
 * Registry that maps each [LanguageMode] to a [SyntaxHighlighter] implementation.
 *
 * This is the extension point for highlighting (OCP/DIP):
 * - Built-in languages are pre-registered via [registerBuiltinHighlighters].
 * - Third-party / app-specific languages call [register] with a custom [SyntaxHighlighter].
 *
 * Usage — register once at startup:
 * ```
 * SyntaxHighlighterRegistry.registerBuiltinHighlighters()
 * // Optionally override a built-in:
 * SyntaxHighlighterRegistry.register(MyCustomJsonHighlighter())
 * ```
 *
 * Usage — at every highlight call (via [highlightText]):
 * ```
 * SyntaxHighlighterRegistry.getHighlighter(mode).highlight(text)
 * ```
 */
object SyntaxHighlighterRegistry {

    private val highlighters = mutableMapOf<LanguageMode, SyntaxHighlighter>()

    /** Register [highlighter] for its declared [SyntaxHighlighter.mode]. Overwrites any prior entry. */
    fun register(highlighter: SyntaxHighlighter) {
        highlighters[highlighter.mode] = highlighter
    }

    /**
     * Returns the [SyntaxHighlighter] for [mode], falling back to the
     * [LanguageMode.PLAIN_TEXT] highlighter (which returns unstyled text).
     */
    fun getHighlighter(mode: LanguageMode): SyntaxHighlighter =
        highlighters[mode] ?: highlighters[LanguageMode.PLAIN_TEXT] ?: PlainHighlighter

    fun hasHighlighter(mode: LanguageMode): Boolean = mode in highlighters

    fun clear() { highlighters.clear() }

    // ── Built-in registration ─────────────────────────────────────

    /**
     * Registers [DefaultSyntaxHighlighter] instances for all built-in language modes.
     * Also ensures [com.reqlab.editor.core.LanguageRegistry] and
     * [TokenColorRegistry] built-ins are registered.
     * Safe to call multiple times.
     */
    fun registerBuiltinHighlighters() {
        TokenColorRegistry.registerBuiltinSchemes()
        buildDefaultHighlightersFor(
            LanguageMode.PLAIN_TEXT,
            LanguageMode.JSON,
            LanguageMode.XML,
            LanguageMode.HTML,
            LanguageMode.JAVASCRIPT,
            LanguageMode.GRAPHQL,
        ).forEach { register(it) }
    }

    // ── Fallback ──────────────────────────────────────────────────

    /** Emergency fallback when the registry is empty (shouldn't happen in practice). */
    private object PlainHighlighter : SyntaxHighlighter {
        override val mode = LanguageMode.PLAIN_TEXT
        override fun highlight(text: String) = androidx.compose.ui.text.AnnotatedString(text)
    }
}
