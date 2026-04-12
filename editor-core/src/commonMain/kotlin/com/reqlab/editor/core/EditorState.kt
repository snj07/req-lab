package com.reqlab.editor.core

data class EditorState(
    val document: EditorDocument = EditorDocument.empty(),
    val languageMode: LanguageMode = LanguageMode.PLAIN_TEXT,
    val cursor: CursorState = CursorState(),
    val selection: SelectionModel = SelectionModel.EMPTY,
    val folding: FoldingModel = FoldingModel(),
    val diagnostics: List<InlineEditorError> = emptyList(),
    val lineNumbersEnabled: Boolean = true,
    val foldingEnabled: Boolean = true,
) {
    val text: String get() = document.text
    val lineCount: Int get() = document.lineCount

    companion object {
        fun fromText(
            text: String,
            languageMode: LanguageMode = LanguageMode.PLAIN_TEXT,
            lineNumbersEnabled: Boolean = true,
            foldingEnabled: Boolean = true,
        ): EditorState = EditorState(
            document = EditorDocument.create(text),
            languageMode = languageMode,
            lineNumbersEnabled = lineNumbersEnabled,
            foldingEnabled = foldingEnabled,
        )
    }
}
