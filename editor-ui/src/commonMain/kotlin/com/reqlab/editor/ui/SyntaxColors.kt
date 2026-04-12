package com.reqlab.editor.ui

import androidx.compose.ui.graphics.Color
import com.reqlab.editor.core.LanguageMode
import com.reqlab.editor.core.TokenType

// ── Syntax token colours ────────────────────────────────────────

internal object SyntaxColors {
    // JSON
    val jsonKey     = Color(0xFF92C5F8)
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

    // JavaScript
    val jsKeyword  = Color(0xFFC586C0)
    val jsString   = Color(0xFFCE9178)
    val jsComment  = Color(0xFF6A9955)
    val jsOperator = Color(0xFFD4D4D4)
    val jsBuiltin  = Color(0xFF4EC9B0)

    // GraphQL
    val gqlKeyword = Color(0xFFC586C0)
    val gqlType    = Color(0xFF4EC9B0)
    val gqlField   = Color(0xFF9CDCFE)

    // Common
    val plain      = Color(0xFFD4D4D4)
    val error      = Color(0xFFF44747)
}

// ── Token → Color mapping ────────────────────────────────────────

internal fun colorForToken(type: TokenType, language: LanguageMode): Color = when (language) {
    LanguageMode.JSON, LanguageMode.PLAIN_TEXT -> when (type) {
        TokenType.STRING      -> SyntaxColors.jsonString
        TokenType.NUMBER      -> SyntaxColors.jsonNumber
        TokenType.KEYWORD     -> SyntaxColors.jsonBoolean
        TokenType.PUNCTUATION -> SyntaxColors.jsonBrace
        TokenType.ERROR       -> Color(0xFFFF6B6B)
        else                  -> SyntaxColors.plain
    }
    LanguageMode.XML, LanguageMode.HTML -> when (type) {
        TokenType.TAG         -> SyntaxColors.xmlTagName
        TokenType.ATTRIBUTE   -> SyntaxColors.xmlAttrName
        TokenType.STRING      -> SyntaxColors.xmlAttrValue
        TokenType.PUNCTUATION -> SyntaxColors.xmlBracket
        TokenType.COMMENT     -> SyntaxColors.xmlComment
        TokenType.PLAIN       -> SyntaxColors.xmlContent
        TokenType.ERROR       -> Color(0xFFFF6B6B)
        else                  -> SyntaxColors.xmlContent
    }
    LanguageMode.JAVASCRIPT -> when (type) {
        TokenType.KEYWORD     -> SyntaxColors.jsKeyword
        TokenType.STRING      -> SyntaxColors.jsString
        TokenType.COMMENT     -> SyntaxColors.jsComment
        TokenType.OPERATOR    -> SyntaxColors.jsOperator
        TokenType.TAG         -> SyntaxColors.gqlType
        TokenType.ATTRIBUTE   -> SyntaxColors.gqlField
        TokenType.ERROR       -> Color(0xFFFF6B6B)
        else                  -> SyntaxColors.plain
    }
}
