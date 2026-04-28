package com.reqlab.ui.shared.components

import com.reqlab.core.model.BodyType
import com.reqlab.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Failing tests for issues M-5 and U-1 from qa-pre-release-report.md.
 *
 * These tests FAIL before the fixes are applied, and PASS after.
 *
 * M-5 — shouldPauseValidation() is a package-level helper extracted from
 *        BodyEditor.kt so it can be tested in isolation.
 *        BEFORE FIX: the function does not exist → compile error (= failing test).
 *        AFTER FIX:  the function is present and returns the correct boolean.
 *
 * U-1 — New tab default body type must be NONE (not JSON).
 *        BEFORE FIX: bodyType = BodyType.JSON → assertEquals(NONE, ...) fails.
 *        AFTER FIX:  bodyType = BodyType.NONE → passes.
 */

// ═══════════════════════════════════════════════════════════════════════
// M-5 — shouldPauseValidation helper (compile-error before fix)
// ═══════════════════════════════════════════════════════════════════════

class ValidationPauseIndicatorTest {

    @Test
    fun shouldPauseValidation_returns_false_for_small_content() {
        assertFalse(shouldPauseValidation(0),         "Empty content must not pause")
        assertFalse(shouldPauseValidation(19_999_999), "Content just under threshold must not pause")
        assertFalse(shouldPauseValidation(20_000_000), "Content at exact threshold must not pause (exclusive)")
    }

    @Test
    fun shouldPauseValidation_returns_true_above_threshold() {
        assertTrue(shouldPauseValidation(20_000_001), "Content one byte over 20 MB must pause validation")
        assertTrue(shouldPauseValidation(25_000_000), "25 MB content must pause validation")
    }

    @Test
    fun shouldPauseValidation_boundary_is_strictly_greater_than_20_000_000() {
        assertFalse(shouldPauseValidation(20_000_000), "Exactly 20 000 000 chars: must NOT pause")
        assertTrue(shouldPauseValidation(20_000_001),  "Exactly 20 000 001 chars: MUST pause")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// U-1 — Default body type must be NONE (runtime failure before fix)
// ═══════════════════════════════════════════════════════════════════════

class DefaultBodyTypeFixTest {

    /**
     * BEFORE FIX: bodyType defaults to BodyType.JSON → assertEquals(NONE) fails.
     * AFTER FIX:  bodyType defaults to BodyType.NONE → passes.
     */
    @Test
    fun new_tab_defaults_to_none_body_type() {
        val tab = RequestTabState()
        assertEquals(BodyType.NONE, tab.bodyType,
            "New tab must default to BodyType.NONE, not BodyType.JSON. Got: ${tab.bodyType}")
    }

    @Test
    fun new_tab_body_type_is_not_json_by_default() {
        val tab = RequestTabState()
        assertNotEquals(BodyType.JSON, tab.bodyType,
            "A new tab must NOT default to JSON body type")
    }

    @Test
    fun switching_to_json_body_type_updates_content_type_header() {
        // Regression guard: after the fix, explicitly selecting JSON must still wire up the header.
        val tab = RequestTabState()
        tab.bodyType = BodyType.JSON
        tab.syncSystemHeaders()
        val ct = tab.headers.find { it.key.equals("Content-Type", ignoreCase = true) }
        assertEquals("application/json", ct?.value,
            "Switching to JSON body type must set Content-Type: application/json")
    }
}
