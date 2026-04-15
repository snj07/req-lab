package com.reqlab.editor.ui

import androidx.compose.ui.graphics.Color
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.TokenType

// ── Syntax token colours ────────────────────────────────────────

object SyntaxColors {
    // JSON
    val jsonKey     = Color(0xFF9CDCFE)
    val jsonString  = Color(0xFFCE9178)
    val jsonNumber  = Color(0xFFB5CEA8)
    val jsonBoolean = Color(0xFF569CD6)
    val jsonNull    = Color(0xFF569CD6)
    val jsonBrace   = Color(0xFFD4D4D4)

    // XML / HTML
    val xmlTagName   = Color(0xFF569CD6)
    val xmlAttrName  = Color(0xFF9CDCFE)
    val xmlAttrValue = Color(0xFFCE9178)
    val xmlBracket   = Color(0xFF808080)
    val xmlComment   = Color(0xFF6A9955)
    val xmlContent   = Color(0xFFD4D4D4)
    val xmlDoctype   = Color(0xFF569CD6)

    // JavaScript
    val jsKeyword  = Color(0xFFC678DD)
    val jsString   = Color(0xFFCE9178)
    val jsComment  = Color(0xFF6A9955)
    val jsOperator = Color(0xFFD4D4D4)
    val jsBuiltin  = Color(0xFF4EC9B0)

    // GraphQL
    val gqlKeyword   = Color(0xFFC678DD)
    val gqlType      = Color(0xFF4EC9B0)
    val gqlField     = Color(0xFF9CDCFE)
    val gqlDirective = Color(0xFFE5C07B)

    // Common
    val plain       = Color(0xFFD4D4D4)
    val error       = Color(0xFFF44747)

    // Search highlights
    val searchMatch  = Color(0x44FFEB3B)
    val searchActive = Color(0x88FFEB3B)
}

// ── Token → Color mapping ────────────────────────────────────────

/**
 * Maps [type] to a display [Color] for [language].
 *
 * Delegates to [TokenColorRegistry]; color schemes are registered per language
 * via [TokenColorRegistry.register] so **new languages never require editing
 * this function**.  Call [TokenColorRegistry.registerBuiltinSchemes] at startup.
 */
fun colorForToken(type: TokenType, language: LanguageMode): Color =
    TokenColorRegistry.colorFor(language, type)
