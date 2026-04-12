package com.reqlab.editor.core

/**
 * Gap buffer — O(1) amortized text insert/delete near the current edit position.
 *
 * Text is stored in a single CharArray [buf] with a "gap" (empty region) positioned
 * at the most recent edit point.  Inserting into the gap fills characters; deleting
 * widens the gap over the deleted range.  Moving the gap to a new position costs
 * O(distance) but is amortized O(1) for sequential edits from the same position
 * (e.g. typing character-by-character).
 *
 * Inspired by Scintilla's SplitVector / CellBuffer design.
 * Thread-safety: NOT thread-safe.  Callers must ensure single-thread access or use
 * an external Mutex (see DocumentModel).
 */
class GapBuffer(initialCapacity: Int = 8192) {

    private var buf = CharArray(maxOf(initialCapacity, 32))
    private var gapStart = 0          // first index of the gap (inclusive)
    private var gapEnd = buf.size     // first index after the gap (exclusive)

    /** Logical character count (excludes the gap). */
    val length: Int get() = buf.size - (gapEnd - gapStart)

    // ── Read ──────────────────────────────────────────────────────

    /** O(1): return the character at logical [index]. */
    fun charAt(index: Int): Char {
        val g = gapEnd - gapStart
        return if (index < gapStart) buf[index] else buf[index + g]
    }

    /** O(end-start): extract logical substring [from, to) as a String. */
    fun subSequence(from: Int, to: Int): String {
        val f = from.coerceIn(0, length)
        val t = to.coerceIn(f, length)
        if (f == t) return ""
        val g = gapEnd - gapStart
        val sb = StringBuilder(t - f)
        for (i in f until t) sb.append(if (i < gapStart) buf[i] else buf[i + g])
        return sb.toString()
    }

    /** O(n): return full document text. Used for validation / snapshot. */
    fun toFullString(): String = subSequence(0, length)

    /**
     * O(length - from): return the index of the first occurrence of [ch]
     * starting at logical position [from], or -1 if not found.
     */
    fun indexOfFrom(ch: Char, from: Int): Int {
        val g = gapEnd - gapStart
        for (i in from until length) {
            if ((if (i < gapStart) buf[i] else buf[i + g]) == ch) return i
        }
        return -1
    }

    // ── Write ─────────────────────────────────────────────────────

    /**
     * O(1) amortized (O(|at - gapStart|) gap move + O(text.length) copy).
     * Inserts [text] at logical position [at].
     */
    fun insert(at: Int, text: String) {
        if (text.isEmpty()) return
        val pos = at.coerceIn(0, length)
        ensureGap(pos, text.length)
        // Gap is now at pos: fill it
        for (i in text.indices) buf[gapStart + i] = text[i]
        gapStart += text.length
    }

    /**
     * O(|from - gapStart|) gap move.  Deletes logical range [from, to).
     */
    fun delete(from: Int, to: Int) {
        val f = from.coerceIn(0, length)
        val t = to.coerceIn(f, length)
        if (f == t) return
        moveGapTo(f)
        gapEnd += (t - f)
    }

    // ── Private ───────────────────────────────────────────────────

    /** Move the gap so that gapStart == pos. */
    private fun moveGapTo(pos: Int) {
        if (gapStart == pos) return
        val gapSize = gapEnd - gapStart
        if (pos < gapStart) {
            // Shift text right to fill the right end of the gap
            val count = gapStart - pos
            buf.copyInto(buf, gapEnd - count, pos, gapStart)
            gapStart = pos
            gapEnd = pos + gapSize
        } else {
            // Shift text left to fill the left end of the gap
            val count = pos - gapStart
            buf.copyInto(buf, gapStart, gapEnd, gapEnd + count)
            gapStart = pos
            gapEnd = pos + gapSize
        }
    }

    /** Ensure the gap at [pos] has at least [needed] free slots; grows if necessary. */
    private fun ensureGap(pos: Int, needed: Int) {
        moveGapTo(pos)
        if (gapEnd - gapStart >= needed) return
        // Grow: at least double, always accommodate needed
        val newCap = maxOf(buf.size * 2, buf.size + needed + 32)
        val newBuf = CharArray(newCap)
        buf.copyInto(newBuf, 0, 0, gapStart)
        val newGapEnd = newCap - (buf.size - gapEnd)
        buf.copyInto(newBuf, newGapEnd, gapEnd, buf.size)
        buf = newBuf
        gapEnd = newGapEnd
    }
}
