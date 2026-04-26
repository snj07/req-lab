package com.reqlab.editor.core

/**
 * Parallel byte array storing one [TokenType] per character in the document.
 *
 * Mirrors Scintilla's CellBuffer style array + endStyled high-water mark:
 *  - [endStyled] is the char offset up to which styling is valid.
 *  - On any edit at position P call [invalidateFrom](P) → O(1).
 *  - The IdleLexer calls [applyStyle] from a background coroutine.
 *  - The UI reads [styleAt] / [nextStyleChangeAfter] only for visible lines.
 *
 * Thread-safety contract (no explicit locks):
 *  - [invalidateFrom] is called from the UI thread.
 *  - [applyStyle] / [grow] are called from a background coroutine (Dispatchers.Default).
 *  - A StateFlow update in [EditorViewModel] acts as the memory barrier: the
 *    background coroutine updates [styleClock] via the StateFlow, so the UI thread's
 *    next StateFlow read sees all preceding style writes (JVM happens-before chain).
 *  - Benign races on individual style bytes cause at most a one-frame stale colour,
 *    which is imperceptible and self-corrects on the next style update.
 */
class StyleBuffer(initialCapacity: Int) {

    // One byte per logical char position (TokenType ordinal).
    private var styles = ByteArray(maxOf(initialCapacity, 64)) { TokenType.PLAIN.ordinal.toByte() }

    /**
     * High-water mark: all positions < [endStyled] have valid styling.
     * Monotonically increases during lexing; reset to 0 (or edit point) on invalidation.
     * Written by both threads, but only via [invalidateFrom] (UI) or [applyStyle] (bg).
     */
    var endStyled: Int = 0
        private set

    /**
     * Incremented each time styles change.  The UI uses this as a [remember] key in
     * [LineView] to decide when to rebuild the per-line [AnnotatedString].
     * Written ONLY by the IdleLexer (background), read by UI after StateFlow emission.
     */
    var styleClock: Long = 0L
        private set

    // ── UI thread ─────────────────────────────────────────────────

    /**
     * O(1): invalidate styles from [pos] onward.  Called by EditorViewModel after
     * each document edit.
     */
    fun invalidateFrom(pos: Int) {
        endStyled = minOf(endStyled, pos.coerceAtLeast(0))
    }

    // ── Background thread (IdleLexer) ─────────────────────────────

    /**
     * Apply [type] to all positions in [from, to).  Extend [endStyled] and bump
     * [styleClock].  Called from the IdleLexer on a background coroutine.
     */
    fun applyStyle(from: Int, to: Int, type: TokenType) {
        val f = from.coerceAtLeast(0)
        val t = to.coerceAtMost(styles.size)
        if (f >= t) return
        val b = type.ordinal.toByte()
        for (i in f until t) styles[i] = b
        if (to > endStyled) endStyled = to
        styleClock++
    }

    /**
     * Grow the styles array to [newSize] (when the document grows).  Fills new
     * positions with [TokenType.PLAIN].  Called from background thread.
     */
    fun grow(newSize: Int) {
        if (newSize <= styles.size) return
        val old = styles
        styles = ByteArray(maxOf(newSize, styles.size * 2)) { i ->
            if (i < old.size) old[i] else TokenType.PLAIN.ordinal.toByte()
        }
    }

    /**
     * Shrink (or reset) the styles array after a large delete.  Called from background.
     */
    fun shrink(newSize: Int) {
        if (newSize >= styles.size) return
        styles = styles.copyOf(maxOf(newSize, 64))
        endStyled = minOf(endStyled, newSize)
    }

    // ── UI thread reads for visible lines ─────────────────────────

    /** O(1): return the [TokenType] at logical position [pos]. */
    fun styleAt(pos: Int): TokenType {
        if (pos < 0 || pos >= styles.size) return TokenType.PLAIN
        return TokenType.entries.getOrElse(styles[pos].toInt() and 0xFF) { TokenType.PLAIN }
    }

    /**
     * O(run-length): return the first position > [from] where the style changes,
     * stopping at [limit] (exclusive).  Used for run-length encoding per-line spans.
     */
    fun nextStyleChangeAfter(from: Int, limit: Int): Int {
        if (from < 0 || from >= styles.size) return limit
        val current = styles[from]
        var i = from + 1
        val end = minOf(limit, styles.size)
        while (i < end && styles[i] == current) i++
        return i
    }
}
