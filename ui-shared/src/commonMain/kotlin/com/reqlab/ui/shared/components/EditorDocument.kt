package com.reqlab.ui.shared.components

/**
 * Immutable document model for the code editor.
 *
 * Holds the full document text and a precomputed line-start index for O(1)
 * line-to-offset and offset-to-line conversions.  All mutations return a new
 * [EditorDocument] — the model is pure-functional (no mutable state).
 *
 * Design notes:
 * - [lineOffsets] stores the char index of the first character on each line.
 *   `lineOffsets[0]` is always 0; `lineOffsets[n]` is `lineOffsets[n-1] +
 *   lineLength(n-1) + 1` (the +1 accounts for the '\n' separator).
 * - The class is intentionally dependency-free (no Compose imports) so it can
 *   be tested in pure Kotlin.
 */
class EditorDocument(val text: String) {

    /** Number of lines in the document (always ≥ 1). */
    val lineCount: Int

    /**
     * Character offset of the first character on each line.
     * `lineOffsets[i]` is the index within [text] where line `i` starts.
     */
    val lineOffsets: IntArray

    init {
        // Pre-scan the text to build the line-start index.
        // A document with no '\n' has exactly one line.
        val offsets = mutableListOf(0)
        var i = 0
        while (i < text.length) {
            if (text[i] == '\n') offsets.add(i + 1)
            i++
        }
        lineOffsets = offsets.toIntArray()
        lineCount = lineOffsets.size
    }

    // ── Line accessors ────────────────────────────────────────────

    /**
     * Returns the text of line [lineIndex] (0-based), WITHOUT the trailing '\n'.
     * Throws [IndexOutOfBoundsException] if [lineIndex] is out of range.
     */
    fun lineAt(lineIndex: Int): String {
        require(lineIndex in 0 until lineCount) {
            "lineIndex $lineIndex out of bounds (lineCount=$lineCount)"
        }
        val start = lineOffsets[lineIndex]
        val end = if (lineIndex + 1 < lineCount) lineOffsets[lineIndex + 1] - 1 else text.length
        return text.substring(start, end)
    }

    /**
     * Returns the character offset (index into [text]) of the first character
     * on line [lineIndex] (0-based).
     *
     * Equivalent to `lineOffsets[lineIndex]` but bounds-checked.
     */
    fun offsetOfLine(lineIndex: Int): Int {
        require(lineIndex in 0 until lineCount) {
            "lineIndex $lineIndex out of bounds (lineCount=$lineCount)"
        }
        return lineOffsets[lineIndex]
    }

    /**
     * Returns the 0-based line index for a given character offset.
     * Uses binary search on [lineOffsets] for O(log n) performance.
     *
     * If [offset] is beyond the last character the last line index is returned.
     */
    fun lineForOffset(offset: Int): Int {
        val clamped = offset.coerceIn(0, text.length)
        // Binary search: find the largest index i where lineOffsets[i] <= clamped
        var lo = 0
        var hi = lineCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (lineOffsets[mid] <= clamped) lo = mid else hi = mid - 1
        }
        return lo
    }

    // ── Mutation helpers ──────────────────────────────────────────

    /**
     * Returns a new [EditorDocument] with characters in [startOffset]..[endOffset)
     * replaced by [replacement].
     */
    fun replaceRange(startOffset: Int, endOffset: Int, replacement: String): EditorDocument {
        val newText = text.substring(0, startOffset.coerceIn(0, text.length)) +
            replacement +
            text.substring(endOffset.coerceIn(0, text.length))
        return EditorDocument(newText)
    }

    /**
     * Returns a new [EditorDocument] with line [lineIndex] replaced by [newLineText].
     * The trailing newline of the line is preserved; only the line body changes.
     */
    fun replaceLine(lineIndex: Int, newLineText: String): EditorDocument {
        val start = offsetOfLine(lineIndex)
        val end = if (lineIndex + 1 < lineCount) lineOffsets[lineIndex + 1] - 1 else text.length
        return replaceRange(start, end, newLineText)
    }

    /**
     * Returns a new [EditorDocument] with a blank line inserted after [afterLineIndex].
     * If [afterLineIndex] == lineCount - 1 a '\n' is appended at the end.
     */
    fun insertLineAfter(afterLineIndex: Int, lineText: String = ""): EditorDocument {
        val insertOffset = if (afterLineIndex + 1 < lineCount) {
            lineOffsets[afterLineIndex + 1]
        } else {
            text.length
        }
        val newText = text.substring(0, insertOffset) + lineText + "\n" +
            text.substring(insertOffset)
        return EditorDocument(newText)
    }

    /**
     * Returns a new [EditorDocument] with line [lineIndex] deleted.
     * Merges the line's content into the previous line (Backspace-at-col-0 semantics):
     * the '\n' before [lineIndex] is removed so the two logical lines join.
     *
     * Throws when [lineIndex] == 0 (no previous line to merge into).
     */
    fun mergeLineWithPrevious(lineIndex: Int): EditorDocument {
        require(lineIndex > 0) { "Cannot merge line 0 with a previous line" }
        // The '\n' that terminates line [lineIndex - 1] is at offset
        // lineOffsets[lineIndex] - 1.  Remove it.
        val newlineOffset = lineOffsets[lineIndex] - 1
        val newText = text.substring(0, newlineOffset) + text.substring(newlineOffset + 1)
        return EditorDocument(newText)
    }

    // ── View helpers ──────────────────────────────────────────────

    /**
     * Returns the text covering lines [fromLine]..[toLine] (inclusive, 0-based).
     * Used to populate the viewport [BasicTextField] with a window of lines.
     */
    fun linesText(fromLine: Int, toLine: Int): String {
        val start = lineOffsets[fromLine.coerceIn(0, lineCount - 1)]
        val endLine = toLine.coerceIn(0, lineCount - 1)
        val end = if (endLine + 1 < lineCount) lineOffsets[endLine + 1] - 1 else text.length
        return text.substring(start, end)
    }

    override fun equals(other: Any?): Boolean =
        other is EditorDocument && text == other.text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String =
        "EditorDocument(lineCount=$lineCount, chars=${text.length})"
}
