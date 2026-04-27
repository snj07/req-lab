package com.reqlab.editor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.TimeSource

/**
 * Tests for [DisplayLineMap] in-place reset optimisation (performance issue MN-1).
 *
 * TDD contract:
 *   - Tests in the "FAILING BEFORE FIX" section will NOT COMPILE until the
 *     `capacity` property is added to [DisplayLineMap].
 *   - Tests in the "REGRESSION GUARD" section verify that existing correctness
 *     is preserved after the optimisation is applied.
 *
 * Issue MN-1 (performance-test-report.md):
 *   DisplayLineMap.reset() always allocates two new IntArrays on every keystroke,
 *   adding ~40 KB of short-lived garbage per keypress (200 KB/s at 5 keystrokes/s).
 *   Fix: track a logical line count separately from physical array capacity so that
 *   arrays can be reused (in-place fill) when the new count <= current capacity.
 */
class DisplayLineMapCapacityTest {

    // ═══════════════════════════════════════════════════════════════════════
    // MN-1 — DisplayLineMap.capacity: fails before fix
    //
    // BEFORE FIX: 'capacity' property does not exist → compile error.
    // AFTER FIX:  property is accessible and tracks current array capacity.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * MN-1-A: DisplayLineMap must expose a 'capacity' property reflecting the
     * current physical array size (>= the logical line count).
     *
     * BEFORE FIX → unresolved reference 'capacity' → compile error.
     * AFTER FIX  → property accessible; capacity >= docLineCount.
     */
    @Test
    fun mn1_a_capacity_property_exists_and_is_at_least_the_initial_linecount() {
        val map = DisplayLineMap(100)
        // BEFORE FIX: this line does not compile.
        assertTrue(map.capacity >= 100,
            "capacity must be >= the initial docLineCount (100), got ${map.capacity}")
    }

    /**
     * MN-1-B: After reset() with a SMALLER line count, the physical capacity must
     * NOT shrink — the array is reused in-place.
     *
     * BEFORE FIX → compile error on 'capacity'.
     * AFTER FIX  → capacity unchanged; arrays reused without reallocation.
     */
    @Test
    fun mn1_b_reset_with_smaller_linecount_does_not_shrink_capacity() {
        val map = DisplayLineMap(1_000)
        val capacityAfterInit = map.capacity
        assertTrue(capacityAfterInit >= 1_000, "Sanity: initial capacity >= 1 000")

        map.reset(500)   // shrink logical size; physical array should be reused

        // BEFORE FIX: compile error.
        // AFTER FIX: capacity must be the same or larger (not reallocated).
        assertTrue(map.capacity >= capacityAfterInit,
            "capacity after reset(500) must be >= capacity before reset, " +
            "got ${map.capacity} (expected >= $capacityAfterInit)")
    }

    /**
     * MN-1-C: After reset() with an EQUAL line count, capacity is unchanged.
     *
     * BEFORE FIX → compile error.
     * AFTER FIX  → capacity identical.
     */
    @Test
    fun mn1_c_reset_with_equal_linecount_keeps_capacity_identical() {
        val map = DisplayLineMap(200)
        val capBefore = map.capacity

        map.reset(200)

        assertEquals(capBefore, map.capacity,
            "reset(200) on a map created with 200 lines must not change capacity")
    }

