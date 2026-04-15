package com.reqlab.editor.core

enum class LanguageMode {
    PLAIN_TEXT, JSON, XML, HTML, JAVASCRIPT, GRAPHQL;

    companion object {
        fun fromContentType(contentType: String?): LanguageMode {
            val type = contentType?.lowercase().orEmpty()
            return when {
                "json" in type                              -> JSON
                "graphql" in type                           -> GRAPHQL
                "xml" in type                               -> XML
                "html" in type || "text/html" in type       -> HTML
                "javascript" in type || "ecmascript" in type || "js" in type -> JAVASCRIPT
                else                                        -> PLAIN_TEXT
            }
        }
    }
}

/**
 * Describes the structural folding strategy that the editor should use for
 * a language.  Language providers declare which strategy applies; the editor-ui
 * layer dispatches to the appropriate detector without ever naming individual
 * [LanguageMode] values.
 *
 * - [BRACE]  – fold by matching `{` / `}` and `[` / `]` (JSON, JS, GraphQL …)
 * - [XML]    – fold by matching open/close XML/HTML tags
 * - [PLAIN]  – no structural folding (plain text, CSV …)
 */
enum class FoldingStyle { BRACE, XML, PLAIN }

interface LanguageModeProvider : TextFormatter {
    val mode: LanguageMode
    val displayName: String
    val fileExtensions: List<String>
    val mimeTypes: List<String>

    /** Structural folding strategy.  Defaults to [FoldingStyle.PLAIN]. */
    val foldingStyle: FoldingStyle get() = FoldingStyle.PLAIN

    fun tokenizeLine(line: String, lineNumber: Int, state: Any? = null): Pair<List<Token>, Any?>
    fun foldingRegions(document: EditorDocument): List<FoldRegion>
    fun validate(text: String): List<InlineEditorError>

    /**
     * Default no-op formatter — returns [text] unchanged.
     * Language modes that support formatting override this.
     * Declared here so [LanguageModeProvider] fulfils [TextFormatter] automatically.
     */
    override fun format(text: String): String = text

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
