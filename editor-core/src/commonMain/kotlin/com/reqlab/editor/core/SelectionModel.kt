package com.reqlab.editor.core

data class TextRange(
    val start: CursorPosition,
    val end: CursorPosition,
) {
    val isEmpty: Boolean get() = start == end
    val isMultiLine: Boolean get() = start.line != end.line

    fun normalized(): TextRange =
        if (start.line > end.line || (start.line == end.line && start.column > end.column))
            TextRange(end, start) else this

    companion object {
        val EMPTY = TextRange(CursorPosition(), CursorPosition())
    }
}

data class SelectionModel(
    val ranges: List<TextRange> = emptyList(),
) {
    val hasSelection: Boolean get() = ranges.any { !it.isEmpty }
    val primaryRange: TextRange? get() = ranges.firstOrNull()

    fun selectAll(document: EditorDocument): SelectionModel {
        val start = CursorPosition(1, 0)
        val end = CursorPosition(document.lineCount, document.lineLength(document.lineCount))
        return SelectionModel(listOf(TextRange(start, end)))
    }

    fun clearSelection(): SelectionModel = SelectionModel(emptyList())

    fun setSelection(range: TextRange): SelectionModel = SelectionModel(listOf(range))

    fun extendSelection(anchor: CursorPosition, head: CursorPosition): SelectionModel =
        SelectionModel(listOf(TextRange(anchor, head)))

    companion object {
        val EMPTY = SelectionModel()
    }
}
