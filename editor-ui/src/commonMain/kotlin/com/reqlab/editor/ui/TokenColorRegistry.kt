package com.reqlab.editor.ui

import androidx.compose.ui.graphics.Color
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.TokenType

/**
 * Registry that maps each [LanguageMode] to a color-scheme function
 * `(TokenType) -> Color`.
 *
 * Language providers (built-in or third-party) call [register] once to declare
 * their color scheme.  [colorFor] looks up the registered scheme — or falls back
 * to [SyntaxColors.plain] — so **adding a new language never requires editing
 * [colorForToken]**.
 *
 * Call [registerBuiltinSchemes] during app startup (alongside
 * [com.reqlab.editor.core.LanguageRegistry.registerBuiltins]).
 */
object TokenColorRegistry {

    private val schemes = mutableMapOf<LanguageMode, (TokenType) -> Color>()

    /** Register a color-scheme function for [mode]. Overwrites any prior registration. */
    fun register(mode: LanguageMode, scheme: (TokenType) -> Color) {
        schemes[mode] = scheme
    }

    /**
     * Returns the color for [type] in [mode], or [SyntaxColors.plain] if no
     * scheme has been registered for [mode].
     */
    fun colorFor(mode: LanguageMode, type: TokenType): Color =
        schemes[mode]?.invoke(type) ?: SyntaxColors.plain

    fun hasScheme(mode: LanguageMode): Boolean = mode in schemes

    fun clear() { schemes.clear() }

    // ── Built-in schemes ─────────────────────────────────────────

    /**
     * Registers color schemes for all built-in language modes.
     * Safe to call multiple times.
     */
    fun registerBuiltinSchemes() {
        register(LanguageMode.JSON) { type ->
            when (type) {
                TokenType.STRING      -> SyntaxColors.jsonString
                TokenType.NUMBER      -> SyntaxColors.jsonNumber
                TokenType.KEYWORD     -> SyntaxColors.jsonBoolean
                TokenType.PROPERTY    -> SyntaxColors.jsonKey
                TokenType.PUNCTUATION -> SyntaxColors.jsonBrace
                TokenType.ERROR       -> Color(0xFFFF6B6B)
                else                  -> SyntaxColors.plain
            }
        }

        register(LanguageMode.PLAIN_TEXT) { type ->
            when (type) {
                TokenType.ERROR -> Color(0xFFFF6B6B)
                else            -> SyntaxColors.plain
            }
        }

        val xmlScheme: (TokenType) -> Color = { type ->
            when (type) {
                TokenType.TAG         -> SyntaxColors.xmlTagName
                TokenType.ATTRIBUTE   -> SyntaxColors.xmlAttrName
                TokenType.STRING      -> SyntaxColors.xmlAttrValue
                TokenType.PUNCTUATION -> SyntaxColors.xmlBracket
                TokenType.COMMENT     -> SyntaxColors.xmlComment
                TokenType.KEYWORD     -> SyntaxColors.xmlDoctype
                TokenType.PLAIN       -> SyntaxColors.xmlContent
                TokenType.ERROR       -> Color(0xFFFF6B6B)
                else                  -> SyntaxColors.xmlContent
            }
        }
        register(LanguageMode.XML, xmlScheme)
        register(LanguageMode.HTML, xmlScheme)

        register(LanguageMode.JAVASCRIPT) { type ->
            when (type) {
                TokenType.KEYWORD     -> SyntaxColors.jsKeyword
                TokenType.STRING      -> SyntaxColors.jsString
                TokenType.COMMENT     -> SyntaxColors.jsComment
                TokenType.OPERATOR    -> SyntaxColors.jsOperator
                TokenType.TAG         -> SyntaxColors.gqlType
                TokenType.ATTRIBUTE   -> SyntaxColors.gqlField
                TokenType.NUMBER      -> SyntaxColors.jsonNumber
                TokenType.ERROR       -> Color(0xFFFF6B6B)
                else                  -> SyntaxColors.plain
            }
        }

        register(LanguageMode.GRAPHQL) { type ->
            when (type) {
                TokenType.KEYWORD     -> SyntaxColors.gqlKeyword
                TokenType.STRING      -> SyntaxColors.jsonString
                TokenType.TAG         -> SyntaxColors.gqlType
                TokenType.PROPERTY    -> SyntaxColors.gqlField
                TokenType.ATTRIBUTE   -> SyntaxColors.gqlDirective
                TokenType.NUMBER      -> SyntaxColors.jsonNumber
                TokenType.COMMENT     -> SyntaxColors.jsComment
                TokenType.PUNCTUATION -> SyntaxColors.jsonBrace
                TokenType.ERROR       -> Color(0xFFFF6B6B)
                else                  -> SyntaxColors.plain
            }
        }
    }
}
