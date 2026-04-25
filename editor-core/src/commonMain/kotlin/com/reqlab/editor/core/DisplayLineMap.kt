package com.reqlab.editor.core

/**
 * Display-to-document line mapping supporting code folding.
 *
 * Inspired by Scintilla's ContractionState.  Maintains a prefix-sum array over
 * per-doc-line display heights (0 = hidden/folded, 1 = visible).  This gives:
 *  - [docFromDisplay]: O(log n) binary search in prefix sums
 *  - [displayFromDoc]: O(1) prefix sum lookup
 *  - [setFolded] / [setVisible]: O(n) suffix update — acceptable since folding
 *    is rare; a segment tree optimisation can be added later if needed.
 *
 * Lines are 0-based throughout.
 */
class DisplayLineMap(docLineCount: Int) {

    // displayHeight[i] = 1 if doc line i is visible, 0 if folded/hidden
    private var displayHeight = IntArray(maxOf(docLineCount, 1)) { 1 }
    // prefixSums[i] = sum of displayHeight[0..i-1] — number of display lines BEFORE doc line i
    private var prefixSums = IntArray(maxOf(docLineCount + 1, 2))

    init {
        buildPrefixSums()
    }

    // ── Properties ────────────────────────────────────────────────

    /** Total number of display (visible) lines. */
    val totalDisplayLines: Int get() = prefixSums[displayHeight.size]

    // ── Mappings ──────────────────────────────────────────────────

    /**
     * O(log n): return the 0-based doc line corresponding to 0-based [displayLine].
     */
    fun docFromDisplay(displayLine: Int): Int {
        if (displayLine <= 0) return 0
        if (displayLine >= totalDisplayLines) return displayHeight.size - 1
        // Binary search: find the largest doc line i where prefixSums[i] <= displayLine
        var lo = 0
        var hi = displayHeight.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (prefixSums[mid] <= displayLine) lo = mid else hi = mid - 1
        }
        return lo
    }

    /**
     * O(1): return the 0-based display line for 0-based doc line [docLine],
     * or -1 if the line is hidden (folded).
     */
    fun displayFromDoc(docLine: Int): Int {
        if (docLine < 0 || docLine >= displayHeight.size) return -1
        if (displayHeight[docLine] == 0) return -1
        return prefixSums[docLine]
    }

    /** Whether doc line [docLine] is currently visible. */
    fun isVisible(docLine: Int): Boolean =
        docLine in displayHeight.indices && displayHeight[docLine] != 0

    // ── Folding mutations ─────────────────────────────────────────

    /**
     * Hide doc lines [fromDocLine+1 .. toDocLine] (i.e. fold the region whose
     * start line is [fromDocLine]).
     */
    fun setFolded(fromDocLine: Int, toDocLine: Int) {
        val f = (fromDocLine + 1).coerceAtLeast(0)
        val t = toDocLine.coerceAtMost(displayHeight.size - 1)
        if (f > t) return
        for (i in f..t) displayHeight[i] = 0
        rebuildPrefixSums(fromDocLine)
    }

    /**
     * Reveal doc lines [fromDocLine+1 .. toDocLine] (unfold a region).
     */
    fun setVisible(fromDocLine: Int, toDocLine: Int) {
        val f = (fromDocLine + 1).coerceAtLeast(0)
        val t = toDocLine.coerceAtMost(displayHeight.size - 1)
        if (f > t) return
        for (i in f..t) displayHeight[i] = 1
        rebuildPrefixSums(fromDocLine)
    }

    /**
     * Reset to a new document line count; all lines visible.
     * Called when the document changes structurally (large paste / replace-all).
     */
    fun reset(newDocLineCount: Int) {
        displayHeight = IntArray(maxOf(newDocLineCount, 1)) { 1 }
        prefixSums = IntArray(displayHeight.size + 1)
        buildPrefixSums()
    }

    /**
     * Returns all currently-folded regions as a list of (startLine, endLine) pairs (0-based).
     *
     * A "fold" is a run of one or more consecutive hidden lines immediately following a
     * visible line.  The returned pair `(start, end)` matches the contract of [setFolded] and
     * [applyFolds]: `start` is the last **visible** line before the hidden run and `end` is
     * the last **hidden** line in the run.  Calling `applyFolds(getFoldedRegions())` on a
     * fresh [DisplayLineMap] therefore restores the identical fold shape.
     */
    fun getFoldedRegions(): List<Pair<Int, Int>> {
        val regions = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < displayHeight.size) {
            // A fold starts at the visible line that is immediately followed by hidden lines.
            if (displayHeight[i] == 1 && i + 1 < displayHeight.size && displayHeight[i + 1] == 0) {
                val foldStart = i
                i++
                while (i < displayHeight.size && displayHeight[i] == 0) i++
                regions.add(Pair(foldStart, i - 1))
            } else {
                i++
            }
        }
        return regions
    }

    /**
     * Apply a fold-set from [FoldingModel] (used to sync external fold state).
     * [collapsedRegions] is a list of (startLine, endLine) pairs (0-based).
     */
    fun applyFolds(collapsedRegions: List<Pair<Int, Int>>) {
        // Reset all to visible first
        displayHeight.fill(1)
        // Apply each fold
        for ((start, end) in collapsedRegions) {
            val f = (start + 1).coerceAtLeast(0)
            val t = end.coerceAtMost(displayHeight.size - 1)
            for (i in f..t) displayHeight[i] = 0
        }
        buildPrefixSums()
    }

    // ── Private ───────────────────────────────────────────────────

    private fun buildPrefixSums() {
        prefixSums[0] = 0
        for (i in displayHeight.indices) {
            prefixSums[i + 1] = prefixSums[i] + displayHeight[i]
        }
    }

    private fun rebuildPrefixSums(fromDocLine: Int) {
        val from = fromDocLine.coerceAtLeast(0)
        for (i in from until displayHeight.size) {
            prefixSums[i + 1] = prefixSums[i] + displayHeight[i]
        }
    }
}
