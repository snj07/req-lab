package com.reqlab.editor.core

object PlainTextMode : LanguageModeProvider {
    override val mode = LanguageMode.PLAIN_TEXT
    override val displayName = "Plain Text"
    override val fileExtensions = listOf("txt", "text", "log")
    override val mimeTypes = listOf("text/plain")

    override fun tokenizeLine(line: String, lineNumber: Int, state: Any?): Pair<List<Token>, Any?> {
        if (line.isEmpty()) return emptyList<Token>() to null
        return listOf(Token(0, line.length, TokenType.PLAIN)) to null
    }

    override fun foldingRegions(document: EditorDocument): List<FoldRegion> = emptyList()
    override fun validate(text: String): List<InlineEditorError> = emptyList()
}
