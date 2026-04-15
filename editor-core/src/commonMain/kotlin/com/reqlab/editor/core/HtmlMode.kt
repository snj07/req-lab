package com.reqlab.editor.core

object HtmlMode : LanguageModeProvider {
    override val mode = LanguageMode.HTML
    override val displayName = "HTML"
    override val fileExtensions = listOf("html", "htm", "xhtml")
    override val mimeTypes = listOf("text/html", "application/xhtml+xml")
    override val foldingStyle = FoldingStyle.XML

    override fun tokenizeLine(line: String, lineNumber: Int, state: Any?): Pair<List<Token>, Any?> =
        XmlMode.tokenizeLine(line, lineNumber, state)

    override fun foldingRegions(document: EditorDocument): List<FoldRegion> =
        XmlMode.foldingRegions(document)

    override fun validate(text: String): List<InlineEditorError> {
        if (text.isBlank()) return emptyList()
        return XmlMode.validate(text).map { error ->
            if ("Unclosed" in error.message) error.copy(severity = InlineErrorSeverity.WARNING) else error
        }
    }

    override fun format(text: String): String = XmlMode.format(text)
}
