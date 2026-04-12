package com.reqlab.editor.core

data class FoldingModel(
    val regions: List<FoldRegion> = emptyList(),
    val collapsedLines: Set<Int> = emptySet(),
) {
    val hasFolds: Boolean get() = collapsedLines.isNotEmpty()

    fun toggleFold(startLine: Int): FoldingModel {
        val region = regions.find { it.startLine == startLine } ?: return this
        return if (startLine in collapsedLines)
            copy(collapsedLines = collapsedLines - startLine)
        else copy(collapsedLines = collapsedLines + region.startLine)
    }

    fun fold(startLine: Int): FoldingModel {
        regions.find { it.startLine == startLine } ?: return this
        return copy(collapsedLines = collapsedLines + startLine)
    }

    fun unfold(startLine: Int): FoldingModel =
        copy(collapsedLines = collapsedLines - startLine)

    fun foldAll(): FoldingModel =
        copy(collapsedLines = regions.map { it.startLine }.toSet())

    fun unfoldAll(): FoldingModel = copy(collapsedLines = emptySet())

    fun isLineHidden(line: Int): Boolean {
        for (sl in collapsedLines) {
            val r = regions.find { it.startLine == sl } ?: continue
            if (line > r.startLine && line <= r.endLine) return true
        }
        return false
    }

    fun isFoldStart(line: Int): Boolean = regions.any { it.startLine == line }
    fun isCollapsed(line: Int): Boolean = line in collapsedLines
    fun getRegion(startLine: Int): FoldRegion? = regions.find { it.startLine == startLine }

    fun visibleLines(totalLines: Int): List<Int> {
        if (collapsedLines.isEmpty()) return (1..totalLines).toList()
        return (1..totalLines).filter { !isLineHidden(it) }
    }

    fun updateRegions(newRegions: List<FoldRegion>): FoldingModel {
        val validStarts = newRegions.map { it.startLine }.toSet()
        return copy(
            regions = newRegions,
            collapsedLines = collapsedLines.filter { it in validStarts }.toSet()
        )
    }
}
