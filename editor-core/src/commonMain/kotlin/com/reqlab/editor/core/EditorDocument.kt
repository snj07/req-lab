package com.reqlab.editor.core

class EditorDocument private constructor(
    val text: String,
    private val lineStarts: IntArray,
) {
    val lineCount: Int get() = lineStarts.size
    val length: Int get() = text.length

    fun lineText(line: Int): String {
        require(line in 1..lineCount) { "Line $line out of range [1, $lineCount]" }
        val start = lineStarts[line - 1]
        val end = if (line < lineCount) {
            val nextStart = lineStarts[line]
            if (nextStart > 0 && text[nextStart - 1] == '\n') nextStart - 1 else nextStart
        } else {
            text.length
        }
        return text.substring(start, end)
    }

    fun lineLength(line: Int): Int = lineText(line).length

    fun positionToOffset(position: CursorPosition): Int {
        require(position.line in 1..lineCount) { "Line ${position.line} out of range" }
        val lineStart = lineStarts[position.line - 1]
        return (lineStart + position.column).coerceAtMost(text.length)
    }

    fun offsetToPosition(offset: Int): CursorPosition {
        val clamped = offset.coerceIn(0, text.length)
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (lineStarts[mid] <= clamped) lo = mid else hi = mid - 1
        }
        return CursorPosition(line = lo + 1, column = clamped - lineStarts[lo])
    }

    fun lines(): List<String> = (1..lineCount).map { lineText(it) }

    fun insert(offset: Int, insertText: String): EditorDocument {
        val o = offset.coerceIn(0, text.length)
        return create(text.substring(0, o) + insertText + text.substring(o))
    }

    fun delete(startOffset: Int, endOffset: Int): EditorDocument {
        val s = startOffset.coerceIn(0, text.length)
        val e = endOffset.coerceIn(s, text.length)
        return create(text.substring(0, s) + text.substring(e))
    }

    fun replace(startOffset: Int, endOffset: Int, replacement: String): EditorDocument {
        val s = startOffset.coerceIn(0, text.length)
        val e = endOffset.coerceIn(s, text.length)
        return create(text.substring(0, s) + replacement + text.substring(e))
    }

    fun replaceAll(newText: String): EditorDocument = create(newText)

    fun getText(startOffset: Int, endOffset: Int): String {
        val s = startOffset.coerceIn(0, text.length)
        val e = endOffset.coerceIn(s, text.length)
        return text.substring(s, e)
    }

    fun getText(range: TextRange): String {
        val norm = range.normalized()
        return getText(positionToOffset(norm.start), positionToOffset(norm.end))
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is EditorDocument && text == other.text)

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = "EditorDocument(${text.length} chars, $lineCount lines)"

    companion object {
        fun create(text: String): EditorDocument =
            EditorDocument(text, buildLineStarts(text))

        fun empty(): EditorDocument = create("")

        private fun buildLineStarts(text: String): IntArray {
            if (text.isEmpty()) return intArrayOf(0)
            val starts = mutableListOf(0)
            for (i in text.indices) {
                if (text[i] == '\n') starts.add(i + 1)
            }
            return starts.toIntArray()
        }
    }
}
