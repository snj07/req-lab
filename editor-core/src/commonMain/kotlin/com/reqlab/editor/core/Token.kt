package com.reqlab.editor.core

enum class TokenType {
    KEYWORD, STRING, NUMBER, COMMENT, OPERATOR,
    PUNCTUATION, TAG, ATTRIBUTE, PROPERTY, VALUE,
    PLAIN, ERROR,
}

data class Token(
    val startOffset: Int,
    val endOffset: Int,
    val type: TokenType,
)

data class FoldRegion(
    val startLine: Int,
    val endLine: Int,
    val placeholder: String = "...",
) {
    init {
        require(startLine < endLine) { "Fold region must span at least 2 lines" }
    }
}
