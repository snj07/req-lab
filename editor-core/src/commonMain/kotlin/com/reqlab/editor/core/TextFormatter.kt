package com.reqlab.editor.core

/**
 * Single-responsibility interface for language-aware text formatting.
 *
 * Separated from [LanguageModeProvider] (ISP) so that:
 * - A lightweight formatter can be provided without implementing a full tokenizer.
 * - Custom formatters can be registered via [LanguageRegistry.registerFormatter]
 *   without replacing the entire [LanguageModeProvider].
 *
 * [LanguageModeProvider] extends this interface and provides a no-op default,
 * meaning every registered language automatically fulfils [TextFormatter].
 */
interface TextFormatter {
    /**
     * Pretty-format / indent [text] for this language.
     * Implementations must return the original [text] unchanged on any error.
     */
    fun format(text: String): String
}
