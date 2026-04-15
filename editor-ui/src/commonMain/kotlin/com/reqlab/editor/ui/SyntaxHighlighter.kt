package com.reqlab.editor.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.reqlab.editor.core.EditorDocument
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.LanguageModeProvider
import com.reqlab.editor.core.LanguageRegistry
import com.reqlab.editor.core.TokenType

/**
 * Single-responsibility interface for turning source text into a styled [AnnotatedString].
 *
 * Separated from [com.reqlab.editor.core.LanguageModeProvider] (ISP) because highlighting
 * is a UI concern (Compose) while tokenization is a pure-Kotlin concern.
 *
 * Custom implementations can be registered per language via [SyntaxHighlighterRegistry]:
 * ```
 * SyntaxHighlighterRegistry.register(object : SyntaxHighlighter {
 *     override val mode = LanguageMode.JSON
 *     override fun highlight(text: String) = myCustomJsonHighlight(text)
 * })
 * ```
 */
interface SyntaxHighlighter {
    /** The language this highlighter handles. */
    val mode: LanguageMode

    /**
     * Highlight [text] and return a styled [AnnotatedString].
     * Implementations must never throw — return plain [AnnotatedString] on any error.
     */
    fun highlight(text: String): AnnotatedString

    /**
     * Convenience for single-line highlighting.
     * Defaults to [highlight]; override for line-stateless implementations.
     */
    fun highlightLine(line: String): AnnotatedString = highlight(line)
}

// ── Default implementation ───────────────────────────────────────

/**
 * Default [SyntaxHighlighter] that delegates to:
 * - [LanguageModeProvider.tokenizeLine] for structural token positions
 * - [TokenColorRegistry] for the language-specific color scheme
 *
 * This is the implementation registered for all built-in languages.
 * Provide a custom [SyntaxHighlighter] via [SyntaxHighlighterRegistry.register]
 * to replace this for any specific language.
 */
class DefaultSyntaxHighlighter(
    override val mode: LanguageMode,
    private val provider: LanguageModeProvider,
) : SyntaxHighlighter {

    override fun highlight(text: String): AnnotatedString {
        if (mode == LanguageMode.PLAIN_TEXT) return AnnotatedString(text)
        return try {
            val doc = EditorDocument.create(text)
            buildAnnotatedString {
                append(text)
                var tokenState: Any? = null
                var charOffset = 0
                for (lineNum in 1..doc.lineCount) {
                    val lineText = doc.lineText(lineNum)
                    val (tokens, newState) = provider.tokenizeLine(lineText, lineNum, tokenState)
                    tokenState = newState
                    for (token in tokens) {
                        val start = charOffset + token.startOffset
                        val end   = charOffset + token.endOffset
                        if (start < end && end <= text.length) {
                            val color = TokenColorRegistry.colorFor(mode, token.type)
                            val style = if (token.type == TokenType.KEYWORD)
                                SpanStyle(color = color, fontWeight = FontWeight.SemiBold)
                            else
                                SpanStyle(color = color)
                            addStyle(style, start, end.coerceAtMost(text.length))
                        }
                    }
                    charOffset += lineText.length + if (lineNum < doc.lineCount) 1 else 0
                }
            }
        } catch (_: Throwable) {
            AnnotatedString(text)
        }
    }
}

// ── Helpers called only at startup ───────────────────────────────

internal fun buildDefaultHighlightersFor(vararg modes: LanguageMode): List<SyntaxHighlighter> {
    if (!LanguageRegistry.hasProvider(LanguageMode.PLAIN_TEXT)) LanguageRegistry.registerBuiltins()
    return modes.map { mode ->
        DefaultSyntaxHighlighter(mode, LanguageRegistry.getProvider(mode))
    }
}
