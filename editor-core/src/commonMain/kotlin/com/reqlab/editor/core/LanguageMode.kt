package com.reqlab.editor.core

enum class LanguageMode {
    PLAIN_TEXT, JSON, XML, HTML, JAVASCRIPT;

    companion object {
        fun fromContentType(contentType: String?): LanguageMode {
            val type = contentType?.lowercase().orEmpty()
            return when {
                "json" in type                        -> JSON
                "xml" in type                         -> XML
                "html" in type || "text/html" in type -> HTML
                "javascript" in type || "js" in type  -> JAVASCRIPT
                else                                  -> PLAIN_TEXT
            }
        }
    }
}

interface LanguageModeProvider {
    val mode: LanguageMode
    val displayName: String
    val fileExtensions: List<String>
    val mimeTypes: List<String>

    fun tokenizeLine(line: String, lineNumber: Int, state: Any? = null): Pair<List<Token>, Any?>
    fun foldingRegions(document: EditorDocument): List<FoldRegion>
    fun validate(text: String): List<InlineEditorError>

    /**
     * Tokenize [document] in the char range [fromChar, toChar) and write results
     * into [buffer] via [StyleBuffer.applyStyle].
     *
     * The default implementation iterates affected lines using [tokenizeLine] and
     * translates token positions to absolute char offsets.  Language modes with a
     * stateful lexer (e.g. multi-line strings) should override this for accuracy.
     *
     * Called exclusively from IdleLexer on Dispatchers.Default.
     */
    fun tokenizeRangeIntoBuffer(
        document: DocumentModel,
        fromChar: Int,
        toChar: Int,
        buffer: StyleBuffer,
    ) {
        val firstLine = document.lineAt(fromChar)
        val lastLine  = document.lineAt((toChar - 1).coerceAtLeast(0))
        var lexState: Any? = null
        for (line in 0..lastLine) {
            val lineText  = document.lineText(line)
            val lineStart = document.lineStart(line)
            val (tokens, nextState) = tokenizeLine(lineText, line + 1, if (line == 0) null else lexState)
            lexState = nextState
            if (line < firstLine) continue   // warm up lexer state but don't write below fromChar
            for (token in tokens) {
                val absFrom = lineStart + token.startOffset
                val absTo   = lineStart + token.endOffset
                if (absTo <= fromChar) continue
                if (absFrom >= toChar) break
                buffer.applyStyle(
                    maxOf(absFrom, fromChar),
                    minOf(absTo, toChar),
                    token.type,
                )
            }
        }
    }
}
