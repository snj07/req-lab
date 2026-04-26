package com.reqlab.editor.core

class EditorEngine {
    companion object {
        const val LARGE_FILE_CHAR_THRESHOLD = 1_000_000
    }

    init {
        if (!LanguageRegistry.hasProvider(LanguageMode.PLAIN_TEXT)) {
            LanguageRegistry.registerBuiltins()
        }
    }

    fun createState(
        text: String,
        languageMode: LanguageMode,
        lineNumbersEnabled: Boolean = true,
        foldingEnabled: Boolean = true,
    ): EditorState = revalidate(
        EditorState.fromText(text, languageMode, lineNumbersEnabled, foldingEnabled)
    )

    fun updateText(state: EditorState, text: String): EditorState =
        revalidate(state.copy(document = EditorDocument.create(text)))

    fun insertText(state: EditorState, position: CursorPosition, insertText: String): EditorState {
        val offset = state.document.positionToOffset(position)
        val newDoc = state.document.insert(offset, insertText)
        return revalidate(state.copy(document = newDoc))
    }

    fun deleteRange(state: EditorState, range: TextRange): EditorState {
        val norm = range.normalized()
        val s = state.document.positionToOffset(norm.start)
        val e = state.document.positionToOffset(norm.end)
        return revalidate(state.copy(document = state.document.delete(s, e)))
    }

    fun replaceRange(state: EditorState, range: TextRange, replacement: String): EditorState {
        val norm = range.normalized()
        val s = state.document.positionToOffset(norm.start)
        val e = state.document.positionToOffset(norm.end)
        return revalidate(state.copy(document = state.document.replace(s, e, replacement)))
    }

    fun switchMode(state: EditorState, languageMode: LanguageMode): EditorState {
        val provider = LanguageRegistry.getProvider(languageMode)
        val diagnostics = provider.validate(state.text)
        val foldRegions = if (state.foldingEnabled) provider.foldingRegions(state.document) else emptyList()
        return state.copy(
            languageMode = languageMode,
            diagnostics = diagnostics,
            folding = FoldingModel(regions = foldRegions),
        )
    }

    fun tokenizeRange(state: EditorState, startLine: Int, endLine: Int): Map<Int, List<Token>> {
        val provider = LanguageRegistry.getProvider(state.languageMode)
        val result = mutableMapOf<Int, List<Token>>()
        var tokenState: Any? = null
        val actualEnd = minOf(endLine, state.document.lineCount)
        for (line in 1..actualEnd) {
            val lineText = state.document.lineText(line)
            val (tokens, newState) = provider.tokenizeLine(lineText, line, tokenState)
            tokenState = newState
            if (line in startLine..actualEnd) result[line] = tokens
        }
        return result
    }

    fun tokenizeLine(state: EditorState, lineNumber: Int): List<Token> =
        tokenizeRange(state, lineNumber, lineNumber)[lineNumber] ?: emptyList()

    fun toggleFold(state: EditorState, startLine: Int): EditorState =
        state.copy(folding = state.folding.toggleFold(startLine))

    fun foldAll(state: EditorState): EditorState =
        state.copy(folding = state.folding.foldAll())

    fun unfoldAll(state: EditorState): EditorState =
        state.copy(folding = state.folding.unfoldAll())

    fun selectAll(state: EditorState): EditorState {
        val sel = state.selection.selectAll(state.document)
        val endPos = CursorPosition(state.document.lineCount, state.document.lineLength(state.document.lineCount))
        return state.copy(selection = sel, cursor = CursorState(endPos))
    }

    fun clearSelection(state: EditorState): EditorState =
        state.copy(selection = state.selection.clearSelection())

    fun moveCursorUp(state: EditorState): EditorState =
        state.copy(cursor = state.cursor.moveUp(state.document), selection = SelectionModel.EMPTY)

    fun moveCursorDown(state: EditorState): EditorState =
        state.copy(cursor = state.cursor.moveDown(state.document), selection = SelectionModel.EMPTY)

    fun moveCursorLeft(state: EditorState): EditorState =
        state.copy(cursor = state.cursor.moveLeft(state.document), selection = SelectionModel.EMPTY)

    fun moveCursorRight(state: EditorState): EditorState =
        state.copy(cursor = state.cursor.moveRight(state.document), selection = SelectionModel.EMPTY)

    fun validate(text: String, languageMode: LanguageMode): List<InlineEditorError> {
        if (text.isBlank()) return emptyList()
        return LanguageRegistry.getProvider(languageMode).validate(text)
    }

    fun visibleLines(state: EditorState): List<Pair<Int, String>> {
        val visible = state.folding.visibleLines(state.document.lineCount)
        return visible.map { it to state.document.lineText(it) }
    }

    private fun revalidate(state: EditorState): EditorState {
        val provider = LanguageRegistry.getProvider(state.languageMode)
        val diagnostics = provider.validate(state.text)
        val foldRegions = if (state.foldingEnabled) provider.foldingRegions(state.document) else emptyList()
        return state.copy(diagnostics = diagnostics, folding = state.folding.updateRegions(foldRegions))
    }
}
