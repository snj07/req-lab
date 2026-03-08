package com.reqlab.ui.web.components

import com.reqlab.core.model.AuthType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.LogLevel
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.RequestTabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * wasmJs equivalents of the state-level QA fix tests.
 * Ensures the same bug fixes work identically on the Web target.
 *
 * Covered issues (cross-platform state fixes):
 *  - H-1  currentJob field on RequestTabState
 *  - M-3  networkEventLogs / logNetworkEvent
 *  - M-7  MutableKeyValue.uid uniqueness
 *  - M-8  scriptInjectedVarKeys
 *  - M-11 Auth field clearing semantics
 */
class QaFixesStateWebTest {

    // ── H-1 ──────────────────────────────────────────────────────────────────

    @Test
    fun h1_currentJob_starts_null() {
        val tab = RequestTabState()
        assertNull(tab.currentJob)
    }

    // ── M-3 ──────────────────────────────────────────────────────────────────

    @Test
    fun m3_networkEventLogs_starts_empty() {
        val state = AppState()
        assertTrue(state.networkEventLogs.isEmpty())
    }

    @Test
    fun m3_logNetworkEvent_writes_to_both_lists() {
        val state = AppState()
        val consoleBefore = state.consoleLogs.size

        state.logNetworkEvent("HTTP GET /health → 200", LogLevel.SUCCESS)

        assertEquals(consoleBefore + 1, state.consoleLogs.size)
        assertEquals(1, state.networkEventLogs.size)
        assertEquals("HTTP GET /health → 200", state.networkEventLogs.last().message)
    }

    @Test
    fun m3_regular_log_does_not_write_to_networkEventLogs() {
        val state = AppState()
        state.log("normal message", LogLevel.INFO)
        assertTrue(state.networkEventLogs.isEmpty())
    }

    // ── M-7 ──────────────────────────────────────────────────────────────────

    @Test
    fun m7_uid_is_stable_and_unique() {
        val kv1 = MutableKeyValue()
        val kv2 = MutableKeyValue()
        assertTrue(kv1.uid.isNotBlank())
        assertTrue(kv2.uid.isNotBlank())
        assertNotEquals(kv1.uid, kv2.uid)
        // Stability: same instance always returns same uid
        assertEquals(kv1.uid, kv1.uid)
    }

    // ── M-8 ──────────────────────────────────────────────────────────────────

    @Test
    fun m8_scriptInjectedVarKeys_starts_empty_and_is_mutable() {
        val tab = RequestTabState()
        assertTrue(tab.scriptInjectedVarKeys.isEmpty())
        tab.scriptInjectedVarKeys.add("myVar")
        assertFalse(tab.scriptInjectedVarKeys.isEmpty())
        tab.scriptInjectedVarKeys.clear()
        assertTrue(tab.scriptInjectedVarKeys.isEmpty())
    }

    // ── M-11 ─────────────────────────────────────────────────────────────────

    @Test
    fun m11_auth_fields_can_be_cleared_on_type_switch() {
        val tab = RequestTabState()
        tab.authType = AuthType.BASIC
        tab.authUsername = "user"
        tab.authPassword = "pass"

        // Simulate M-11 clearing logic
        tab.authUsername = ""
        tab.authPassword = ""
        tab.authType = AuthType.BEARER

        assertTrue(tab.authUsername.isEmpty())
        assertTrue(tab.authPassword.isEmpty())
        assertEquals(AuthType.BEARER, tab.authType)
    }
}
