package com.reqlab.editor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.reqlab.editor.core.TokenType

data class EditorTheme(
    val background: Color = EditorColors.background,
    val foreground: Color = EditorColors.foreground,
    val lineNumberFg: Color = EditorColors.lineNumberFg,
    val lineNumberBg: Color = EditorColors.lineNumberBg,
    val gutterBorder: Color = EditorColors.gutterBorder,
    val selectionBg: Color = EditorColors.selectionBg,
    val cursorLine: Color = EditorColors.cursorLine,
    val foldIndicator: Color = EditorColors.foldIndicator,
    val indentGuide: Color = EditorColors.indentGuide,
    val errorUnderline: Color = EditorColors.errorUnderline,
    val warningUnderline: Color = EditorColors.warningUnderline,
    val accent: Color = EditorColors.accent,
    val tokenColors: Map<TokenType, Color> = defaultTokenColors(),
) {
    fun spanStyleFor(type: TokenType): SpanStyle =
        SpanStyle(color = tokenColors[type] ?: foreground)

    companion object {
        val Dark = EditorTheme()

        val Light = EditorTheme(
            background = Color(0xFFFFFFFF),
            foreground = Color(0xFF333333),
            lineNumberFg = Color(0xFF999999),
            lineNumberBg = Color(0xFFF5F5F5),
            gutterBorder = Color(0xFFE0E0E0),
            selectionBg = Color(0xFFADD6FF),
            cursorLine = Color(0xFFF0F0F0),
            foldIndicator = Color(0xFF999999),
            indentGuide = Color(0xFFE0E0E0),
            accent = Color(0xFF0066CC),
            tokenColors = mapOf(
                TokenType.KEYWORD to Color(0xFF0000FF),
                TokenType.STRING to Color(0xFFA31515),
                TokenType.NUMBER to Color(0xFF098658),
                TokenType.COMMENT to Color(0xFF008000),
                TokenType.OPERATOR to Color(0xFF333333),
                TokenType.PUNCTUATION to Color(0xFF333333),
                TokenType.TAG to Color(0xFF800000),
                TokenType.ATTRIBUTE to Color(0xFFFF0000),
                TokenType.PROPERTY to Color(0xFF0451A5),
                TokenType.VALUE to Color(0xFFA31515),
                TokenType.PLAIN to Color(0xFF333333),
                TokenType.ERROR to Color(0xFFFF0000),
            ),
        )

        private fun defaultTokenColors(): Map<TokenType, Color> = mapOf(
            TokenType.KEYWORD to EditorColors.keyword,
            TokenType.STRING to EditorColors.string,
            TokenType.NUMBER to EditorColors.number,
            TokenType.COMMENT to EditorColors.comment,
            TokenType.OPERATOR to EditorColors.operator,
            TokenType.PUNCTUATION to EditorColors.punctuation,
            TokenType.TAG to EditorColors.tag,
            TokenType.ATTRIBUTE to EditorColors.attribute,
            TokenType.PROPERTY to EditorColors.property,
            TokenType.VALUE to EditorColors.value,
            TokenType.PLAIN to EditorColors.plain,
            TokenType.ERROR to EditorColors.error,
        )
    }
}
