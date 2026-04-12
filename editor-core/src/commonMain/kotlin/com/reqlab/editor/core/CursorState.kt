package com.reqlab.editor.core

data class CursorPosition(
    val line: Int = 1,
    val column: Int = 0,
) {
    init {
        require(line >= 1) { "Line must be >= 1, got $line" }
        require(column >= 0) { "Column must be >= 0, got $column" }
    }
}

data class CursorState(
    val position: CursorPosition = CursorPosition(),
) {
    fun moveUp(document: EditorDocument): CursorState {
        if (position.line <= 1) return this
        val newLine = position.line - 1
        return copy(position = CursorPosition(newLine, minOf(position.column, document.lineLength(newLine))))
    }

    fun moveDown(document: EditorDocument): CursorState {
        if (position.line >= document.lineCount) return this
        val newLine = position.line + 1
        return copy(position = CursorPosition(newLine, minOf(position.column, document.lineLength(newLine))))
    }

    fun moveLeft(document: EditorDocument): CursorState {
        return if (position.column > 0) {
            copy(position = CursorPosition(position.line, position.column - 1))
        } else if (position.line > 1) {
            copy(position = CursorPosition(position.line - 1, document.lineLength(position.line - 1)))
        } else this
    }

    fun moveRight(document: EditorDocument): CursorState {
        val lineLen = document.lineLength(position.line)
        return if (position.column < lineLen) {
            copy(position = CursorPosition(position.line, position.column + 1))
        } else if (position.line < document.lineCount) {
            copy(position = CursorPosition(position.line + 1, 0))
        } else this
    }

    fun moveToLineStart(): CursorState = copy(position = CursorPosition(position.line, 0))

    fun moveToLineEnd(document: EditorDocument): CursorState =
        copy(position = CursorPosition(position.line, document.lineLength(position.line)))

    fun moveToDocumentStart(): CursorState = copy(position = CursorPosition(1, 0))

    fun moveToDocumentEnd(document: EditorDocument): CursorState {
        val last = document.lineCount
        return copy(position = CursorPosition(last, document.lineLength(last)))
    }
}
