package com.reqlab.editor.core

/**
 * Document model backed by a [GapBuffer] (O(1) amortized insert) and a [LineIndex]
 * (O(log n) line lookup, O(1) lazy-step for sequential typing).
 *
 * This is the V2 replacement for the plain-String [EditorDocument].  The API is
 * intentionally similar so that callers can migrate incrementally.
 *
 * Concurrency model:
 *  - All mutations ([insert], [delete], [rebuild]) must be called from a SINGLE
 *    coroutine at a time.  [EditorViewModel] uses a Mutex to guarantee this.
 *  - Reads ([lineText], [lineStart], [lineCount], [length]) are safe from the UI
 *    thread AFTER the version StateFlow has been observed (i.e. after the memory
 *    barrier provided by StateFlow's atomic read).
 *  - [version] is incremented atomically after each mutation so Compose's
 *    `remember(version)` keys invalidate correctly.
 */
class DocumentModel(initialText: String = "") {

    val buffer = GapBuffer(maxOf(initialText.length + 256, 4096))
    val lineIndex = LineIndex()

    /** Monotonically increasing counter; incremented after every edit. */
    var version: Int = 0
        private set

    init {
        if (initialText.isNotEmpty()) {
            buffer.insert(0, initialText)
        }
        lineIndex.rebuild(buffer)
    }

    // ── Properties ────────────────────────────────────────────────

    val length: Int get() = buffer.length
    val lineCount: Int get() = lineIndex.lineCount

    // ── Line accessors (0-based lines) ────────────────────────────

    /** O(1): char offset of the start of 0-based line [line]. */
    fun lineStart(line: Int): Int = lineIndex.lineStart(line)

    /**
     * O(line length): return the text of 0-based [line] WITHOUT the trailing '\n'.
     */
    fun lineText(line: Int): String {
        val start = lineIndex.lineStart(line)
        val end = lineEnd(line)
        if (start < 0 || start >= buffer.length) return ""
        return buffer.subSequence(start, end)
    }

    /** O(1): return the char offset ONE PAST the last char of 0-based [line] (before '\n'). */
    private fun lineEnd(line: Int): Int {
        val nextStart = lineIndex.lineStart(line + 1)
        return if (nextStart == Int.MAX_VALUE) {
            buffer.length
        } else {
            // nextStart points to the char after '\n'; subtract 1 to exclude '\n'
            (nextStart - 1).coerceAtLeast(lineIndex.lineStart(line))
        }
    }

    /** O(log n): return the 0-based line number for absolute char [offset]. */
    fun lineAt(offset: Int): Int = lineIndex.lineAt(offset)

    // ── Mutations ─────────────────────────────────────────────────

    /**
     * Insert [text] at absolute char offset [at].
     * For single chars: O(1) amortized (gap buffer) + O(1) lazy step (LineIndex).
     * For large text: O(text.length) — call from background coroutine.
     */
    fun insert(at: Int, text: String) {
        if (text.isEmpty()) return
        val pos = at.coerceIn(0, buffer.length)
        buffer.insert(pos, text)
        lineIndex.onInsert(pos, text)
        version++
    }

    /**
     * Delete the range [from, to).
     * For small ranges: O(range) — acceptable on UI thread.
     * For large ranges: call from background coroutine.
     */
    fun delete(from: Int, to: Int) {
        val f = from.coerceIn(0, buffer.length)
        val t = to.coerceIn(f, buffer.length)
        if (f == t) return
        buffer.delete(f, t)
        lineIndex.onDelete(f, t)
        version++
    }

    /**
     * Replace the entire document content.  O(newText.length).
     * Call from background coroutine for large texts.
     */
    fun replaceAll(newText: String) {
        buffer.delete(0, buffer.length)
        if (newText.isNotEmpty()) buffer.insert(0, newText)
        lineIndex.rebuild(buffer)
        version++
    }

    /**
     * Rebuild the line index from the current buffer.  O(n).
     * Called after large pastes to ensure the line index is exact.
     */
    fun rebuildLineIndex() {
        lineIndex.rebuild(buffer)
        version++
    }

    // ── Full text extraction ──────────────────────────────────────

    /** O(n): extract the full document as a String. Avoid on the UI thread for large docs. */
    fun toFullString(): String = buffer.toFullString()

    /**
     * O(line length): extract the text of lines [fromLine, toLine] (0-based, inclusive)
     * joined by '\n'.  Useful for vocabulary/range extraction in the IdleLexer.
     */
    fun linesText(fromLine: Int, toLine: Int): String {
        if (fromLine > toLine || fromLine >= lineCount) return ""
        val f = fromLine.coerceAtLeast(0)
        val t = toLine.coerceAtMost(lineCount - 1)
        val sb = StringBuilder()
        for (line in f..t) {
            if (line > f) sb.append('\n')
            sb.append(lineText(line))
        }
        return sb.toString()
    }

    override fun toString(): String = "DocumentModel(${buffer.length} chars, ${lineIndex.lineCount} lines, v$version)"
}