    /**
     * MN-1-D: After reset() with a LARGER line count, capacity must grow to
     * accommodate the new logical size.
     *
     * BEFORE FIX → compile error.
     * AFTER FIX  → capacity >= new docLineCount.
     */
    @Test
    fun mn1_d_reset_with_larger_linecount_grows_capacity() {
        val map = DisplayLineMap(100)
        map.reset(500)   // grow beyond initial capacity

        assertTrue(map.capacity >= 500,
            "capacity after reset(500) must be >= 500, got ${map.capacity}")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MN-1 — Correctness regression guards
    //
    // These tests verify that the in-place optimisation does not break
    // any existing behaviour.  They pass BOTH before and after the fix.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * After reset() to a SMALLER size, totalDisplayLines must equal the new size.
     */
    @Test
    fun mn1_r1_reset_to_smaller_size_correct_totalDisplayLines() {
        val map = DisplayLineMap(1_000)
        map.reset(400)
        assertEquals(400, map.totalDisplayLines,
            "totalDisplayLines after reset(400) must be 400 (all lines visible)")
    }

    /**
     * After reset() to a LARGER size, totalDisplayLines must equal the new size.
     */
    @Test
    fun mn1_r2_reset_to_larger_size_correct_totalDisplayLines() {
        val map = DisplayLineMap(100)
        map.reset(800)
        assertEquals(800, map.totalDisplayLines,
            "totalDisplayLines after reset(800) must be 800")
    }

    /**
     * After reset() with an ACTIVE fold, the map is fully visible (fold is cleared).
     */
    @Test
    fun mn1_r3_reset_clears_all_existing_folds() {
        val map = DisplayLineMap(10)
        map.setFolded(2, 7)   // fold lines 3–7
        assertTrue(map.totalDisplayLines < 10, "Fold should reduce display lines")

        map.reset(10)

        assertEquals(10, map.totalDisplayLines,
            "reset() must clear all folds; totalDisplayLines must be 10 after reset(10)")
    }

    /**
     * docFromDisplay and displayFromDoc must be consistent after reset() to smaller size.
     */
    @Test
    fun mn1_r4_mappings_consistent_after_reset_to_smaller_size() {
        val map = DisplayLineMap(200)
        map.reset(50)

        // Every doc line 0..49 should map to itself (no folds)
        for (line in 0 until 50) {
            assertEquals(line, map.displayFromDoc(line),
                "displayFromDoc($line) must be $line after reset(50) with no folds")
            assertEquals(line, map.docFromDisplay(line),
                "docFromDisplay($line) must be $line after reset(50) with no folds")
        }
    }

    /**
     * After reset() to smaller size followed by setFolded, folding must work correctly.
     */
    @Test
    fun mn1_r5_folding_works_correctly_after_reset_to_smaller_size() {
        val map = DisplayLineMap(1_000)
        map.reset(10)   // shrink logical size in-place
        map.setFolded(0, 5)  // fold lines 1–5 under line 0

        assertEquals(5, map.totalDisplayLines,
            "After reset(10) + setFolded(0,5): 10 - 5 = 5 visible display lines")
        assertFalse(map.isVisible(3), "Line 3 must be hidden after setFolded(0, 5)")
        assertTrue(map.isVisible(6), "Line 6 must be visible")
    }

    /**
     * After reset() to smaller size, isVisible for a line BEYOND the new logical
     * size must return false (the line does not exist).
     */
    @Test
    fun mn1_r6_isVisible_returns_false_for_line_beyond_new_logical_size() {
        val map = DisplayLineMap(500)
        map.reset(10)
        assertFalse(map.isVisible(50),
            "isVisible(50) must be false after reset(10) — line 50 is outside logical size")
    }

    /**
     * getFoldedRegions() must return empty after reset() (all lines visible, no folds).
     */
    @Test
    fun mn1_r7_getFoldedRegions_empty_after_reset() {
        val map = DisplayLineMap(100)
        map.setFolded(10, 50)
        assertTrue(map.getFoldedRegions().isNotEmpty(), "Sanity: folds exist before reset")

        map.reset(100)
        assertTrue(map.getFoldedRegions().isEmpty(),
            "No folds must exist after reset(); getFoldedRegions() must be empty")
    }

    /**
     * Repeated alternating reset(small)/reset(large) cycles must give correct
     * totalDisplayLines each time.
     */
    @Test
    fun mn1_r8_alternating_reset_cycles_are_correct() {
        val map = DisplayLineMap(500)
        for (i in 1..10) {
            val n = if (i % 2 == 0) 1_000 else 50
            map.reset(n)
            assertEquals(n, map.totalDisplayLines,
                "Cycle $i: totalDisplayLines after reset($n) must be $n")
        }
    }

    /**
     * prefixSums must be accurate (docFromDisplay binary-search result is correct)
     * after in-place reset to a SMALLER size.
     */
    @Test
    fun mn1_r9_docFromDisplay_accurate_after_in_place_reset() {
        val map = DisplayLineMap(1_000)
        map.reset(5)   // in-place

        // With 5 lines all visible: display line K → doc line K
        for (d in 0 until 5) {
            assertEquals(d, map.docFromDisplay(d),
                "docFromDisplay($d) must be $d after reset(5)")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MN-1 — Performance assertion
    //
    // Verifies that the reset optimisation actually avoids allocation for the
    // "same or smaller size" case.  This is a timing / throughput test.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 10 000 repeated in-place resets (same docLineCount) must complete in < 500 ms.
     * With the old code (always allocates ~40 KB per reset), the GC pressure would
     * be ~400 MB total; on some GC configurations this would cause stalling.
     * After the fix, total allocation is near zero.
     */
    @Test
    fun mn1_perf_10K_inplace_resets_complete_within_500ms() {
        val map = DisplayLineMap(5_013)   // same as 10mb-sample.json line count
        val mark = TimeSource.Monotonic.markNow()
        repeat(10_000) { map.reset(5_013) }
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        println("[PERF] 10 000 × DisplayLineMap.reset(5013): $elapsed ms")
        assertTrue(elapsed < 500,
            "10 000 in-place resets of a 5013-line map must complete in < 500 ms, " +
            "took $elapsed ms. Indicates allocation still happening on each reset.")
    }
}
