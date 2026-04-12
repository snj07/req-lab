package com.reqlab.editor.core

/**
 * Line-start offset index for a [GapBuffer] document.
 *
 * Stores the char offset of the first character on each line (0-based).
 * [lineStarts[0]] is always 0.  Lines are 0-based throughout.
 *
 * The "lazy step" optimisation (Scintilla's Partitioning technique): after a
 * single-char insert that adds no newlines, instead of shifting O(n) line-start
 * entries we record a pending addend ([stepAfterLine], [stepLength]) and apply it
 * lazily on the next [rebuild] or structural mutation.  For sequential typing this
 * makes single-char inserts O(1).
 *
 * Thread-safety: NOT thread-safe.  Use via [DocumentModel] which serialises access.
 */
class LineIndex {

    private var lineStarts = IntArray(1024)
    private var count = 0

    // ── Lazy step ─────────────────────────────────────────────────
    // All entries at index > stepAfterLine have an implicit +stepLength addend.
    private var stepAfterLine = Int.MAX_VALUE
    private var stepLength = 0

    val lineCount: Int get() = count

    // ── Queries ───────────────────────────────────────────────────

    /** O(1): return the char offset of the start of 0-based [line]. */
    fun lineStart(line: Int): Int {
        if (line <= 0) return 0
        if (line >= count) return Int.MAX_VALUE
        val raw = lineStarts[line]
        return if (line > stepAfterLine) raw + stepLength else raw
    }

    /**
     * O(log n): binary-search for the 0-based line number that contains [offset].
     */
    fun lineAt(offset: Int): Int {
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (effectiveStart(mid) <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    // ── Mutations ─────────────────────────────────────────────────

    /**
     * O(n): full rebuild from [buf].  Called on initial load and large pastes.
     * Always call from a background coroutine for large documents.
     */
    fun rebuild(buf: GapBuffer) {
        stepAfterLine = Int.MAX_VALUE
        stepLength = 0
        count = 0
        ensureCapacity(64)
        addEntry(0)
        var i = 0
        while (i < buf.length) {
            if (buf.charAt(i) == '\n') addEntry(i + 1)
            i++
        }
    }

    /**
     * O(newlineCount): update after inserting [text] at char offset [at].
     * For single non-newline chars this is O(1) via the lazy step.
     * For multi-line inserts it flushes the step and inserts new entries.
     * Prefer [rebuild] after very large pastes (called on background thread).
     */
    fun onInsert(at: Int, text: String) {
        if (text.isEmpty()) return
        val newlineCount = text.count { it == '\n' }
        val insertLine = lineAt(at)

        if (newlineCount == 0) {
            // Fast path: just extend the lazy step
            if (insertLine >= stepAfterLine && stepLength > 0) {
                // Extending a step that already covers this region
                if (insertLine == stepAfterLine) {
                    stepLength += text.length
                } else {
                    flushStep()
                    stepAfterLine = insertLine
                    stepLength = text.length
                }
            } else {
                flushStep()
                stepAfterLine = insertLine
                stepLength = text.length
            }
            return
        }

        // Multi-line insert: flush first, then insert new entries and re-shift
        flushStep()

        // Collect positions of each newline in the inserted text relative to 'at'
        val newEntries = IntArray(newlineCount)
        var ni = 0
        var relPos = 0
        for (ch in text) {
            if (ch == '\n') newEntries[ni++] = at + relPos + 1
            relPos++
        }

        // Shift all existing entries after the insert point by text.length
        val shift = text.length
        for (i in (insertLine + 1) until count) lineStarts[i] += shift

        // Insert new line entries after insertLine
        val insertAt = insertLine + 1
        val needed = count + newlineCount
        ensureCapacity(needed)
        // Make room
        lineStarts.copyInto(lineStarts, insertAt + newlineCount, insertAt, count)
        for (i in newEntries.indices) lineStarts[insertAt + i] = newEntries[i]
        count = needed
    }

    /**
     * O(deletedNewlines + remaining lines): update after deleting range [from, to).
     * For large deletions prefer [rebuild] on a background thread.
     */
    fun onDelete(from: Int, to: Int) {
        if (from >= to) return
        flushStep()

        val deletedCount = to - from
        val firstDeletedLine = lineAt(from) + 1  // lines whose starts fall in [from, to)
        var firstAfterDeleted = firstDeletedLine
        while (firstAfterDeleted < count && lineStarts[firstAfterDeleted] <= to) {
            firstAfterDeleted++
        }
        val removedEntries = firstAfterDeleted - firstDeletedLine

        // Remove entries for deleted lines
        if (removedEntries > 0) {
            lineStarts.copyInto(lineStarts, firstDeletedLine, firstAfterDeleted, count)
            count -= removedEntries
        }

        // Shift remaining entries after the delete point
        for (i in firstDeletedLine until count) lineStarts[i] -= deletedCount
    }

    // ── Private ───────────────────────────────────────────────────

    private fun effectiveStart(line: Int): Int {
        val raw = lineStarts[line]
        return if (line > stepAfterLine) raw + stepLength else raw
    }

    private fun flushStep() {
        if (stepAfterLine == Int.MAX_VALUE || stepLength == 0) {
            stepAfterLine = Int.MAX_VALUE; stepLength = 0; return
        }
        val limit = minOf(stepAfterLine + 1, count - 1)
        for (i in (stepAfterLine + 1) until count) lineStarts[i] += stepLength
        stepAfterLine = Int.MAX_VALUE
        stepLength = 0
    }

    private fun addEntry(offset: Int) {
        ensureCapacity(count + 1)
        lineStarts[count++] = offset
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= lineStarts.size) return
        lineStarts = lineStarts.copyOf(maxOf(needed, lineStarts.size * 2))
    }
}
