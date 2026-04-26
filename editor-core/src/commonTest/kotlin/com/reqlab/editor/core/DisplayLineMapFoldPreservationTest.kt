package com.reqlab.editor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [DisplayLineMap.getFoldedRegions] (new method — tests FAIL before fix)
 * and for fold-state preservation across reset/applyFolds cycles.
 *
 * C-2 root: DisplayLineMap has no way to export the current fold state, so
 *           EditorViewModel cannot re-apply it after calling reset().
 */
class DisplayLineMapFoldPreservationTest {

    // ── getFoldedRegions (NEW METHOD — fails before fix) ─────────────────────

    @Test
    fun getFoldedRegions_no_folds_returns_empty() {
        val map = DisplayLineMap(5)
        // Before fix: getFoldedRegions() doesn't exist → compile error / UnsupportedOperationException
        val regions = map.getFoldedRegions()
        assertTrue(regions.isEmpty(), "A fresh map with no folds must return empty list")
    }

    @Test
    fun getFoldedRegions_after_setFolded_returns_that_region() {
        val map = DisplayLineMap(5)  // lines 0..4
        map.setFolded(0, 3)          // fold: line 0 visible, lines 1-3 hidden
        val regions = map.getFoldedRegions()
        assertEquals(1, regions.size, "Should detect one fold region")
        val (start, end) = regions[0]
        assertEquals(0, start, "Fold start must be the visible anchor line (0)")
        assertEquals(3, end,   "Fold end must be the last hidden line (3)")
    }

    @Test
    fun getFoldedRegions_multiple_folds_returns_all() {
        val map = DisplayLineMap(10)  // lines 0..9
        map.setFolded(0, 2)           // fold A: lines 1-2 hidden
        map.setFolded(5, 8)           // fold B: lines 6-8 hidden
        val regions = map.getFoldedRegions()
        assertEquals(2, regions.size, "Should detect both fold regions")
        assertTrue(regions.any { it.first == 0 && it.second == 2 }, "Fold A must be detected")
        assertTrue(regions.any { it.first == 5 && it.second == 8 }, "Fold B must be detected")
    }

    @Test
    fun getFoldedRegions_after_setVisible_returns_empty() {
        val map = DisplayLineMap(5)
        map.setFolded(0, 4)
        map.setVisible(0, 4)
        val regions = map.getFoldedRegions()
        assertTrue(regions.isEmpty(), "Unfolded map must have no regions")
    }

    @Test
    fun getFoldedRegions_after_applyFolds_matches_input() {
        val map = DisplayLineMap(8)
        val input = listOf(Pair(0, 3), Pair(5, 7))
        map.applyFolds(input)
        val regions = map.getFoldedRegions()
        assertEquals(2, regions.size)
        assertTrue(regions.any { it.first == 0 && it.second == 3 })
        assertTrue(regions.any { it.first == 5 && it.second == 7 })
    }

    @Test
    fun re_applying_snapshot_restores_identical_state() {
        val map = DisplayLineMap(6)
        map.setFolded(1, 4)

        val snapshot = map.getFoldedRegions()
        // Simulate what reset() does
        map.reset(6)
        // Re-apply snapshot
        map.applyFolds(snapshot)

        val restored = map.getFoldedRegions()
        assertEquals(snapshot, restored, "Restored fold state must equal the original snapshot")
    }

    // ── Display height after snapshot-restore ──────────────────────────────

    @Test
    fun display_lines_preserved_after_snapshot_restore() {
        val map = DisplayLineMap(6)
        // Fold lines 1..4 under line 0, and keep line 5 visible
        map.setFolded(0, 4)
        val visibleBefore = map.totalDisplayLines  // should be 2 (line 0 + line 5)

        val snapshot = map.getFoldedRegions()
        map.reset(6)
        map.applyFolds(snapshot)

        assertEquals(visibleBefore, map.totalDisplayLines,
            "Total display lines must be the same after snapshot-restore")
    }
}
