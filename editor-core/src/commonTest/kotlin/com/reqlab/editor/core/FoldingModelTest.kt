package com.reqlab.editor.core

import kotlin.test.*

class FoldingModelTest {

    private val regions = listOf(
        FoldRegion(1, 5, "..."),
        FoldRegion(7, 10, "..."),
    )

    @Test
    fun emptyFoldingModel() {
        val model = FoldingModel()
        assertFalse(model.hasFolds)
        assertEquals(emptyList(), model.regions)
    }

    @Test
    fun toggleFoldCollapse() {
        val model = FoldingModel(regions = regions)
        val folded = model.toggleFold(1)
        assertTrue(folded.isCollapsed(1))
        assertFalse(folded.isCollapsed(7))
    }

    @Test
    fun toggleFoldExpand() {
        val model = FoldingModel(regions = regions, collapsedLines = setOf(1))
        val expanded = model.toggleFold(1)
        assertFalse(expanded.isCollapsed(1))
    }

    @Test
    fun toggleFoldInvalidLineNoOp() {
        val model = FoldingModel(regions = regions)
        val same = model.toggleFold(99)
        assertFalse(same.hasFolds)
    }

    @Test
    fun fold() {
        val model = FoldingModel(regions = regions)
        val folded = model.fold(1)
        assertTrue(folded.isCollapsed(1))
    }

    @Test
    fun unfold() {
        val model = FoldingModel(regions = regions, collapsedLines = setOf(1))
        val unfolded = model.unfold(1)
        assertFalse(unfolded.isCollapsed(1))
    }

    @Test
    fun foldAll() {
        val model = FoldingModel(regions = regions)
        val allFolded = model.foldAll()
        assertTrue(allFolded.isCollapsed(1))
        assertTrue(allFolded.isCollapsed(7))
    }

    @Test
    fun unfoldAll() {
        val model = FoldingModel(regions = regions, collapsedLines = setOf(1, 7))
        val allUnfolded = model.unfoldAll()
        assertFalse(allUnfolded.hasFolds)
    }

    @Test
    fun isLineHidden() {
        val model = FoldingModel(regions = regions, collapsedLines = setOf(1))
        // Lines 2..5 should be hidden when region(1,5) is collapsed
        assertFalse(model.isLineHidden(1))
        assertTrue(model.isLineHidden(2))
        assertTrue(model.isLineHidden(3))
        assertTrue(model.isLineHidden(4))
        assertTrue(model.isLineHidden(5))
        assertFalse(model.isLineHidden(6))
        assertFalse(model.isLineHidden(7))
    }

    @Test
    fun visibleLines() {
        val model = FoldingModel(regions = regions, collapsedLines = setOf(1))
        val visible = model.visibleLines(10)
        // Hidden: 2,3,4,5 → visible: 1,6,7,8,9,10
        assertEquals(listOf(1, 6, 7, 8, 9, 10), visible)
    }

    @Test
    fun visibleLinesNoFolds() {
        val model = FoldingModel(regions = regions)
        val visible = model.visibleLines(5)
        assertEquals(listOf(1, 2, 3, 4, 5), visible)
    }

    @Test
    fun isFoldStart() {
        val model = FoldingModel(regions = regions)
        assertTrue(model.isFoldStart(1))
        assertTrue(model.isFoldStart(7))
        assertFalse(model.isFoldStart(3))
    }

    @Test
    fun getRegion() {
        val model = FoldingModel(regions = regions)
        assertEquals(FoldRegion(1, 5, "..."), model.getRegion(1))
        assertNull(model.getRegion(3))
    }

    @Test
    fun updateRegionsKeepsValidCollapsed() {
        val model = FoldingModel(regions = regions, collapsedLines = setOf(1, 7))
        val newRegions = listOf(FoldRegion(1, 5, "...")) // removed region at 7
        val updated = model.updateRegions(newRegions)
        assertTrue(updated.isCollapsed(1))
        assertFalse(updated.isCollapsed(7)) // removed
        assertEquals(1, updated.regions.size)
    }
}
