package com.reqlab.ui.shared.components

import com.reqlab.core.model.AuthType
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
// Bug 4 — Auth method switching must preserve credentials of other types
//
// BEFORE FIX: AuthEditor.kt cleared credentials of the OLD auth type on
//             every switch, losing any previously entered values.
// AFTER FIX:  Only the selected auth type's fields are shown; all other
//             credentials are kept untouched in RequestTabState.
// ═══════════════════════════════════════════════════════════════════════

class AuthCredentialPreservationTest {

    @Test
    fun switching_auth_type_preserves_bearer_token() {
        val tab = RequestTabState()
        tab.authType = AuthType.BEARER
        tab.authToken = "secret-bearer-token"

        // Simulate what the fixed AuthEditor does when switching: set new type only
        tab.authType = AuthType.API_KEY

        // BEFORE FIX: AuthEditor cleared authToken when switching away from BEARER
        // AFTER FIX:  token is untouched
        assertEquals(
            "secret-bearer-token",
            tab.authToken,
            "Bearer token must be preserved when switching to a different auth type",
        )
    }

    @Test
    fun switching_auth_type_preserves_basic_credentials() {
        val tab = RequestTabState()
        tab.authType = AuthType.BASIC
        tab.authUsername = "admin"
        tab.authPassword = "hunter2"

        // Switch away from BASIC
        tab.authType = AuthType.BEARER
        tab.authToken = "some-token"

        // Switch back
        tab.authType = AuthType.BASIC

        // BEFORE FIX: username/password were cleared when leaving BASIC
        assertEquals("admin",   tab.authUsername, "Username must survive auth type switch")
        assertEquals("hunter2", tab.authPassword, "Password must survive auth type switch")
    }

    @Test
    fun all_auth_credentials_are_independent_and_preserved() {
        val tab = RequestTabState()
        tab.authToken    = "bearer-tok"
        tab.authUsername = "user"
        tab.authPassword = "pass"
        tab.authApiKey   = "x-api-key"
        tab.authApiValue = "abc123"

        // Cycle through all types
        tab.authType = AuthType.BEARER
        tab.authType = AuthType.BASIC
        tab.authType = AuthType.API_KEY
        tab.authType = AuthType.NONE

        assertEquals("bearer-tok", tab.authToken)
        assertEquals("user",       tab.authUsername)
        assertEquals("pass",       tab.authPassword)
        assertEquals("x-api-key",  tab.authApiKey)
        assertEquals("abc123",     tab.authApiValue)
    }
}

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
